package baseballgm.events

import baseballgm.league.LeagueEnvironment
import baseballgm.market.DRAFT_BALANCE
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 리그 환경 이벤트 (docs/04). */
class LeagueEnvironmentEventTest {

    private val events = LeagueEnvironmentEvents(DRAFT_BALANCE)

    @Test
    fun `절반 정도는 변화가 없다`() {
        val changes = (1..400).map { events.next(Random(it)) }
        val unchanged = changes.count { !it.changed }
        assertTrue(unchanged in 120..280, "변화 없음이 ${unchanged}/400")
        changes.filterNot { it.changed }.forEach {
            assertEquals(LeagueEnvironment.NEUTRAL, it.environment)
            assertTrue(it.announcement.contains("변화 없음"))
        }
    }

    @Test
    fun `공인구와 스트라이크존 두 갈래로 바뀐다`() {
        val changed = (1..400).map { events.next(Random(it)) }.filter { it.changed }
        assertTrue(changed.any { it.environment.homeRunMultiplier != 1.0 }, "공인구 변화가 없다")
        assertTrue(changed.any { it.environment.strikeoutDelta != 0.0 }, "스트라이크존 변화가 없다")
        assertTrue(changed.all { it.announcement.isNotBlank() })
    }

    @Test
    fun `존이 넓어지면 삼진이 늘고 볼넷이 줄어든다`() {
        val zone = (1..400).map { events.next(Random(it)) }
            .filter { it.environment.strikeoutDelta != 0.0 }
        assertTrue(zone.isNotEmpty())
        zone.forEach { change ->
            val k = change.environment.strikeoutDelta
            val bb = change.environment.walkDelta
            assertTrue(k * bb < 0.0, "삼진 $k 와 볼넷 $bb 이 같은 방향으로 움직였다")
        }
    }

    @Test
    fun `변화 폭은 작게 유지된다`() {
        val changed = (1..400).map { events.next(Random(it)) }.filter { it.changed }
        changed.forEach { change ->
            assertTrue(change.environment.homeRunMultiplier in 0.8..1.25, "홈런 배율 ${change.environment.homeRunMultiplier}")
            assertTrue(kotlin.math.abs(change.environment.strikeoutDelta) < 0.05)
        }
    }
}
