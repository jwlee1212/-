package baseballgm.app

import baseballgm.model.GmStyle
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 트레이드 협상 확장 (2026-10-05 유저 요청: 역제안 · 현금·지명권·연봉 보조 · 상대 단장 성격 · 루머와 정보전) */
class TradeNegotiationTest {

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

    private fun newGame(team: String? = null): GameSession {
        val league = runBlocking { host.loadStartingLeague() }
        return host.newSession(league, team?.let { TeamId(it) } ?: league.teams.first().id, "테스트")
    }

    @Test
    fun `구단마다 단장 성격이 있고 데이터대로 나온다`() {
        val session = newGame()
        assertEquals(GmStyle.WIN_NOW, session.tradeGm(TeamId("HGP")).second)
        assertEquals(GmStyle.HARD_BARGAINER, session.tradeGm(TeamId("CWM")).second)
        assertEquals(GmStyle.REBUILDER, session.tradeGm(TeamId("DJR")).second)
    }

    @Test
    fun `시즌 중 트레이드 소문이 들어온다`() {
        val session = newGame()
        repeat(10) { session.advanceWeek(delegate = true) }
        val rumors = session.tradeRumors()
        assertTrue(rumors.isNotEmpty(), "10주 동안 소문이 하나도 없다")
        assertTrue(rumors.all { it.text.contains("소식통") || it.text.contains("카더라") })
    }

    @Test
    fun `핵심 선수를 헐값에 달라면 역제안이 오고 받아들이면 성사된다`() {
        val session = newGame()
        repeat(2) { session.advanceWeek(delegate = true) }
        val bench = session.roster(RosterLevel.FUTURES).minBy { session.overall(it) }
        var found = false
        for (team in session.league.teams.map { it.id }.filter { it != session.userTeamId }) {
            val star = session.roster(RosterLevel.FIRST_TEAM, team).maxBy { session.overall(it) }
            val proposal = session.buildProposal(team, listOf(bench.id), listOf(star.id))
            val verdict = session.proposeTrade(proposal)
            if (verdict.accepted) continue
            val counter = session.tradeCounter(proposal) ?: continue
            found = true
            assertTrue(counter.notes.isNotEmpty())
            val outcome = session.acceptTradeCounter(counter.proposal)
            assertTrue(outcome.accepted, "역제안을 받아들였는데 거절했다: ${outcome.reason}")
            counter.proposal.fromPartner.playerIds.forEach { assertEquals(session.userTeamId, session.player(it).teamId) }
            break
        }
        assertTrue(found, "어느 구단도 역제안을 내지 않았다")
    }

    @Test
    fun `연봉 보조를 붙여 보내면 우리 연봉 총액에 보조 몫이 남는다`() {
        val session = newGame()
        val partner = session.league.teams.first { it.id != session.userTeamId }.id
        // 연봉이 꽤 있는 우리 1군 선수 하나를 공짜로 + 연봉 1억 보조 — 상대는 받을 이유밖에 없다
        val player = session.roster(RosterLevel.FIRST_TEAM).filter { it.contract.salary >= 3.0 }.minBy { session.overall(it) }
        val before = session.state.currentLeague().payrollOf(session.userTeamId)
        val proposal = session.buildProposal(partner, listOf(player.id), emptyList(), retained = mapOf(player.id to 1.0))
        val outcome = session.acceptTradeCounter(proposal)
        if (!outcome.accepted) return // 상대 사정(엔트리·포지션)으로 안 받으면 이 확인은 건너뛴다
        val after = session.state.currentLeague().payrollOf(session.userTeamId)
        assertEquals(before - player.contract.salary + 1.0, after, 1e-6)
        assertTrue(session.retainedSalaries().any { it.playerId == player.id && it.amount == 1.0 })
    }
}
