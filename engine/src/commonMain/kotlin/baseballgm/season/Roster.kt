package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId

/** 엔트리 이동 한 건. 알림함에 그대로 올라간다. */
data class RosterMove(
    val teamId: TeamId,
    val playerId: PlayerId,
    val promoted: Boolean,
    val reason: String,
)

/**
 * 엔트리 이력 (docs/07).
 *
 * 말소한 선수는 **다음 주에 다시 못 올리고 그다음 주부터** 가능하다. 부상 복귀 선수는 2군에서
 * 재활 1주를 채워야 한다. 두 규칙 모두 "언제 내려갔는지"를 기억해야 해서 따로 들고 있는다.
 */
class RosterState {
    private val demotedAtWeek = mutableMapOf<PlayerId, Int>()
    private val rehabReadyWeek = mutableMapOf<PlayerId, Int>()

    fun markDemoted(playerId: PlayerId, week: Int) {
        demotedAtWeek[playerId] = week
    }

    fun markRehab(playerId: PlayerId, readyWeek: Int) {
        rehabReadyWeek[playerId] = readyWeek
    }

    fun clearRehab(playerId: PlayerId) {
        rehabReadyWeek.remove(playerId)
    }

    fun rehabReadyAt(playerId: PlayerId): Int? = rehabReadyWeek[playerId]

    /** 말소 후 재등록 제한 (임시값: 다음 주 불가, 그다음 주부터 가능). */
    fun canPromote(playerId: PlayerId, week: Int, blockWeeks: Int): Boolean {
        val demoted = demotedAtWeek[playerId] ?: return true
        if (week < demoted + 1 + blockWeeks) return false
        val rehab = rehabReadyWeek[playerId] ?: return true
        return week >= rehab
    }
}

/**
 * 엔트리 관리 (docs/07).
 *
 * AI 구단과 (아직 화면이 없는) 유저 구단 모두 이 규칙으로 자동 운영한다. M10 에서 유저는 직접
 * 등록·말소를 하게 되고, 이 클래스는 AI 구단용 + "자동 진행" 기본값으로 남는다.
 *
 * 순서가 중요하다: ① 부상자를 내리고 ② 복귀 선수를 올리고 ③ 남은 자리를 2군 최고 선수로 채운다.
 */
class RosterManager(balance: BalanceConfig) {

    private val firstTeamSize = balance.int("roster.firstTeamRegistered")
    private val blockWeeks = balance.int("roster.reRegisterBlockWeeks")
    private val rehabWeeks = balance.int("injury.rehabWeeksInFutures")
    private val minimumPitchers = MINIMUM_PITCHERS
    private val minimumBatters = MINIMUM_BATTERS

    fun manage(
        teamId: TeamId,
        roster: List<Player>,
        week: Int,
        state: RosterState,
        rank: (Player) -> Double,
        onChange: (Player) -> Unit,
    ): List<RosterMove> {
        val moves = mutableListOf<RosterMove>()
        // 작업용 사본. 승격·말소할 때마다 이 목록의 선수를 바꿔 끼워야 인원 계산이 맞는다
        val current = roster.toMutableList()

        fun replace(player: Player, level: RosterLevel) {
            val updated = player.withRosterLevel(level)
            val index = current.indexOfFirst { it.id == player.id }
            if (index >= 0) current[index] = updated else current += updated
            onChange(updated)
        }

        fun demote(player: Player, reason: String) {
            replace(player, RosterLevel.FUTURES)
            state.markDemoted(player.id, week)
            moves += RosterMove(teamId, player.id, promoted = false, reason = reason)
        }

        fun promote(player: Player, reason: String) {
            replace(player, RosterLevel.FIRST_TEAM)
            state.clearRehab(player.id)
            moves += RosterMove(teamId, player.id, promoted = true, reason = reason)
        }

        // ① 부상자는 엔트리 자리를 차지하지 않는다 → 2군으로 내리고 재활 복귀 주차를 기록한다
        current.filter { it.rosterLevel == RosterLevel.FIRST_TEAM && it.condition.isInjured }.forEach { player ->
            val injury = player.condition.injury!!
            state.markRehab(player.id, week + injury.weeksRemaining + rehabWeeks)
            demote(player, "${injury.part} 부상 (${injury.weeksRemaining}주)")
        }
        // 복무 중인 선수도 엔트리에서 빠진다 (docs/12)
        current.filter { it.rosterLevel == RosterLevel.FIRST_TEAM && !it.military.isAvailable }.forEach { player ->
            demote(player, "군 복무")
        }

        val candidates = {
            current.filter {
                it.rosterLevel == RosterLevel.FUTURES && !it.condition.isInjured && it.military.isAvailable &&
                    state.canPromote(it.id, week, blockWeeks)
            }
        }

        // ② 부상에서 돌아온 선수를 먼저 올린다
        candidates().filter { state.rehabReadyAt(it.id) != null && week >= state.rehabReadyAt(it.id)!! }
            .sortedByDescending(rank)
            .forEach { player ->
                if (firstTeamCount(current) >= firstTeamSize) return@forEach
                promote(player, "부상 복귀")
            }

        // ③ 빈 자리를 2군 최고 선수로 채운다.
        // 투수·야수 최소 인원을 먼저 맞춘다 — 한 주 사이 부상이 겹쳐도 라인업이 무너지지 않게
        // 여유를 두고 채운다 (야수 9명은 무조건 있어야 경기를 한다).
        while (firstTeamCount(current) < firstTeamSize) {
            val needPitcher = firstTeamPitchers(current) < minimumPitchers
            val needBatter = firstTeamBatters(current) < minimumBatters
            val pool = when {
                needBatter -> candidates().filter { it !is Pitcher }
                needPitcher -> candidates().filter { it is Pitcher }
                else -> candidates()
            }.ifEmpty { candidates() }
            val pick = pool.maxByOrNull(rank) ?: break
            promote(
                pick,
                when {
                    needBatter -> "야수 보강 콜업"
                    needPitcher -> "투수 보강 콜업"
                    else -> "콜업"
                },
            )
        }

        // ④ 인원이 넘치면 가장 아래 선수를 내린다
        while (firstTeamCount(current) > firstTeamSize) {
            val pick = current.filter { it.rosterLevel == RosterLevel.FIRST_TEAM }.minByOrNull(rank) ?: break
            demote(pick, "엔트리 정리")
        }

        return moves
    }

    private fun firstTeamCount(roster: List<Player>): Int =
        roster.count { it.rosterLevel == RosterLevel.FIRST_TEAM }

    private fun firstTeamPitchers(roster: List<Player>): Int =
        roster.count { it.rosterLevel == RosterLevel.FIRST_TEAM && it is Pitcher }

    private fun firstTeamBatters(roster: List<Player>): Int =
        roster.count { it.rosterLevel == RosterLevel.FIRST_TEAM && it !is Pitcher }

    private companion object {
        const val MINIMUM_PITCHERS = 10

        /** 주중에 부상이 두세 명 나와도 9명을 채울 수 있도록 여유를 둔다. */
        const val MINIMUM_BATTERS = 14
    }
}

/** 로스터 레벨만 바꾼 사본. `Player` 가 sealed 라 여기서 한 번만 분기한다. */
fun Player.withRosterLevel(level: RosterLevel): Player = when (this) {
    is baseballgm.model.Batter -> copy(rosterLevel = level)
    is Pitcher -> copy(rosterLevel = level)
}

/** 컨디션만 바꾼 사본. */
fun Player.withCondition(condition: baseballgm.model.Condition): Player = when (this) {
    is baseballgm.model.Batter -> copy(condition = condition)
    is Pitcher -> copy(condition = condition)
}
