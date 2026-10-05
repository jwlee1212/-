package baseballgm.scouting

import baseballgm.market.DRAFT_BALANCE
import baseballgm.market.TEST_SEASON
import baseballgm.market.testBatter
import baseballgm.market.prospect
import baseballgm.model.Origin
import baseballgm.model.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 이름난 고교 유망주는 관찰 전부터 어느 정도 보인다 (유저 요청 2026-10-03, balance `scouting.knownProspect`) */
class KnownProspectTest {

    private val service = ScoutingService(DRAFT_BALANCE)
    private val viewer = TeamId("AAA")
    private fun department() = ScoutingDepartment(viewer, level = 3)

    private fun known(id: String, potential: Int = 86, teamId: TeamId? = null, origin: Origin = Origin.HIGH_SCHOOL) =
        testBatter(id, potential = potential, teamId = teamId, origin = origin).withKnownProspect(true)

    @Test
    fun `해마다 고졸 중 3~4명이 이름난 유망주가 되고 다시 정해도 같다`() {
        (2026..2035).forEach { season ->
            val pool = baseballgm.market.DraftPool(
                season,
                (1..30).map { prospect("H$season-$it", potential = 40 + it * 2, highSchool = true) } +
                    (1..10).map { prospect("C$season-$it", potential = 95, highSchool = false) },
            )
            val marked = service.budget.markKnownProspects(pool)
            val names = marked.prospects.filter { it.player.knownProspect }
            assertTrue(names.size in 3..4, "$season: ${names.size}명")
            assertTrue(names.all { it.isHighSchool }, "대졸이 주목 유망주가 됐다")
            // 흔들림(±4)이 있어도 맨 위 선수는 빠지지 않는다 (2점 간격)
            assertTrue(names.any { it.id.value == "H$season-30" }, "풀 최상위 고졸이 빠졌다")
            assertEquals(marked, service.budget.markKnownProspects(pool), "다시 정했더니 달라졌다")
            assertEquals(marked, service.budget.markKnownProspects(marked), "이미 정한 풀을 또 바꿨다")
        }
    }

    @Test
    fun `프로에 입단하거나 대졸이면 이름난 유망주로 보지 않는다`() {
        assertTrue(service.budget.isKnownProspect(known("K")))
        assertFalse(service.budget.isKnownProspect(known("P", teamId = viewer)), "프로 입단 후는 해당 없음")
        assertFalse(service.budget.isKnownProspect(known("C", origin = Origin.COLLEGE)), "대졸은 해당 없음")
        assertFalse(service.budget.isKnownProspect(testBatter("N", potential = 95)), "표시 없는 선수")
    }

    @Test
    fun `이름난 유망주는 관찰 없이도 범위가 좁고 관찰하면 더 좁아진다`() {
        val known = known("K")
        val plain = testBatter("N", potential = 74)
        val knownPrecision = service.precisionFor(department(), known, viewer)
        val plainPrecision = service.precisionFor(department(), plain, viewer)
        assertTrue(knownPrecision.halfWidth < plainPrecision.halfWidth, "${knownPrecision.halfWidth} vs ${plainPrecision.halfWidth}")
        assertTrue(knownPrecision.gradeSpread < plainPrecision.gradeSpread, "잠재력 등급 폭도 좁아야 한다")

        // 기본 주차 위에 실제 관찰 주차가 더해진다
        val watching = department()
        watching.addFocus(known.id, slots = 15)
        repeat(4) { watching.observeWeek() }
        assertTrue(service.precisionFor(watching, known, viewer).halfWidth < knownPrecision.halfWidth)
    }

    @Test
    fun `어느 구단이 봐도 같다`() {
        val known = known("K2", potential = 88)
        val mine = service.precisionFor(department(), known, viewer)
        val theirs = service.precisionFor(ScoutingDepartment(TeamId("BBB"), level = 3), known, TeamId("BBB"))
        assertEquals(mine, theirs)
    }

    @Test
    fun `리포트에 이름난 유망주 표시와 코멘트가 붙는다`() {
        val report = service.report(department(), known("K3"), viewer, TEST_SEASON)
        assertTrue(report.knownProspect)
        assertTrue(report.comments.first().contains("이름난 유망주"))
        assertFalse(service.report(department(), testBatter("N3", potential = 74), viewer, TEST_SEASON).knownProspect)
    }
}
