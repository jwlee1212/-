package baseballgm.season

import baseballgm.events.Incident
import baseballgm.events.IncidentEffect
import baseballgm.events.IncidentRecord
import baseballgm.events.NewsDesk
import baseballgm.events.NewsItem
import baseballgm.events.NewsKind
import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.management.FanSentiment
import baseballgm.model.RosterLevel
import baseballgm.development.withRatings
import kotlin.math.roundToInt

/**
 * 돌발 이벤트에 답한다 (docs/07·16 주중 개입).
 *
 * 고른 선택지의 효과를 시즌 상태에 그대로 적용하고, 언론에 나갈 말이 있으면 뉴스로, 팬 반응은 주말 SNS 로 넘긴다.
 * **난수를 쓰지 않는다** — 같은 답이면 항상 같은 결과다.
 *
 * 답하기 전에 상황이 바뀌었을 수 있다(그사이 트레이드로 선수가 떠났다든가). 그럴 땐 할 수 없는 효과만 건너뛴다.
 */
class IncidentResolver(balance: BalanceConfig, strength: StrengthCalculator) {

    private val roster = RosterActions(balance)
    private val trades = TradeService(balance, strength)
    private val fans = FanSentiment(balance)
    private val formMin = balance.int("form.min")
    private val formMax = balance.int("form.max")
    private val playThroughPenalty = balance.int("incidents.injury.playThroughFormPenalty")
    private val playThroughRelapse = balance.int("incidents.injury.playThroughRelapseWeeks")
    private val morale = baseballgm.management.MoraleService(balance, strength)
    private val trialBoost = balance.int("incidents.opportunity.prospectTrial.ratingBoost")
    private val trialAttributes = balance.int("incidents.opportunity.prospectTrial.boostAttributes")
    private val ratingMax = baseballgm.model.RATING_MAX

    /**
     * @param delegated 비서에게 맡겼는가 (추천 선택지를 고른 것과 결과는 같고, 기록만 다르다)
     * @return 비서의 후속 한마디. 그런 선택지가 없으면 null
     */
    fun resolve(state: SeasonState, incidentId: String, optionId: String, delegated: Boolean = false): IncidentRecord? {
        val incident = state.pendingIncidents.firstOrNull { it.id == incidentId } ?: return null
        val option = incident.option(optionId) ?: return null
        state.pendingIncidents.remove(incident)

        // 효과를 적용하기 전에 기준값을 찍는다 — 결정 성적표가 "그 뒤로" 바뀐 것을 센다 (재미 개선 2번)
        val baseline = baselineOf(state, incident, option)
        option.effects.forEach { apply(state, incident, it, delegated) }

        option.quote?.let { quote ->
            val gm = state.league.management.career?.gmName ?: "단장"
            val team = state.league.team(incident.teamId)
            state.news += NewsItem(
                season = state.season,
                week = incident.week,
                kind = NewsKind.FRONT_OFFICE,
                headline = "[단장 인터뷰] ${team.name} $gm 단장 \"$quote\"",
                teams = listOf(incident.teamId),
                outlet = NewsDesk.OUTLETS[(incident.id.hashCode() and Int.MAX_VALUE) % NewsDesk.OUTLETS.size],
                lead = "${incident.headline}에 대해 ${team.name} 프런트가 입장을 밝혔다.",
            )
        }

        val record = IncidentRecord(
            season = state.season,
            week = incident.week,
            day = incident.day,
            kind = incident.kind,
            headline = incident.headline,
            choice = option.label,
            summary = option.followUp,
            delegated = delegated,
            reactions = option.reactions,
            playerId = incident.playerId,
            baseline = baseline,
        )
        state.incidentLog += record
        state.inbox.add(
            week = state.week,
            category = InboxCategory.DECISION,
            teamId = incident.teamId,
            text = "${incident.headline} → ${option.label}" + if (delegated) " (비서 처리)" else "",
        )
        return record
    }

    /**
     * 결정 순간의 기준값 (재미 개선 2번, 2026-10-03 "관련 지표만" 개정).
     *
     * 고른 선택이 **무엇을 건드리는지**([baseballgm.events.DecisionFocus])를 효과에서 읽어 함께 남긴다. 성적표는 그것만 보여 준다.
     * 선수는 역할을 나눈다: 효과가 직접 건드리는 선수(콜업·영입·유망주 등)가 주인공, 트레이드면 받은/보낸(거절이면 지킨/놓친) 선수.
     * 아무 효과 없는 선택(더 지켜본다 등)은 이벤트 대상 선수를 주인공으로 남겨 "그 뒤 그 선수는 어땠나"를 본다.
     */
    private fun baselineOf(state: SeasonState, incident: Incident, option: baseballgm.events.IncidentOption): baseballgm.events.DecisionBaseline {
        val team = incident.teamId
        val record = state.standings.record(team)
        val focus = linkedSetOf<baseballgm.events.DecisionFocus>()
        val roles = linkedMapOf<baseballgm.model.PlayerId, baseballgm.events.BaselineRole>()
        fun role(id: baseballgm.model.PlayerId, role: baseballgm.events.BaselineRole) { if (id !in roles) roles[id] = role }
        val subject = baseballgm.events.BaselineRole.SUBJECT

        // 이 이벤트가 걸고 있는 거래 (수락하면 성사될 묶음). 거절해도 "지킨 선수·놓친 선수"를 보려고 찾아 둔다
        val trade = incident.options.flatMap { it.effects }.filterIsInstance<IncidentEffect.ExecuteTrade>().firstOrNull()?.proposal
            ?: state.pendingTradeOffer?.takeIf { incident.kind == baseballgm.events.IncidentKind.TRADE_OFFER }
        option.effects.forEach { effect ->
            when (effect) {
                is IncidentEffect.Promote -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.Demote -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.InjuredList -> focus += baseballgm.events.DecisionFocus.PLAYER
                is IncidentEffect.PlayThrough -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.DelayReturn -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.Rest -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.PlayerForm -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.AcquirePlayer -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.RatingBoost -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.ExtendContract -> { focus += baseballgm.events.DecisionFocus.CONTRACT; role(effect.playerId, subject) }
                // 기억은 다른 효과에 딸린 것이라 초점이 아니다. 약속·이적 시장은 그 선수를 본다
                is IncidentEffect.Memory -> Unit
                is IncidentEffect.Promise -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.TransferList -> { focus += baseballgm.events.DecisionFocus.PLAYER; role(effect.playerId, subject) }
                is IncidentEffect.TeamForm -> focus += baseballgm.events.DecisionFocus.TEAM_FORM
                is IncidentEffect.TeamFatigue -> focus += baseballgm.events.DecisionFocus.TEAM_FATIGUE
                is IncidentEffect.Fan -> focus += baseballgm.events.DecisionFocus.FAN
                is IncidentEffect.OwnerTrust -> focus += baseballgm.events.DecisionFocus.OWNER_TRUST
                is IncidentEffect.PolicyThisWeek -> focus += baseballgm.events.DecisionFocus.POLICY
                IncidentEffect.AcceptTrade, is IncidentEffect.ExecuteTrade -> focus += baseballgm.events.DecisionFocus.TRADE_ACCEPTED
                IncidentEffect.RejectTrade -> focus += baseballgm.events.DecisionFocus.TRADE_DECLINED
            }
        }
        if (trade != null) {
            val incoming = trade.incomingOf(team).playerIds
            val outgoing = trade.outgoingOf(team).playerIds
            if (baseballgm.events.DecisionFocus.TRADE_ACCEPTED in focus) {
                incoming.forEach { role(it, baseballgm.events.BaselineRole.ACQUIRED) }
                outgoing.forEach { role(it, baseballgm.events.BaselineRole.DEPARTED) }
            } else {
                // 거절이든 미루기든 이 자리에서 성사되지 않았다 → 지킨 선수 vs 놓친 선수
                focus += baseballgm.events.DecisionFocus.TRADE_DECLINED
                outgoing.forEach { role(it, baseballgm.events.BaselineRole.KEPT) }
                incoming.forEach { role(it, baseballgm.events.BaselineRole.MISSED) }
            }
        }
        // 부상자 명단 + 콜업이면 콜업된 선수가 주인공이고, 다친 선수는 뒤에 둔다
        incident.playerId?.let { role(it, subject) }
        if (focus.isEmpty()) focus += baseballgm.events.DecisionFocus.NOTHING

        val rival = state.league.team(team).rival
        val (rivalWins, rivalLosses) = rival?.let { state.headToHeadOf(team, it) } ?: (0 to 0)
        val firstTeam = state.firstTeamOf(team)
        return baseballgm.events.DecisionBaseline(
            wins = record.wins,
            losses = record.losses,
            ties = record.ties,
            runsScored = record.runsScored,
            runsAllowed = record.runsAllowed,
            fanSupport = state.fanSupportOf(team),
            ownerTrust = state.ownerTrustOf(team),
            players = roles.mapNotNull { (id, role) ->
                val player = runCatching { state.player(id) }.getOrNull() ?: return@mapNotNull null
                baseballgm.events.PlayerBaseline(
                    playerId = player.id,
                    batting = state.stats.battingOf(player.id).total,
                    pitching = state.stats.pitchingOf(player.id).total,
                    fatigue = player.condition.fatigue,
                    injured = player.condition.isInjured,
                    role = role,
                    futuresBatting = state.stats.futuresBattingOf(player.id),
                    futuresPitching = state.stats.futuresPitchingOf(player.id),
                    // 우리 선수만 능력치를 남긴다 — 타 팀 선수의 진짜 값은 기록에 남기지 않는다 (불변 원칙 4)
                    overall = if (player.teamId == team) kotlin.math.round(player.ratingsMap().values.average()).toInt() else -1,
                    salary = player.contract.salary,
                    yearsRemaining = player.contract.yearsRemaining,
                )
            },
            focus = focus.toList(),
            streak = record.streak,
            teamForm = if (firstTeam.isEmpty()) 0 else firstTeam.map { it.condition.form }.average().roundToInt(),
            teamFatigue = if (firstTeam.isEmpty()) 0 else firstTeam.map { it.condition.fatigue }.average().roundToInt(),
            rival = rival,
            rivalWins = rivalWins,
            rivalLosses = rivalLosses,
        )
    }

    /** @param delegated 비서 처리면 콜업한 선수를 "단장이 고른 선수"로 보호하지 않는다 — 다음 주 베스트 맞추기가 정리한다 */
    private fun apply(state: SeasonState, incident: Incident, effect: IncidentEffect, delegated: Boolean) {
        val team = incident.teamId
        when (effect) {
            is IncidentEffect.Promote -> {
                val swap = effect.swapOut
                val picked = !delegated
                if (swap != null && state.player(swap).rosterLevel == RosterLevel.FIRST_TEAM) {
                    if (roster.swap(state, team, effect.playerId, swap, picked).isEmpty()) roster.promote(state, team, effect.playerId, picked)
                } else {
                    roster.promote(state, team, effect.playerId, picked)
                }
            }
            is IncidentEffect.Demote -> roster.demote(state, team, effect.playerId)
            is IncidentEffect.InjuredList -> roster.placeOnInjuredList(state, team, effect.playerId)
            is IncidentEffect.PlayThrough -> {
                val player = ownPlayer(state, team, effect.playerId) ?: return
                if (player.condition.injury == null) return
                state.update(
                    player.withCondition(
                        player.condition.copy(
                            injury = null,
                            relapseRiskWeeks = maxOf(player.condition.relapseRiskWeeks, playThroughRelapse),
                            form = (player.condition.form - playThroughPenalty).coerceIn(formMin, formMax),
                        ),
                    ),
                )
            }
            is IncidentEffect.DelayReturn -> {
                val player = ownPlayer(state, team, effect.playerId) ?: return
                if (player.rosterLevel != RosterLevel.FUTURES) return
                // 다음 주부터 올라올 수 있고, 그 주에는 다시 묻지 않는다 (엔트리 자동 정리가 올린다)
                val nextWeek = state.week + 1
                state.roster.markRehab(player.id, nextWeek)
                state.incidentKeys += "${IncidentDesk.RETURN_DELAYED}:${state.season}:${player.id.value}:$nextWeek"
                state.update(
                    player.withCondition(
                        player.condition.copy(relapseRiskWeeks = (player.condition.relapseRiskWeeks - effect.relapseCut).coerceAtLeast(0)),
                    ),
                )
            }
            is IncidentEffect.Rest -> if (ownPlayer(state, team, effect.playerId) != null) state.restingThisWeek += effect.playerId
            is IncidentEffect.PlayerForm -> {
                val player = ownPlayer(state, team, effect.playerId) ?: return
                state.update(player.withCondition(player.condition.copy(form = (player.condition.form + effect.delta).coerceIn(formMin, formMax))))
            }
            is IncidentEffect.TeamForm -> state.firstTeamOf(team).forEach { player ->
                state.update(player.withCondition(player.condition.copy(form = (player.condition.form + effect.delta).coerceIn(formMin, formMax))))
            }
            is IncidentEffect.TeamFatigue -> state.firstTeamOf(team).forEach { player ->
                state.update(player.withCondition(player.condition.copy(fatigue = (player.condition.fatigue + effect.delta).coerceIn(0, MAX_FATIGUE))))
            }
            is IncidentEffect.Fan -> state.fanSupport[team] =
                fans.nudge(state.fanSupportOf(team), effect.delta.toDouble(), state.league.team(team).fanVolatility)
            is IncidentEffect.OwnerTrust -> state.ownerTrust[team] =
                (state.ownerTrustOf(team) + effect.delta).coerceIn(0, MAX_TRUST)
            is IncidentEffect.PolicyThisWeek -> {
                if (team !in state.policyToRestore) state.policyToRestore[team] = state.policyOf(team)
                state.policies[team] = effect.policy
            }
            IncidentEffect.AcceptTrade -> {
                val offer = state.pendingTradeOffer ?: return
                // 그사이 선수가 떠났으면 성사시킬 수 없다
                val stillThere = (offer.fromProposer.playerIds + offer.fromPartner.playerIds).all { id ->
                    runCatching { state.player(id).teamId }.getOrNull().let { it == offer.proposer || it == offer.partner }
                }
                if (stillThere) trades.apply(state, offer)
                state.pendingTradeOffer = null
                state.pendingTradeOfferWeek = null
            }
            IncidentEffect.RejectTrade -> {
                state.pendingTradeOffer = null
                state.pendingTradeOfferWeek = null
            }
            is IncidentEffect.ExecuteTrade -> {
                val proposal = effect.proposal
                // 그사이 선수가 떠났거나 마감이 지났으면 성사시킬 수 없다
                val stillThere = proposal.fromProposer.playerIds.all { runCatching { state.player(it).teamId }.getOrNull() == proposal.proposer } &&
                    proposal.fromPartner.playerIds.all { runCatching { state.player(it).teamId }.getOrNull() == proposal.partner }
                if (stillThere && trades.isOpen(state)) trades.apply(state, proposal)
            }
            is IncidentEffect.AcquirePlayer -> {
                val player = runCatching { state.player(effect.playerId) }.getOrNull() ?: return
                if (player.teamId != null && player.teamId != team) state.movePlayer(player.id, team)
            }
            is IncidentEffect.RatingBoost -> {
                val player = ownPlayer(state, team, effect.playerId) ?: return
                // 잠재력과 차이가 가장 큰 능력치부터. 잠재력을 넘기지 않는다
                val ratings = player.ratingsMap()
                val boosted = ratings.keys
                    .sortedByDescending { (player.hidden.potential[it] ?: 0) - ratings.getValue(it) }
                    .take(effect.attributes ?: trialAttributes)
                    .associateWith { attribute ->
                        val cap = minOf(player.hidden.potential[attribute] ?: ratingMax, ratingMax)
                        maxOf(ratings.getValue(attribute), minOf(ratings.getValue(attribute) + (effect.amount ?: trialBoost), cap))
                    }
                state.update(player.withRatings(ratings + boosted))
            }
            is IncidentEffect.ExtendContract -> {
                val player = ownPlayer(state, team, effect.playerId) ?: return
                state.update(
                    player.withContract(
                        player.contract.copy(
                            salary = effect.salary,
                            // 이번 시즌 + 연장 연수. FA 자격은 연장이 끝날 때 다시 생긴다
                            yearsRemaining = effect.years + 1,
                            seasonsToFreeAgency = effect.years,
                            signingBonusRemaining = 0.0,
                            type = baseballgm.model.ContractType.STANDARD,
                        ),
                    ),
                )
                // 계약 약속을 지킨 것으로 친다 (만족도, docs/13)
                state.extendedThisSeason += player.id
            }
            is IncidentEffect.Memory -> morale.remember(state, team, effect.playerId, effect.slot, effect.key, effect.label)
            is IncidentEffect.Promise -> morale.promise(state, team, effect.playerId, effect.kind, effect.deadlineWeek)
            is IncidentEffect.TransferList -> morale.listForTrade(state, team, effect.playerId)
        }
    }

    private fun ownPlayer(state: SeasonState, team: baseballgm.model.TeamId, id: baseballgm.model.PlayerId) =
        runCatching { state.player(id) }.getOrNull()?.takeIf { it.teamId == team }

    private companion object {
        const val MAX_FATIGUE = 100
        const val MAX_TRUST = 100
    }
}
