package baseballgm.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import baseballgm.app.screen.LeagueSection
import baseballgm.app.screen.PlayerDetailScreen
import baseballgm.app.ui.AppTheme
import baseballgm.io.LeagueLoader
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 기록표 · 팀 내 순위 · 리그 백분위 (2026-10-04) */
@OptIn(ExperimentalTestApi::class)
class StatBoardTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))
    private val session by lazy { GameSession(balance, league, TeamId("SWR"), seed = 77L).also { s -> repeat(8) { s.advanceWeek(delegate = true) } } }

    @Test
    fun `리그 기록표는 규정 이상만, 고른 열 순서대로`() {
        val ops = StatBoard.defaultBattingSort(StatView.BASIC)
        val rows = StatBoard.battingTable(session, StatScope.LEAGUE, StatView.BASIC, ops, descending = true)
        assertTrue(rows.size >= StatBoard.MIN_POPULATION, "규정 이상 타자가 ${rows.size}명")
        assertTrue(rows.all { it.line.plateAppearances >= session.qualifyingPa() })
        assertTrue(rows.zipWithNext().all { (a, b) -> a.line.ops >= b.line.ops }, "OPS 높은 순이 아니다")

        val ascending = StatBoard.battingTable(session, StatScope.LEAGUE, StatView.BASIC, ops, descending = false)
        assertEquals(rows.first().player.id, ascending.last().player.id, "방향을 바꾸면 순서가 뒤집혀야 한다")

        // 평균자책은 낮을수록 좋아서 기본 정렬이 오름차순
        val era = StatBoard.defaultPitchingSort(StatView.BASIC)
        assertTrue(StatBoard.pitchingColumns(StatView.BASIC)[era].lowerIsBetter)
    }

    @Test
    fun `우리 팀 표는 우리 선수 전원이고, 비율 기록은 표본이 적은 선수를 아래로`() {
        val ops = StatBoard.defaultBattingSort(StatView.BASIC)
        val rows = StatBoard.battingTable(session, StatScope.TEAM, StatView.BASIC, ops, descending = true)
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.player.teamId == session.userTeamId })
        val sample = StatBoard.batterSample(session)
        val firstSmall = rows.indexOfFirst { it.line.plateAppearances < sample }
        if (firstSmall >= 0) assertTrue(rows.drop(firstSmall).all { it.line.plateAppearances < sample }, "표본 적은 선수가 위에 섞였다")
    }

    @Test
    fun `선수 기록 머리 - 타일 넷과 팀 내 순위, 백분위 여섯`() {
        val best = StatBoard.battingTable(session, StatScope.TEAM, StatView.BASIC, StatBoard.defaultBattingSort(StatView.BASIC), true).first()
        val sheet = assertNotNull(PlayerStatSheet.of(session, best.player))
        assertEquals(listOf("OPS", "홈런", "wRC+", "WAR"), sheet.tiles.map { it.label })
        assertEquals(1, sheet.tiles.first().teamRank, "팀 OPS 1위가 1위로 안 나온다")
        sheet.tiles.forEach { tile -> tile.teamRank?.let { assertTrue(it in 1..tile.teamSize) } }

        val bars = assertNotNull(sheet.percentiles, sheet.note)
        assertEquals(6, bars.size)
        assertTrue(bars.all { it.percentile in 0..100 && it.topShare in 1..100 })

        // 리그 OPS 1위는 OPS 백분위가 100 에 가깝다
        val leagueBest = StatBoard.battingTable(session, StatScope.LEAGUE, StatView.BASIC, StatBoard.defaultBattingSort(StatView.BASIC), true).first()
        val top = assertNotNull(PlayerStatSheet.of(session, leagueBest.player)?.percentiles).first { it.label == "OPS" }
        assertTrue(top.percentile >= 95, "리그 OPS 1위의 백분위가 ${top.percentile}")
    }

    @Test
    fun `표본이 적으면 백분위 대신 까닭을, 기록이 없으면 머리가 없다`() {
        val sample = StatBoard.batterSample(session)
        val small = StatBoard.batters(session).firstOrNull { it.line.plateAppearances in 1 until sample }
        if (small != null) {
            val sheet = assertNotNull(PlayerStatSheet.of(session, small.player))
            assertNull(sheet.percentiles)
            assertTrue(sheet.note!!.contains("타석"))
        }
        val bench = session.state.allPlayers().first { it.teamId != null && session.batting(it.id).plateAppearances == 0 && session.pitching(it.id).outs == 0 }
        assertNull(PlayerStatSheet.of(session, bench))
    }

    @Test
    fun `리그 탭 - 우리 팀으로 바꾸면 우리 선수만, 머리글을 누르면 정렬이 바뀐다`() = runComposeUiTest {
        setContent { AppTheme { baseballgm.app.screen.LeagueSectionScreen(session, LeagueSection.BATTING, onPlayer = {}) } }
        waitForIdle()
        onAllNodesWithText("우리 팀").onFirst().performClick()
        waitForIdle()
        val ours = StatBoard.battingTable(session, StatScope.TEAM, StatView.BASIC, StatBoard.defaultBattingSort(StatView.BASIC), true)
        onAllNodesWithText(ours.first().player.registeredName).onFirst().assertExists()
        onAllNodesWithText("1군 기록이 있는 우리 선수 전원", substring = true).onFirst().assertExists()

        onAllNodesWithText("세이버").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("wRC+").onFirst().assertExists()
    }

    @Test
    fun `선수 상세 기록 탭에 팀 내 순위와 백분위가 나온다`() = runComposeUiTest {
        val best = StatBoard.battingTable(session, StatScope.TEAM, StatView.BASIC, StatBoard.defaultBattingSort(StatView.BASIC), true).first()
        setContent { AppTheme { PlayerDetailScreen(session, best.player.id) } }
        waitForIdle()
        onAllNodesWithText("기록").onFirst().performClick()
        waitForIdle()
        // 타일·막대는 읽기 쉬운 한 문장으로 합쳐 읽힌다 (접근성 라벨)
        onAllNodesWithContentDescription("팀 내 1위", substring = true).onFirst().assertExists()
        onAllNodesWithText("리그 백분위").onFirst().assertExists()
        onAllNodesWithContentDescription("리그 ", substring = true).onFirst().assertExists()
    }

    @Test
    fun `필터 - 유격수만, 최소 타석 직접 지정`() {
        val ops = StatBoard.defaultBattingSort(StatView.BASIC)
        val shortstops = StatBoard.battingTable(
            session, StatScope.LEAGUE, StatView.BASIC, ops, true,
            StatFilter(StatPosition.SS, SampleMode.ALL),
        )
        assertTrue(shortstops.isNotEmpty())
        assertTrue(shortstops.all { (it.player as baseballgm.model.Batter).primaryPosition == baseballgm.model.Position.SHORTSTOP })

        val fifty = StatBoard.battingTable(session, StatScope.LEAGUE, StatView.BASIC, ops, true, StatFilter(StatPosition.ALL_BATTERS, SampleMode.CUSTOM, custom = 50))
        assertTrue(fifty.all { it.line.plateAppearances >= 50 })
        assertTrue(fifty.size > StatBoard.battingTable(session, StatScope.LEAGUE, StatView.BASIC, ops, true).size, "규정보다 낮은 기준인데 더 적다")

        val bullpen = StatBoard.pitchingTable(session, StatScope.TEAM, StatView.BASIC, 0, true, StatFilter(StatPosition.BULLPEN, SampleMode.ALL))
        assertTrue(bullpen.isNotEmpty() && bullpen.all { (it.player as baseballgm.model.Pitcher).role.isReliever })
    }

    @Test
    fun `팀 기록 - 경기 수는 순위표와 같고, 리그 안타 합은 선수 기록 합과 같다`() {
        val teams = StatBoard.teams(session)
        assertEquals(session.league.teams.size, teams.size)
        teams.forEach { team -> assertEquals(session.record(team.teamId).games, team.totals.games, "${team.name} 경기 수") }
        // 트레이드가 있어도 리그 전체 합은 같다 (팀 쪽엔 뛴 경기의 팀으로, 선수 쪽엔 선수 누적으로 쌓인다)
        assertEquals(StatBoard.batters(session).sumOf { it.line.hits }, teams.sumOf { it.totals.batting.hits })
        assertEquals(StatBoard.pitchers(session).sumOf { it.line.earnedRuns }, teams.sumOf { it.totals.pitching.earnedRuns })

        val era = StatBoard.defaultTeamSort(TeamView.PITCHING)
        val table = StatBoard.teamTable(session, TeamView.PITCHING, era, descending = false)
        assertTrue(table.zipWithNext().all { (a, b) -> a.totals.pitching.era <= b.totals.pitching.era }, "팀 평균자책 낮은 순이 아니다")
        val (_, rank) = assertNotNull(StatBoard.teamRankOf(session, TeamView.PITCHING, "평균자책"))
        assertEquals(table.indexOfFirst { it.teamId == session.userTeamId } + 1, rank)
    }

    @Test
    fun `선수단 탭의 기록 바로가기는 리그 탭 우리 팀 기록표로 연다`() = runComposeUiTest {
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
        val loaded = kotlinx.coroutines.runBlocking { host.loadStartingLeague() }
        val played = host.newSession(loaded, loaded.teams.first().id, "테스트").also { s -> repeat(4) { s.advanceWeek(delegate = true) } }
        setContent { AppTheme { baseballgm.app.screen.MainShell(host, played, welcome = false, onDismissWelcome = {}) } }
        waitForIdle()
        onAllNodesWithText("선수단").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("타자 기록 · 팀 내 순위").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("1군 기록이 있는 우리 선수 전원", substring = true).onFirst().assertExists()

        onAllNodesWithText("선수단").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("팀 기록 · 열 팀 비교").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("우리 팀 OPS 리그", substring = true).onFirst().assertExists()
    }

    @Test
    fun `리그 탭은 허브 - 카드를 누르면 그 화면, 뒤로 가면 허브`() = runComposeUiTest {
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
        val loaded = kotlinx.coroutines.runBlocking { host.loadStartingLeague() }
        val played = host.newSession(loaded, loaded.teams.first().id, "테스트").also { s -> repeat(4) { s.advanceWeek(delegate = true) } }
        setContent { AppTheme { baseballgm.app.screen.MainShell(host, played, welcome = false, onDismissWelcome = {}) } }
        waitForIdle()
        onAllNodesWithText("리그").onFirst().performClick()
        waitForIdle()
        // 허브: 카드 일곱 장 (상단 가로 탭 없음)
        listOf("순위", "타자", "투수", "팀 기록", "뉴스", "팬 반응", "역대").forEach { label ->
            onAllNodesWithContentDescription("$label 전체 보기").onFirst().assertExists()
        }
        onAllNodesWithContentDescription("팀 기록 전체 보기").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("우리 팀 OPS 리그", substring = true).onFirst().assertExists()
        onNodeWithContentDescription("뒤로").performClick()
        waitForIdle()
        onAllNodesWithContentDescription("순위 전체 보기").onFirst().assertExists()
    }
}
