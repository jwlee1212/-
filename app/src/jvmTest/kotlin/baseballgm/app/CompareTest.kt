package baseballgm.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import baseballgm.app.screen.CompareScreen
import baseballgm.app.screen.MainShell
import baseballgm.app.ui.AppTheme
import baseballgm.io.LeagueLoader
import baseballgm.market.TradePackage
import baseballgm.market.TradeProposal
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 선수 비교·트레이드 화면의 타 팀 선수 정보 (2026-10-01). */
@OptIn(ExperimentalTestApi::class)
class CompareTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))
    private fun session() = GameSession(balance, league, league.teams.first().id, seed = 4242L)

    private fun otherTeamStar(session: GameSession) =
        session.state.firstTeamOf(league.teams[1].id).maxBy { session.overall(it) }

    @Test
    fun `비교함은 네 명까지 담고 넘치면 가장 먼저 담은 선수를 밀어낸다`() {
        val session = session()
        val ids = session.roster(RosterLevel.FIRST_TEAM).take(5).map { it.id }
        ids.forEach { session.toggleCompare(it) }
        assertEquals(ids.drop(1), session.compareList.toList())
        session.toggleCompare(ids[2])
        assertEquals(listOf(ids[1], ids[3], ids[4]), session.compareList.toList())
        session.clearCompare()
        assertTrue(session.compareList.isEmpty())
    }

    @Test
    fun `다른 팀 선수 핵심 능력치는 범위로만, 우리 선수는 정확히 나온다`() {
        val session = session()
        val theirs = otherTeamStar(session)
        val ours = session.roster(RosterLevel.FIRST_TEAM).first()
        assertTrue(session.keyRatings(theirs).all { (_, range) -> !range.isExact }, "타 팀 선수 능력치가 정확히 보인다")
        assertTrue(session.keyRatings(ours).all { (_, range) -> range.isExact })
        assertTrue(session.seasonSummary(theirs).isNotBlank())
    }

    @Test
    fun `비교 화면은 이름과 범위를 보여 주고 이름을 누르면 상세로 간다`() = runComposeUiTest {
        val session = session()
        val ours = session.roster(RosterLevel.FIRST_TEAM).maxBy { session.overall(it) }
        val theirs = otherTeamStar(session)
        var opened: PlayerId? = null
        setContent { AppTheme { CompareScreen(session, listOf(ours.id, theirs.id)) { opened = it } } }
        waitForIdle()
        onAllNodesWithText(theirs.registeredName).onFirst().assertExists()
        onAllNodesWithText("우리 팀").onFirst().assertExists()
        // 타 팀 선수 종합은 "낮음~높음" 범위로
        onAllNodesWithText(session.scout(theirs).overall.toString(), substring = true).onFirst().assertExists()
        onAllNodes(hasClickAction() and hasText(theirs.registeredName)).onFirst().performClick()
        waitForIdle()
        assertEquals(theirs.id, opened)
    }

    // ---------- 들어온 트레이드 제안 ----------

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
    fun `들어온 제안에서 선수 이름을 누르면 상세, 비교 버튼은 비교 화면`() = runComposeUiTest {
        val loaded = runBlocking { host.loadStartingLeague() }
        val session = host.newSession(loaded, loaded.teams.first().id, "테스트")
        val partner = loaded.teams[1].id
        val theirs = session.state.firstTeamOf(partner).maxBy { session.overall(it) }
        val ours = session.roster(RosterLevel.FIRST_TEAM).maxBy { session.overall(it) }
        session.state.pendingTradeOffer = TradeProposal(
            proposer = partner,
            partner = session.userTeamId,
            fromProposer = TradePackage(listOf(theirs.id)),
            fromPartner = TradePackage(listOf(ours.id)),
        )
        setContent { AppTheme { MainShell(host, session, welcome = false, onDismissWelcome = {}) } }
        waitForIdle()
        onAllNodesWithText("영입").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("들어온 제안", substring = true).onFirst().assertExists()

        onAllNodes(hasClickAction() and hasText(theirs.registeredName, substring = true)).onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("선수 상세").onFirst().assertExists()
        onAllNodesWithText("정보 정확도").onFirst().assertExists()

        // 뒤로 → 비교
        onAllNodes(hasClickAction() and hasText("영입")).onFirst().performClick()
        waitForIdle()
        onAllNodes(hasClickAction() and hasText("제안 선수 한눈에 비교")).onFirst().performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithText("선수 비교").onFirst().assertExists()
        onAllNodesWithText(ours.registeredName).onFirst().assertExists()
    }
}
