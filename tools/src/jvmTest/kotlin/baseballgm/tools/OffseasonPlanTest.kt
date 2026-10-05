package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
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

/** 스토브리그 단장 결정 (2026-10-01, 진단 2번). 계획이 미리보기대로, 그대로 처리되는가. */
class OffseasonPlanTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val strength = StrengthCalculator(balance)
    private val user = league.teams.first().id
    private val seed = 99L

    /** 한 시즌을 끝까지 돌린 상태 (대량 시뮬 경로 — 유저 개입 없음) */
    private fun finishedSeason(): SeasonState {
        val state = SeasonState.of(league, SeasonCalendar.from(balance), balance)
        val loop = WeekLoop(balance, league)
        val random = Random(seed)
        while (!state.isRegularSeasonOver) loop.playWeek(state, random, validate = false)
        return state
    }

    private fun randomFor(state: SeasonState) = { id: baseballgm.model.PlayerId ->
        Seeds.random(seed, state.season, Seeds.Phase.OFFSEASON_PLAN, id.value.hashCode().toLong())
    }

    @Test
    fun `미리보기는 몇 번을 봐도 같고 추천 계획은 비서 추천을 그대로 담는다`() {
        val state = finishedSeason()
        val offseason = Offseason(balance, strength)
        val a = offseason.preview(state, user, randomFor(state))
        val b = offseason.preview(state, user, randomFor(state))
        assertEquals(a, b)
        val plan = offseason.defaultPlan(a)
        a.foreign.forEach { assertEquals(if (it.recommendResign) it.asking else null, plan.foreign[it.playerId]) }
        assertEquals(a.releaseCandidates.filter { it.recommended }.map { it.playerId }.toSet(), plan.releases)
        assertTrue(a.releaseCandidates.isNotEmpty())
        // 방출 후보는 남길 가치가 낮은 순
        assertEquals(a.releaseCandidates.sortedBy { it.keepScore }, a.releaseCandidates)
    }

    @Test
    fun `계획대로 처리된다 — 외국인 재계약 액수, 깎은 연봉과 서운한 개막 폼, 방출 명단`() {
        val state = finishedSeason()
        val offseason = Offseason(balance, strength)
        val preview = offseason.preview(state, user, randomFor(state))
        var plan = offseason.defaultPlan(preview)

        // 외국인: 첫 번째는 재계약, 나머지는 결별
        val foreign = preview.foreign
        if (foreign.isNotEmpty()) {
            plan = plan.copy(foreign = foreign.mapIndexed { i, it -> it.playerId to (if (i == 0) it.asking else null) }.toMap())
        }
        // 연봉: 첫 번째만 깎는다
        val lowballed = preview.salaries.firstOrNull()
        if (lowballed != null) {
            plan = plan.copy(salaries = plan.salaries + (lowballed.playerId to baseballgm.season.SalaryDecision(lowballed.lowball, lowball = true)))
        }
        // 방출: 추천 대신 맨 아래 두 명만
        val releases = preview.releaseCandidates.take(2).map { it.playerId }.toSet()
        plan = plan.copy(releases = releases)

        val (next, report) = offseason.run(
            state, Seeds.random(seed, state.season, Seeds.Phase.OFFSEASON), RookieFactory(balance, strength, league),
            autoFreeAgency = true, userTeam = user, plan = plan,
        )

        foreign.forEachIndexed { i, renewal ->
            val player = next.players.firstOrNull { it.id == renewal.playerId }
            if (i == 0) {
                // 은퇴하지 않았으면 미리보기 액수 그대로 재계약
                if (renewal.playerId !in report.retired) {
                    assertEquals(user, assertNotNull(player).teamId)
                    assertEquals(renewal.asking, player.contract.salary)
                }
            } else {
                assertTrue(player == null || player.teamId != user, "결별한 외국인이 남아 있다")
            }
        }
        lowballed?.let { case ->
            if (case.playerId !in report.retired) {
                val player = assertNotNull(next.players.firstOrNull { it.id == case.playerId && it.teamId == user })
                assertEquals(case.lowball, player.contract.salary, "깎아서 제시한 액수로 계약되지 않았다")
                assertEquals(50 + balance.int("offseasonDecisions.lowballForm"), player.condition.form, "서운한 개막 폼")
            }
        }
        releases.forEach { id ->
            assertTrue(next.players.none { it.id == id && it.teamId == user }, "방출 명단의 선수가 남아 있다")
        }
        assertTrue(report.planNotes.isNotEmpty())
        // 유저 구단은 최대 인원까지 둘 수 있다
        assertTrue(next.players.count { it.teamId == user } <= offseason.userRosterCap)
    }

    @Test
    fun `방출을 하나도 안 하면 최대 인원에서 자동으로 잘린다`() {
        val state = finishedSeason()
        val offseason = Offseason(balance, strength)
        val preview = offseason.preview(state, user, randomFor(state))
        val plan = offseason.defaultPlan(preview).copy(releases = emptySet())
        val (next, report) = offseason.run(
            state, Seeds.random(seed, state.season, Seeds.Phase.OFFSEASON), RookieFactory(balance, strength, league),
            autoFreeAgency = true, userTeam = user, plan = plan,
        )
        assertTrue(next.players.count { it.teamId == user } <= offseason.userRosterCap)
        if (preview.projectedRoster > offseason.userRosterCap) {
            assertTrue(report.planNotes.any { "추가 방출" in it })
        }
    }
}
