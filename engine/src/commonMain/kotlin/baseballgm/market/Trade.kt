package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.season.withTeam
import kotlinx.serialization.Serializable
import kotlin.math.max

/** 거래 한쪽이 내놓는 것. 선수·지명권·현금을 섞을 수 있다 (docs/11). */
@Serializable
data class TradePackage(
    val playerIds: List<PlayerId> = emptyList(),
    val picks: List<DraftPickRight> = emptyList(),
    /** 운용 자금에서 나가는 현금(억원) */
    val cash: Double = 0.0,
    /**
     * 연봉 보조 (2026-10-05): 이 묶음으로 보내는 선수 → 보내는 쪽이 계약 끝까지 계속 내 주는 연봉(억/년).
     * 받는 팀의 연봉 총액에서 빠지고 보내는 팀 연봉 총액에 남는다 ([RetainedSalary])
     */
    val retained: Map<PlayerId, Double> = emptyMap(),
) {
    val isEmpty: Boolean get() = playerIds.isEmpty() && picks.isEmpty() && cash <= 0.0

    fun describe(nameOf: (PlayerId) -> String): String {
        val parts = playerIds.map { id -> nameOf(id) + (retained[id]?.takeIf { it > 0.0 }?.let { " (연봉 ${it}억 보조)" } ?: "") } +
            picks.map { it.label() } + listOfNotNull(
            if (cash > 0.0) "현금 ${cash}억" else null,
        )
        return if (parts.isEmpty()) "없음" else parts.joinToString(", ")
    }
}

/** 트레이드 제안. [fromProposer] 를 주고 [fromPartner] 를 받는다. */
@Serializable
data class TradeProposal(
    val proposer: TeamId,
    val partner: TeamId,
    val fromProposer: TradePackage = TradePackage(),
    val fromPartner: TradePackage = TradePackage(),
    /** AI 가 먼저 건 제안이면 왜 거는지. 비서가 이걸로 설명한다 */
    val reason: TradeReason? = null,
) {
    fun reversed(): TradeProposal = TradeProposal(partner, proposer, fromPartner, fromProposer)

    fun outgoingOf(teamId: TeamId): TradePackage = if (teamId == proposer) fromProposer else fromPartner

    fun incomingOf(teamId: TeamId): TradePackage = if (teamId == proposer) fromPartner else fromProposer
}

/** AI 가 제안을 거는 동기 (2026-10-04). */
@Serializable
enum class TradeMotive {
    /** 주전이 비었거나 약한 자리를 메운다 */
    NEED,

    /** 리빌딩 구단이 베테랑을 정리한다 */
    SELL,
}

/**
 * @param playerId 거래의 중심 선수 (보강이면 데려가려는 선수, 정리면 내놓는 베테랑)
 * @param contending 제안 구단이 우승 도전 중인가
 */
@Serializable
data class TradeReason(val motive: TradeMotive, val playerId: PlayerId, val contending: Boolean)

/**
 * 연봉 보조 한 건 (2026-10-05). [payer] 가 [playerId] 의 연봉 중 [amount] 억을 [years] 시즌 동안 대신 낸다.
 * 연봉 총액([League.payrollOf])에서 선수 소속 팀은 빼고 [payer] 는 더한다. 스토브리그마다 한 해씩 줄어든다
 */
@Serializable
data class RetainedSalary(val payer: TeamId, val playerId: PlayerId, val amount: Double, val years: Int)

/** 성사된 거래 기록. 되팔기 금지와 협상 이력에 쓴다. */
@Serializable
data class TradeRecord(
    val season: Int,
    val week: Int,
    val teamA: TeamId,
    val teamB: TeamId,
    val playersToA: List<PlayerId>,
    val playersToB: List<PlayerId>,
) {
    fun involves(teamId: TeamId): Boolean = teamId == teamA || teamId == teamB

    /** [playerId] 가 이 거래로 [teamId] 를 떠났는가. */
    fun leftTeam(playerId: PlayerId, teamId: TeamId): Boolean =
        (teamId == teamA && playerId in playersToB) || (teamId == teamB && playerId in playersToA)
}

/**
 * 트레이드 규칙 검사 (docs/11 제약·꼼수 방지).
 *
 * 문제를 예외로 던지지 않고 목록으로 돌려준다 — 화면이 "왜 안 되는지" 보여 줘야 하기 때문이다.
 */
class TradeRules(balance: BalanceConfig) {
    private companion object {
        const val EPSILON = 1e-6
    }

    private val section = balance.section("trade")
    private val maxPlayersPerSide = section.int("maxPlayersPerSide")
    private val cashLimit = section.double("cashLimit")
    private val maxRetainRate = section.double("salaryRetention.maxRate")
    private val buybackBlockSeasons = section.int("buybackBlockSeasons")
    val rejectionsBeforeRefusal: Int = section.int("rejectionsBeforeRefusal")
    val refusalWeeks: Int = section.int("refusalWeeks")

    private val pickRules = DraftPickTradeRules(balance)
    private val rosterLimits = balance.section("rosterLimits")
    private val minRosterSize = rosterLimits.int("minRosterSize")
    private val maxForeign = balance.int("foreignPlayers.maxPerTeam")
    private val firstTeamSize = balance.int("roster.firstTeamRegistered")

    fun problems(
        league: League,
        proposal: TradeProposal,
        week: Int,
        draftDone: Boolean,
        history: List<TradeRecord>,
    ): List<String> {
        val problems = mutableListOf<String>()
        if (proposal.proposer == proposal.partner) problems += "같은 구단끼리는 거래할 수 없다"
        if (proposal.fromProposer.isEmpty && proposal.fromPartner.isEmpty) problems += "주고받을 것이 없다"

        listOf(proposal.proposer to proposal.fromProposer, proposal.partner to proposal.fromPartner)
            .forEach { (teamId, pack) ->
                if (pack.playerIds.size > maxPlayersPerSide) {
                    problems += "한 번에 ${maxPlayersPerSide}명까지만 보낼 수 있다"
                }
                if (pack.cash > cashLimit) problems += "현금은 ${cashLimit}억까지만 넣을 수 있다"
                pack.retained.forEach { (playerId, amount) ->
                    val player = league.players.firstOrNull { it.id == playerId }
                    when {
                        playerId !in pack.playerIds -> problems += "연봉 보조는 보내는 선수에게만 걸 수 있다"
                        player != null && amount > player.contract.salary * maxRetainRate + EPSILON ->
                            problems += "연봉 보조는 연봉의 ${(maxRetainRate * 100).toInt()}%까지만 된다"
                        amount < 0.0 -> problems += "연봉 보조는 0억 이상이어야 한다"
                    }
                }
                if (pack.cash > league.team(teamId).operatingFunds) {
                    problems += "${league.team(teamId).name} 의 운용 자금이 모자란다"
                }
                pack.playerIds.forEach { playerId ->
                    val player = league.players.firstOrNull { it.id == playerId }
                    when {
                        player == null -> problems += "없는 선수다"
                        player.teamId != teamId -> problems += "${player.registeredName} 은(는) 그 구단 선수가 아니다"
                        !player.military.isAvailable -> problems += "${player.registeredName} 은(는) 복무 중이라 거래할 수 없다"
                    }
                }
                pack.picks.forEach { pick ->
                    problems += pickRules.problems(
                        rights = league.draftRights,
                        pick = pick,
                        from = teamId,
                        to = if (teamId == proposal.proposer) proposal.partner else proposal.proposer,
                        currentSeason = league.season,
                        draftDone = draftDone,
                    )
                }
            }

        problems += rosterProblems(league, proposal)
        problems += buybackProblems(league, proposal, history)
        return problems.distinct()
    }

    /** 거래 뒤 선수단이 운영 가능한 크기로 남는가. 외국인 보유 한도도 여기서 본다. */
    private fun rosterProblems(league: League, proposal: TradeProposal): List<String> {
        val problems = mutableListOf<String>()
        listOf(proposal.proposer, proposal.partner).forEach { teamId ->
            val out = proposal.outgoingOf(teamId).playerIds.toSet()
            val incoming = proposal.incomingOf(teamId).playerIds.mapNotNull { id ->
                league.players.firstOrNull { it.id == id }
            }
            val remaining = league.playersOf(teamId).filterNot { it.id in out }
            val after = remaining + incoming
            if (after.size < minRosterSize) problems += "${league.team(teamId).name} 의 선수가 너무 적어진다"
            if (after.count { it.isForeign } > maxForeign) {
                problems += "${league.team(teamId).name} 의 외국인 선수가 ${maxForeign}명을 넘는다"
            }
            // 트레이드로 온 선수는 일단 2군에 들어간다. 그러므로 1군 인원은 남는 선수만 센다
            if (remaining.count { it.rosterLevel == RosterLevel.FIRST_TEAM } > firstTeamSize) {
                problems += "${league.team(teamId).name} 의 1군 등록 인원이 넘친다"
            }
        }
        return problems
    }

    /** 받은 선수를 원 소속팀에 곧바로 되파는 것 금지 (docs/11 꼼수 방지). */
    private fun buybackProblems(
        league: League,
        proposal: TradeProposal,
        history: List<TradeRecord>,
    ): List<String> {
        val problems = mutableListOf<String>()
        val recent = history.filter { it.season >= league.season - buybackBlockSeasons }
        listOf(proposal.proposer, proposal.partner).forEach { teamId ->
            val other = if (teamId == proposal.proposer) proposal.partner else proposal.proposer
            proposal.outgoingOf(teamId).playerIds.forEach { playerId ->
                val cameFromOther = recent.any { it.involves(teamId) && it.involves(other) && it.leftTeam(playerId, other) }
                if (cameFromOther) {
                    val name = league.players.firstOrNull { it.id == playerId }?.registeredName ?: playerId.value
                    problems += "$name 은(는) 최근 그 구단에서 받은 선수라 되팔 수 없다"
                }
            }
        }
        return problems
    }
}

/** 트레이드 성사 결과. */
data class TradeOutcome(val league: League, val record: TradeRecord, val summary: String)

/**
 * 거래를 실제로 반영한다.
 *
 * 선수 소속·지명권 주인·운용 자금이 한꺼번에 바뀐다. 리그가 불변 객체라 새 리그를 만들어 돌려주고,
 * 부르는 쪽이 갈아 끼운다 — "어딘가에서 몰래 절반만 반영되는" 상태를 막기 위해서다.
 */
object TradeExecutor {

    fun apply(league: League, proposal: TradeProposal, week: Int): TradeOutcome {
        val toPartner = proposal.fromProposer
        val toProposer = proposal.fromPartner

        val movedPlayers = league.players.map { player ->
            when (player.id) {
                in toPartner.playerIds -> player.movedTo(proposal.partner)
                in toProposer.playerIds -> player.movedTo(proposal.proposer)
                else -> player
            }
        }

        var rights = league.draftRights
        toPartner.picks.forEach { pick -> rights = rights.transferred(pick, proposal.partner) }
        toProposer.picks.forEach { pick -> rights = rights.transferred(pick, proposal.proposer) }

        val netCashToPartner = toPartner.cash - toProposer.cash
        val teams = league.teams.map { team ->
            when (team.id) {
                proposal.proposer -> team.copy(operatingFunds = max(0.0, team.operatingFunds - netCashToPartner))
                proposal.partner -> team.copy(operatingFunds = max(0.0, team.operatingFunds + netCashToPartner))
                else -> team
            }
        }

        val record = TradeRecord(
            season = league.season,
            week = week,
            teamA = proposal.proposer,
            teamB = proposal.partner,
            playersToA = toProposer.playerIds,
            playersToB = toPartner.playerIds,
        )
        val nameOf = { id: PlayerId -> league.players.first { it.id == id }.registeredName }
        val summary = "${league.team(proposal.proposer).name} ⇄ ${league.team(proposal.partner).name}: " +
            "${toPartner.describe(nameOf)} ↔ ${toProposer.describe(nameOf)}"

        // 연봉 보조: 보내는 쪽이 계약 끝까지 일부를 계속 낸다
        val retained = listOf(proposal.proposer to toPartner, proposal.partner to toProposer).flatMap { (payer, pack) ->
            pack.retained.filter { it.value > 0.0 }.mapNotNull { (playerId, amount) ->
                val player = league.players.firstOrNull { it.id == playerId } ?: return@mapNotNull null
                RetainedSalary(payer, playerId, amount, player.contract.yearsRemaining.coerceAtLeast(1))
            }
        }

        return TradeOutcome(
            league = league.copy(
                retainedSalaries = league.retainedSalaries + retained,
                // 옮긴 선수는 새 팀에서 번호가 겹치면 바꿔 단다 (원래 있던 선수가 번호를 지킨다)
                players = baseballgm.model.UniformNumbers.assign(movedPlayers, (toPartner.playerIds + toProposer.playerIds).toSet()),
                teams = teams,
                draftRights = rights,
                tradeHistory = league.tradeHistory + record,
            ),
            record = record,
            summary = summary,
        )
    }

    /** 트레이드된 선수는 일단 2군으로 간다. 엔트리는 다음 주 엔트리 관리가 정리한다 (docs/07). */
    private fun Player.movedTo(teamId: TeamId): Player = withTeam(teamId, RosterLevel.FUTURES)
}
