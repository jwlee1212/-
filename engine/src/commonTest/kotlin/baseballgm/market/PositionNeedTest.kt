package baseballgm.market

import baseballgm.league.StrengthCalculator
import baseballgm.model.PitcherRole
import baseballgm.model.Position
import baseballgm.model.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 포지션 필요도 (docs/11). 드래프트·FA·트레이드가 같은 값을 쓴다. */
class PositionNeedTest {

    private val team = TeamId("AAA")
    private val need = PositionNeed(DRAFT_BALANCE, StrengthCalculator(DRAFT_BALANCE))

    private fun lineup(vararg ratings: Pair<Position, Int>) =
        ratings.mapIndexed { index, (position, rating) ->
            testBatter("B$index", rating = rating, position = position, age = 27, teamId = team)
        }

    @Test
    fun `주전이 없는 포지션은 필요도가 가장 높다`() {
        val roster = lineup(Position.CATCHER to 40)
        val value = need.forPosition(roster, Position.CATCHER)
        assertTrue(value >= 1.3, "주전 없음인데 $value")
        assertTrue(value <= 1.5)
    }

    @Test
    fun `주전은 있고 백업이 없으면 중간`() {
        val roster = lineup(Position.SHORTSTOP to 70)
        assertEquals(1.1, need.forPosition(roster, Position.SHORTSTOP), 0.001)
    }

    @Test
    fun `주전급이 둘이면 넘친다`() {
        val roster = lineup(Position.SHORTSTOP to 72, Position.SHORTSTOP to 68)
        assertEquals(0.8, need.forPosition(roster, Position.SHORTSTOP), 0.001)
    }

    @Test
    fun `선발이 모자라면 투수 필요도가 올라간다`() {
        val empty = emptyList<baseballgm.model.Player>()
        assertTrue(need.forPitcherRole(empty, PitcherRole.STARTER) >= 1.3)

        val full = (1..6).map { testPitcher("SP$it", rating = 68, age = 27, teamId = team) }
        assertEquals(0.8, need.forPitcherRole(full, PitcherRole.STARTER), 0.001)
    }

    @Test
    fun `영입 이후 상태로 계산한다`() {
        val roster = lineup(Position.THIRD_BASE to 70)
        val incoming = testBatter("NEW", rating = 70, position = Position.THIRD_BASE, age = 24, teamId = team)
        val before = need.of(roster, incoming)
        val after = need.afterAdding(roster, listOf(incoming), incoming)
        assertTrue(after < before, "같은 포지션 두 번째는 가치가 떨어져야 한다 ($before → $after)")
    }
}
