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
import baseballgm.league.AwardKind
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 통산 기록·시상식·포스트시즌 다시 보기·역대 (2026-10-01, 진단 3번). */
@OptIn(ExperimentalTestApi::class)
class HistoryFlowTest {

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

    private fun finishSeason(session: GameSession) {
        val last = session.state.calendar.regularSeasonWeeks
        session.advanceUntil(last, delegate = true)
        if (session.isDraftWeek && !session.draftDone) {
            session.autoDraft()
            session.advanceUntil(last, delegate = true)
        }
        session.runPostseason()
    }

    @Test
    fun `시상식에서 본 수상자가 그대로 역사에 남고 다음 시즌 통산에 이어진다`() {
        val session = host.newSession(league, league.teams.first().id, "테스트")
        finishSeason(session)
        val season = session.league.season
        val awards = session.seasonAwards()
        assertTrue(awards.any { it.kind == AwardKind.MVP })
        assertTrue(session.postseasonGames.isNotEmpty() && session.postseasonGames.all { it.events.isNotEmpty() }, "포스트시즌 중계 이벤트가 없다")

        // 통산을 볼 선수: 올 시즌 1군에서 뛴 우리 타자
        val veteran = session.roster(baseballgm.model.RosterLevel.FIRST_TEAM).filterIsInstance<baseballgm.model.Batter>()
            .maxBy { session.batting(it.id).plateAppearances }
        val thisYear = session.batting(veteran.id)

        session.confirmOffseasonPlan()
        session.skipFreeAgency()
        if (session.unemployed) return

        val record = assertNotNull(session.history().seasonOf(season))
        assertEquals(awards, record.awards, "시상식과 역사 기록이 다르다")
        assertEquals(awards, session.awardsFor(season))

        // 새 시즌: 지난 시즌 기록이 통산에 남아 있다
        if (session.inLeague(veteran.id)) {
            val player = session.player(veteran.id)
            val lines = session.careerLines(player)
            assertEquals(season, lines.last().season)
            assertEquals(thisYear.homeRuns, lines.last().bat?.hr)
            session.advanceWeek(delegate = true)
            val after = session.careerLines(session.player(veteran.id))
            if (session.batting(veteran.id).plateAppearances > 0) {
                assertEquals(season + 1, after.last().season, "올 시즌 진행 중 기록이 통산에 안 붙었다")
                assertEquals(thisYear.plateAppearances + session.batting(veteran.id).plateAppearances, session.careerTotal(session.player(veteran.id))?.bat?.pa)
            }
        }
    }

    @Test
    fun `홈에서 포스트시즌 다시 보기와 시상식을 열고 경기를 중계로 본다`() = runComposeUiTest {
        val session = host.newSession(league, league.teams.first().id, "테스트")
        finishSeason(session)
        val mvp = session.seasonAwards().first { it.kind == AwardKind.MVP }
        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }
        waitForIdle()

        onAllNodes(hasClickAction() and hasText("포스트시즌 다시 보기", substring = true)).onFirst().performScrollTo().performClick()
        waitForIdle()
        onAllNodes(hasClickAction() and hasText("차전", substring = true)).onFirst().performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithText("포스트시즌 중계").onFirst().assertExists()
        onAllNodesWithText("경기 시작", substring = true).onFirst().assertExists()

        // 홈으로 돌아가 시상식
        onAllNodes(hasClickAction() and hasText("단장실")).onFirst().performClick()
        waitForIdle()
        onAllNodes(hasClickAction() and hasText("시상식", substring = true)).onFirst().performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithText(mvp.name, substring = true).onFirst().assertExists()
        onAllNodesWithText("골든글러브").onFirst().assertExists()
    }
}
