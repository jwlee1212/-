package baseballgm.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import baseballgm.app.screen.MainShell
import baseballgm.app.ui.AppTheme
import baseballgm.events.IncidentKind
import baseballgm.io.LeagueLoader
import baseballgm.model.Pitcher
import baseballgm.model.RosterLevel
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 돌발 이벤트 흐름(멈춤 → 답 → 이어 가기), 브리핑 결정 항목, 단장 직접 엔트리 — 화면과 세션 양쪽에서. */
@OptIn(ExperimentalTestApi::class)
class IncidentFlowTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))

    private fun session(seed: Long = 4242L) = GameSession(balance, league, league.teams.first().id, seed = seed)

    /** 돌발 이벤트가 뜰 때까지 진행 버튼을 누른다 (비서에게 안 맡기고) */
    private fun untilIncident(session: GameSession): Boolean {
        var guard = 0
        while (session.pendingIncident == null && !session.seasonOver && guard++ < 200) {
            if (session.isDraftWeek && !session.draftDone) session.autoDraft()
            session.advanceWeek()
        }
        return session.pendingIncident != null
    }

    @Test
    fun `돌발 이벤트가 생기면 주중에 멈추고 답하면 이어서 간다`() {
        val session = session()
        assertTrue(untilIncident(session), "한 시즌 동안 돌발 이벤트가 하나도 없었다")
        val incident = session.pendingIncident!!
        val week = session.week
        val games = session.record().games

        // 멈춘 동안: 진행 버튼은 "결정", 브리핑 맨 위에 급한 결정, 다시 눌러도 경기가 안 늘어난다
        assertEquals(ProgressAction.ResolveIncident, ProgressAction.of(session))
        assertEquals(BriefingTarget.INCIDENT, Briefing.decisions(session).first().target)
        session.advanceWeek()
        assertEquals(games, session.record().games)

        session.resolveIncident(incident.options.first().id)
        val record = assertNotNull(session.lastIncidentRecord)
        assertEquals(incident.headline, record.headline)
        assertTrue(!record.delegated)

        // 같은 주의 남은 경기를 이어 가거나 (주중에 멈췄던 경우) 다음 결정으로
        while (session.pendingIncident != null) session.delegateIncident()
        if (session.weekPaused) assertIs<ProgressAction.ContinueWeek>(ProgressAction.of(session))
        session.advanceWeek(delegate = true)
        assertEquals(week + 1, session.week)
        assertTrue(session.lastReport!!.incidents.any { it.headline == incident.headline })
        assertTrue(session.incidentLog().isNotEmpty())
    }

    @Test
    fun `자동 진행은 계약 결정에만 멈추고 부상·콜업 등은 비서가 처리한다`() {
        val session = session(77L)
        session.advanceUntil(balance.int("season.allStarBreakAfterWeek"))
        session.pendingIncident?.let { assertTrue(it.kind.contract, "계약과 무관한 ${it.kind}에 멈췄다") }
        session.incidentLog().filter { !it.kind.contract }.forEach { assertTrue(it.delegated, "${it.kind}를 비서가 처리하지 않았다") }
    }

    @Test
    fun `자동 진행은 계약 결정에 답하면 목표 주차까지 이어 간다`() {
        val target = balance.int("season.allStarBreakAfterWeek")
        val session = (1L..20L).map { session(it) }.first { candidate ->
            candidate.advanceUntil(target)
            candidate.pendingIncident != null
        }
        var decisions = 0
        while (session.pendingIncident != null && decisions++ < 30) {
            val incident = session.pendingIncident!!
            assertTrue(incident.kind.contract)
            assertEquals(target, session.autoTarget)
            session.resolveIncident(incident.options.last().id)
            assertEquals(incident.headline, session.lastIncidentRecord?.headline, "단장 답의 후속 한마디가 남는다")
        }
        assertNull(session.pendingIncident)
        assertNull(session.autoTarget)
        assertTrue(session.week > target, "답한 뒤 ${session.week}주차에서 멈췄다")
    }

    @Test
    fun `트레이드 제안에 시장에서 답하면 돌발 카드도 내려간다`() {
        val session = session()
        var guard = 0
        while (session.pendingIncident?.kind != IncidentKind.TRADE_OFFER && guard++ < 60 && !session.seasonOver) {
            if (session.pendingIncident != null) session.delegateIncident() else session.advanceWeek()
            if (session.isDraftWeek && !session.draftDone) session.autoDraft()
        }
        if (session.pendingIncident?.kind != IncidentKind.TRADE_OFFER) return // 이 시드에선 제안이 없었다
        session.rejectPendingTrade()
        assertNull(session.pendingTradeOffer())
        assertTrue(session.state.pendingIncidents.none { it.kind == IncidentKind.TRADE_OFFER })
    }

    @Test
    fun `로스터에서 맞바꾸면 28명을 지키고 브리핑이 빈자리를 알린다`() {
        val session = session()
        val up = session.roster(RosterLevel.FUTURES).first { it !is Pitcher && session.swapOutCandidates(it).isNotEmpty() }
        val down = session.swapOutCandidates(up).first()
        val size = session.roster(RosterLevel.FIRST_TEAM).size
        assertTrue(session.swap(up, down))
        assertEquals(size, session.roster(RosterLevel.FIRST_TEAM).size)

        // 하나 더 내리면 빈자리 → 브리핑 결정 항목
        val more = session.roster(RosterLevel.FIRST_TEAM).first { session.demoteProblem(it) == null }
        assertTrue(session.demote(more))
        assertTrue(Briefing.decisions(session).any { it.target == BriefingTarget.ROSTER })
    }

    // ---------- 화면 ----------

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

    @Test
    fun `단장실 허브에 다음 경기·메시지·타일이 있고 결정 일지는 메시지의 미스 백 대화에 있다`() = runComposeUiTest {
        val loaded = runBlocking { host.loadStartingLeague() }
        val session = host.newSession(loaded, loaded.teams.first().id, "테스트")
        var guard = 0
        while (session.incidentLog().isEmpty() && guard++ < 20) session.advanceWeek(delegate = true)
        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }
        waitForIdle()
        // 2026-10-03 화면 개편: 허브 = 다음 경기 매치업 + 메시지 미리보기 + 타일 넷
        onAllNodesWithText("다음 경기", substring = true).onFirst().assertExists()
        listOf("선수단", "영입", "구단주 목표", "리그").forEach { onAllNodesWithText(it).onFirst().assertExists() }
        listOf("단장실", "선수단", "영입", "메시지", "리그").forEach { onAllNodesWithText(it).onFirst().assertExists() }

        // 메시지 탭 → 미스 백 대화에 이번 시즌 결정이 쌓여 있다 (예전 뉴스 화면의 결정 일지)
        onAllNodes(hasClickAction() and hasText("메시지")).onFirst().performClick()
        waitForIdle()
        onAllNodes(hasClickAction() and hasText(baseballgm.app.ui.Secretary.NAME, substring = true)).onFirst().performClick()
        waitForIdle()
        val first = session.incidentLog().first()
        onAllNodesWithText(first.headline, substring = true).onFirst().assertExists()
    }

    @Test
    fun `돌발 이벤트는 메시지 탭에서 답하고 후속 한마디가 남는다`() = runComposeUiTest {
        val loaded = runBlocking { host.loadStartingLeague() }
        val session = host.newSession(loaded, loaded.teams.first().id, "테스트")
        assertTrue(untilIncident(session))
        val incident = session.pendingIncident!!
        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }
        waitForIdle()
        // 2026-10-03: 급한 일만 창으로 뜨고, 나머지는 메시지 탭에서 답한다. 어느 쪽이든 메시지 탭 카드로 답할 수 있다
        if (incident.kind.urgent) {
            onAllNodes(hasClickAction() and hasText(incident.options.first().label)).onLast().performClick()
        } else {
            onAllNodes(hasClickAction() and hasText("메시지")).onFirst().performClick()
            waitForIdle()
            onAllNodes(hasClickAction() and hasText(incident.options.first().label)).onFirst().performClick()
        }
        waitForIdle()
        assertTrue(session.state.pendingIncidents.none { it.id == incident.id })
        assertEquals(incident.options.first().label, session.lastIncidentRecord?.choice)
    }
}
