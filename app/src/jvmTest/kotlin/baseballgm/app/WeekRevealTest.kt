package baseballgm.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import baseballgm.app.screen.MainShell
import baseballgm.app.ui.AppTheme
import baseballgm.io.LeagueLoader
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 한 주 결과 공개 (2026-10-02, 재미 개선 1번) */
@OptIn(ExperimentalTestApi::class)
class WeekRevealTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(
        ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)),
    )

    /** 여러 주를 진행하며 매주 공개 데이터를 만들어 [check] 에 넘긴다 (그 주가 끝난 직후의 세션과 함께) */
    private fun eachWeek(weeks: Int, check: (GameSession, WeekReveal) -> Unit) {
        val session = GameSession(balance, league, TeamId("SWR"), seed = 777L)
        repeat(weeks) {
            val before = session.rank()
            session.advanceWeek(delegate = true)
            check(session, WeekReveal.of(session, session.lastReport!!.week, before))
        }
    }

    @Test
    fun `공개하는 경기는 이번 주 우리 경기와 같고 점수·전적이 박스스코어와 맞다`() {
        eachWeek(6) { session, reveal ->
            val report = session.lastReport!!
            val mine = report.games.filter { it.home.teamId == session.userTeamId || it.away.teamId == session.userTeamId }
            assertEquals(mine.size, reveal.games.size, "${reveal.week}주차 경기 수")
            assertTrue(reveal.complete)
            assertEquals(reveal.games.size, reveal.played, "끝난 주는 모든 경기에 결과가 있다")
            val week = Briefing.weekRecord(session, report)
            assertEquals(week.wins, reveal.wins)
            assertEquals(week.losses, reveal.losses)
            assertEquals(week.ties, reveal.ties)
            reveal.games.forEach { slot ->
                assertNotNull(slot.ourStarter, "치른 경기는 선발 투수가 있다")
                assertNotNull(slot.theirStarter)
                val game = slot.result!!
                if (game.outcome != GameOutcome.TIE) assertNotNull(game.decisions, "승부가 난 경기는 승패 투수 줄이 있다")
                assertEquals(game.ourScore, game.ourInnings.sum(), "라인 스코어 합 = 점수")
                assertEquals(game.theirScore, game.theirInnings.sum())
                assertTrue(game.tags.size <= 2, "경기 성격 칩은 2개까지")
                when (game.outcome) {
                    GameOutcome.WIN -> assertTrue(game.ourScore > game.theirScore)
                    GameOutcome.LOSS -> assertTrue(game.ourScore < game.theirScore)
                    GameOutcome.TIE -> assertEquals(game.ourScore, game.theirScore)
                }
                if (game.outcome != GameOutcome.TIE) assertNotNull(game.keyPlay, "승부가 난 경기는 결승 장면이 있다")
            }
            assertEquals(session.rank(), reveal.rankAfter)
        }
    }

    @Test
    fun `이번 주 영웅은 우리 선수이고 같은 주는 몇 번 만들어도 같다`() {
        eachWeek(4) { session, reveal ->
            reveal.hero?.let { assertEquals(session.userTeamId, session.player(it.playerId).teamId) }
            assertEquals(reveal, WeekReveal.of(session, reveal.week, reveal.rankBefore))
        }
    }

    @Test
    fun `주중 이벤트로 멈추면 그때까지 치른 경기만 공개하고 답하면 이어서 나머지가 생긴다`() {
        val session = GameSession(balance, league, TeamId("SWR"), seed = 777L)
        var checked = false
        repeat(12) {
            if (checked) return@repeat
            val week = session.week
            session.advanceWeek()
            while (session.weekPaused || session.pendingIncident != null) {
                val partial = WeekReveal.of(session, week, null)
                if (session.pendingIncident != null && session.weekGamesSoFar().isNotEmpty() && !checked) {
                    assertTrue(!partial.complete)
                    assertEquals(session.weekGamesSoFar().size, partial.played, "공개할 수 있는 건 지금까지 치른 경기뿐")
                    assertTrue(partial.played < partial.games.size, "남은 경기는 아직 결과가 없다")
                    assertTrue(partial.games.drop(partial.played).all { it.result == null && it.ourStarter == null })
                    session.delegateIncident()
                    session.advanceWeek()
                    val resumed = WeekReveal.of(session, week, null)
                    assertTrue(resumed.played > partial.played || resumed.complete, "답하면 남은 경기가 이어진다")
                    assertTrue(resumed.incidents.isNotEmpty(), "답한 이벤트가 공개 목록에 남는다")
                    assertTrue(resumed.incidents.all { it.afterGame < resumed.games.size })
                    checked = true
                } else {
                    if (session.pendingIncident != null) session.delegateIncident()
                    session.advanceWeek()
                }
            }
        }
        assertTrue(checked, "12주 안에 경기 뒤 돌발 이벤트가 한 번은 있어야 한다")
    }

    @Test
    fun `진행 버튼 → 공개 화면이 주를 진행하고 주중 이벤트 창에 답하면 이어서 공개 → 홈으로`() = runComposeUiTest {
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
        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }

        onNodeWithText("1주차 진행").performClick()
        waitForIdle()
        // 공개 화면이 주를 진행한다. 주중 돌발 이벤트는 공개가 따라오면 창으로 뜬다 → 비서에게 맡기면 공개가 이어진다
        var guard = 0
        var sawIncident = false
        while (session.lastReport?.week != 1) {
            check(guard++ < 20) { "주가 끝나지 않는다" }
            waitForIdle()
            // 이벤트 창의 "맡길게요" = 비서 추천대로 답하기
            if (onAllNodesWithText("맡길게요").fetchSemanticsNodes().isNotEmpty()) {
                onNodeWithText("맡길게요").performClick()
                sawIncident = true
            }
            mainClock.advanceTimeBy(2_000)
        }
        waitForIdle()
        // 이 구단·시드의 1주차엔 화요일 경기 뒤 기자 질문이 생긴다 — 공개 도중 창이 떴다가 답한 뒤 이어졌어야 한다
        assertTrue(sawIncident, "주중 이벤트 창이 공개 화면에서 떠야 한다")
        // 남은 연출이 끝날 때까지 테스트 시계를 돌린다 (건너뛰기 버튼은 맨 위라 스크롤되면 안 보인다)
        mainClock.advanceTimeBy(30_000)
        waitForIdle()
        // 요약 카드는 지연 목록 맨 아래라 화면 밖이면 아직 안 그려져 있다 → 스크롤해서 찾는다
        val list = onAllNodes(hasScrollToNodeAction()).onFirst()
        list.performScrollToNode(hasText("1주차 결과 정리했어요."))
        list.performScrollToNode(hasText("이번 주 영웅"))
        list.performScrollToNode(hasText("홈으로"))
        onNodeWithText("홈으로").performClick()
        waitForIdle()
        // 홈으로 돌아오면 다음 주 진행 버튼이 다시 보인다
        assertTrue(onAllNodes(hasText("2주차 진행")).fetchSemanticsNodes().isNotEmpty() || session.pendingIncident != null)
    }
}
