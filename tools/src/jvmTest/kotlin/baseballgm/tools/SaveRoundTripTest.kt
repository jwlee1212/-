package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.io.SaveGame
import baseballgm.io.SaveGameCodec
import baseballgm.io.SeasonSnapshots
import baseballgm.league.StrengthCalculator
import baseballgm.season.IncidentResolver
import baseballgm.season.SeasonCalendar
import baseballgm.season.SeasonState
import baseballgm.season.WeekLoop
import baseballgm.util.Seeds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 시즌 중 세이브 (docs/14, 2026-10-01).
 *
 * 핵심 약속: **저장했다 불러온 시즌과 그냥 이어 간 시즌은 그 뒤로 완전히 같다** (불변 원칙 2).
 * 주차 난수를 게임 시드에서 파생하기 때문에 세이브에는 시드 하나만 있으면 된다.
 */
class SaveRoundTripTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val calendar = SeasonCalendar.from(balance)
    private val resolver = IncidentResolver(balance, StrengthCalculator(balance))
    private val user = league.teams.first().id
    private val seed = 777L

    /** 유저 구단 방식으로 [weeks] 주를 진행한다. 돌발 이벤트는 추천대로 답한다 */
    private fun play(state: SeasonState, loop: WeekLoop, weeks: Int) {
        repeat(weeks) {
            if (state.isRegularSeasonOver) return
            val random = Seeds.random(seed, state.season, Seeds.Phase.WEEK, state.week.toLong())
            var guard = 0
            while (guard++ < 50) {
                state.pendingIncidents.firstOrNull()?.let { resolver.resolve(state, it.id, it.recommended.id, delegated = true) }
                if (loop.advance(state, random, user) != null) break
            }
        }
    }

    private fun saveAndLoad(state: SeasonState): Pair<SeasonState, Int> {
        val snapshot = assertNotNull(SeasonSnapshots.capture(state))
        val text = SaveGameCodec.encode(SaveGame(userTeam = user, league = state.currentLeague(), seed = seed, season = snapshot))
        val save = assertNotNull(SaveGameCodec.parseOrNull(text), "저장한 세이브를 읽지 못했다")
        return SeasonSnapshots.restore(save.league, calendar, balance, assertNotNull(save.season)) to text.length
    }

    @Test
    fun `교체로 떠난 외국인도 저장했다 불러오면 이름을 찾을 수 있다`() {
        val state = SeasonState.of(league, calendar, balance)
        val loop = WeekLoop(balance, league)
        loop.prepareRosters(state)
        play(state, loop, 3)

        val service = baseballgm.season.ForeignService(balance, StrengthCalculator(balance))
        val outgoing = state.playersOf(user).first { it.isForeign }
        val candidate = state.foreignPool().candidates.first { it.isPitcher == (outgoing is baseballgm.model.Pitcher) }
        service.replace(state, user, outgoing.id, candidate, candidate.askingSalary)
        assertTrue(!state.isInLeague(outgoing.id))
        assertEquals(outgoing.registeredName, state.player(outgoing.id).registeredName)

        val (restored, _) = saveAndLoad(state)
        assertTrue(!restored.isInLeague(outgoing.id), "떠난 선수가 불러온 뒤 리그로 돌아왔다")
        assertEquals(outgoing.registeredName, restored.player(outgoing.id).registeredName)
        assertNull(restored.player(outgoing.id).teamId)
    }

    @Test
    fun `저장했다 불러와도 그 뒤 시즌이 그대로 흘러간다`() {
        val original = SeasonState.of(league, calendar, balance)
        val loop = WeekLoop(balance, league)
        loop.prepareRosters(original)
        play(original, loop, 8)

        val (restored, size) = saveAndLoad(original)
        val restoredLoop = WeekLoop(balance, restored.league)
        assertEquals(original.week, restored.week)
        assertEquals(original.standings, restored.standings)
        println("시즌 중 세이브 크기: ${size / 1024}KB (${original.week - 1}주 진행)")
        assertTrue(size < MAX_SAVE_CHARS, "세이브가 ${size}자 — 폰 웹 저장 한도가 걱정된다")

        // 같은 시드로 남은 시즌 끝까지 — 드래프트·트레이드 마감·올스타·국제대회를 모두 지난다
        play(original, loop, 30)
        play(restored, restoredLoop, 30)

        assertEquals(original.standings, restored.standings, "불러온 시즌의 순위가 다르다")
        assertEquals(original.stats.allBatting(), restored.stats.allBatting(), "타격 기록이 다르다")
        assertEquals(original.stats.allPitching(), restored.stats.allPitching(), "투구 기록이 다르다")
        assertEquals(original.allPlayers(), restored.allPlayers(), "선수 상태(컨디션·성장·소속)가 다르다")
        assertEquals(original.fanSupport, restored.fanSupport)
        assertEquals(original.news.map { it.headline }, restored.news.map { it.headline })
        assertEquals(original.incidentLog, restored.incidentLog, "돌발 이벤트가 다르게 났다")
        assertEquals(original.draftResult, restored.draftResult, "드래프트 결과가 다르다")
        assertEquals(original.tradeHistory, restored.tradeHistory, "트레이드가 다르게 났다")
        // 팀 합계 (2026-10-04): 저장했다 불러와도 그대로 이어 쌓인다
        assertTrue(restored.stats.hasTeamTotals)
        league.teams.forEach { team ->
            assertEquals(original.stats.teamTotalsOf(team.id), restored.stats.teamTotalsOf(team.id), "${team.name} 팀 합계가 다르다")
        }
    }

    @Test
    fun `주중에 멈춘 상태는 찍지 않는다 — 그 주 시작 세이브가 남는다`() {
        val state = SeasonState.of(league, calendar, balance)
        val loop = WeekLoop(balance, league)
        var guard = 0
        while (state.pendingIncidents.isEmpty() && !state.isRegularSeasonOver && guard++ < 30) {
            loop.advance(state, Seeds.random(seed, state.season, Seeds.Phase.WEEK, state.week.toLong()), user)
        }
        if (state.pendingIncidents.isEmpty()) return // 이 시드에선 멈춘 적이 없다
        assertNull(SeasonSnapshots.capture(state))
    }

    @Test
    fun `포스트시즌까지 끝난 시즌도 저장된다`() {
        val state = SeasonState.of(league, calendar, balance)
        val loop = WeekLoop(balance, league)
        play(state, loop, 30)
        assertTrue(state.isRegularSeasonOver)
        baseballgm.season.Postseason(balance, league).run(state, Seeds.random(seed, state.season, Seeds.Phase.POSTSEASON))
        val (restored, _) = saveAndLoad(state)
        assertEquals(state.postseason, restored.postseason)
        assertTrue(restored.isRegularSeasonOver)
    }

    private companion object {
        /** localStorage 한도(약 5백만 자)에 여유를 둔다 */
        const val MAX_SAVE_CHARS = 3_500_000
    }
}
