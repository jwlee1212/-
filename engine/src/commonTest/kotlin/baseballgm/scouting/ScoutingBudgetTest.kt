package baseballgm.scouting

import baseballgm.io.BalanceConfig
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val BALANCE = BalanceConfig.parse(
    """
    {
      "scouting": {
        "defaultLevel": 3,
        "focusSlotsByLevel": { "1": 3, "2": 5, "3": 8, "4": 11, "5": 15 },
        "amateurHalfWidthByLevel": { "1": 18, "2": 16, "3": 13, "4": 11, "5": 9 },
        "annualCostByLevel": { "1": 0.5, "2": 1.2, "3": 2.2, "4": 3.6, "5": 5.5 },
        "focus": {
          "narrowingPerWeek": 0.55,
          "halfWidthFloor": 3,
          "gradeSpreadWeeks": [6, 13],
          "growthTypeReliableWeeks": 16,
          "newsEveryWeeks": 3
        },
        "aiEvaluationNoise": 0.55,
        "potentialGrades": { "C": 54, "B": 64, "A": 76, "S": 84, "edgeHalfBand": 4.0 },
        "coreProspect": { "maxAge": 24, "perTeam": 3, "minGrade": "B" },
        "knownProspect": { "perClass": [3, 4], "rankNoise": 4.0, "headStartWeeks": 12 }
      }
    }
    """.trimIndent(),
)

/** 스카우트 투자 단계와 집중 관찰 (docs/10). */
class ScoutingBudgetTest {

    private val budget = ScoutingBudget(BALANCE)

    @Test
    fun `투자 단계가 오르면 슬롯이 늘고 기본 정확도가 좋아진다`() {
        assertEquals(3, budget.focusSlots(1))
        assertEquals(15, budget.focusSlots(5))
        assertTrue(budget.amateurPrecision(1).halfWidth > budget.amateurPrecision(5).halfWidth)
        assertTrue(budget.annualCost(5) > budget.annualCost(1), "높은 단계가 더 비싸야 한다")
    }

    @Test
    fun `준주전급 공개 기록은 관찰해도 능력치는 정확한 채로 잠재력·숨김 성질만 좁혀진다`() {
        val base = ScoutingAccuracy.ESTABLISHED.precision
        assertEquals(0, base.halfWidth)
        assertTrue(base.traitHalfWidth > 0 && !base.traitsExact)
        val watched = budget.refine(base, focusWeeks = 30)
        assertEquals(0, watched.halfWidth)
        assertTrue(watched.traitHalfWidth < base.traitHalfWidth, "관찰해도 숨김 성질이 그대로다")
        assertEquals(base.label, watched.label)
        // 우리 팀은 더 볼 게 없다
        assertEquals(ScoutingAccuracy.OWN_TEAM.precision, budget.refine(ScoutingAccuracy.OWN_TEAM.precision, 30))
    }

    @Test
    fun `범위 밖 단계는 양 끝으로 자른다`() {
        assertEquals(budget.focusSlots(1), budget.focusSlots(0))
        assertEquals(budget.focusSlots(5), budget.focusSlots(9))
    }

    @Test
    fun `관찰을 오래 할수록 범위가 좁아지고 바닥에서 멈춘다`() {
        val base = budget.amateurPrecision(3)
        val fourWeeks = budget.refine(base, 4)
        val twentyWeeks = budget.refine(base, 20)

        assertTrue(fourWeeks.halfWidth < base.halfWidth)
        assertTrue(twentyWeeks.halfWidth < fourWeeks.halfWidth)
        assertEquals(3, twentyWeeks.halfWidth, "바닥(3)보다 더 좁아지면 안 된다")
    }

    @Test
    fun `오래 보면 잠재력 등급이 좁아지고 성장 타입을 확신한다`() {
        val base = budget.amateurPrecision(3)
        assertEquals(2, base.gradeSpread)
        assertEquals(1, budget.refine(base, 7).gradeSpread)
        assertEquals(0, budget.refine(base, 14).gradeSpread)
        assertFalse(budget.refine(base, 14).growthTypeReliable)
        assertTrue(budget.refine(base, 16).growthTypeReliable)
    }

    @Test
    fun `정확한 정보는 관찰해도 그대로다`() {
        val exact = ScoutingAccuracy.OWN_TEAM.precision
        assertEquals(exact, budget.refine(exact, 20))
    }
}

/** 집중 관찰 슬롯 관리. */
class ScoutingDepartmentTest {

    private val team = TeamId("AAA")
    private val budget = ScoutingBudget(BALANCE)

    @Test
    fun `슬롯이 꽉 차면 더 붙일 수 없다`() {
        val department = ScoutingDepartment(team, level = 1)
        val slots = budget.focusSlots(department.level)
        repeat(slots) { assertTrue(department.addFocus(PlayerId("P$it"), slots)) }
        assertFalse(department.addFocus(PlayerId("PX"), slots), "슬롯 3개인데 4명을 봤다")

        department.removeFocus(PlayerId("P0"))
        assertTrue(department.addFocus(PlayerId("PX"), slots))
    }

    @Test
    fun `관찰 주차는 보고 있는 선수만 쌓인다`() {
        val department = ScoutingDepartment(team, level = 3)
        department.addFocus(PlayerId("P1"), 8)
        repeat(4) { department.observeWeek() }
        assertEquals(4, department.weeksOn(PlayerId("P1")))
        assertEquals(0, department.weeksOn(PlayerId("P2")))
    }

    @Test
    fun `슬롯에서 빼도 본 기억은 남지만 더 쌓이지 않는다`() {
        val department = ScoutingDepartment(team, level = 3)
        department.addFocus(PlayerId("P1"), 8)
        repeat(3) { department.observeWeek() }
        department.removeFocus(PlayerId("P1"))
        repeat(5) { department.observeWeek() }
        assertEquals(3, department.weeksOn(PlayerId("P1")))
    }

    @Test
    fun `슬롯이 줄면 오래 본 선수부터 남는다`() {
        val department = ScoutingDepartment(team, level = 3)
        department.addFocus(PlayerId("오래"), 8)
        repeat(5) { department.observeWeek() }
        department.addFocus(PlayerId("방금"), 8)

        department.setLevel(1)
        department.trimToSlots(1)
        assertTrue(department.isFocused(PlayerId("오래")))
        assertFalse(department.isFocused(PlayerId("방금")))
    }
}
