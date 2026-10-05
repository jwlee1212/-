package baseballgm.app

import baseballgm.io.LeagueLoader
import baseballgm.model.RosterLevel
import baseballgm.tools.ProjectFiles
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.season.PositionLimits
import baseballgm.season.RosterManager
import baseballgm.season.RosterSlot
import baseballgm.season.RosterState
import baseballgm.season.withRosterLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 우리 엔트리 자동 관리 (2026-10-03, 유저 요청).
 * 부상으로 내려간 1군 선수는 회복하면 돌아오고, 2군에 남은 고종합 선수는 1군 최약체와 맞바꿔 베스트를 유지한다.
 */
class InjuryReturnTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))

    @Test
    fun `회복한 우리 1군 선수는 다음 주 시작에 1군으로 돌아온다`() {
        val session = GameSession(balance, league, league.teams.first().id, seed = 2026L)
        val blockWeeks = balance.int("roster.reRegisterBlockWeeks")
        var returns = 0
        while (!session.seasonOver) {
            val played = session.week
            session.advanceWeek(delegate = true)
            if (session.isDraftWeek && !session.draftDone) session.autoDraft()
            val state = session.state
            returns += session.lastReport?.messages.orEmpty().count { "부상 복귀" in it.text && "자리 양보" !in it.text && it.teamId == session.userTeamId }
            // 이 주 시작의 엔트리 관리가 올렸어야 하는데 2군에 남은 선수
            val stuck = state.playersOf(session.userTeamId).filter { player ->
                val ready = state.roster.rehabReadyAt(player.id)
                player.rosterLevel == RosterLevel.FUTURES && ready != null && ready <= played &&
                    !player.condition.isInjured && player.military.isAvailable &&
                    state.roster.canPromote(player.id, played, blockWeeks)
            }
            assertTrue(stuck.isEmpty(), "${played}주차: 회복했는데 2군에 남음 ${stuck.map { it.registeredName }}")
        }
        println("부상 복귀 ${returns}건")
    }

    @Test
    fun `2군에 내려간 최고 선수는 재등록 제한이 풀리면 자동으로 1군에 돌아온다`() {
        val session = GameSession(balance, league, league.teams.first().id, seed = 7L)
        val star = session.roster(RosterLevel.FIRST_TEAM).first { !it.condition.isInjured }
        assertTrue(session.demote(star))
        // 말소한 주 + 제한 주 + 다시 올라오는 주의 시작 관리까지
        repeat(2 + balance.int("roster.reRegisterBlockWeeks")) { session.advanceWeek(delegate = true) }
        val now = session.state.player(star.id)
        if (now.condition.isInjured) return // 2군에서 다쳤다
        assertEquals(RosterLevel.FIRST_TEAM, now.rosterLevel, "${star.registeredName}이 아직 2군")
    }

    @Test
    fun `단장이 직접 올린 약한 선수는 보호 기간 동안 자동으로 내려가지 않고 그 뒤엔 정리된다`() {
        val session = GameSession(balance, league, league.teams.first().id, seed = 11L)
        val protectWeeks = balance.int("roster.userPickProtectWeeks")
        val blockWeeks = balance.int("roster.reRegisterBlockWeeks")
        val state = session.state
        // 2군에서 가장 약한 야수를 1군 최약체가 아닌 야수와 맞바꿔 올린다 (일부러 하는 유망주 기용)
        val weakProspect = session.roster(RosterLevel.FUTURES)
            .filter { it is Batter && session.eligibilityProblem(it) == null }
            .minBy { session.overall(it) }
        val down = session.roster(RosterLevel.FIRST_TEAM).filter { it is Batter && !it.condition.isInjured }
            .maxBy { session.overall(it) - 100 * (if (baseballgm.season.RosterSlot.of(it) == baseballgm.season.RosterSlot.of(weakProspect)) 1 else 0) }
        assertTrue(session.swap(weakProspect, down), "맞바꾸기 실패")
        val pickedWeek = state.week
        while (state.week <= pickedWeek + protectWeeks) {
            session.advanceWeek(delegate = true)
            val now = state.player(weakProspect.id)
            if (now.condition.isInjured || now.teamId != session.userTeamId) return // 다쳤거나 떠났다
            assertEquals(RosterLevel.FIRST_TEAM, now.rosterLevel, "${state.week - 1}주차: 보호 기간인데 내려갔다")
        }
        // 보호가 끝난 주의 시작 관리에서 정리된다 (2군에 훨씬 나은 야수가 있다)
        repeat(1 + blockWeeks) { session.advanceWeek(delegate = true) }
        val after = state.player(weakProspect.id)
        if (after.condition.isInjured) return
        assertEquals(RosterLevel.FUTURES, after.rosterLevel, "보호가 끝났는데도 1군에 남았다")
    }

    @Test
    fun `트레이드로 온 고종합 선수가 2군에 있으면 1군 최약체와 맞바꾼다`() {
        val manager = RosterManager(balance)
        val calculator = StrengthCalculator(balance)
        val ours = league.teams[0].id
        val roster = league.players.filter { it.teamId == ours }.toMutableList()
        // 다른 팀 1군 최고 타자가 트레이드로 와서 2군에 들어왔다
        val star = league.players.filter { it.teamId != ours && it is Batter && it.rosterLevel == RosterLevel.FIRST_TEAM }
            .maxBy { calculator.overallOf(it) }
            .withRosterLevel(RosterLevel.FUTURES)
        roster += star
        val weakest = roster.filter { it.rosterLevel == RosterLevel.FIRST_TEAM && it is Batter }.minBy { calculator.overallOf(it) }
        val changed = mutableMapOf<PlayerId, Player>()
        manager.manage(ours, roster, week = 3, state = RosterState(), rank = { calculator.overallOf(it) }, onChange = { changed[it.id] = it }, guided = false)
        assertEquals(RosterLevel.FIRST_TEAM, changed[star.id]?.rosterLevel, "트레이드로 온 선수가 1군에 안 올라왔다")
        val after = roster.map { changed[it.id] ?: it }
        assertEquals(balance.int("roster.firstTeamRegistered"), after.count { it.rosterLevel == RosterLevel.FIRST_TEAM })
        assertTrue(after.filter { it.rosterLevel == RosterLevel.FIRST_TEAM }.none { it.id == weakest.id } || RosterSlot.of(weakest) != RosterSlot.of(star))
        // 맞바꾼 뒤엔 2군에 1군 최약체보다 확실히 나은 선수가 없다 (포지션 최소 때문에 못 내리는 선수 제외)
        val margin = balance.double("roster.userAutoBestMargin")
        val first = after.filter { it.rosterLevel == RosterLevel.FIRST_TEAM }
        after.filter { it.rosterLevel == RosterLevel.FUTURES && !it.condition.isInjured && it.military.isAvailable }.forEach { player ->
            val spareable = first.filter { (it is Pitcher) == (player is Pitcher) && (RosterSlot.of(it) == RosterSlot.of(player) || PositionLimits(balance).canSpareSlot(first, it)) }
            spareable.minOfOrNull { calculator.overallOf(it) }?.let { low ->
                assertTrue(calculator.overallOf(player) <= low + margin, "2군 ${player.registeredName}이 1군 최약체보다 확실히 낫다")
            }
        }
    }
}
