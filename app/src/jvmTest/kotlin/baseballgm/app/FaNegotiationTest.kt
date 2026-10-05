package baseballgm.app

import baseballgm.market.FaTalkOutcome
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FA 협상 테이블 (docs/11, 2026-10-04 유저 요청 "FM식 협상 — 세부 조건을 조율하는 별개의 창") */
class FaNegotiationTest {

    private val host = HostFactory.create(
        ProjectFiles.read(ProjectFiles.BALANCE_PATH),
        ProjectFiles.read(ProjectFiles.TEAMS_PATH),
        { ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)) },
        object : SaveStore {
            override fun read(): String? = null
            override fun write(text: String) = Unit
        },
        null,
    )

    private fun marketSession(): GameSession {
        val league = runBlocking { host.loadStartingLeague() }
        val session = host.newSession(league, league.teams.first().id, "테스트")
        val weeks = session.balance.int("season.regularSeasonWeeks")
        session.advanceUntil(weeks, delegate = true)
        session.autoDraft()
        session.advanceUntil(weeks, delegate = true)
        session.startNextSeason()
        assertTrue(session.inFreeAgency, "FA 시장이 열려야 한다")
        return session
    }

    @Test
    fun `요구 조건은 쓸 수 있는 계약금 안에서 나오고 모자라게 내면 역제안 · 맞추면 바로 도장`() {
        val session = marketSession()
        session.faAgents().forEach { each ->
            val wanted = assertNotNull(session.faDemand(each))
            assertTrue(wanted.signingBonus <= session.faBonusBudget(each) + 1e-6, "못 낼 계약금을 요구했다: $wanted")
            assertTrue(wanted.salary + wanted.signingBonus / wanted.years + 0.05 >= each.salaryFloor, "최저 연봉보다 낮게 요구했다: $wanted")
        }
        // 최저 연봉이 걸리지 않는 선수 — 요구보다 15% 싸게 불러도 최저 연봉은 넘는다
        val agent = session.faAgents().first { each ->
            val wanted = session.faDemand(each)!!
            (wanted.salary + wanted.signingBonus / wanted.years) * 0.85 > each.salaryFloor
        }
        val demand = assertNotNull(session.faDemand(agent))
        val short = demand.asOffer(session.userTeamId, agent.playerId).let { it.copy(salary = it.salary * 0.85, signingBonus = it.signingBonus * 0.85) }
        val first = assertNotNull(session.proposeFa(agent, short))
        assertTrue(first.outcome == FaTalkOutcome.COUNTERED || first.outcome == FaTalkOutcome.CONSIDERING, "${first.outcome} ${first.message}")
        assertNotNull(session.faOffer(agent), "모자란 제안도 시장에 남는다")
        assertEquals(session.faMaxPatience - 1, session.faPatience(agent.playerId))
        assertEquals(2, session.faTalkLog(agent.playerId).size)

        val counter = assertNotNull(first.counter)
        val second = assertNotNull(session.proposeFa(agent, counter))
        assertEquals(FaTalkOutcome.SIGNED, second.outcome, second.message)
        assertNull(session.faAgent(agent.playerId), "도장 찍은 선수는 시장에서 빠진다")
        assertNotNull(session.faOurSigning(agent.playerId))
    }

    @Test
    fun `옵션 조항을 넣은 계약은 시장이 닫히면 계약에 조항이 남고 선수가 가능성을 매긴다`() {
        val session = marketSession()
        // 보장 연봉을 1억 줄여도 최저 연봉이 넘는 선수
        val agent = session.faAgents().first { each ->
            val wanted = session.faDemand(each)!!
            wanted.salary - 1.0 + wanted.signingBonus / wanted.years > each.salaryFloor
        }
        val kinds = session.faOptionKinds(agent)
        assertTrue(baseballgm.market.OptionKind.TEAM_POSTSEASON in kinds)
        assertTrue(kinds.all { it.pitcher == null || it.pitcher == session.isPitcher(session.faPlayer(agent)) }, "투수·타자 조항이 섞였다")
        // 선수가 가장 해낼 만하다고 보는 조항에 넉넉히 건다
        val easiest = kinds.maxBy { session.faOptionOutlook(agent, it).second }
        val demand = assertNotNull(session.faDemand(agent))
        val offer = demand.asOffer(session.userTeamId, agent.playerId).copy(
            salary = demand.salary - 1.0,
            options = listOf(baseballgm.market.OptionClause(easiest, 3.0)),
        )
        val result = assertNotNull(session.proposeFa(agent, offer))
        assertTrue(result.outcome == FaTalkOutcome.SIGNED || result.outcome == FaTalkOutcome.COUNTERED, "${result.outcome} ${result.message}")
        if (result.outcome != FaTalkOutcome.SIGNED) session.proposeFa(agent, assertNotNull(result.counter))
        session.skipFreeAgency()
        val signed = session.player(agent.playerId)
        assertEquals(session.userTeamId, signed.teamId)
        assertEquals(listOf(baseballgm.market.OptionClause(easiest, 3.0)), signed.contract.options)
    }
}
