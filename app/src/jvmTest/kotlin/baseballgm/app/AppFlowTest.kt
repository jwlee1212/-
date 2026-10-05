package baseballgm.app

import baseballgm.app.navigation.MainTab
import baseballgm.app.navigation.Route
import baseballgm.app.navigation.TabNavigator
import baseballgm.io.LeagueLoader
import baseballgm.io.SaveGame
import baseballgm.io.SaveGameCodec
import baseballgm.league.StrengthCalculator
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.tools.ForeignFactory
import baseballgm.tools.ProjectFiles
import baseballgm.tools.ProspectFactory
import baseballgm.tools.RookieFactory
import baseballgm.tools.nextPlayerIdNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 앱 흐름(시작 분기·탭 백스택·진행 버튼·이어하기)을 화면 없이 검사한다.
 */
class AppFlowTest {

    // ---------- 탭 백스택 ----------

    @Test
    fun `탭마다 백스택이 따로 있고 뒤로가기는 지금 탭만 줄인다`() {
        val navigator = TabNavigator()
        navigator.selectTab(MainTab.SQUAD)
        navigator.push(Route.PlayerDetail(PlayerId("P1")))

        navigator.selectTab(MainTab.HOME)
        navigator.push(Route.WeeklyReport)
        assertTrue(navigator.pop())
        assertEquals(Route.Root(MainTab.HOME), navigator.currentRoute)

        // 로스터로 돌아오면 보던 선수 상세가 그대로 있다
        navigator.selectTab(MainTab.SQUAD)
        assertEquals(Route.PlayerDetail(PlayerId("P1")), navigator.currentRoute)
    }

    @Test
    fun `탭 루트에서 뒤로가기는 실패해서 종료 확인으로 넘어간다`() {
        val navigator = TabNavigator()
        assertFalse(navigator.canPop)
        assertFalse(navigator.pop())
        assertEquals(Route.Root(MainTab.HOME), navigator.currentRoute)
    }

    @Test
    fun `같은 탭을 다시 누르면 루트로 돌아가고 빠진 화면의 저장 상태를 지운다`() {
        val discarded = mutableListOf<String>()
        val navigator = TabNavigator(onDiscard = { discarded += it })
        navigator.selectTab(MainTab.HOME)
        navigator.push(Route.Career)
        navigator.push(Route.PlayerDetail(PlayerId("P9")))

        navigator.selectTab(MainTab.HOME)

        assertEquals(listOf<Route>(Route.Root(MainTab.HOME)), navigator.stackOf(MainTab.HOME))
        assertEquals(listOf("HOME/2/player:P9", "HOME/1/career"), discarded)
    }

    @Test
    fun `같은 화면을 연달아 쌓지 않는다`() {
        val navigator = TabNavigator()
        navigator.push(Route.WeeklyReport)
        navigator.push(Route.WeeklyReport)
        assertEquals(2, navigator.stackOf(MainTab.HOME).size)
    }

    @Test
    fun `다른 탭의 하위 화면으로 바로 보낼 수 있다`() {
        val navigator = TabNavigator()
        navigator.open(MainTab.RECRUIT, Route.PlayerDetail(PlayerId("P3")))
        assertEquals(MainTab.RECRUIT, navigator.currentTab)
        assertEquals(Route.PlayerDetail(PlayerId("P3")), navigator.currentRoute)
        assertTrue(navigator.pop())
        assertEquals(Route.Root(MainTab.RECRUIT), navigator.currentRoute)
    }

    @Test
    fun `하단 탭은 5개이고 뉴스와 구단은 홈 위에 쌓이는 화면이다`() {
        assertEquals(listOf("단장실", "선수단", "영입", "메시지", "리그"), MainTab.entries.map { it.label })
        val navigator = TabNavigator()
        navigator.push(Route.News(1))
        assertEquals(MainTab.HOME, navigator.currentTab)
        navigator.push(Route.Club)
        assertEquals(listOf(Route.Root(MainTab.HOME), Route.News(1), Route.Club), navigator.stackOf(MainTab.HOME))
    }

    // ---------- 시작 분기 ----------

    @Test
    fun `세이브가 없거나 깨졌으면 새 게임으로 간다`() {
        assertEquals(LaunchState.NoSave, LaunchState.from(null))
        assertEquals(LaunchState.NoSave, LaunchState.from("{ broken"))
    }

    @Test
    fun `읽을 수 있는 세이브가 있으면 이어하기로 간다`() {
        val save = SaveGame(userTeam = userTeam, league = league)
        val launch = LaunchState.from(SaveGameCodec.encode(save))
        assertIs<LaunchState.HasSave>(launch)
        assertEquals(userTeam, launch.save.userTeam)
    }

    // ---------- 진행 버튼 + 이어하기 ----------

    @Test
    fun `진행 버튼이 시즌 단계를 따라가고 새 시즌 개막 세이브로 이어할 수 있다`() {
        val store = MemorySaveStore()
        val host = host(store)
        val session = host.newSession(league, userTeam)

        assertIs<ProgressAction.PlayWeek>(ProgressAction.of(session))

        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        assertEquals(ProgressAction.GoToDraft, ProgressAction.of(session), "드래프트 주차에는 지명하러 보내야 한다")

        session.autoDraft()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        val postseason = ProgressAction.of(session)
        assertTrue(postseason is ProgressAction.PostseasonStep || postseason == ProgressAction.PostseasonFinish, "포스트시즌 단계가 아니다: $postseason")

        session.runPostseason()
        assertIs<ProgressAction.PrepareOffseason>(ProgressAction.of(session))

        session.startNextSeason()
        assertIs<ProgressAction.FreeAgencyRound>(ProgressAction.of(session))

        session.skipFreeAgency()
        if (session.unemployed) {
            // 해임됐으면 이직부터 — 이 시드에서는 드물지만 분기 자체는 확인한다
            assertEquals(ProgressAction.ChooseJob, ProgressAction.of(session))
            return
        }
        assertIs<ProgressAction.PlayWeek>(ProgressAction.of(session))

        // 새 시즌 개막 스냅샷을 저장하고 다시 읽는다
        host.save(session)
        val launch = LaunchState.from(store.read())
        assertIs<LaunchState.HasSave>(launch)
        val restored = host.resume(launch.save)

        assertEquals(session.userTeamId, restored.userTeamId)
        assertEquals(session.league.season, restored.league.season)
        assertEquals(1, restored.week)
        assertEquals(
            session.league.management.seasonGoals,
            restored.league.management.seasonGoals,
            "이어하기가 스토브리그에서 정한 시즌 목표를 덮어썼다",
        )
        assertEquals(session.league.players.size, restored.league.players.size)
        restored.advanceWeek(delegate = true)
        assertTrue(restored.record().games > 0)
    }

    // ---------- 준비물 ----------

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val userTeam = TeamId("SWR")

    private class MemorySaveStore : SaveStore {
        var text: String? = null
        override fun read(): String? = text
        override fun write(text: String) {
            this.text = text
        }
    }

    private fun host(store: SaveStore) = GameHost(
        balance = balance,
        teams = emptyList(),
        saveStore = store,
        loadStartingLeague = { league },
        rookieSupplier = { current -> RookieFactory(balance, StrengthCalculator(balance), current) },
        prospectSupplier = { current ->
            ProspectFactory(
                balance = balance,
                strength = StrengthCalculator(balance),
                seed = 4242L,
                startingIdNumber = nextPlayerIdNumber(current.players, current.draftPool.prospects),
            )
        },
        foreignSupplier = { current ->
            ForeignFactory(
                balance = balance,
                strength = StrengthCalculator(balance),
                seed = 909L,
                startingIdNumber = nextPlayerIdNumber(current.players, current.draftPool.prospects) +
                    current.draftPool.prospects.size,
            )
        },
        exitApp = {},
    )
}
