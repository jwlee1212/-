package baseballgm.tools

import baseballgm.events.Incident
import baseballgm.events.IncidentKind
import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.model.Injury
import baseballgm.model.InjurySeverity
import baseballgm.model.Pitcher
import baseballgm.model.RosterLevel
import baseballgm.season.IncidentResolver
import baseballgm.season.PositionLimits
import baseballgm.season.RosterSlot
import baseballgm.season.RosterActions
import baseballgm.season.SeasonState
import baseballgm.season.WeekLoop
import baseballgm.season.withCondition
import baseballgm.season.withRosterLevel
import baseballgm.tactics.WeeklyPolicy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 돌발 이벤트(주중 개입)와 단장 직접 엔트리 조작 검증. */
class IncidentTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val runner = SeasonRunner(balance, league)
    private val strength = StrengthCalculator(balance)
    private val resolver = IncidentResolver(balance, strength)
    private val actions = RosterActions(balance)
    private val user = league.teams.first().id
    private val limit = balance.int("roster.firstTeamRegistered")

    /** 시즌 끝까지 진행. 돌발 이벤트가 뜨면 [choose] 로 답한다 */
    private fun runSeason(seed: Long, choose: (Incident) -> String): Pair<SeasonState, List<Incident>> {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(seed)
        val seen = mutableListOf<Incident>()
        var guard = 0
        while (!state.isRegularSeasonOver && guard++ < 2_000) {
            if (loop.advance(state, random, user) == null) {
                val incident = state.pendingIncidents.first()
                seen += incident
                assertNotNull(resolver.resolve(state, incident.id, choose(incident)), "답할 수 없는 돌발 이벤트: ${incident.id}")
            }
        }
        return state to seen
    }

    @Test
    fun `한 시즌에 돌발 이벤트가 여러 종류 생기고 전부 유저 구단 일이다`() {
        val (state, seen) = runSeason(2026L) { it.recommended.id }
        assertTrue(state.isRegularSeasonOver)
        assertTrue(seen.size >= 8, "한 시즌 돌발 이벤트가 ${seen.size}건뿐")
        assertTrue(seen.map { it.kind }.toSet().size >= 4, "종류가 ${seen.map { it.kind }.toSet()}")
        assertTrue(seen.all { it.teamId == user })
        assertTrue(seen.all { it.options.size >= 2 && it.options.count { o -> o.recommended } == 1 }, "모든 카드에 추천은 정확히 하나")
        assertEquals(seen.size, state.incidentLog.size)
        // 주당 한도: 부상·연패·트레이드처럼 피할 수 없는 일을 빼면 maxPerWeek 를 넘지 않는다
        val forced = setOf(
            IncidentKind.INJURY_REPLACEMENT, IncidentKind.PLAY_THROUGH, IncidentKind.INJURY_RETURN,
            IncidentKind.LOSING_STREAK, IncidentKind.MANAGER_HEAT, IncidentKind.TRADE_OFFER,
        )
        seen.filter { it.kind !in forced }.groupBy { it.week }.values.forEach {
            assertTrue(it.size <= balance.int("incidents.maxPerWeek"))
        }
        league.teams.forEach { team ->
            assertTrue(state.firstTeamOf(team.id).size <= limit, "${team.id} 1군 ${state.firstTeamOf(team.id).size}명")
        }
        assertEquals(balance.int("schedule.gamesPerTeam"), state.standings.record(user).games)
    }

    @Test
    fun `같은 시드에 같은 답이면 같은 시즌이 나온다`() {
        val choose = { incident: Incident -> incident.options.first().id }
        val (a, seenA) = runSeason(77L, choose)
        val (b, seenB) = runSeason(77L, choose)
        assertEquals(seenA.map { it.id }, seenB.map { it.id })
        assertEquals(a.standings.ranked().map { it.teamId to it.wins }, b.standings.ranked().map { it.teamId to it.wins })
        assertEquals(a.fanSupport, b.fanSupport)
    }

    @Test
    fun `멈춘 동안에는 답하기 전까지 한 경기도 진행되지 않는다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(5L)
        while (loop.advance(state, random, user) != null) { /* 멈출 때까지 */ }
        val progress = assertNotNull(state.weekInProgress)
        val day = progress.nextDay
        val games = state.standings.record(user).games
        assertNull(loop.advance(state, random, user))
        assertEquals(day, progress.nextDay)
        assertEquals(games, state.standings.record(user).games)
    }

    /** 1군 최고 야수를 "재활 끝나고 2군에 있는" 상태로 만든다 */
    private fun rehabbedStar(state: SeasonState): baseballgm.model.Player {
        val star = state.firstTeamOf(user).filter { it !is Pitcher }.maxBy { strength.overallOf(it) }
        state.update(star.withRosterLevel(RosterLevel.FUTURES).withCondition(star.condition.copy(relapseRiskWeeks = 4)))
        state.roster.markDemoted(star.id, state.week - 5)
        state.roster.markRehab(star.id, state.week)
        return star
    }

    @Test
    fun `재활을 마친 주전은 자동으로 올라오지 않고 주 시작에 복귀 카드로 묻는다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val star = rehabbedStar(state)
        assertNull(loop.advance(state, Random(3L), user))
        val incident = state.pendingIncidents.first { it.kind == IncidentKind.INJURY_RETURN }
        assertEquals(star.id, incident.playerId)
        assertNull(incident.day)
        assertEquals(RosterLevel.FUTURES, state.player(star.id).rosterLevel, "묻기 전에 이미 올라갔다")
        assertEquals("back", incident.recommended.id)

        assertNotNull(resolver.resolve(state, incident.id, "back"))
        assertEquals(RosterLevel.FIRST_TEAM, state.player(star.id).rosterLevel)
        assertTrue(state.firstTeamOf(user).size <= limit)
    }

    @Test
    fun `복귀를 한 주 미루면 재발 위험이 줄고 다음 주에는 묻지 않고 올라온다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(3L)
        val star = rehabbedStar(state)
        val week = state.week
        var seen = 0
        var guard = 0
        // 두 주를 진행한다. 복귀 카드에는 "조율", 나머지는 추천대로
        while (state.week < week + 2 && guard++ < 100) {
            if (loop.advance(state, random, user) != null) continue
            val incident = state.pendingIncidents.first()
            val choice = if (incident.kind == IncidentKind.INJURY_RETURN && incident.playerId == star.id) {
                seen++
                "tune"
            } else {
                incident.recommended.id
            }
            resolver.resolve(state, incident.id, choice)
            if (choice == "tune") {
                val after = state.player(star.id)
                assertEquals(RosterLevel.FUTURES, after.rosterLevel)
                assertEquals(4 - balance.int("incidents.injuryReturn.tuneRelapseCutWeeks"), after.condition.relapseRiskWeeks)
            }
        }
        assertEquals(1, seen, "복귀 카드가 ${seen}번 떴다")
        val after = state.player(star.id)
        assertTrue(after.rosterLevel == RosterLevel.FIRST_TEAM || after.condition.isInjured, "다음 주에 자동 복귀하지 않았다")
    }

    @Test
    fun `돌발 이벤트 없이 도는 주는 예전처럼 부상 복귀 선수를 바로 올린다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val star = rehabbedStar(state)
        loop.beginWeek(state, Random(3L), validate = false, highlightTeam = user)
        assertEquals(RosterLevel.FIRST_TEAM, state.player(star.id).rosterLevel)
    }

    @Test
    fun `참고 뛰기는 부상을 지우고 폼을 깎고 재발 위험을 남긴다`() {
        val state = runner.newSeason()
        val player = state.firstTeamOf(user).first { it !is Pitcher }
        state.update(player.withCondition(player.condition.copy(injury = Injury("손목", InjurySeverity.MINOR, 1))))
        val incident = Incident(
            id = "t", season = state.season, week = 1, day = 0, teamId = user, kind = IncidentKind.PLAY_THROUGH,
            headline = "", message = "",
            options = listOf(
                baseballgm.events.IncidentOption(
                    "play", "참고", "", effects = listOf(baseballgm.events.IncidentEffect.PlayThrough(player.id)),
                ),
            ),
        )
        state.pendingIncidents += incident
        resolver.resolve(state, "t", "play")
        val after = state.player(player.id)
        assertNull(after.condition.injury)
        assertEquals(player.condition.form - balance.int("incidents.injury.playThroughFormPenalty"), after.condition.form)
        assertEquals(balance.int("incidents.injury.playThroughRelapseWeeks"), after.condition.relapseRiskWeeks)
        assertTrue(state.pendingIncidents.isEmpty())
    }

    @Test
    fun `쉬라고 한 선수는 이번 주 경기에 안 나오고 주가 끝나면 풀린다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(9L)
        val star = state.firstTeamOf(user).filter { it !is Pitcher }.maxBy { strength.overallOf(it) }
        val progress = loop.beginWeek(state, random, validate = true, highlightTeam = null)
        state.restingThisWeek += star.id
        while (!progress.isDone) loop.playDay(state, progress, random)
        val played = progress.gamesSoFar.flatMap { it.home.batting.keys + it.away.batting.keys }
        assertFalse(star.id in played, "쉬는 선수가 경기에 나왔다")
        loop.endWeek(state, progress, random)
        assertTrue(state.restingThisWeek.isEmpty())
    }

    @Test
    fun `이번 주만 바꾼 방침은 주가 끝나면 돌아온다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(3L)
        state.policies[user] = WeeklyPolicy.PROTECT
        val incident = Incident(
            id = "r", season = state.season, week = 1, day = null, teamId = user, kind = IncidentKind.RIVAL_WEEK,
            headline = "", message = "",
            options = listOf(baseballgm.events.IncidentOption("a", "총력", "", effects = listOf(baseballgm.events.IncidentEffect.PolicyThisWeek(WeeklyPolicy.ALL_OUT)))),
        )
        state.pendingIncidents += incident
        resolver.resolve(state, "r", "a")
        assertEquals(WeeklyPolicy.ALL_OUT, state.policyOf(user))
        loop.playWeek(state, random)
        assertEquals(WeeklyPolicy.PROTECT, state.policyOf(user))
    }

    // ---------- 단장 직접 엔트리 (docs/07) ----------

    @Test
    fun `맞바꾸기는 28명을 지키고 말소한 선수는 다음 주까지 못 올린다`() {
        val state = runner.newSeason()
        WeekLoop(balance, league).prepareRosters(state)
        val up = state.futuresOf(user).first { it !is Pitcher && actions.eligibilityProblem(state, it.id) == null }
        val down = state.firstTeamOf(user).filter { RosterSlot.of(it) == RosterSlot.of(up) }.minBy { strength.overallOf(it) }
        val before = state.firstTeamOf(user).size

        assertEquals(2, actions.swap(state, user, up.id, down.id).size)
        assertEquals(before, state.firstTeamOf(user).size)
        assertEquals(RosterLevel.FIRST_TEAM, state.player(up.id).rosterLevel)
        assertEquals(RosterLevel.FUTURES, state.player(down.id).rosterLevel)

        // 같은 주에 되돌릴 수 없다 (재등록 제한)
        assertNotNull(actions.promoteProblem(state, user, down.id, swappingOut = up.id))
        assertEquals(actions.reRegisterWeekIfDemotedNow(state), actions.reRegisterWeek(state, down.id))
    }

    @Test
    fun `유저는 포지션 제한 없이 등록·말소한다 — 꽉 찼을 때만 맞바꾼다`() {
        val state = runner.newSeason()
        WeekLoop(balance, league).prepareRosters(state)
        val up = state.futuresOf(user).first { actions.eligibilityProblem(state, it.id) == null }
        if (state.firstTeamOf(user).size >= limit) assertNotNull(actions.promoteProblem(state, user, up.id), "28명 상한은 남는다")

        // 포수를 전부 내려도 막지 않는다
        state.firstTeamOf(user).filter { RosterSlot.of(it) == RosterSlot.C }.forEach {
            assertNull(actions.demoteProblem(state, user, it.id))
            assertNotNull(actions.demote(state, user, it.id))
        }
        assertEquals(0, state.firstTeamOf(user).count { RosterSlot.of(it) == RosterSlot.C })

        // 선발을 범위(최대) 넘게 올려도 된다 — 자리만 있으면
        val max = PositionLimits(balance).rangeOf(RosterSlot.SP).last
        state.futuresOf(user).filter { RosterSlot.of(it) == RosterSlot.SP && actions.eligibilityProblem(state, it.id) == null }
            .forEach { if (state.firstTeamOf(user).size < limit) assertNotNull(actions.promote(state, user, it.id)) }
        val starters = state.firstTeamOf(user).count { RosterSlot.of(it) == RosterSlot.SP }
        assertTrue(starters <= limit)
        if (starters > max) println("선발 ${starters}명 (가이드 최대 $max) — 유저는 허용")
    }

    @Test
    fun `주 시작 자동 관리는 유저 구단의 구성을 뒤집지 않고 AI 구단만 맞춘다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        loop.prepareRosters(state)
        val limits = PositionLimits(balance)
        // 유저: 포수를 하나만 남기고 그 자리를 야수로 채운다 (가이드 최소 2 아래)
        state.firstTeamOf(user).filter { RosterSlot.of(it) == RosterSlot.C }.drop(1).forEach { catcher ->
            val up = state.futuresOf(user).first { it !is Pitcher && RosterSlot.of(it) != RosterSlot.C && actions.eligibilityProblem(state, it.id) == null }
            assertEquals(2, actions.swap(state, user, up.id, catcher.id).size)
        }
        val random = Random(12L)
        val progress = loop.beginWeek(state, random, validate = true, highlightTeam = user)
        assertTrue(
            state.firstTeamOf(user).count { RosterSlot.of(it) == RosterSlot.C } <= 1,
            "자동 관리가 유저 구단에 포수를 채워 넣었다",
        )
        league.teams.filter { it.id != user }.forEach { team ->
            assertEquals(emptyList(), limits.crowdedSlots(state.firstTeamOf(team.id)), "AI ${team.id} 는 가이드대로")
        }
        // 포수 하나로도 경기는 굴러간다
        while (!progress.isDone) loop.playDay(state, progress, random)
        assertTrue(loop.endWeek(state, progress, random).validationProblems.isEmpty())
    }

    @Test
    fun `시즌 내내 AI 구단의 1군이 포지션 가이드를 지킨다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(31L)
        val limits = PositionLimits(balance)
        val eligible = { team: baseballgm.model.TeamId, slot: RosterSlot ->
            state.futuresOf(team).any { RosterSlot.of(it) == slot && actions.eligibilityProblem(state, it.id) == null }
        }
        while (!state.isRegularSeasonOver) {
            val progress = loop.beginWeek(state, random, validate = false, highlightTeam = null)
            league.teams.forEach { team ->
                val firstTeam = state.firstTeamOf(team.id)
                assertTrue(firstTeam.size <= limit)
                RosterSlot.entries.forEach { slot ->
                    val range = limits.rangeOf(slot)
                    val count = firstTeam.count { RosterSlot.of(it) == slot }
                    assertTrue(count <= range.last, "${progress.week}주 ${team.id} ${slot.label} ${count}명 (최대 ${range.last})")
                    if (count < range.first) {
                        assertFalse(eligible(team.id, slot), "${progress.week}주 ${team.id} ${slot.label} ${count}명인데 2군에 올릴 선수가 있다")
                    }
                }
            }
            while (!progress.isDone) loop.playDay(state, progress, random)
            loop.endWeek(state, progress, random)
        }
    }

    @Test
    fun `개막 1군도 포지션 범위에 맞춘다`() {
        val state = runner.newSeason()
        val limits = PositionLimits(balance)
        val before = league.teams.sumOf { team -> limits.crowdedSlots(state.firstTeamOf(team.id)).size }
        assertTrue(before > 0, "개막 데이터가 이미 범위 안이면 이 테스트는 의미가 없다")
        WeekLoop(balance, league).prepareRosters(state)
        league.teams.forEach { team ->
            assertEquals(emptyList(), limits.crowdedSlots(state.firstTeamOf(team.id)), team.id.value)
            assertEquals(limit, state.firstTeamOf(team.id).size)
        }
    }

    @Test
    fun `답하지 않은 트레이드 제안은 기한이 지나면 철회된다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(1L)
        val partner = league.teams.last().id
        state.pendingTradeOffer = baseballgm.market.TradeProposal(proposer = partner, partner = user)
        state.pendingTradeOfferWeek = 1
        state.week.let { assertEquals(1, it) }
        loop.playWeek(state, random, highlightTeam = user) // 1주차: 아직 유효
        assertNotNull(state.pendingTradeOffer)
        loop.playWeek(state, random, highlightTeam = user) // 2주차 시작에 철회
        assertTrue(state.pendingTradeOffer == null || state.pendingTradeOfferWeek == 2, "기한이 지난 제안이 남아 있다")
        assertTrue(state.inbox.all().any { "거둬들였다" in it.text })
    }
}
