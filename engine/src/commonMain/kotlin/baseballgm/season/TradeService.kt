package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.market.MarketView
import baseballgm.market.PositionNeed
import baseballgm.market.TeamMode
import baseballgm.market.TeamModeResolver
import baseballgm.market.TradeAI
import baseballgm.market.TradeExecutor
import baseballgm.market.TradeProposal
import baseballgm.market.TradeRules
import baseballgm.market.TradeVerdict
import baseballgm.market.Valuation
import baseballgm.model.TeamId
import baseballgm.util.chance
import kotlin.random.Random

/** 성사된 트레이드 한 건의 알림 문구. */
data class TradeNews(val teams: Pair<TeamId, TeamId>, val text: String)

/**
 * 시즌 중 트레이드 (docs/11).
 *
 * 트레이드는 리그 데이터를 통째로 바꾸는 일이라, 시즌 중에는 [SeasonState] 쪽 값(소속·지명권·자금)을
 * 고치고 리그 원본은 건드리지 않는다. 스토브리그가 시즌 상태를 읽어 다음 리그를 만든다.
 */
class TradeService(
    private val balance: BalanceConfig,
    private val strength: StrengthCalculator,
) {
    private val valuation = Valuation(balance, baseballgm.development.AgingCurves(balance))
    private val marketView = MarketView(balance, strength)
    private val positionNeed = PositionNeed(balance, strength)
    private val modeResolver = TeamModeResolver(balance, strength)

    val rules: TradeRules = TradeRules(balance)
    val ai: TradeAI = TradeAI(balance, valuation, marketView, positionNeed, modeResolver)

    private val section = balance.section("trade")
    private val tradesPerSeason = section.intRange("aiTradesPerSeason")
    private val offers = section.section("offers")

    fun modeOf(state: SeasonState, teamId: TeamId): TeamMode =
        modeResolver.modeOf(state.currentLeague(), state.standings, teamId)

    /** 이 주에 트레이드를 할 수 있는가. 마감 주차까지만 가능하다 (docs/11). */
    fun isOpen(state: SeasonState): Boolean = state.week <= state.calendar.tradeDeadlineWeek

    /** 제안을 검토한다. 실제로 반영하지는 않는다 (화면이 미리 보여줄 때 쓴다). */
    fun evaluate(state: SeasonState, proposal: TradeProposal, difficulty: String): TradeVerdict {
        val league = state.currentLeague()
        val partner = proposal.partner
        if (state.negotiationFatigue.refuses(proposal.proposer, partner, state.week)) {
            return TradeVerdict(
                accepted = false,
                reason = "지금은 협상하지 않겠다고 한다 (${state.negotiationFatigue.refusedUntil(proposal.proposer, partner)}주차까지)",
                incomingValue = 0.0,
                outgoingValue = 0.0,
                requiredValue = 0.0,
            )
        }
        if (!isOpen(state)) {
            return TradeVerdict(false, "트레이드 마감이 지났다", 0.0, 0.0, 0.0)
        }
        val problems = rules.problems(league, proposal, state.week, state.draftResult != null, state.tradeHistory)
        return ai.judge(league, state.standings, proposal, partner, difficulty, problems)
    }

    /**
     * 제안을 넣는다. 받아들여지면 즉시 반영하고, 거절되면 **협상 피로도가 쌓인다**.
     * 같은 상대에게 조금씩 고쳐 가며 반복하면 한동안 협상을 거부당한다 (docs/11 꼼수 방지).
     */
    fun propose(state: SeasonState, proposal: TradeProposal, difficulty: String): TradeVerdict {
        val verdict = evaluate(state, proposal, difficulty)
        if (verdict.accepted) {
            apply(state, proposal)
            state.negotiationFatigue.recordAcceptance(proposal.proposer, proposal.partner)
        } else if (verdict.problems.isEmpty()) {
            state.negotiationFatigue.recordRejection(
                from = proposal.proposer,
                to = proposal.partner,
                week = state.week,
                // 단장 성격: 깐깐한 협상가는 더 빨리, 즉시 전력형은 조금 늦게 등을 돌린다 (2026-10-05)
                limit = (rules.rejectionsBeforeRefusal + ai.rejectionsDelta(state.currentLeague(), proposal.partner)).coerceAtLeast(1),
                refusalWeeks = rules.refusalWeeks,
            )
        }
        return verdict
    }

    /**
     * 역제안 (2026-10-05). 유저 제안이 거절됐을 때 상대가 "이렇게면 받겠다"는 조건. 없으면 null.
     * 마감이 지났거나 규칙에 걸리는 제안에는 역제안하지 않는다
     */
    fun counterFor(state: SeasonState, proposal: TradeProposal, difficulty: String): baseballgm.market.TradeCounter? {
        if (!isOpen(state)) return null
        val league = state.currentLeague()
        if (rules.problems(league, proposal, state.week, state.draftResult != null, state.tradeHistory).isNotEmpty()) return null
        val legal = { candidate: TradeProposal ->
            rules.problems(league, candidate, state.week, state.draftResult != null, state.tradeHistory).isEmpty()
        }
        val nameOf = { id: baseballgm.model.PlayerId -> runCatching { state.player(id).registeredName }.getOrDefault(id.value) }
        return ai.counterOffer(league, state.standings, proposal, proposal.partner, difficulty, legal, nameOf)
    }

    /**
     * 상대가 낸 역제안을 받아들인다. 상대가 만든 조건이라 협상 피로도(거절 끝 협상 거부)와 상관없이 다시 판정만 한다 —
     * 그사이 상황이 바뀌어 더는 이득이 아니면 성사되지 않는다
     */
    fun acceptCounter(state: SeasonState, counter: TradeProposal, difficulty: String): TradeVerdict {
        if (!isOpen(state)) return TradeVerdict(false, "트레이드 마감이 지났다", 0.0, 0.0, 0.0)
        val league = state.currentLeague()
        val problems = rules.problems(league, counter, state.week, state.draftResult != null, state.tradeHistory)
        val verdict = ai.judge(league, state.standings, counter, counter.partner, difficulty, problems)
        if (verdict.accepted) {
            apply(state, counter)
            state.negotiationFatigue.recordAcceptance(counter.proposer, counter.partner)
        }
        return verdict
    }

    /** 거래를 시즌 상태에 반영한다. */
    fun apply(state: SeasonState, proposal: TradeProposal): String {
        val league = state.currentLeague()
        val outcome = TradeExecutor.apply(league, proposal, state.week)

        proposal.fromProposer.playerIds.forEach { state.movePlayer(it, proposal.partner) }
        proposal.fromPartner.playerIds.forEach { state.movePlayer(it, proposal.proposer) }
        state.draftRights = outcome.league.draftRights
        state.tradeHistory = outcome.league.tradeHistory
        state.retainedSalaries = outcome.league.retainedSalaries
        outcome.league.teams.forEach { state.funds[it.id] = it.operatingFunds }
        return outcome.summary
    }

    /**
     * AI 끼리의 거래 (docs/11 "AI끼리 시즌당 몇 건").
     *
     * 매주 낮은 확률로 두 구단을 붙여 본다. 양쪽 모두 수익 요구치를 넘겨야 성사되므로
     * 대부분은 그냥 무산된다 — 그래서 시즌당 몇 건 정도만 나온다.
     */
    fun runAiTrades(
        state: SeasonState,
        random: Random,
        difficulty: String,
        userTeam: TeamId?,
    ): List<TradeNews> {
        if (!isOpen(state)) return emptyList()
        if (state.aiTradeCount >= tradesPerSeason.last) return emptyList()
        if (!random.chance(WEEKLY_ATTEMPT_CHANCE)) return emptyList()

        val league = state.currentLeague()
        val candidates = league.teams.map { it.id }
            .filter { it != userTeam && it !in state.aiTradedTeams }
        if (candidates.size < 2) return emptyList()

        // 짝을 몇 번 바꿔 가며 맞는 거래를 찾는다. 양쪽 다 이득이어야 해서 대부분은 무산된다
        repeat(PAIRS_PER_WEEK) {
            val pair = candidates.shuffled(random).take(2)
            val proposal = ai.findTrade(league, state.standings, pair[0], pair[1], difficulty, random) { proposal ->
                rules.problems(league, proposal, state.week, state.draftResult != null, state.tradeHistory).isEmpty()
            } ?: return@repeat
            state.aiTradeCount += 1
            state.aiTradedTeams += pair
            return listOf(TradeNews(pair[0] to pair[1], apply(state, proposal)))
        }
        return emptyList()
    }

    /**
     * AI 가 유저에게 거는 제안 (docs/11 "AI → 유저 제안(알림함)").
     * 유저가 수락하면 그대로 성사된다 — AI 쪽은 이미 이득이라고 판단한 거래다.
     *
     * 마감이 가까울수록 자주 온다. 구단을 섞어 차례로 보며 **동기(보강·정리)가 있는 첫 구단**이 건다.
     * 같은 시즌에 같은 구단이 같은 선수로 다시 걸지 않는다.
     */
    fun aiProposalToUser(
        state: SeasonState,
        userTeam: TeamId,
        random: Random,
        difficulty: String,
    ): TradeProposal? {
        if (!isOpen(state)) return null
        val weeksLeft = state.calendar.tradeDeadlineWeek - state.week
        val chance = if (weeksLeft < offers.int("deadlineWindowWeeks")) offers.double("deadlineChance") else offers.double("chance")
        if (!random.chance(chance)) return null
        val league = state.currentLeague()
        val legal = { proposal: TradeProposal ->
            rules.problems(league, proposal, state.week, state.draftResult != null, state.tradeHistory).isEmpty()
        }
        league.teams.map { it.id }.filter { it != userTeam }.sortedBy { it.value }.shuffled(random).forEach { partner ->
            val proposal = ai.buildOffer(
                league = league,
                standings = state.standings,
                proposer = partner,
                partner = userTeam,
                difficulty = difficulty,
                random = random,
                partnerIsAi = false,
                legal = legal,
                skip = { id -> offerKey(state, partner, id) in state.incidentKeys },
            ) ?: return@forEach
            proposal.reason?.let { state.incidentKeys += offerKey(state, partner, it.playerId) }
            return proposal
        }
        return null
    }

    private fun offerKey(state: SeasonState, team: TeamId, player: baseballgm.model.PlayerId) =
        "trade-offer:${state.season}:${team.value}:${player.value}"

    private companion object {
        const val WEEKLY_ATTEMPT_CHANCE = 0.45
        const val PAIRS_PER_WEEK = 4
    }
}
