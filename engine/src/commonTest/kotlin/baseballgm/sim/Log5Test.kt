package baseballgm.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Log5Test {

    @Test
    fun `평균 선수끼리 붙으면 리그 평균이 나온다`() {
        val league = 0.19
        assertEquals(league, Log5.rate(league, league, league), absoluteTolerance = 1e-9)
    }

    @Test
    fun `한쪽이 평균이면 다른 쪽 비율이 그대로 나온다`() {
        val league = 0.19
        assertEquals(0.30, Log5.rate(0.30, league, league), absoluteTolerance = 1e-9)
        assertEquals(0.10, Log5.rate(league, 0.10, league), absoluteTolerance = 1e-9)
    }

    @Test
    fun `극단 매치업에서도 0과 1 사이를 벗어나지 않는다`() {
        val values = listOf(0.0, 0.001, 0.2, 0.5, 0.9, 0.999, 1.0)
        for (batter in values) {
            for (pitcher in values) {
                for (league in listOf(0.05, 0.19, 0.5, 0.8)) {
                    val rate = Log5.rate(batter, pitcher, league)
                    assertTrue(rate in 0.0..1.0, "B=$batter P=$pitcher L=$league → $rate")
                }
            }
        }
    }

    @Test
    fun `좋은 타자와 나쁜 투수가 만나면 둘 중 더 좋은 쪽보다 높아진다`() {
        // 삼진처럼 "낮을수록 좋은" 결과가 아니라 비율 그 자체로 본다
        val rate = Log5.rate(batter = 0.35, pitcher = 0.28, league = 0.19)
        assertTrue(rate > 0.35, "좋은 타자 0.35 + 잘 맞는 투수 0.28 → $rate")
    }

    @Test
    fun `나쁜 타자와 좋은 투수가 만나면 둘 중 더 낮은 쪽보다 낮아진다`() {
        val rate = Log5.rate(batter = 0.12, pitcher = 0.10, league = 0.19)
        assertTrue(rate < 0.10, "$rate")
    }

    @Test
    fun `타자와 투수를 바꿔도 결과가 같다`() {
        val a = Log5.rate(0.26, 0.14, 0.19)
        val b = Log5.rate(0.14, 0.26, 0.19)
        assertTrue(abs(a - b) < 1e-12)
    }
}
