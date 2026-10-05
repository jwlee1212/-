package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.scouting.ScoutingService
import baseballgm.season.Offseason
import baseballgm.season.SeasonCalendar
import baseballgm.season.SeasonState
import baseballgm.season.WeekLoop
import baseballgm.util.Seeds
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 연도별 능력치 기록 (2026-10-03, 선수 상세 그래프). */
class RatingHistoryTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val strength = StrengthCalculator(balance)
    private val user = league.teams.first().id

    @Test
    fun `시즌이 끝나면 능력치가 기록되고 우리 선수는 정확히 타 팀 선수는 범위로 나온다`() {
        val state = SeasonState.of(league, SeasonCalendar.from(balance), balance)
        val loop = WeekLoop(balance, league)
        val random = Random(3)
        while (!state.isRegularSeasonOver) loop.playWeek(state, random, validate = false)
        val before = state.playersOf(user).first { !it.isForeign }

        val (next, _) = Offseason(balance, strength).run(
            state, Seeds.random(3L, state.season, Seeds.Phase.OFFSEASON), RookieFactory(balance, strength, league),
            autoFreeAgency = true,
        )
        val snapshots = assertNotNull(next.history.ratings[before.id], "능력치 기록이 없다")
        assertEquals(listOf(league.season), snapshots.map { it.season })

        val scouting = ScoutingService(balance)
        val now = next.players.first { it.id == before.id }
        val own = scouting.ratingHistory(null, now, now.teamId, next.history, next.season)
        assertEquals(listOf(league.season, next.season), own.map { it.season })
        assertTrue(own.all { it.overall.isExact })
        // 지난 시즌 점 = 성장 전 그 시즌 능력치
        val expected = before.ratingsMap().values.average()
        assertEquals(kotlin.math.round(expected).toInt(), own.first().overall.low)
        assertTrue(own.last().current)

        val other = next.players.first { it.teamId != null && it.teamId != now.teamId && !it.isForeign && next.history.ratings[it.id] != null }
        val seen = scouting.ratingHistory(null, other, now.teamId, next.history, next.season)
        assertTrue(seen.size == 2 && seen.none { it.overall.isExact }, "타 팀 선수 과거 능력치가 정확히 보인다")
        // 리그를 떠난 선수의 기록은 버린다
        assertTrue(next.history.ratings.keys.all { id -> next.players.any { it.id == id } })
    }
}
