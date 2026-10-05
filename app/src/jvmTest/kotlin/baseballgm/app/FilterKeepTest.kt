package baseballgm.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import baseballgm.app.screen.MainShell
import baseballgm.app.ui.AppTheme
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * 선수 상세에 들어갔다 나와도 목록 필터가 남는지 화면을 직접 눌러 확인한다.
 *
 * 스카우트 탭은 상세를 목록 안에서 갈아 끼우고 있어서, 목록이 사라졌다 다시 만들어지며 필터가 풀렸다.
 */
@OptIn(ExperimentalTestApi::class)
class FilterKeepTest {

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

    private fun session(): GameSession {
        val league = runBlocking { host.loadStartingLeague() }
        return host.newSession(league, league.teams.first().id, "테스트")
    }

    @Test
    fun `스카우트 리포트를 보고 돌아와도 필터가 남는다`() = checkFilterKept(tabs = listOf("영입", "스카우트"), rowHint = "세 · ") // 2026-10-02 후보 줄: 나이 · 고졸·대졸

    @Test
    fun `로스터 선수 상세를 보고 돌아와도 필터가 남는다`() = checkFilterKept(tabs = listOf("선수단"), rowHint = "세") // 2026-10-02 목록 줄: 이름 + 포지션 배지 + 나이

    private fun checkFilterKept(tabs: List<String>, rowHint: String) = runComposeUiTest {
        val session = session()
        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }

        // 2026-10-03 탭 개편: 스카우트는 영입 탭 안의 구역이라 두 번 누른다
        tabs.forEach { clickTab(it); waitForIdle() }
        // 로스터는 전력 요약·뎁스·리그 비교 카드 아래에 목록이 있어서 필터까지 내려간다
        scrollTo("필터")
        onNodeWithText("필터").performClick()
        // 펼친 칩이 하단 탭 막대 밑에 깔릴 수 있다 (선수단 탭 위쪽 카드가 늘어서, 2026-10-04).
        // 칩 줄은 반만 보여도 "보인다"로 쳐서, 칩 줄 다음 줄(목록 안내·목록)까지 내려 둔다
        val spChip = hasText("SP") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(rowHint, substring = true) and hasClickAction())
        onAllNodes(spChip).onFirst().performClick()
        onNodeWithText("1개 적용").assertExists()

        // 목록 첫 줄(선수 카드)을 눌러 상세로 들어간다
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasClickAction() and hasText(rowHint, substring = true))
        onAllNodes(hasClickAction() and hasText(rowHint, substring = true)).onFirst().performClick()
        waitForIdle()
        onNodeWithContentDescription("뒤로").performClick()
        waitForIdle()

        scrollTo("1개 적용")
        onNodeWithText("1개 적용").assertExists()
    }

    private fun SemanticsNodeInteractionsProvider.scrollTo(text: String) {
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
    }

    private fun SemanticsNodeInteractionsProvider.clickTab(label: String) {
        onAllNodesWithText(label).onFirst().performClick()
    }
}
