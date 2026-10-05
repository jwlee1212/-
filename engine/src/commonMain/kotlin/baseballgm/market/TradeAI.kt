package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.Standings
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.util.eulReul
import baseballgm.util.eunNeun
import baseballgm.util.interpolateAnchors
import kotlin.math.abs
import kotlin.random.Random

/**
 * AI 의 역제안 (2026-10-05 유저 요청 "그 선수는 안 되고 대신 이 선수면 된다").
 * @param notes 비서가 전할 문장들 ("OOO 는 안 된대요. 대신 XXX 어때요?", "여기에 △△ 를 얹어 주면 받겠대요")
 */
data class TradeCounter(val proposal: TradeProposal, val notes: List<String>)

/** 트레이드 판정 결과. 거절 이유를 유저에게 보여 줘야 해서 숫자까지 들고 있는다. */
data class TradeVerdict(
    val accepted: Boolean,
    val reason: String,
    val incomingValue: Double,
    val outgoingValue: Double,
    val requiredValue: Double,
    val problems: List<String> = emptyList(),
) {
    /** 얼마나 모자라는가 (양수면 부족). 화면에서 "조금만 더 얹으면 된다"를 보여 줄 때 쓴다. */
    val shortfall: Double get() = requiredValue - incomingValue
}

/**
 * 협상 피로도 (docs/11 꼼수 방지).
 *
 * 같은 구단에 거절당한 제안을 계속 고쳐 가며 던지면 **수락 기준선을 이진 탐색**할 수 있다.
 * 그래서 거절이 쌓이면 한동안 협상 자체를 거부한다.
 */
class NegotiationFatigue {
    private val rejections = mutableMapOf<Pair<TeamId, TeamId>, Int>()
    private val refuseUntilWeek = mutableMapOf<Pair<TeamId, TeamId>, Int>()

    fun rejectionsOf(from: TeamId, to: TeamId): Int = rejections[from to to] ?: 0

    fun refuses(from: TeamId, to: TeamId, week: Int): Boolean = (refuseUntilWeek[from to to] ?: 0) > week

    fun refusedUntil(from: TeamId, to: TeamId): Int = refuseUntilWeek[from to to] ?: 0

    fun recordRejection(from: TeamId, to: TeamId, week: Int, limit: Int, refusalWeeks: Int) {
        val count = rejectionsOf(from, to) + 1
        rejections[from to to] = count
        if (count >= limit) {
            refuseUntilWeek[from to to] = week + refusalWeeks
            rejections[from to to] = 0
        }
    }

    /** 세이브: [제안 구단, 상대 구단, 값] */
    internal fun export(): Pair<List<Triple<TeamId, TeamId, Int>>, List<Triple<TeamId, TeamId, Int>>> =
        rejections.map { Triple(it.key.first, it.key.second, it.value) } to
            refuseUntilWeek.map { Triple(it.key.first, it.key.second, it.value) }

    internal fun import(saved: List<Triple<TeamId, TeamId, Int>>, refuse: List<Triple<TeamId, TeamId, Int>>) {
        rejections.clear(); saved.forEach { rejections[it.first to it.second] = it.third }
        refuseUntilWeek.clear(); refuse.forEach { refuseUntilWeek[it.first to it.second] = it.third }
    }

    /** 거래가 성사되면 그 상대와의 피로는 풀린다. */
    fun recordAcceptance(from: TeamId, to: TeamId) {
        rejections.remove(from to to)
        refuseUntilWeek.remove(from to to)
    }
}

/**
 * 트레이드 AI (docs/11).
 *
 * 핵심 목표는 **AI 가 호구가 되지 않는 것**이다. 그래서 세 겹으로 막는다.
 *
 * 1. **수익 요구치**: 받는 가치가 주는 가치보다 난이도만큼 더 커야 수락한다
 * 2. **자기 눈으로 평가**: AI 도 스카우트 오차가 섞인 능력치를 본다. 대신 그 오차는 고정이라
 *    "여러 번 제안해서 잘 보이는 조합을 찾는" 짓은 협상 피로도가 막는다
 * 3. **영입 이후 기준 포지션 필요도**: 같은 포지션을 두 명 받으면 두 번째는 가치가 떨어진다
 *
 * 가치는 [Valuation.tradeValue] — 기본 가치(능력치·나이·잠재력) × 팀 상황(모드·필요도·연봉 부담).
 *
 * AI 가 먼저 거는 제안([buildOffer])은 **동기가 있을 때만** 나온다 (2026-10-04, 유저 피드백 "트레이드 제안이 현실성 없다").
 * - 보강([TradeMotive.NEED]): 주전이 비거나 리그 하위 수준인 자리를 메울 선수를 상대에게서 찾고, 자기 쪽 남는 선수·지명권으로 값을 맞춘다
 * - 정리([TradeMotive.SELL]): 리빌딩 구단이 1군 베테랑을 내놓고 상대 유망주·지명권을 받는다
 */
class TradeAI(
    private val balance: BalanceConfig,
    private val valuation: Valuation,
    private val marketView: MarketView,
    private val positionNeed: PositionNeed,
    private val modeResolver: TeamModeResolver,
) {
    private val section = balance.section("trade")
    private val offers = section.section("offers")
    private val pickValue = DraftPickValue(balance)
    private val pickSurplus = balance.double("draftPickValue.firstPickSurplus")
    private val minimumStake = section.double("minValueToConsider")
    private val rosterSpotCost = section.double("rosterSpotCost")
    private val prospectAge = balance.int("valuation.youngPlayerAge")
    private val salaryCap = balance.double("softCap.cap")
    private val salaryWeights = balance.section("valuation").section("trade").numericMap("salaryWeightByPayroll")

    private val needThreshold = offers.double("needThreshold")
    private val minTargetValue = offers.double("minTargetValue")
    private val minAssetValue = offers.double("minAssetValue")
    private val targetsPerTeam = offers.int("targetsPerTeam")
    private val protectedCore = offers.int("protectedCore")
    private val maxPayerPlayers = offers.int("maxPayerPlayers")
    private val maxPayerPicks = offers.int("maxPayerPicks")
    private val maxPickRound = offers.int("maxPickRound")
    private val assetPool = offers.int("assetPool")
    private val fairness = offers.double("fairness")
    private val sellTolerance = offers.double("sellTolerance")
    private val veteranAge = offers.int("veteranAge")
    private val retentionValueRate = section.double("salaryRetention.valueRate")
    private val counterCfg = section.section("counter")

    /**
     * AI 단장 성격 (2026-10-05). 유저 구단(사람 단장)과 단장 정보가 없는 팀은 무난형.
     * 성격 배율은 그 팀이 **보는** 가치에만 걸린다 — 판정하는 쪽의 눈이 바뀐다
     */
    fun styleOf(league: League, teamId: TeamId): baseballgm.model.GmStyle =
        league.generalManagerOf(teamId)?.takeUnless { it.isHuman }?.tradeStyle() ?: baseballgm.model.GmStyle.BALANCED

    private fun styleValue(style: baseballgm.model.GmStyle, key: String): Double = section.double("gmStyles.${style.key}.$key")

    /** 성격이 거절 몇 번 만에 등을 돌리는지를 바꾼다 (기본 + 이 값) */
    fun rejectionsDelta(league: League, teamId: TeamId): Int = section.int("gmStyles.${styleOf(league, teamId).key}.rejectionsDelta")

    fun marginFor(difficulty: String): Double = section.double("profitMargin.$difficulty")

    fun modeOf(league: League, standings: Standings, teamId: TeamId): TeamMode =
        modeResolver.modeOf(league, standings, teamId)

    /** 연봉 총액이 캡에 가까울수록 연봉 1억을 무겁게 본다. */
    fun salaryWeightOf(league: League, teamId: TeamId): Double {
        // 실수 덧셈은 순서에 따라 끝자리가 달라진다. 세이브를 불러와 명단 순서가 바뀌어도 같은 값이 나오게 반올림한다
        val payroll = kotlin.math.round(league.payrollOf(teamId) * PERCENT) / PERCENT
        return interpolateAnchors(salaryWeights, payroll / salaryCap * PERCENT)
    }

    /**
     * 한 묶음의 가치를 [viewer] 의 눈으로 계산한다.
     *
     * 선수는 **받는 순서대로** 포지션 필요도를 다시 계산한다 (docs/11 "영입 이후 상태로 계산").
     */
    fun packageValue(
        league: League,
        standings: Standings,
        viewer: TeamId,
        pack: TradePackage,
        incoming: Boolean,
    ): Double {
        val mode = modeOf(league, standings, viewer)
        val style = styleOf(league, viewer)
        val roster = league.playersOf(viewer).toMutableList()
        val outgoing = if (incoming) emptySet() else pack.playerIds.toSet()
        if (!incoming) roster.removeAll { it.id in outgoing }

        var total = 0.0
        pack.playerIds.forEach { playerId ->
            val player = league.players.firstOrNull { it.id == playerId } ?: return@forEach
            val need = positionNeed.of(roster, player)
            total += playerValue(player, viewer, league, mode, need) * styleMultiplier(style, player, league.season)
            // 받는 선수마다 엔트리 한 자리를 쓴다 — 잡선수 여럿을 얹어 값을 맞추는 거래를 막는다
            if (incoming) total -= rosterSpotCost
            if (incoming) roster += player
        }
        pack.picks.forEach { pick -> total += pickValueOf(league, pick, mode) * styleValue(style, "pickMultiplier") }
        total += pack.cash
        // 연봉 보조: 보내는 쪽이 계약 끝까지 내 주는 돈 — 현금처럼 친다 (받는 쪽엔 이득, 보내는 쪽엔 비용)
        pack.retained.forEach { (playerId, amount) ->
            val years = league.players.firstOrNull { it.id == playerId }?.contract?.yearsRemaining ?: 0
            total += amount * years.coerceAtLeast(1) * retentionValueRate
        }
        return total
    }

    /** 성격 배율: 유망주(어린 선수) / 베테랑. 값이 양수일 때만 의미가 있어서 곱하는 쪽에서 그대로 쓴다 */
    private fun styleMultiplier(style: baseballgm.model.GmStyle, player: Player, season: Int): Double {
        val age = player.ageIn(season)
        return when {
            age <= prospectAge -> styleValue(style, "prospectMultiplier")
            age >= veteranAge -> styleValue(style, "veteranMultiplier")
            else -> 1.0
        }
    }

    fun playerValue(
        player: Player,
        viewer: TeamId,
        league: League,
        mode: TeamMode,
        need: Double = 1.0,
    ): Double = tradeValueOf(player, viewer, league, mode, need).value

    fun tradeValueOf(
        player: Player,
        viewer: TeamId,
        league: League,
        mode: TeamMode,
        need: Double = 1.0,
    ): TradeValue {
        val estimate = marketView.estimate(player, viewer)
        val situation = TeamSituation(mode, need, salaryWeightOf(league, viewer))
        return valuation.tradeValue(player, estimate, league.season, situation)
    }

    fun pickValueOf(league: League, pick: DraftPickRight, mode: TeamMode): Double {
        val seasonsAhead = (pick.season - league.season).coerceAtLeast(0)
        val normalized = pickValue.of(pick, league.teams.size, seasonsAhead) { teamId ->
            league.team(teamId).draftPick
        }
        return normalized * pickSurplus * balance.double("draftPickValue.modeMultiplier.${mode.configKey}")
    }

    /**
     * [judge] 하는 팀의 입장에서 제안을 받아들일지 정한다.
     *
     * 요구치는 `주는 가치 + |주는 가치| × 수익 요구치` 다. 곱셈이 아니라 덧셈으로 둔 이유는,
     * 주는 가치가 음수(악성 계약 떠넘기기)일 때 곱셈이면 요구치가 **낮아지는** 역전이 생기기 때문이다.
     */
    fun judge(
        league: League,
        standings: Standings,
        proposal: TradeProposal,
        judgingTeam: TeamId,
        difficulty: String,
        problems: List<String> = emptyList(),
    ): TradeVerdict {
        if (problems.isNotEmpty()) {
            return TradeVerdict(false, problems.first(), 0.0, 0.0, 0.0, problems)
        }
        val incoming = packageValue(league, standings, judgingTeam, proposal.incomingOf(judgingTeam), incoming = true)
        val outgoing = packageValue(league, standings, judgingTeam, proposal.outgoingOf(judgingTeam), incoming = false)
        // 깐깐한 협상가는 이익을 더 남겨야 받는다 (2026-10-05 단장 성격)
        val margin = marginFor(difficulty) * styleValue(styleOf(league, judgingTeam), "marginMultiplier")
        val required = outgoing + maxOf(abs(outgoing), minimumStake) * margin

        val accepted = incoming >= required
        val reason = when {
            accepted -> "이득이 된다"
            incoming < 0 -> "받는 쪽이 짐이 된다"
            required - incoming < required * NEAR_MISS -> "조금 모자란다"
            else -> "가치 차이가 크다"
        }
        return TradeVerdict(accepted, reason, incoming, outgoing, required)
    }

    /** [viewer] 눈으로 본 받는 가치 ÷ 주는 가치. 1 이면 공정, 1 보다 크면 [viewer] 이득. */
    fun fairnessFor(league: League, standings: Standings, proposal: TradeProposal, viewer: TeamId): Double {
        val incoming = packageValue(league, standings, viewer, proposal.incomingOf(viewer), incoming = true)
        val outgoing = packageValue(league, standings, viewer, proposal.outgoingOf(viewer), incoming = false)
        return when {
            outgoing <= minimumStake -> if (incoming > 0) Double.MAX_VALUE else 0.0
            else -> incoming / outgoing
        }
    }

    /**
     * AI 끼리의 거래 찾기 (docs/11 "AI끼리 시즌당 몇 건"). [buildOffer] 와 같은 동기로 만들고,
     * 양쪽 모두 수익 요구치를 넘겨야 성사된다.
     */
    fun findTrade(
        league: League,
        standings: Standings,
        a: TeamId,
        b: TeamId,
        difficulty: String,
        random: Random,
        legal: (TradeProposal) -> Boolean = { true },
    ): TradeProposal? = buildOffer(league, standings, a, b, difficulty, random, partnerIsAi = true, legal = legal)

    /**
     * AI [proposer] 가 [partner] 에게 거는 제안 하나. 동기(보강·정리)가 없으면 null.
     *
     * - [proposer] 는 언제나 수익 요구치를 넘겨야 한다 (호구 방지)
     * - [partner] 가 AI 면 그쪽도 수익 요구치를 넘겨야 하고, 유저면 **유저 스카우트 시선으로 공정해 보일 만큼**
     *   ([fairness]) 값을 채운다 — 받자마자 거절할 제안은 걸지 않는다
     *
     * @param skip 이번 시즌 이미 제안에 쓴 선수 (같은 제안 반복 방지)
     */
    fun buildOffer(
        league: League,
        standings: Standings,
        proposer: TeamId,
        partner: TeamId,
        difficulty: String,
        random: Random,
        partnerIsAi: Boolean,
        legal: (TradeProposal) -> Boolean = { true },
        skip: (PlayerId) -> Boolean = { false },
    ): TradeProposal? {
        val ctx = OfferContext(league, standings, proposer, partner, difficulty, partnerIsAi, legal, skip)
        TradeMotive.entries.shuffled(random).forEach { motive ->
            val offer = when (motive) {
                TradeMotive.NEED -> buy(ctx, random)
                TradeMotive.SELL -> sell(ctx, random)
            }
            if (offer != null) return offer
        }
        return null
    }

    /**
     * 역제안 (2026-10-05). 유저([TradeProposal.proposer])의 제안을 [aiTeam] 이 거절할 때 "이렇게면 받겠다"를 만든다.
     *
     * ① 요구한 선수 중 **핵심 선수**(그 팀 가치 상위 `protectedCore` 명)는 내주지 않는다 → 같은 자리의 핵심 아닌 선수 중
     *    유저 눈에 가장 값진 선수로 바꾼다 ("그 선수는 안 되고 대신 이 선수")
     * ② 그래도 모자라면 유저 쪽 남는 선수·지명권을 얹는다 ("여기에 △△ 를 얹으면") — 유저가 받는 가치 ÷ 주는 가치가
     *    `counter.userFloor`(깐깐한 협상가는 `hardUserFloor`) 아래로 떨어지는 요구는 하지 않는다
     * 그래도 안 되면 null (역제안 없음).
     */
    fun counterOffer(
        league: League,
        standings: Standings,
        proposal: TradeProposal,
        aiTeam: TeamId,
        difficulty: String,
        legal: (TradeProposal) -> Boolean,
        nameOf: (PlayerId) -> String,
    ): TradeCounter? {
        if (proposal.partner != aiTeam) return null
        val user = proposal.proposer
        val aiMode = modeOf(league, standings, aiTeam)
        val userMode = modeOf(league, standings, user)
        val roster = league.playersOf(aiTeam).sortedBy { it.id.value }
        val core = roster.sortedByDescending { playerValue(it, aiTeam, league, aiMode) }.take(protectedCore).map { it.id }.toSet()
        val notes = mutableListOf<String>()
        var counter = proposal

        proposal.fromPartner.playerIds.filter { it in core }.forEach { star ->
            val starPlayer = league.players.firstOrNull { it.id == star } ?: return@forEach
            val used = counter.fromPartner.playerIds.toSet()
            val substitute = roster
                .filter { offerable(it) && it.id !in core && it.id !in used && samePosition(it, starPlayer) }
                .maxByOrNull { playerValue(it, user, league, userMode) }
            counter = counter.copy(
                fromPartner = counter.fromPartner.copy(
                    playerIds = counter.fromPartner.playerIds.mapNotNull { if (it == star) substitute?.id else it },
                    retained = counter.fromPartner.retained - star,
                ),
            )
            notes += if (substitute != null) {
                "${nameOf(star).eunNeun()} 팀의 핵심이라 안 된대요. 대신 ${nameOf(substitute.id)} 어때요?"
            } else {
                "${nameOf(star).eunNeun()} 팀의 핵심이라 안 된대요."
            }
        }
        // 받을 것이 다 빠졌는데 우리만 주는 꼴이면 역제안이 아니다
        if (proposal.fromPartner.playerIds.isNotEmpty() && counter.fromPartner.playerIds.isEmpty() && counter.fromPartner.picks.isEmpty()) return null

        val accepts = { candidate: TradeProposal -> judge(league, standings, candidate, aiTeam, difficulty).accepted }
        if (accepts(counter) && legal(counter)) return TradeCounter(counter, notes.ifEmpty { listOf("이대로면 받겠대요.") })

        val floor = if (styleOf(league, aiTeam) == baseballgm.model.GmStyle.HARD_BARGAINER) {
            counterCfg.double("hardUserFloor")
        } else {
            counterCfg.double("userFloor")
        }
        val already = counter.fromProposer.playerIds.toSet()
        val assets = assetsOf(league, standings, payer = user, receiver = aiTeam, avoid = null)
            .filter { asset ->
                when (asset) {
                    is Asset.OfPlayer -> asset.player.id !in already
                    is Asset.OfPick -> asset.pick !in counter.fromProposer.picks
                }
            }
        val filled = fill(
            start = counter,
            payerIsProposer = true,
            assets = assets,
            legal = legal,
            goal = accepts,
            limit = { fairnessFor(league, standings, it, user) >= floor },
        ) ?: return null
        val addedPlayers = filled.fromProposer.playerIds - counter.fromProposer.playerIds.toSet()
        val addedPicks = filled.fromProposer.picks - counter.fromProposer.picks.toSet()
        val added = addedPlayers.map(nameOf) + addedPicks.map { it.label() }
        if (added.isNotEmpty()) notes += "여기에 ${added.joinToString(", ").eulReul()} 얹어 주면 받겠대요."
        return TradeCounter(filled, notes)
    }

    private class OfferContext(
        val league: League,
        val standings: Standings,
        val proposer: TeamId,
        val partner: TeamId,
        val difficulty: String,
        val partnerIsAi: Boolean,
        val legal: (TradeProposal) -> Boolean,
        val skip: (PlayerId) -> Boolean,
    )

    private fun proposerAccepts(ctx: OfferContext, proposal: TradeProposal): Boolean =
        judge(ctx.league, ctx.standings, proposal, ctx.proposer, ctx.difficulty).accepted

    private fun partnerSatisfied(ctx: OfferContext, proposal: TradeProposal, tolerance: Double): Boolean =
        if (ctx.partnerIsAi) {
            judge(ctx.league, ctx.standings, proposal, ctx.partner, ctx.difficulty).accepted
        } else {
            fairnessFor(ctx.league, ctx.standings, proposal, ctx.partner) >= tolerance
        }

    /** 보강: 우리 구멍 자리를 메울 상대 1군 선수를 데려오고, 남는 선수·지명권으로 값을 맞춘다. */
    private fun buy(ctx: OfferContext, random: Random): TradeProposal? {
        val league = ctx.league
        val mode = modeOf(league, ctx.standings, ctx.proposer)
        val roster = league.playersOf(ctx.proposer)
        val targets = league.playersOf(ctx.partner).sortedBy { it.id.value }.asSequence()
            .filter { offerable(it) && it.rosterLevel == RosterLevel.FIRST_TEAM && !ctx.skip(it.id) }
            .map { it to positionNeed.of(roster, it) }
            .filter { (_, need) -> need >= needThreshold }
            .map { (player, need) -> player to playerValue(player, ctx.proposer, league, mode, need) }
            .filter { (_, value) -> value >= minTargetValue }
            .sortedByDescending { it.second }
            .take(targetsPerTeam)
            .map { it.first }
            .toList()
            .shuffled(random)

        targets.forEach { target ->
            val start = TradeProposal(
                proposer = ctx.proposer,
                partner = ctx.partner,
                fromPartner = TradePackage(playerIds = listOf(target.id)),
                reason = TradeReason(TradeMotive.NEED, target.id, mode == TeamMode.CONTEND),
            )
            val assets = assetsOf(league, ctx.standings, payer = ctx.proposer, receiver = ctx.partner, avoid = target)
            fill(
                start = start,
                payerIsProposer = true,
                assets = assets,
                legal = ctx.legal,
                goal = { partnerSatisfied(ctx, it, fairness) },
                limit = { proposerAccepts(ctx, it) },
            )?.let { return it }
        }
        return null
    }

    /** 정리: 리빌딩 구단이 1군 베테랑을 내놓고 상대 유망주·지명권을 받는다. */
    private fun sell(ctx: OfferContext, random: Random): TradeProposal? {
        val league = ctx.league
        if (modeOf(league, ctx.standings, ctx.proposer) != TeamMode.REBUILD) return null
        val buyerMode = modeOf(league, ctx.standings, ctx.partner)
        if (buyerMode == TeamMode.REBUILD) return null
        val buyerRoster = league.playersOf(ctx.partner)
        val veterans = league.playersOf(ctx.proposer).sortedBy { it.id.value }.asSequence()
            .filter { offerable(it) && it.rosterLevel == RosterLevel.FIRST_TEAM && !ctx.skip(it.id) }
            .filter { it.ageIn(league.season) >= veteranAge }
            .map { it to positionNeed.of(buyerRoster, it) }
            .filter { (_, need) -> need >= 1.0 }
            .map { (player, need) -> player to playerValue(player, ctx.partner, league, buyerMode, need) }
            .filter { (_, value) -> value >= minTargetValue }
            .sortedByDescending { it.second }
            .take(targetsPerTeam)
            .map { it.first }
            .toList()
            .shuffled(random)

        veterans.forEach { veteran ->
            val start = TradeProposal(
                proposer = ctx.proposer,
                partner = ctx.partner,
                fromProposer = TradePackage(playerIds = listOf(veteran.id)),
                reason = TradeReason(TradeMotive.SELL, veteran.id, contending = false),
            )
            // 리빌딩 구단은 상대 유망주·지명권을 원한다
            val assets = assetsOf(league, ctx.standings, payer = ctx.partner, receiver = ctx.proposer, avoid = null)
                .filter { asset -> asset !is Asset.OfPlayer || asset.player.ageIn(league.season) <= prospectAge }
            fill(
                start = start,
                payerIsProposer = false,
                assets = assets,
                legal = ctx.legal,
                goal = { proposerAccepts(ctx, it) },
                limit = { partnerSatisfied(ctx, it, sellTolerance) },
            )?.let { return it }
        }
        return null
    }

    private sealed interface Asset {
        data class OfPlayer(val player: Player) : Asset
        data class OfPick(val pick: DraftPickRight) : Asset
    }

    /**
     * [payer] 가 내놓을 수 있는 것: 핵심 선수([protectedCore] 명)는 빼고, 내보내면 구멍이 나는 자리도 뺀다.
     * [receiver] 눈에 값이 큰 순으로 [assetPool] 개 + 앞 라운드 지명권.
     */
    private fun assetsOf(league: League, standings: Standings, payer: TeamId, receiver: TeamId, avoid: Player?): List<Asset> {
        val payerMode = modeOf(league, standings, payer)
        val receiverMode = modeOf(league, standings, receiver)
        // 세이브를 불러오면 목록 순서가 달라질 수 있어서, 같은 값일 때의 순서까지 id 로 고정한다
        val roster = league.playersOf(payer).sortedBy { it.id.value }
        val core = roster.sortedByDescending { playerValue(it, payer, league, payerMode) }.take(protectedCore).map { it.id }.toSet()
        val players = roster.asSequence()
            .filter { offerable(it) && it.id !in core && it.id != avoid?.id }
            .filter { avoid == null || !samePosition(it, avoid) }
            .filter { player -> positionNeed.of(roster - player, player) < needThreshold }
            .map { it to playerValue(it, receiver, league, receiverMode) }
            .filter { (_, value) -> value >= minAssetValue }
            .sortedByDescending { it.second }
            .take(assetPool)
            .map { Asset.OfPlayer(it.first) }
            .toList()
        val picks = league.draftRights.ofOwner(payer)
            .filter { it.round <= maxPickRound }
            .sortedWith(compareBy({ it.season }, { it.round }, { it.originalTeam.value }))
            .map { Asset.OfPick(it) }
        return players + picks
    }

    /**
     * [start] 에 [assets] 를 하나씩 얹어 [goal] 을 채운다. 얹을 때마다 [limit] (내는 쪽이 아직 괜찮은가) 를 지킨다.
     * 한 번에 채워지는 게 있으면 그중 내는 쪽에 가장 덜 아까운 것, 없으면 받는 쪽 눈에 가장 값진 것을 얹는다.
     */
    private fun fill(
        start: TradeProposal,
        payerIsProposer: Boolean,
        assets: List<Asset>,
        legal: (TradeProposal) -> Boolean,
        goal: (TradeProposal) -> Boolean,
        limit: (TradeProposal) -> Boolean,
    ): TradeProposal? {
        var proposal = start
        val remaining = assets.toMutableList()
        repeat(maxPayerPlayers + maxPayerPicks + 1) {
            if (goal(proposal)) return proposal.takeIf { limit(it) && legal(it) }
            val pack = if (payerIsProposer) proposal.fromProposer else proposal.fromPartner
            val options = remaining
                .filter { asset ->
                    when (asset) {
                        is Asset.OfPlayer -> pack.playerIds.size < maxPayerPlayers
                        is Asset.OfPick -> pack.picks.size < maxPayerPicks
                    }
                }
                .map { asset -> asset to proposal.adding(asset, payerIsProposer) }
                .filter { (_, next) -> limit(next) && legal(next) }
            if (options.isEmpty()) return null
            val closing = options.filter { (_, next) -> goal(next) }
            val (asset, next) = if (closing.isNotEmpty()) {
                closing.minBy { (asset, _) -> assetOrder(asset, remaining) }
            } else {
                options.minBy { (asset, _) -> remaining.indexOf(asset) }
            }
            remaining.remove(asset)
            proposal = next
        }
        return proposal.takeIf { goal(it) && limit(it) && legal(it) }
    }

    /** 채워지는 후보 중 고를 순서: 지명권은 뒤 라운드부터, 선수는 값이 작은 쪽부터 (목록 뒤쪽이 값이 작다). */
    private fun assetOrder(asset: Asset, remaining: List<Asset>): Int = when (asset) {
        is Asset.OfPick -> -asset.pick.round * PICK_ORDER_SCALE - asset.pick.season
        is Asset.OfPlayer -> -remaining.indexOf(asset)
    }

    private fun TradeProposal.adding(asset: Asset, toProposer: Boolean): TradeProposal {
        val pack = if (toProposer) fromProposer else fromPartner
        val added = when (asset) {
            is Asset.OfPlayer -> pack.copy(playerIds = pack.playerIds + asset.player.id)
            is Asset.OfPick -> pack.copy(picks = pack.picks + asset.pick)
        }
        return if (toProposer) copy(fromProposer = added) else copy(fromPartner = added)
    }

    private fun offerable(player: Player): Boolean =
        player.military.isAvailable && player.contract.yearsRemaining > 0 && !player.condition.isInjured

    private fun samePosition(a: Player, b: Player): Boolean = when {
        a is baseballgm.model.Batter && b is baseballgm.model.Batter -> a.primaryPosition == b.primaryPosition
        a is baseballgm.model.Pitcher && b is baseballgm.model.Pitcher -> a.role.isReliever == b.role.isReliever
        else -> false
    }

    private companion object {
        const val NEAR_MISS = 0.25
        const val PERCENT = 100.0
        const val PICK_ORDER_SCALE = 100
    }
}
