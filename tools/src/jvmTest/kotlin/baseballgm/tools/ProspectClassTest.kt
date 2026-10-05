package baseballgm.tools

import baseballgm.league.StrengthCalculator
import baseballgm.market.DraftPool
import baseballgm.scouting.ScoutingAccuracy
import baseballgm.scouting.ScoutingBudget
import baseballgm.scouting.ScoutingView
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 해마다의 드래프트 풀 (유저 요청 2026-10-04): 이름난 유망주 3~4명, 역대급 재능은 3~4년에 한 명.
 * 이전 방식(잠재력 기준선)에서는 첫해 이후 이름난 유망주가 0~1명인 해가 많았다.
 */
class ProspectClassTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val budget = ScoutingBudget(balance)
    private val exact = ScoutingAccuracy.OWN_TEAM.precision
    private val generationalFloor = balance.section("draft").doubleRange("generational.potential").start

    @Test
    fun `40년 동안 해마다 이름난 유망주가 3~4명이고 역대급 재능은 3~4년에 한 명꼴이다`() {
        val factory = ProspectFactory(balance, StrengthCalculator(balance), seed = 77L, startingIdNumber = 900_000)
        val random = Random(123)
        var generationalYears = 0
        repeat(YEARS) { i ->
            val season = 2027 + i
            val pool = budget.markKnownProspects(DraftPool(season, factory.create(season, balance.int("draft.poolSize"), random)))
            val known = pool.prospects.filter { it.player.knownProspect }
            assertTrue(known.size in 3..4, "$season 이름난 유망주 ${known.size}명")
            assertTrue(known.all { it.isHighSchool })
            if (pool.prospects.any { ScoutingView.potentialRange(it.player, exact).low >= generationalFloor }) generationalYears++
        }
        // 기대값 30% × 40년 = 12 해. 우연 폭을 넉넉히 둔다
        assertTrue(generationalYears in 6..20, "역대급 재능이 나온 해 $generationalYears/$YEARS")
    }

    private companion object {
        const val YEARS = 40
    }
}
