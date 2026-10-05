package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.io.SaveGame
import baseballgm.io.SaveGameCodec
import baseballgm.io.SeasonSnapshots
import baseballgm.model.Batter
import baseballgm.model.RosterLevel
import baseballgm.season.Postseason
import baseballgm.season.PostseasonRound
import baseballgm.season.RosterActions
import baseballgm.season.SeasonCalendar
import baseballgm.season.SeasonState
import baseballgm.season.SeriesProgress
import baseballgm.tactics.WeeklyPolicy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 포스트시즌 한 경기씩 진행 (2026-10-03).
 *
 * 정규시즌은 한 번만 돌리고, 끝난 상태를 세이브 코덱으로 복제해 테스트마다 새로 꺼내 쓴다.
 */
class PostseasonStepTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val calendar = SeasonCalendar.from(balance)

    /** 세이브로 한 번 왕복시킨다 — 복제이자 세이브 검사 */
    private fun roundTrip(state: SeasonState): SeasonState {
        val snapshot = assertNotNull(SeasonSnapshots.capture(state))
        val text = SaveGameCodec.encode(SaveGame(userTeam = league.teams.first().id, league = state.currentLeague(), seed = 1L, season = snapshot))
        val save = assertNotNull(SaveGameCodec.parseOrNull(text))
        return SeasonSnapshots.restore(save.league, calendar, balance, assertNotNull(save.season))
    }

    private fun freshEndOfSeason(): SeasonState = roundTrip(seasonEnd)

    private fun engine(state: SeasonState) = Postseason(balance, state.league)

    private fun perGame(index: Int) = Random(5_000L + index)

    @Test
    fun `한 경기씩 치러도 한 번에 치른 것과 같다`() {
        val once = freshEndOfSeason()
        engine(once).run(once, Random(7L))

        val stepped = freshEndOfSeason()
        val random = Random(7L)
        val postseason = engine(stepped)
        postseason.start(stepped)
        var games = 0
        while (postseason.playNextGame(stepped, random) != null) games++

        assertEquals(once.postseason, stepped.postseason)
        assertEquals(games, stepped.postseasonProgress!!.gamesPlayed)
        assertTrue(stepped.postseasonProgress!!.isFinished)
    }

    @Test
    fun `포스트시즌 도중에 저장했다 불러와도 결과가 같다`() {
        val straight = freshEndOfSeason()
        engine(straight).start(straight)
        while (straight.postseason == null) engine(straight).playSeries(straight, ::perGame)

        var paused = freshEndOfSeason()
        engine(paused).start(paused)
        engine(paused).playSeries(paused, ::perGame) // 와일드카드
        engine(paused).playNextGame(paused, perGame(paused.postseasonProgress!!.gamesPlayed)) // 준PO 1차전
        paused = roundTrip(paused)
        assertEquals(PostseasonRound.SEMI_PLAYOFF, paused.postseasonProgress!!.current!!.round)
        while (paused.postseason == null) engine(paused).playSeries(paused, ::perGame)

        assertEquals(straight.postseason, paused.postseason)
    }

    @Test
    fun `와일드카드 4위는 1승을 안고 시작해 1무로도 올라간다`() {
        val state = freshEndOfSeason()
        val progress = engine(state).start(state)
        val wildcard = progress.current!!
        assertEquals(PostseasonRound.WILDCARD, wildcard.round)
        assertEquals(1, wildcard.higherWins)
        assertEquals(2, wildcard.winsNeeded)

        assertEquals(wildcard.higherSeed, wildcard.copy(ties = 1, gamesPlayed = 1).winner, "4위는 1무로 진출한다")
        assertEquals(wildcard.higherSeed, wildcard.copy(higherWins = 2, gamesPlayed = 1).winner)
        assertNull(wildcard.copy(lowerWins = 1, gamesPlayed = 1).winner, "5위가 1승이면 2차전을 한다")
        assertEquals(wildcard.lowerSeed, wildcard.copy(lowerWins = 2, gamesPlayed = 2).winner)
        // 준PO 이상은 무승부가 나면 경기를 더한다
        val semi = SeriesProgress(PostseasonRound.SEMI_PLAYOFF, wildcard.higherSeed, wildcard.lowerSeed, winsNeeded = 3)
        assertNull(semi.copy(ties = 1, gamesPlayed = 1).winner)
    }

    @Test
    fun `엔트리는 우리 시리즈를 시작하기 전까지만 바꿀 수 있다`() {
        val state = freshEndOfSeason()
        val actions = RosterActions(balance)
        val ranked = state.standings.ranked().map { it.teamId }
        val first = ranked[0]
        val fifth = ranked[4]
        val outside = ranked[6]

        // 시작 전에는 아무도 못 바꾼다
        val firstPlayer = state.firstTeamOf(first).first()
        assertNotNull(actions.demoteProblem(state, first, firstPlayer.id))

        engine(state).start(state)
        // 1위는 한국시리즈까지 기다리는 동안 언제든 바꿀 수 있고, 재등록 제한도 없다
        assertNull(actions.demoteProblem(state, first, firstPlayer.id))
        assertNotNull(actions.demote(state, first, firstPlayer.id))
        assertNull(actions.promoteProblem(state, first, firstPlayer.id), "포스트시즌 엔트리는 재등록 제한이 없다")
        assertNotNull(actions.promote(state, first, firstPlayer.id))
        // 못 나간 팀은 못 바꾼다
        assertNotNull(actions.demoteProblem(state, outside, state.firstTeamOf(outside).first().id))

        // 5위: 와일드카드 1차전을 치르면 잠긴다 (지면 탈락, 이기면 2차전이 남아 있다)
        assertNull(actions.demoteProblem(state, fifth, state.firstTeamOf(fifth).first().id))
        engine(state).playNextGame(state, perGame(0))
        assertNotNull(actions.demoteProblem(state, fifth, state.firstTeamOf(fifth).first().id))
    }

    @Test
    fun `휴식 지시한 선수는 다음 경기에 안 나오고 지시는 풀린다`() {
        val baseline = freshEndOfSeason()
        engine(baseline).start(baseline)
        val home = baseline.postseasonProgress!!.current!!.higherSeed
        val game = assertNotNull(engine(baseline).playNextGame(baseline, perGame(0)))
        val starterBatter = game.box.home.batting
            .filter { (id, line) -> line.total.plateAppearances > 0 && baseline.player(id) is Batter }
            .keys.first()

        val rested = freshEndOfSeason()
        engine(rested).start(rested)
        rested.updatePostseasonOrders { it.copy(resting = it.resting + starterBatter) }
        val restedGame = assertNotNull(engine(rested).playNextGame(rested, perGame(0)))
        val line = restedGame.box.home.batting[starterBatter]
        assertTrue(line == null || line.total.plateAppearances == 0, "쉬라고 한 선수가 타석에 섰다")
        assertTrue(starterBatter !in rested.postseasonProgress!!.resting, "경기를 치렀는데 휴식 지시가 남았다")
        assertEquals(RosterLevel.FIRST_TEAM, rested.player(starterBatter).rosterLevel, "휴식은 말소가 아니다")
    }

    @Test
    fun `방침은 팀마다 따로이고 기본은 총력전이다`() {
        val state = freshEndOfSeason()
        val progress = engine(state).start(state)
        val team = progress.participants[0]
        assertEquals(WeeklyPolicy.ALL_OUT, progress.policyOf(team))
        state.updatePostseasonOrders { it.copy(policies = it.policies + (team to WeeklyPolicy.PROTECT)) }
        assertEquals(WeeklyPolicy.PROTECT, state.postseasonProgress!!.policyOf(team))
        assertEquals(WeeklyPolicy.ALL_OUT, state.postseasonProgress!!.policyOf(progress.participants[1]))
        // 세이브에 남는다
        assertEquals(WeeklyPolicy.PROTECT, roundTrip(state).postseasonProgress!!.policyOf(team))
    }

    private companion object {
        /** 정규시즌이 끝난 상태 (한 번만 만든다) */
        val seasonEnd: SeasonState by lazy {
            val balance = ProjectFiles.loadBalanceConfig()
            val templates = ProjectFiles.loadTeamTemplates()
            val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
            SeasonRunner(balance, league).playSeason(31L, validate = false, playPostseason = false).state
        }
    }
}
