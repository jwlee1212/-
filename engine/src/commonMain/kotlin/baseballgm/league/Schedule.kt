package baseballgm.league

import baseballgm.io.BalanceConfig
import baseballgm.model.TeamId
import kotlinx.serialization.Serializable

/** 일정 규칙 (docs/01, `balance.json` 의 `schedule`). */
data class ScheduleRules(
    val weeks: Int,
    val gamesPerWeek: Int,
    val gamesPerTeam: Int,
    val threeGameSeriesPerOpponent: Int,
    val twoGameSeriesPerOpponent: Int,
    val twoGameWeeksAtSeasonEnd: Int,
) {
    /** 상대별 경기 수. 3연전 4회 + 2연전 2회 = 16 */
    val gamesPerOpponent: Int
        get() = threeGameSeriesPerOpponent * 3 + twoGameSeriesPerOpponent * 2

    companion object {
        fun from(balance: BalanceConfig): ScheduleRules {
            val schedule = balance.section("schedule")
            return ScheduleRules(
                weeks = schedule.int("weeks"),
                gamesPerWeek = schedule.int("gamesPerWeek"),
                gamesPerTeam = schedule.int("gamesPerTeam"),
                threeGameSeriesPerOpponent = schedule.int("threeGameSeriesPerOpponent"),
                twoGameSeriesPerOpponent = schedule.int("twoGameSeriesPerOpponent"),
                twoGameWeeksAtSeasonEnd = schedule.int("twoGameWeeksAtSeasonEnd"),
            )
        }
    }
}

/**
 * 경기 한 판.
 *
 * @param week 1 부터 시작하는 주차
 * @param day 주 안의 날짜 순서. 0=화 … 5=일 (월요일은 휴식)
 * @param seriesId 같은 시리즈(연전)에 속한 경기는 같은 번호를 가진다
 */
@Serializable
data class ScheduledGame(
    val week: Int,
    val day: Int,
    val home: TeamId,
    val away: TeamId,
    val seriesId: Int,
) {
    fun involves(teamId: TeamId): Boolean = home == teamId || away == teamId

    fun opponentOf(teamId: TeamId): TeamId = when (teamId) {
        home -> away
        away -> home
        else -> error("$teamId 는 이 경기에 없다")
    }
}

/** 정규시즌 일정표. 고정 리그 데이터에 미리 만들어 포함한다 (docs/07). */
@Serializable
data class Schedule(
    val season: Int,
    val games: List<ScheduledGame>,
) {
    fun gamesInWeek(week: Int): List<ScheduledGame> = games.filter { it.week == week }.sortedBy { it.day }

    fun gamesOf(teamId: TeamId): List<ScheduledGame> = games.filter { it.involves(teamId) }

    fun weekCount(): Int = games.maxOfOrNull { it.week } ?: 0

    /**
     * 일정표가 규칙에 맞는지 검사한다. 문제 목록을 돌려주고 비어 있으면 정상이다.
     * (한 팀이 같은 날 두 경기를 뛰는 것 같은 사고를 생성 단계에서 잡는다)
     */
    fun validate(teamIds: List<TeamId>, rules: ScheduleRules): List<String> {
        val problems = mutableListOf<String>()

        for (team in teamIds) {
            val teamGames = gamesOf(team)
            if (teamGames.size != rules.gamesPerTeam) {
                problems += "$team: 경기 수 ${teamGames.size} (기대 ${rules.gamesPerTeam})"
            }
            val perWeek = teamGames.groupBy { it.week }
            if (perWeek.size != rules.weeks) problems += "$team: 경기가 있는 주가 ${perWeek.size}주 (기대 ${rules.weeks})"
            perWeek.forEach { (week, weekGames) ->
                if (weekGames.size != rules.gamesPerWeek) {
                    problems += "$team: ${week}주차 경기 수 ${weekGames.size} (기대 ${rules.gamesPerWeek})"
                }
                val days = weekGames.map { it.day }
                if (days.size != days.toSet().size) problems += "$team: ${week}주차에 하루 두 경기"
            }
        }

        // 팀 쌍별 경기 수와 홈·원정 균형
        for (i in teamIds.indices) {
            for (j in i + 1 until teamIds.size) {
                val a = teamIds[i]
                val b = teamIds[j]
                val between = games.filter { it.involves(a) && it.involves(b) }
                if (between.size != rules.gamesPerOpponent) {
                    problems += "$a-$b: 맞대결 ${between.size}경기 (기대 ${rules.gamesPerOpponent})"
                    continue
                }
                val aHome = between.count { it.home == a }
                if (aHome * 2 != rules.gamesPerOpponent) {
                    problems += "$a-$b: 홈경기 배분 $aHome/${between.size} 가 균등하지 않다"
                }
            }
        }

        // 시리즈 구성: 같은 주·같은 상대·같은 홈팀·연속 날짜
        for ((seriesId, seriesGames) in games.groupBy { it.seriesId }) {
            val sorted = seriesGames.sortedBy { it.day }
            val first = sorted.first()
            if (sorted.any { it.week != first.week || it.home != first.home || it.away != first.away }) {
                problems += "시리즈 $seriesId: 주차나 대진이 섞여 있다"
            }
            val days = sorted.map { it.day }
            if (days != (days.first() until days.first() + days.size).toList()) {
                problems += "시리즈 $seriesId: 날짜가 연속되지 않는다 ($days)"
            }
            if (sorted.size !in setOf(2, 3)) problems += "시리즈 $seriesId: ${sorted.size}연전은 없다"
        }

        val totalGames = teamIds.size * rules.gamesPerTeam / 2
        if (games.size != totalGames) problems += "리그 전체 경기 수 ${games.size} (기대 $totalGames)"

        return problems
    }
}
