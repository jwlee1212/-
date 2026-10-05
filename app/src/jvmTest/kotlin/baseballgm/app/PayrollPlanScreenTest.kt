package baseballgm.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import baseballgm.app.screen.ClubScreen
import baseballgm.app.ui.AppTheme
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** 구단 화면의 연봉 계획 탭이 그려지는지 (2026-10-04). */
@OptIn(ExperimentalTestApi::class)
class PayrollPlanScreenTest {

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
    fun `연봉 계획 탭에서 4년 전망과 선수별 연봉이 보인다`() = runComposeUiTest {
        val league = runBlocking { host.loadStartingLeague() }
        val session = host.newSession(league, league.teams.first().id, "테스트")
        setContent { AppTheme { ClubScreen(session) } }

        onNodeWithText("연봉 계획").performClick()
        waitForIdle()
        onNodeWithText("앞으로 4년").assertExists()
        val season = league.season
        onAllNodes(hasText("${season + 3}")).onFirst().assertExists()
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("선수별 연봉"))
        onNodeWithText("선수별 연봉").assertExists()
    }
}
