package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRole
import baseballgm.model.Player
import baseballgm.model.RosterLevel
import baseballgm.season.Offseason
import baseballgm.season.OffseasonReport
import kotlin.random.Random

/** 한 시즌이 끝난 뒤의 리그 상태 요약 (장기 밸런스 확인용). */
data class SeasonSummary(
    val season: Int,
    val playerCount: Int,
    val starterAverage: Double,
    val eliteRatings: Int,
    val averageAge: Double,
    val bestWinPct: Double,
    val worstWinPct: Double,
    val offseason: OffseasonReport,
    val validationFailures: Int,
) {
    fun line(): String =
        "${season} | 선수 ${playerCount} | 주전평균 ${"%.1f".format(starterAverage)} | 90+ ${eliteRatings} | " +
            "평균나이 ${"%.1f".format(averageAge)} | 승률 ${"%.3f".format(bestWinPct)}~${"%.3f".format(worstWinPct)} | " +
            offseason.summary()
}

/**
 * 여러 시즌을 이어서 돌린다 (M5 장기 밸런스 테스트).
 *
 * 시즌 → 스토브리그(성장·노화·은퇴·신인) → 다음 시즌. 30시즌을 돌려도 리그가 무너지지 않는지
 * (주전 평균 능력치, 90+ 선수 수, 선수 수, 나이 분포) 확인하는 것이 목적이다.
 */
class MultiSeasonRunner(private val balance: BalanceConfig, private val startingLeague: League) {

    private val strength = StrengthCalculator(balance)

    fun run(seasons: Int, seed: Long, onSeason: ((SeasonSummary) -> Unit)? = null): List<SeasonSummary> {
        var league = startingLeague
        val summaries = mutableListOf<SeasonSummary>()
        val offseason = Offseason(balance, strength)

        repeat(seasons) { index ->
            val result = SeasonRunner(balance, league).playSeason(seed + index)
            val rookies = RookieFactory(balance, strength, league)
            val (nextLeague, report) = offseason.run(result.state, Random(seed + index * OFFSEASON_STRIDE), rookies)
            val ranked = result.state.standings.ranked()

            val summary = SeasonSummary(
                season = league.season,
                playerCount = nextLeague.players.size,
                starterAverage = starterAverage(league, result.state.allPlayers()),
                eliteRatings = nextLeague.players.sumOf { player -> player.ratingsMap().values.count { it >= ELITE } },
                averageAge = nextLeague.players.map { it.ageIn(nextLeague.season) }.average(),
                bestWinPct = ranked.first().winPct,
                worstWinPct = ranked.last().winPct,
                offseason = report,
                validationFailures = result.validationProblems.size,
            )
            summaries += summary
            onSeason?.invoke(summary)
            league = nextLeague
        }
        return summaries
    }

    /** 팀별 주전(야수 9 + 선발 5)의 평균 능력치. CLAUDE.md §7 장기 밸런스 기준. */
    private fun starterAverage(league: League, players: List<Player>): Double {
        val byTeam = players.filter { it.rosterLevel == RosterLevel.FIRST_TEAM }.groupBy { it.teamId }
        val starters = league.teams.flatMap { team ->
            val roster = byTeam[team.id].orEmpty()
            roster.filterIsInstance<Batter>().sortedByDescending { strength.overallOf(it) }.take(LINEUP) +
                roster.filterIsInstance<Pitcher>().filter { it.role == PitcherRole.STARTER }
                    .sortedByDescending { strength.overallOf(it) }.take(ROTATION)
        }
        return if (starters.isEmpty()) 0.0 else starters.map { strength.overallOf(it) }.average()
    }

    private companion object {
        const val LINEUP = 9
        const val ROTATION = 5
        const val ELITE = 90
        const val OFFSEASON_STRIDE = 7919L
    }
}
