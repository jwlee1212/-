package baseballgm.app

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import baseballgm.app.screen.MainShell
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.rememberCountUp
import baseballgm.io.LeagueLoader
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 절제된 움직임 (2026-10-03, 재미 개선 5번) */
@OptIn(ExperimentalTestApi::class)
class MotionTest {

    @Test
    fun `숫자는 처음엔 그대로 보이고 바뀔 때만 이전 값에서 새 값으로 간다`() = runComposeUiTest {
        var target by mutableIntStateOf(10)
        mainClock.autoAdvance = false
        setContent { AppTheme { Text("값 ${rememberCountUp(target, "test")}") } }
        mainClock.advanceTimeByFrame()
        onNodeWithText("값 10").assertExists() // 처음 그릴 때 0부터 올라가지 않는다

        target = 30
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(250)
        val mid = onAllNodesWithText("값 ", substring = true).fetchSemanticsNodes().single()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text.removePrefix("값 ").toInt()
        assertTrue(mid in 11..29, "바뀌는 도중에는 사이 값이어야 한다: $mid")

        mainClock.advanceTimeBy(2_000)
        onNodeWithText("값 30").assertExists()
    }

    @Test
    fun `주를 시작할 때 순위를 찍어 둔다`() {
        val balance = ProjectFiles.loadBalanceConfig()
        val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))
        val session = GameSession(balance, league, TeamId("SWR"), seed = 31L)
        repeat(3) { session.advanceWeek(delegate = true) }
        val before = session.teamsRanked().mapIndexed { index, record -> record.teamId to index + 1 }.toMap()
        session.advanceWeek(delegate = true)
        assertEquals(before, session.ranksAtWeekStart, "주 시작 순위 = 진행 직전 순위")
        assertEquals(league.teams.size, session.ranksAtWeekStart.size)
    }

    @Test
    fun `순위표에 지난주 대비 오르내림 화살표가 나온다`() = runComposeUiTest {
        val host = HostFactory.create(
            ProjectFiles.read(ProjectFiles.BALANCE_PATH),
            ProjectFiles.read(ProjectFiles.TEAMS_PATH),
            { ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)) },
            object : SaveStore {
                override fun read(): String? = null
                override fun write(text: String) = Unit
            },
            null,
        )
        val start = runBlocking { host.loadStartingLeague() }
        val session = host.newSession(start, start.teams.first().id, "테스트")
        repeat(4) { session.advanceWeek(delegate = true) }
        val moved = session.teamsRanked().withIndex().count { (index, record) ->
            session.ranksAtWeekStart[record.teamId] != index + 1
        }
        assertTrue(moved > 0, "4주 차엔 순위가 바뀐 팀이 있어야 이 테스트가 의미 있다")
        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }
        onAllNodesWithText("리그").onFirst().performClick()
        waitForIdle()
        // 2026-10-04 리그 탭 허브: 순위표는 "순위" 카드를 눌러 들어간다
        onAllNodesWithContentDescription("순위 전체 보기").onFirst().performClick()
        waitForIdle()
        val arrows = onAllNodesWithContentDescription("계단", substring = true).fetchSemanticsNodes().size
        assertEquals(moved, arrows, "순위가 바뀐 팀 수만큼 화살표")
    }
}
