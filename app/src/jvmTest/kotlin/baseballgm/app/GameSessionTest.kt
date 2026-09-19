package baseballgm.app

import baseballgm.io.LeagueLoader
import baseballgm.model.ManagerTendencies
import baseballgm.model.RosterLevel
import baseballgm.scouting.ScoutingAccuracy
import baseballgm.tactics.WeeklyPolicy
import baseballgm.text.CommentaryRenderer
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 화면이 쓰는 경로를 화면 없이 검사한다.
 *
 * Compose 화면 자체는 사람이 눈으로 보는 게 빠르지만, **화면이 부르는 함수가 터지지 않는지**는
 * 테스트로 잡을 수 있다. 특히 정보 은닉(타 팀 선수는 범위로만)이 지켜지는지를 여기서 확인한다.
 */
class GameSessionTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val userTeam = league.teams.first { it.id.value == "SWR" }.id

    private fun session() = GameSession(
        balance,
        league,
        userTeam,
        rookieSupplier = { current ->
            baseballgm.tools.RookieFactory(balance, baseballgm.league.StrengthCalculator(balance), current)
        },
        seed = 4242L,
    )

    @Test
    fun `주 진행과 화면 조회가 끝까지 돈다`() {
        val session = session()
        repeat(4) { session.advanceWeek() }

        assertEquals(5, session.week)
        assertTrue(session.record().games > 0)
        assertTrue(session.rank() in 1..10)
        assertTrue(session.roster(RosterLevel.FIRST_TEAM).isNotEmpty())
        assertTrue(session.roster(RosterLevel.FUTURES).isNotEmpty())
        assertTrue(session.teamsRanked().size == league.teams.size)
        assertTrue(session.lastReport != null)
        assertTrue(session.weekSchedule().isNotEmpty())
        assertTrue(session.calendarLabel().isNotBlank())
    }

    @Test
    fun `기록실 순위가 나온다`() {
        val session = session()
        repeat(6) { session.advanceWeek() }
        assertTrue(session.battingLeaders().isNotEmpty(), "타율 순위가 비었다")
        assertTrue(session.homeRunLeaders().isNotEmpty())
        assertTrue(session.eraLeaders().isNotEmpty(), "평균자책 순위가 비었다")
        assertTrue(session.winLeaders().isNotEmpty())
        // 화면은 선수의 소속 구단 이름을 찾는다 — 무소속이면 터진다
        session.battingLeaders().forEach { (player, _) ->
            assertTrue(player.teamId != null, "${player.name} 의 소속이 없다")
        }
    }

    @Test
    fun `우리 팀은 정확한 값 타 팀은 범위로 본다`() {
        val session = session()
        val own = session.roster(RosterLevel.FIRST_TEAM).first()
        val other = league.players.first { it.teamId != userTeam }

        assertEquals(ScoutingAccuracy.OWN_TEAM, session.accuracyFor(own))
        val ownView = session.scout(own)
        assertTrue(ownView.ratings.values.all { it.isExact }, "우리 팀 선수인데 범위로 나온다")

        val otherView = session.scout(other)
        assertTrue(otherView.ratings.values.none { it.isExact }, "타 팀 선수인데 정확한 값이 나온다")
        assertTrue(otherView.potentialLabel.isNotBlank())
    }

    @Test
    fun `관전용 경기와 중계 문장이 만들어진다`() {
        val session = session()
        session.advanceWeek()
        val watched = session.lastReport!!.watched
        assertEquals(6, watched.size, "한 주는 6경기다")
        assertTrue(watched.all { it.box.home.teamId == userTeam || it.box.away.teamId == userTeam })

        val renderer = CommentaryRenderer { session.player(it).registeredName }
        val lines = renderer.render(watched.first().events)
        assertTrue(lines.size > 50, "중계 문장이 ${lines.size}줄뿐이다")
        assertTrue(lines.first().contains("경기 시작"))
        assertTrue(lines.last().contains("경기 종료"))
    }

    @Test
    fun `주간 방침과 단장 방침이 규칙표에 반영된다`() {
        val session = session()
        val before = session.sheet().starterHook.pitchLimit

        session.setPolicy(WeeklyPolicy.ALL_OUT)
        assertTrue(session.sheet().starterHook.pitchLimit > before, "총력전인데 투구수 한계가 그대로다")

        session.setPolicy(WeeklyPolicy.PROTECT)
        assertTrue(session.sheet().starterHook.pitchLimit < before, "선수 보호인데 한계가 그대로다")

        // 단장 방침: 번트를 많이 대라고 지시하면 감독 성향이 매주 조금씩 끌려온다
        session.setPolicy(WeeklyPolicy.NORMAL)
        val startBunt = session.tendencies().buntPreference
        session.setDirection(session.tendencies().copy(buntPreference = 100))
        repeat(3) { session.advanceWeek() }
        val afterBunt = session.tendencies().buntPreference
        assertTrue(afterBunt > startBunt, "방침을 줬는데 성향이 안 움직였다 ($startBunt → $afterBunt)")
        assertTrue(afterBunt < 100, "한 번에 목표값까지 가면 안 된다 (반영률)")
    }

    @Test
    fun `시즌 끝까지 자동 진행할 수 있다`() {
        val session = session()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"))
        assertTrue(session.seasonOver)
        assertEquals(balance.int("schedule.gamesPerTeam"), session.record().games)
        assertTrue(session.battingLeaders().isNotEmpty())
    }

    @Test
    fun `단장 방침 없이도 감독 성향은 그대로 유지된다`() {
        val session = session()
        val before = session.tendencies()
        repeat(3) { session.advanceWeek() }
        assertEquals(before, session.tendencies(), "방침을 안 줬는데 성향이 변했다")
    }

    @Test
    fun `시즌이 끝나면 스토브리그를 거쳐 다음 시즌으로 간다`() {
        val session = session()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"))
        val seasonBefore = session.league.season
        val playersBefore = session.league.players.size

        session.startNextSeason()

        assertEquals(seasonBefore + 1, session.league.season)
        assertEquals(1, session.week)
        assertTrue(!session.seasonOver)
        assertEquals(0, session.record().games, "새 시즌 성적은 0부터 시작한다")
        val offseason = session.lastOffseason!!
        assertTrue(offseason.retired.isNotEmpty(), "은퇴한 선수가 없다")
        assertTrue(offseason.rookies.isNotEmpty(), "신인이 없다")
        assertTrue(session.league.players.size in (playersBefore - 30)..(playersBefore + 30))

        // 새 시즌도 정상적으로 굴러간다
        session.advanceWeek()
        assertTrue(session.record().games > 0)
    }

    @Test
    fun `부상 선수도 화면에서 볼 수 있다`() {
        val session = session()
        repeat(8) { session.advanceWeek() }
        val injured = session.state.allPlayers().filter { it.condition.isInjured }
        assertTrue(injured.isNotEmpty(), "8주 동안 부상자가 한 명도 없다")
        injured.take(5).forEach { player ->
            val view = session.scout(player)
            assertTrue(view.name.isNotBlank())
            assertTrue(session.formLabel(player).isNotBlank())
        }
    }
}
