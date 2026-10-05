package baseballgm.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import baseballgm.app.screen.MainShell
import baseballgm.app.ui.AppTheme
import baseballgm.io.SaveGameCodec
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 스토브리그 준비 화면과 흐름 (2026-10-01, 진단 2번). */
@OptIn(ExperimentalTestApi::class)
class OffseasonPrepTest {

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
    private val league = runBlocking { host.loadStartingLeague() }

    /** 정규시즌·포스트시즌을 마친 세션 */
    private fun seasonDone(): GameSession {
        val session = host.newSession(league, league.teams.first().id, "테스트")
        val last = session.state.calendar.regularSeasonWeeks
        session.advanceUntil(last, delegate = true)
        if (session.isDraftWeek && !session.draftDone) {
            session.autoDraft()
            session.advanceUntil(last, delegate = true)
        }
        session.runPostseason()
        return session
    }

    @Test
    fun `시즌이 끝나면 진행 버튼이 준비 화면으로 보내고 브리핑에 겨울 결정이 뜬다`() {
        val session = seasonDone()
        assertTrue(session.offseasonPrep)
        assertIs<ProgressAction.PrepareOffseason>(ProgressAction.of(session))
        assertTrue(Briefing.decisions(session).any { it.target == BriefingTarget.OFFSEASON })
        assertEquals(session.offseasonPreview(), session.offseasonPreview(), "미리보기가 볼 때마다 바뀐다")
    }

    @Test
    fun `외국인과 결별하고 시작하면 그 선수가 떠나고 결과가 홈에 남는다`() {
        val session = seasonDone()
        val renewal = session.offseasonPreview().foreign.firstOrNull() ?: return
        session.setForeignResign(renewal.playerId, false)
        session.confirmOffseasonPlan()
        assertTrue(session.inFreeAgency, "확정하면 스토브리그가 돌고 FA 시장이 열린다")
        val report = assertNotNull(session.lastOffseason)
        assertTrue(report.planNotes.any { "결별" in it })
        assertTrue(report.foreignDepartures.any { it.playerId == renewal.playerId })
    }

    @Test
    fun `스태프를 경질하면 위약금이 나가고 우리 스태프에서 빠진다`() {
        val session = seasonDone()
        val coach = session.coaches().first()
        val before = session.state.funds.getValue(session.userTeamId)
        val cost = assertNotNull(session.fireStaff(coach.id))
        assertEquals(before - cost, session.state.funds.getValue(session.userTeamId), 1e-9)
        assertTrue(session.coaches().none { it.id == coach.id })
        assertTrue(session.state.currentLeague().coaches.first { it.id == coach.id }.teamId == null)
    }

    @Test
    fun `준비하다 껐다 켜도 계획이 남는다`() {
        val session = seasonDone()
        val candidate = session.offseasonPreview().releaseCandidates.first { it.playerId !in session.offseasonPlan().releases }
        session.toggleRelease(candidate.playerId)
        val save = assertNotNull(SaveGameCodec.parseOrNull(SaveGameCodec.encode(assertNotNull(session.saveData()))))
        val restored = host.resume(save)
        assertTrue(restored.offseasonPrep)
        assertEquals(session.offseasonPlan(), restored.offseasonPlan())
        assertTrue(candidate.playerId in restored.offseasonPlan().releases)
    }

    @Test
    fun `화면에서 준비 화면을 열고 시작하면 FA 시장이 열린다`() = runComposeUiTest {
        val session = seasonDone()
        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }
        waitForIdle()
        onAllNodes(hasClickAction() and hasText("스토브리그 준비", substring = true)).onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("방출 명단", substring = true).onFirst().assertExists()
        onAllNodes(hasClickAction() and hasText("이대로 스토브리그 시작")).onFirst().performScrollTo().performClick()
        waitForIdle()
        onAllNodes(hasClickAction() and hasText("시작할게요")).onFirst().performClick()
        waitForIdle()
        assertTrue(session.inFreeAgency)
    }
}
