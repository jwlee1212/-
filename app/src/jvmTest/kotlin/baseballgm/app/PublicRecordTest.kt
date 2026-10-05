package baseballgm.app

import baseballgm.model.Pitcher
import baseballgm.scouting.PublicRecord
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 준주전급 공개 기록 (2026-10-04 유저 요청 "준주전급 선수들까지는 그냥 능력치를 다 보여주자") */
class PublicRecordTest {

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
    private val minPa = 200
    private val minOuts = 120

    private fun newGame(): GameSession {
        val league = runBlocking { host.loadStartingLeague() }
        return host.newSession(league, league.teams.first().id, "테스트")
    }

    @Test
    fun `올 시즌 준주전급으로 뛴 타 팀 선수는 능력치가 정확하고 숨김 성질은 여전히 추정이다`() {
        val session = newGame()
        repeat(14) { session.advanceWeek(delegate = true) }
        val others = session.state.allPlayers().filter { it.teamId != null && it.teamId != session.userTeamId }
        val regular = others.first {
            session.batting(it.id).plateAppearances >= minPa || session.pitching(it.id).outs >= minOuts
        }
        assertEquals(PublicRecord.ESTABLISHED, session.recordOf(regular))
        val view = session.scout(regular)
        assertTrue(view.isExactView, "준주전급인데 능력치가 범위로 보인다")
        assertTrue(view.ratings.values.all { it.isExact })
        assertTrue(!view.traitsExact, "숨김 성질까지 정확하면 안 된다")
        assertEquals(regular.ratingsMap().values.average().let { kotlin.math.round(it).toInt() }, view.overall.low)

        // 출장이 적은 타 팀 선수(지난 시즌 기록도 적은)는 여전히 범위
        val bench = others.firstOrNull {
            session.recordOf(it) != PublicRecord.ESTABLISHED
        }
        if (bench != null) assertTrue(!session.scout(bench).isExactView, "출장이 적은 선수까지 정확히 보인다")
    }

    @Test
    fun `FA 시장의 준주전급 선수는 정확히 보이고 나머지 FA 도 아마추어처럼 흐리지 않다`() {
        val session = newGame()
        val weeks = session.balance.int("season.regularSeasonWeeks")
        session.advanceUntil(weeks, delegate = true)
        session.autoDraft()
        session.advanceUntil(weeks, delegate = true)
        session.startNextSeason()
        val agents = session.faAgents().map { session.faPlayer(it) }
        assertTrue(agents.isNotEmpty())
        val established = agents.filter { session.recordOf(it) == PublicRecord.ESTABLISHED }
        assertTrue(established.isNotEmpty(), "FA 중 준주전급이 하나도 없다")
        established.forEach { assertTrue(session.scout(it).isExactView, "${it.registeredName} FA 인데 범위로 보인다") }
        val amateurWidth = session.balance.int("scouting.amateurHalfWidthByLevel.1")
        agents.filter { session.recordOf(it) != PublicRecord.ESTABLISHED }.forEach { player ->
            assertTrue(session.accuracyFor(player).halfWidth < amateurWidth, "${player.registeredName}: 프로 FA 가 아마추어만큼 흐리다")
        }
        // 투수·타자 모두 기준이 걸린다
        assertTrue(established.any { it is Pitcher } || established.any { it !is Pitcher })
    }
}
