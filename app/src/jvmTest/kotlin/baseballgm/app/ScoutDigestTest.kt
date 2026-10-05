package baseballgm.app

import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.model.Pitcher
import baseballgm.scouting.PotentialGrade
import baseballgm.scouting.ScoutingAccuracy
import baseballgm.scouting.ScoutingView
import baseballgm.season.InboxCategory
import baseballgm.tools.ProjectFiles
import baseballgm.io.SaveGameCodec
import kotlinx.coroutines.runBlocking
import kotlin.test.assertNotNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 스카우트 정기 리포트 · 선발 가중치 · 잠재력 등급 재정의 (유저 요청 2026-10-04) */
class ScoutDigestTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))
    private val draftWeek = balance.int("season.draftWeek")
    private val every = balance.int("draftAdvisor.reportEveryWeeks")

    private fun session(seed: Long = 4242L) = GameSession(balance, league, league.teams.first().id, seed = seed)

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
    fun `리포트는 4주마다 오고 드래프트 주차에 최종 리포트가 오며 받은 뒤엔 바뀌지 않는다`() {
        val s = host.newSession(runBlocking { host.loadStartingLeague() }, league.teams.first().id, "테스트")
        s.advanceUntil(every, delegate = true)
        val first = s.scoutDigests().single()
        assertEquals(every * 2, s.nextDigestWeek(), "다음 리포트 안내가 틀렸다")
        assertEquals(every, first.week)
        assertTrue(first.rounds.isNotEmpty() && first.headline.isNotBlank())
        val frozen = first.copy()

        s.advanceUntil(draftWeek, delegate = true)
        val digests = s.scoutDigests()
        val expectedWeeks = (every until draftWeek - 1 step every).toList() + (draftWeek - 1)
        assertEquals(expectedWeeks, digests.map { it.week }, "리포트 주차가 다르다")
        assertTrue(digests.last().final && digests.dropLast(1).none { it.final })
        assertEquals(frozen, digests.first(), "받은 리포트가 바뀌었다")

        // 알림함(→ 메시지 탭 스카우트팀)에 도착 소식
        val notes = s.state.inbox.all().filter { it.category == InboxCategory.SCOUTING && it.text.contains("리포트") }
        assertEquals(digests.size, notes.size)
        // 관찰이 쌓이며 평가가 움직인 선수·관찰을 마친 선수가 어느 리포트엔가는 실린다
        assertTrue(digests.any { it.risers.isNotEmpty() || it.fallers.isNotEmpty() }, "평가 변화가 한 번도 안 실렸다")
        assertTrue(digests.any { it.completed.isNotEmpty() }, "관찰 완료가 한 번도 안 실렸다")
        // 세이브 → 문자열 → 이어하기: 리포트와 스카우트 설정이 남는다
        s.draftPolicy = baseballgm.market.DraftPolicy.POTENTIAL
        s.autoFocus = false
        val data = assertNotNull(s.saveData(), "저장할 수 있는 순간이어야 한다")
        val restored = host.resume(assertNotNull(SaveGameCodec.parseOrNull(SaveGameCodec.encode(data))))
        assertEquals(digests, restored.scoutDigests())
        assertEquals(baseballgm.market.DraftPolicy.POTENTIAL, restored.draftPolicy)
        assertTrue(!restored.autoFocus)

        // 드래프트가 끝나면 더 오지 않는다
        s.autoDraft()
        repeat(3) { s.advanceWeek(delegate = true) }
        assertEquals(digests.size, s.scoutDigests().size)
    }

    @Test
    fun `선발투수는 풀 비율보다 앞 순번에서 더 많이 뽑힌다`() {
        var spEarly = 0
        var early = 0
        var poolShare = 0.0
        repeat(5) { run ->
            val s = session(seed = 500L + run)
            s.advanceUntil(draftWeek, delegate = true)
            s.autoDraft()
            val pool = s.league.draftPool.prospects
            poolShare = pool.count { (it.player as? Pitcher)?.role?.isReliever == false }.toDouble() / pool.size
            s.draftSelections().take(30).forEach { sel ->
                early++
                if (sel.positionLabel == "SP") spEarly++
            }
        }
        val share = spEarly.toDouble() / early
        println("STARTER 앞 30순위 선발 비율 ${"%.2f".format(share)} (풀 비율 ${"%.2f".format(poolShare)})")
        assertTrue(share > poolShare, "선발 가중치가 앞 순번에 안 드러난다: $share vs $poolShare")
    }

    @Test
    fun `잠재력 등급이 역할 기준으로 나뉜다`() {
        val exact = ScoutingAccuracy.OWN_TEAM.precision
        val scale = baseballgm.scouting.PotentialScale.from(balance)
        fun grade(p: baseballgm.model.Player) = ScoutingView.of(p, exact, league.season, scale).potentialLow
        val pool = league.draftPool.prospects.map { grade(it.player) }.groupingBy { it }.eachCount()
        val strength = StrengthCalculator(balance)
        val pros = league.players.filter { it.teamId != null }
        val proGrades = pros.map { grade(it) }.groupingBy { it }.eachCount()
        fun line(m: Map<PotentialGrade, Int>, n: Int) = PotentialGrade.entries.reversed().joinToString(" ") { "${it.name}=${m[it] ?: 0}(${(m[it] ?: 0) * 100 / n}%)" }
        println("GRADES 드래프트 풀 ${line(pool, league.draftPool.prospects.size)}")
        println("GRADES 프로 전체 ${line(proGrades, pros.size)}")
        // 각 구단 최고 선수는 대부분 A 이상, 드래프트 풀엔 B 이상이 적당히 있다
        val teamBest = league.teams.map { t -> pros.filter { it.teamId == t.id }.maxBy { strength.overallOf(it) } }
        assertTrue(teamBest.count { grade(it) >= PotentialGrade.A } >= league.teams.size * 0.7, "구단 최고 선수가 A 이상이 아니다")
        assertTrue((pool[PotentialGrade.B] ?: 0) + (pool[PotentialGrade.A] ?: 0) + (pool[PotentialGrade.S] ?: 0) >= 10, "드래프트 풀에 주전감이 너무 적다")
        assertTrue((pool[PotentialGrade.D] ?: 0) < league.draftPool.prospects.size * 0.7, "드래프트 풀이 거의 다 D")
    }
}
