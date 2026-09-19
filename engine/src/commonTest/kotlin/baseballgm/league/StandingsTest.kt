package baseballgm.league

import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.stats.BattingLine
import baseballgm.stats.BoxScore
import baseballgm.stats.PitchingLine
import baseballgm.stats.PlayerBatting
import baseballgm.stats.PlayerPitching
import baseballgm.stats.TeamBoxScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val A = TeamId("AAA")
private val B = TeamId("BBB")

private fun box(homeRuns: Int, awayRuns: Int): BoxScore {
    fun team(id: TeamId, runs: Int) = TeamBoxScore(
        teamId = id,
        batting = mapOf(PlayerId("B") to PlayerBatting(unsplit = BattingLine(runs = runs))),
        pitching = mapOf(PlayerId("P") to PlayerPitching(unsplit = PitchingLine(runs = runs))),
        inningRuns = List(9) { 0 }.toMutableList().also { if (runs > 0) it[0] = runs },
        halfInningOuts = List(9) { 3 },
        leftOnBase = 0,
        runnersOutOnBase = 0,
        errors = 0,
    )
    return BoxScore(
        home = team(A, homeRuns),
        away = team(B, awayRuns),
        innings = 9,
        walkOff = false,
        tie = homeRuns == awayRuns,
    )
}

class StandingsTest {

    private val empty = Standings.empty(listOf(A, B))

    @Test
    fun `승패와 득실점이 쌓인다`() {
        val after = empty.withResult(box(homeRuns = 5, awayRuns = 3))
        assertEquals(1, after.record(A).wins)
        assertEquals(1, after.record(B).losses)
        assertEquals(5, after.record(A).runsScored)
        assertEquals(5, after.record(B).runsAllowed)
        assertEquals(2, after.record(A).runDifferential)
    }

    @Test
    fun `무승부는 승률 계산에서 빠진다`() {
        // 정규시즌 무승부는 승률 계산 제외 (docs/05 확정 사항)
        var standings = empty
        repeat(3) { standings = standings.withResult(box(2, 2)) }
        standings = standings.withResult(box(homeRuns = 4, awayRuns = 1))
        val record = standings.record(A)
        assertEquals(3, record.ties)
        assertEquals(1, record.wins)
        assertEquals(4, record.games)
        assertEquals(1.0, record.winPct, "1승 3무면 승률 10할이다")
    }

    @Test
    fun `연승과 연패를 센다`() {
        var standings = empty
        repeat(3) { standings = standings.withResult(box(5, 1)) }
        assertEquals(3, standings.record(A).streak)
        assertEquals(-3, standings.record(B).streak)
        assertEquals("3연승", standings.record(A).streakText())

        standings = standings.withResult(box(1, 5))
        assertEquals(-1, standings.record(A).streak, "졌으면 연승이 끊긴다")

        standings = standings.withResult(box(2, 2))
        assertEquals(0, standings.record(A).streak, "무승부면 연속 기록이 초기화된다")
    }

    @Test
    fun `순위와 게임차를 계산한다`() {
        var standings = empty
        repeat(6) { standings = standings.withResult(box(5, 1)) }
        assertEquals(1, standings.rankOf(A))
        assertEquals(2, standings.rankOf(B))
        assertEquals(0.0, standings.gamesBehind(A))
        assertEquals(6.0, standings.gamesBehind(B), "6승 차이면 6게임 차")
    }

    @Test
    fun `승률이 같으면 득실차로 가른다`() {
        var standings = empty
        standings = standings.withResult(box(10, 0))
        standings = standings.withResult(box(0, 1))
        val ranked = standings.ranked()
        assertEquals(A, ranked.first().teamId, "1승 1패로 같지만 득실차가 좋은 팀이 앞선다")
        assertTrue(ranked.first().runDifferential > ranked.last().runDifferential)
    }
}
