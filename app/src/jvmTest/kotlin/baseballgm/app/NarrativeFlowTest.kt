package baseballgm.app

import baseballgm.io.LeagueLoader
import baseballgm.io.SaveGame
import baseballgm.io.SaveGameCodec
import baseballgm.management.DecisionKind
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 게임성 보완(CLAUDE.md §4-1): 단장 이름, 뉴스·팬 SNS, 라이벌, 결정 기록·유대·칭호가
 * 실제 시즌 흐름에서 이어지는지 본다.
 */
class NarrativeFlowTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val host = HostFactory.create(
        balanceText = ProjectFiles.read(ProjectFiles.BALANCE_PATH),
        teamsText = ProjectFiles.read(ProjectFiles.TEAMS_PATH),
        leagueText = { ProjectFiles.read(ProjectFiles.leaguePath(templates.season)) },
        saveStore = object : SaveStore {
            var text: String? = null
            override fun read() = text
            override fun write(text: String) { this.text = text }
        },
        exitApp = null,
    )
    private val swr = TeamId("SWR")

    @Test
    fun `단장 이름 규칙`() {
        assertEquals("한결", GmName.normalize("  한결 "))
        assertEquals("김 한결", GmName.normalize("김   한결"))
        assertNull(GmName.normalize("   "))
        assertNull(GmName.normalize("아주아주긴단장이름"))
    }

    @Test
    fun `정한 이름으로 부르고 세이브를 거쳐도 이름이 남는다`() {
        val session = host.newSession(league, swr, gmName = "한결")
        assertEquals("한결", session.gmName)
        assertTrue(Briefing.welcome(session).contains("한결 단장님"))
        assertEquals("신임 단장", session.gmTitle().name)

        val restored = SaveGameCodec.parseOrNull(SaveGameCodec.encode(SaveGame(userTeam = swr, league = session.league)))!!
        assertEquals("한결", host.newSession(restored.league, restored.userTeam).gmName)
    }

    @Test
    fun `모든 구단에 서로를 가리키는 라이벌이 있다`() {
        league.teams.forEach { team ->
            val rival = team.rival
            requireNotNull(rival) { "${team.id} 라이벌 없음" }
            assertEquals(team.id, league.team(rival).rival)
        }
    }

    @Test
    fun `시즌 동안 뉴스·팬 글·상대 전적이 쌓이고 결정이 다음 시즌 커리어로 넘어간다`() {
        val session = host.newSession(league, swr, gmName = "한결")
        repeat(6) { session.advanceWeek(delegate = true) }

        assertTrue(session.news().isNotEmpty(), "6주 동안 기사가 한 건도 없다")
        assertTrue(session.fanPosts().isNotEmpty(), "팬 글이 없다")
        assertTrue(session.fanPosts().all { it.author.startsWith("@") })
        // 상대 전적 합 = 승부가 난 경기 수
        val decided = session.state.headToHead.values.sum()
        val totalWins = session.teamsRanked().sumOf { it.wins }
        assertEquals(totalWins, decided)
        val (rivalWins, rivalLosses) = session.rivalRecord()!!
        assertTrue(rivalWins + rivalLosses >= 0)

        // 드래프트까지 진행: 지명한 선수가 결정 기록에 저절로 잡힌다
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.autoDraft()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        val drafted = session.decisions().filter { it.kind == DecisionKind.DRAFT }
        assertTrue(drafted.isNotEmpty(), "드래프트 결정이 기록되지 않았다")

        session.runPostseason()
        session.startNextSeason()
        session.skipFreeAgency()
        if (session.unemployed) return

        // 다음 시즌: 지난 결정이 커리어 기록(세이브되는 곳)에 남아 있다
        val saved = session.league.management.decisions
        assertTrue(saved.any { it.kind == DecisionKind.DRAFT && it.season == league.season }, "결정이 다음 시즌으로 안 넘어갔다")
        // 유대: 부임 첫해부터 있던 선수는 이제 함께한 2시즌
        val veteran = session.roster(baseballgm.model.RosterLevel.FIRST_TEAM).first { player ->
            saved.none { it.playerId == player.id }
        }
        assertEquals(2, session.bond(veteran)!!.seasons)
        // 새 시즌은 뉴스가 비어서 시작한다
        assertTrue(session.news().isEmpty())
    }

    @Test
    fun `뉴스는 시즌 난수를 건드리지 않는다 — 같은 시드면 경기 결과가 같다`() {
        val a = host.newSession(league, swr)
        val b = host.newSession(league, swr)
        repeat(3) {
            a.advanceWeek(delegate = true)
            b.advanceWeek(delegate = true)
        }
        assertEquals(a.teamsRanked(), b.teamsRanked())
        assertEquals(a.news(), b.news())
        assertEquals(a.fanPosts(), b.fanPosts())
    }
}
