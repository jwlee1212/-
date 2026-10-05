package baseballgm.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import baseballgm.app.screen.RosterScreen
import baseballgm.app.ui.AppTheme
import baseballgm.io.LeagueLoader
import baseballgm.model.Player
import baseballgm.model.RosterLevel
import baseballgm.season.RosterSlot
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 엔트리 맞바꾸기 (2026-10-01 유저 지적):
 * "내리면 최소 인원 미달, 그냥 올리면 꽉 차서 한 명 내려야 하는" 모순 없이 한 번에 바꿀 수 있어야 한다.
 */
@OptIn(ExperimentalTestApi::class)
class RosterSwapTest {
    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))
    private val catcherRange = balance.intRange("roster.positionLimits.positions.C")

    /** 2군에 올릴 수 있는 포수가 있는 구단으로 시작한다 */
    private fun session(): GameSession {
        val team = league.teams.first { team ->
            val probe = GameSession(balance, league, team.id, seed = 1L)
            probe.roster(RosterLevel.FUTURES).count { RosterSlot.of(it) == RosterSlot.C && probe.eligibilityProblem(it) == null } >= 2
        }
        return GameSession(balance, league, team.id, seed = 1L)
    }

    private fun GameSession.catchers(level: RosterLevel): List<Player> =
        roster(level).filter { RosterSlot.of(it) == RosterSlot.C && !it.condition.isInjured }

    /** 1군 포수를 정확히 n 명으로 맞춘다 (맞바꾸기로) */
    private fun GameSession.setCatchers(n: Int) {
        while (catchers(RosterLevel.FIRST_TEAM).size > n) {
            val down = catchers(RosterLevel.FIRST_TEAM).minBy { overall(it) }
            val up = swapInCandidates(down).first { RosterSlot.of(it) != RosterSlot.C }
            assertTrue(swap(up, down))
        }
        while (catchers(RosterLevel.FIRST_TEAM).size < n) {
            val up = catchers(RosterLevel.FUTURES).first { eligibilityProblem(it) == null }
            val down = swapOutCandidates(up).first { RosterSlot.of(it) != RosterSlot.C }
            assertTrue(swap(up, down))
        }
    }

    @Test
    fun `포지션 제한이 없어서 포수도 혼자 말소할 수 있다`() {
        val session = session()
        session.setCatchers(catcherRange.first)
        val starter = session.catchers(RosterLevel.FIRST_TEAM).first()
        assertNull(session.demoteProblem(starter))
        assertTrue(session.demote(starter))
        assertEquals(catcherRange.first - 1, session.catchers(RosterLevel.FIRST_TEAM).size)
    }

    @Test
    fun `꽉 찬 1군에는 아무나와 맞바꿔 올린다 — 같은 포지션이 먼저 보인다`() {
        val session = session()
        session.setCatchers(catcherRange.last)
        val up = session.catchers(RosterLevel.FUTURES).first { session.eligibilityProblem(it) == null }
        assertNotNull(session.promoteProblem(up), "28명이 찼으면 혼자는 못 올린다")
        val candidates = session.swapOutCandidates(up)
        assertEquals(RosterSlot.C, RosterSlot.of(candidates.first()), "같은 포지션이 맨 앞")
        assertEquals(session.roster(RosterLevel.FIRST_TEAM).size, candidates.size, "포지션 제한이 없으니 1군 누구와도 바꿀 수 있다")
        val pitcher = candidates.first { RosterSlot.of(it) == RosterSlot.SP || RosterSlot.of(it) == RosterSlot.RP }
        assertTrue(session.swap(up, pitcher))
        assertEquals(catcherRange.last + 1, session.catchers(RosterLevel.FIRST_TEAM).size)
    }

    @Test
    fun `화면에서 말소를 누르면 말소만 하거나 대신 올릴 선수를 골라 한 번에 바꾼다`() = runComposeUiTest {
        val session = session()
        session.setCatchers(catcherRange.first)
        val starter = session.catchers(RosterLevel.FIRST_TEAM).first()
        val replacement = session.swapInCandidates(starter).first()
        setContent { AppTheme { RosterScreen(session, onPlayer = {}) } }
        waitForIdle()

        // 그 포수 줄까지 스크롤한 뒤, 그 줄 안의 "말소"를 누른다
        // 위쪽 뎁스 차트에도 이름이 나오므로, "말소" 버튼을 품은 목록 줄을 찾아 스크롤한다
        val row = hasClickAction() and hasText(starter.registeredName, substring = true) and hasAnyDescendant(hasText("말소"))
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(row)
        waitForIdle()
        // 줄(카드)은 이름을 품은 클릭 가능한 노드, "말소"는 그 안의 버튼이다
        onAllNodes(row).onFirst().onChildren().filterToOne(hasText("말소")).performClick()
        waitForIdle()
        onAllNodesWithText("대신 올릴 2군 선수").onFirst().assertExists()
        onAllNodesWithText("말소만 하기", substring = true).onFirst().assertExists()

        onAllNodes(hasClickAction() and hasText(replacement.registeredName, substring = true)).onFirst().performClick()
        waitForIdle()
        assertEquals(RosterLevel.FUTURES, session.player(starter.id).rosterLevel)
        assertEquals(RosterLevel.FIRST_TEAM, session.player(replacement.id).rosterLevel)
        assertEquals(catcherRange.first, session.catchers(RosterLevel.FIRST_TEAM).size)
    }
}
