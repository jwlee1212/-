package baseballgm.text

import baseballgm.model.Hand
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.sim.GameEnded
import baseballgm.sim.Half
import baseballgm.sim.PaOutcome
import baseballgm.sim.PlateAppearanceCompleted
import baseballgm.sim.StolenBaseAttempted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val NAMES = mapOf(
    PlayerId("B1") to "김민준",
    PlayerId("B2") to "이서우",
    PlayerId("P1") to "박강현",
)

private fun pa(
    batter: String,
    outcome: PaOutcome,
    rbi: Int = 0,
) = PlateAppearanceCompleted(
    inning = 3,
    half = Half.TOP,
    battingTeam = TeamId("AAA"),
    fieldingTeam = TeamId("BBB"),
    batterId = PlayerId(batter),
    batterHand = Hand.RIGHT,
    pitcherId = PlayerId("P1"),
    pitcherHand = Hand.RIGHT,
    outcome = outcome,
    battedBall = null,
    pitches = 4,
    rbi = rbi,
    outsRecorded = if (outcome.batterReaches) 0 else 1,
    runnersOutOnBase = 0,
    runs = emptyList(),
    errorBy = null,
    outsBefore = 1,
    basesBefore = 0,
)

class CommentaryTest {

    private val renderer = CommentaryRenderer { id -> NAMES.getValue(id) }

    @Test
    fun `이름 받침에 따라 조사가 달라진다`() {
        val lines = renderer.render(listOf(pa("B1", PaOutcome.SINGLE), pa("B2", PaOutcome.SINGLE)))
        assertEquals("김민준이 안타", lines[0])
        assertEquals("이서우가 안타", lines[1])
    }

    @Test
    fun `타석 결과마다 문장이 다르다`() {
        val outcomes = PaOutcome.entries.map { renderer.render(listOf(pa("B1", it))).first() }
        assertEquals(outcomes.size, outcomes.toSet().size, "같은 문장이 두 번 나온다")
        assertTrue(outcomes.all { it.isNotBlank() })
    }

    @Test
    fun `타점이 있으면 문장에 붙는다`() {
        val line = renderer.render(listOf(pa("B1", PaOutcome.HOME_RUN, rbi = 3))).first()
        assertEquals("김민준이 홈런! 3타점", line)
    }

    @Test
    fun `도루 성공과 실패를 구분한다`() {
        fun steal(success: Boolean) = StolenBaseAttempted(
            inning = 7, half = Half.BOTTOM, battingTeam = TeamId("AAA"),
            runnerId = PlayerId("B2"), pitcherId = PlayerId("P1"), catcherId = PlayerId("B1"),
            targetBase = 2, success = success,
        )
        assertTrue(renderer.render(listOf(steal(true))).first().contains("성공"))
        assertTrue(renderer.render(listOf(steal(false))).first().contains("실패"))
    }

    @Test
    fun `하이라이트는 큰 장면만 고른다`() {
        val events = listOf(
            pa("B1", PaOutcome.GROUND_OUT),
            pa("B2", PaOutcome.SINGLE),
            pa("B1", PaOutcome.HOME_RUN, rbi = 2),
            pa("B2", PaOutcome.STRIKEOUT),
            GameEnded(TeamId("AAA"), TeamId("BBB"), homeScore = 5, awayScore = 4, innings = 9, walkOff = true, tie = false),
        )
        val highlights = renderer.highlights(events)
        assertEquals(2, highlights.size)
        assertTrue(highlights.first().contains("홈런"))
        assertTrue(highlights.last().contains("끝내기"))
    }

    @Test
    fun `연장과 무승부가 표시된다`() {
        val tie = GameEnded(TeamId("AAA"), TeamId("BBB"), 3, 3, innings = 11, walkOff = false, tie = true)
        val line = renderer.render(listOf(tie)).first()
        assertTrue(line.contains("무승부"))
        assertTrue(line.contains("11회"))
    }
}
