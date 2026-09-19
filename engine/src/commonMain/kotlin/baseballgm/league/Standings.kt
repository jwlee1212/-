package baseballgm.league

import baseballgm.model.TeamId
import baseballgm.stats.BoxScore
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * 팀 성적.
 *
 * 무승부는 승률 계산에서 제외한다 (docs/05 확정 사항). 연승·연패는 [streak] 하나로 들고 있다
 * (양수면 연승, 음수면 연패, 0이면 직전이 무승부).
 */
@Serializable
data class TeamRecord(
    val teamId: TeamId,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val runsScored: Int = 0,
    val runsAllowed: Int = 0,
    val streak: Int = 0,
) {
    val games: Int get() = wins + losses + ties

    /** 승률. 무승부는 분모에서 빠진다. */
    val winPct: Double get() = if (wins + losses == 0) 0.0 else wins.toDouble() / (wins + losses)

    val runDifferential: Int get() = runsScored - runsAllowed

    fun streakText(): String = when {
        streak > 0 -> "${streak}연승"
        streak < 0 -> "${abs(streak)}연패"
        else -> "-"
    }
}

/** 순위표. 경기 결과를 먹이면 새 순위표를 돌려준다. */
@Serializable
data class Standings(val records: Map<TeamId, TeamRecord>) {

    fun record(teamId: TeamId): TeamRecord = records[teamId] ?: TeamRecord(teamId)

    /** 승률 순 정렬. 같으면 상대 전적 대신 득실차로 가른다 (프로토타입 단순화). */
    fun ranked(): List<TeamRecord> =
        records.values.sortedWith(compareByDescending<TeamRecord> { it.winPct }.thenByDescending { it.runDifferential })

    fun rankOf(teamId: TeamId): Int = ranked().indexOfFirst { it.teamId == teamId } + 1

    /** 1위와의 게임 차. */
    fun gamesBehind(teamId: TeamId): Double {
        val top = ranked().firstOrNull() ?: return 0.0
        val target = record(teamId)
        return ((top.wins - target.wins) + (target.losses - top.losses)) / 2.0
    }

    fun withResult(box: BoxScore): Standings {
        val home = record(box.home.teamId)
        val away = record(box.away.teamId)
        val updated = records.toMutableMap()
        updated[home.teamId] = home.applied(box.homeScore, box.awayScore, box.tie)
        updated[away.teamId] = away.applied(box.awayScore, box.homeScore, box.tie)
        return Standings(updated)
    }

    private fun TeamRecord.applied(scored: Int, allowed: Int, tie: Boolean): TeamRecord {
        val won = !tie && scored > allowed
        val lost = !tie && scored < allowed
        return copy(
            wins = wins + if (won) 1 else 0,
            losses = losses + if (lost) 1 else 0,
            ties = ties + if (tie) 1 else 0,
            runsScored = runsScored + scored,
            runsAllowed = runsAllowed + allowed,
            streak = when {
                tie -> 0
                won -> if (streak > 0) streak + 1 else 1
                else -> if (streak < 0) streak - 1 else -1
            },
        )
    }

    companion object {
        fun empty(teamIds: List<TeamId>): Standings =
            Standings(teamIds.associateWith { TeamRecord(it) })
    }
}
