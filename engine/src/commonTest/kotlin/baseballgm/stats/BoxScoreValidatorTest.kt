package baseballgm.stats

import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 검증기 자체를 검증한다.
 *
 * "항상 통과하는 검증기"는 없느니만 못하므로, 일부러 등식을 깬 박스스코어를 넣어
 * **잡히는지**를 확인한다.
 */
class BoxScoreValidatorTest {

    private val homeId = TeamId("DSK")
    private val awayId = TeamId("ICS")

    private fun team(
        teamId: TeamId,
        batting: Map<PlayerId, PlayerBatting> = mapOf(PlayerId("B1") to PlayerBatting(vsRight = BattingLine(plateAppearances = 27, atBats = 27, groundOuts = 27))),
        pitching: Map<PlayerId, PlayerPitching> = mapOf(PlayerId("P1") to PlayerPitching(unsplit = PitchingLine(outs = 27, games = 1, gamesStarted = 1))),
        inningRuns: List<Int> = List(9) { 0 },
        halfInningOuts: List<Int> = List(9) { 3 },
        leftOnBase: Int = 0,
        runnersOutOnBase: Int = 0,
        errors: Int = 0,
    ) = TeamBoxScore(teamId, batting, pitching, inningRuns, halfInningOuts, leftOnBase, runnersOutOnBase, errors)

    private fun box(
        home: TeamBoxScore = team(homeId),
        away: TeamBoxScore = team(awayId),
        tie: Boolean = true,
        winner: PlayerId? = null,
        loser: PlayerId? = null,
        save: PlayerId? = null,
    ) = BoxScore(home, away, innings = 9, walkOff = false, tie = tie, winningPitcher = winner, losingPitcher = loser, savePitcher = save)

    @Test
    fun `앞뒤가 맞는 박스스코어는 통과한다`() {
        assertEquals(emptyList(), BoxScoreValidator.validate(box()))
    }

    @Test
    fun `타석 구성이 안 맞으면 잡는다`() {
        val broken = team(
            homeId,
            batting = mapOf(PlayerId("B1") to PlayerBatting(vsRight = BattingLine(plateAppearances = 28, atBats = 27, groundOuts = 27))),
        )
        val problems = BoxScoreValidator.validate(box(home = broken))
        assertTrue(problems.any { it.contains("타석") }, "$problems")
    }

    @Test
    fun `주자가 사라지면 잡는다`() {
        // 안타 1개가 나왔는데 득점도 잔루도 아웃도 없다
        val broken = team(
            homeId,
            batting = mapOf(PlayerId("B1") to PlayerBatting(vsRight = BattingLine(plateAppearances = 27, atBats = 27, hits = 1, groundOuts = 26))),
        )
        val problems = BoxScoreValidator.validate(box(home = broken))
        assertTrue(problems.any { it.contains("출루") }, "$problems")
    }

    @Test
    fun `투수 아웃 합이 이닝과 안 맞으면 잡는다`() {
        val broken = team(
            homeId,
            pitching = mapOf(PlayerId("P1") to PlayerPitching(unsplit = PitchingLine(outs = 26, games = 1))),
        )
        val problems = BoxScoreValidator.validate(box(home = broken))
        assertTrue(problems.any { it.contains("아웃 합") }, "$problems")
    }

    @Test
    fun `이닝별 점수와 선수 득점이 다르면 잡는다`() {
        val broken = team(homeId, inningRuns = List(8) { 0 } + listOf(1))
        val problems = BoxScoreValidator.validate(box(home = broken, tie = false))
        assertTrue(problems.any { it.contains("선수 득점") }, "$problems")
    }

    @Test
    fun `자책점이 실점보다 많으면 잡는다`() {
        val broken = team(
            homeId,
            pitching = mapOf(PlayerId("P1") to PlayerPitching(unsplit = PitchingLine(outs = 27, runs = 1, earnedRuns = 2, games = 1))),
        )
        val problems = BoxScoreValidator.validate(box(home = broken))
        assertTrue(problems.any { it.contains("자책") }, "$problems")
    }

    @Test
    fun `무승부인데 승리 투수가 있으면 잡는다`() {
        val home = team(
            homeId,
            pitching = mapOf(PlayerId("P1") to PlayerPitching(unsplit = PitchingLine(outs = 27, wins = 1, games = 1))),
        )
        val problems = BoxScoreValidator.validate(box(home = home, tie = true, winner = PlayerId("P1")))
        assertTrue(problems.any { it.contains("무승부") }, "$problems")
    }

    @Test
    fun `승부가 났는데 승패 기록이 없으면 잡는다`() {
        val problems = BoxScoreValidator.validate(box(tie = false))
        assertTrue(problems.any { it.contains("승리 투수") }, "$problems")
        assertTrue(problems.any { it.contains("패전 투수") }, "$problems")
    }

    @Test
    fun `validateOrThrow 는 문제가 있으면 예외를 던진다`() {
        val broken = team(
            homeId,
            batting = mapOf(PlayerId("B1") to PlayerBatting(vsRight = BattingLine(plateAppearances = 28, atBats = 27, groundOuts = 27))),
        )
        assertFailsWith<BoxScoreValidationException> { BoxScoreValidator.validateOrThrow(box(home = broken)) }
        BoxScoreValidator.validateOrThrow(box()) // 정상이면 조용히 지나간다
    }
}
