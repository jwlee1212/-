package baseballgm.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import baseballgm.app.screen.MainShell
import baseballgm.app.ui.AppTheme
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 드래프트를 화면으로 끝까지 치른 뒤의 상태를 확인한다.
 *
 * 끝난 뒤에도 지명 안 된 선수에게 "관찰" 버튼이 살아 있고, 지명된 선수가 슬롯을 계속 차지하던 문제를 막는다.
 */
@OptIn(ExperimentalTestApi::class)
class DraftEndTest {
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
    fun `생중계로 드래프트를 끝까지 치르면 관찰이 정리되고 다음 주로 넘어간다`() = runComposeUiTest {
        val league = runBlocking { host.loadStartingLeague() }
        val session = host.newSession(league, league.teams.first().id, "테스트")
        val watched = session.draftBoard(limit = 5)
        watched.forEach { session.toggleFocus(it) }
        val draftWeek = host.balance.int("season.draftWeek")
        session.advanceUntil(draftWeek, delegate = true)
        // 직접 붙인 5명 + 자동 관찰이 빈 슬롯을 채운다
        assertEquals(session.focusSlots, session.focusUsed)

        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }
        // 2026-10-03: 스카우트는 영입 탭 안 (드래프트 주차엔 스카우트 구역이 저절로 열린다)
        onAllNodesWithText("영입").onFirst().performClick()
        var guard = 0
        while (!session.draftDone && guard++ < 2000) {
            mainClock.advanceTimeBy(800)
            waitForIdle()
            if (session.isMyDraftTurn) {
                // 남은 선수 목록(지연 목록)은 화면 밖이면 아직 안 그려져 있다 → "지명" 버튼까지 스크롤 (2026-10-03 영입 탭 안으로 옮기며 위가 길어졌다)
                onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasClickAction() and hasText("지명"))
                onAllNodes(hasClickAction() and hasText("지명")).onFirst().performClick()
                waitForIdle()
            }
        }
        assertTrue(session.draftDone, "드래프트가 끝나지 않았다 (${session.draftSelections().size}/${session.draftTotalPicks})")

        // 관찰 슬롯이 비고, 지명된 선수는 결과를 보여준다
        assertEquals(0, session.focusUsed, "드래프트가 끝났는데 슬롯이 남았다")
        watched.forEach { prospect ->
            assertTrue(session.wasWatched(prospect.id), "관찰 이력이 사라졌다")
            assertFalse(session.canFocus(prospect))
            assertFalse(session.toggleFocus(prospect), "끝난 드래프트 후보에 관찰이 붙었다")
        }
        assertNotNull(session.selectionOf(watched.first()))

        // 끝나면 우리 신인 화면이 저절로 열린다 (2026-10-03) — 능력치가 정확한 값으로 보인다
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        onNodeWithText("계약 마쳤어요", substring = true).assertExists()
        assertTrue(session.myDraftClass().all { it.second.scouted.isExactView })
        // 영입 탭을 다시 누르면 스카우트 화면으로 돌아온다
        onAllNodesWithText("영입").onFirst().performClick()
        waitForIdle()

        // 드래프트 풀 탭: 종료 안내가 나오고 "관찰" 버튼이 없다
        onAllNodesWithText("드래프트 풀").onFirst().performClick()
        waitForIdle()
        onNodeWithText("올해 드래프트는 끝났어요").assertExists()
        assertTrue(onAllNodes(hasClickAction() and hasText("관찰")).fetchSemanticsNodes().isEmpty(), "끝난 뒤에도 관찰 버튼이 있다")

        // 홈으로 돌아가 다음 주로 넘어간다
        onAllNodesWithText("단장실").onFirst().performClick()
        waitForIdle()
        // 주중에 돌발 이벤트가 생기면 창이 뜨고 멈춘다 → 비서에게 맡기고 이어서 진행
        var steps = 0
        while (session.week == draftWeek && steps++ < 20) {
            if (session.pendingIncident != null) {
                // 결과 공개 화면에서는 공개가 그 경기까지 따라온 뒤에 창이 뜬다 → 시계를 돌려 기다린다 (2026-10-02)
                mainClock.advanceTimeBy(30_000)
                waitForIdle()
                if (onAllNodes(hasClickAction() and hasText("맡길게요")).fetchSemanticsNodes().isNotEmpty()) {
                    onAllNodes(hasClickAction() and hasText("맡길게요")).onFirst().performClick()
                } else {
                    session.delegateIncident()
                }
            } else {
                onAllNodes(hasClickAction() and (hasText("진행", substring = true) or hasText("이어서", substring = true) or hasText("마무리", substring = true)))
                    .onFirst().performClick()
            }
            waitForIdle()
        }
        assertEquals(draftWeek + 1, session.week)
    }

    @Test
    fun `우리 지명도 맡기기를 켜면 손대지 않아도 드래프트가 끝까지 간다`() = runComposeUiTest {
        val league = runBlocking { host.loadStartingLeague() }
        val session = host.newSession(league, league.teams.first().id, "테스트")
        session.advanceUntil(host.balance.int("season.draftWeek"), delegate = true)
        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }
        onAllNodesWithText("영입").onFirst().performClick()
        waitForIdle()
        onAllNodes(isToggleable()).onFirst().performClick()
        var guard = 0
        while (!session.draftDone && guard++ < 2000) {
            mainClock.advanceTimeBy(800)
            waitForIdle()
        }
        assertTrue(session.draftDone, "자동 진행이 멈췄다 (${session.draftSelections().size}/${session.draftTotalPicks})")
        assertEquals(host.balance.int("draft.rounds"), session.myDraftPicks().size)
    }
}
