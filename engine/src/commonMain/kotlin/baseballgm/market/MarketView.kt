package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.Standings
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.Player
import baseballgm.model.TeamId
import baseballgm.scouting.ScoutingDepartment
import baseballgm.scouting.ScoutingService
import baseballgm.scouting.ScoutingView

/**
 * 구단이 선수를 보는 눈 (docs/02 구현 규칙 3: *AI 구단도 완벽한 정보를 쓰지 않는다*).
 *
 * 평가에 들어가는 능력치는 전부 여기를 거친다. 우리 팀 선수는 현재 능력치가 정확하지만
 * **잠재력은 우리 팀이라도 흐릿하고**, 타 팀 선수는 현재 능력치부터 범위다. 그래서
 * "저 팀이 우리 유망주를 과대평가했다"는 상황이 자연스럽게 생긴다.
 */
class MarketView(balance: BalanceConfig, private val strength: StrengthCalculator) {

    private val scouting = ScoutingService(balance)

    fun estimate(player: Player, viewerTeam: TeamId?, department: ScoutingDepartment? = null): RatingEstimate {
        val precision = scouting.precisionFor(department, player, viewerTeam)
        val scouted = ScoutingView.of(player, precision, SEASON_IGNORED, scouting.potentialScale)
        val ratings = scouted.ratings.mapValues { (_, range) -> range.center }
        return RatingEstimate(
            overall = strength.overallOf(ratings, isBatter = player is Batter),
            potential = ScoutingView.potentialRange(player, precision).center,
        )
    }

    private companion object {
        /** 추정치에는 나이가 쓰이지 않는다. [ScoutingView] 가 요구하는 자리만 채운다. */
        const val SEASON_IGNORED = 0
    }
}

/**
 * 구단 모드 판정 (docs/11).
 *
 * 시즌 시작(성적이 없을 때)에는 팀 전력으로, 시즌 중에는 순위로 정한다. 성적이 어중간해도
 * **선수단이 늙었으면 리빌딩으로 기울지 않는다** — 늙은 팀은 지금 이기려 해야 하기 때문이다.
 */
class TeamModeResolver(balance: BalanceConfig, private val strength: StrengthCalculator) {

    private val section = balance.section("teamMode")
    private val contendRank = section.int("contendRank")
    private val rebuildRank = section.int("rebuildRank")
    private val rebuildAverageAge = section.double("rebuildAverageAge")
    private val minGamesForRank = section.int("minGamesForRank")

    fun modeOf(league: League, standings: Standings, teamId: TeamId): TeamMode {
        val record = standings.record(teamId)
        val rank = if (record.games >= minGamesForRank) {
            standings.rankOf(teamId)
        } else {
            strengthRank(league, teamId)
        }
        val averageAge = league.playersOf(teamId)
            .filter { it.rosterLevel == baseballgm.model.RosterLevel.FIRST_TEAM }
            .map { it.ageIn(league.season) }
            .average()

        return when {
            rank <= contendRank -> TeamMode.CONTEND
            rank >= rebuildRank && averageAge <= rebuildAverageAge -> TeamMode.REBUILD
            rank >= rebuildRank -> TeamMode.NEUTRAL
            else -> TeamMode.NEUTRAL
        }
    }

    private fun strengthRank(league: League, teamId: TeamId): Int =
        league.teams
            .sortedByDescending { strength.of(league.playersOf(it.id)).overall }
            .indexOfFirst { it.id == teamId } + 1
}
