package baseballgm.app

import baseballgm.events.IncidentKind
import baseballgm.io.BalanceConfig
import baseballgm.io.SaveGameCodec
import baseballgm.league.StrengthCalculator
import baseballgm.management.MemorySlot
import baseballgm.management.MoraleService
import baseballgm.management.PlayerMorale
import baseballgm.management.PromiseKind
import baseballgm.model.RosterLevel
import baseballgm.season.InboxCategory
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 선수 성향과 만족도 (docs/13, 2026-10-04 유저 요청 "FM처럼 선수 개개인의 충성도 → 선수에게서 오는 돌발 이벤트").
 */
class MoraleTest {

    private val balance = BalanceConfig.parse(ProjectFiles.read(ProjectFiles.BALANCE_PATH))
    private val morale = MoraleService(balance, StrengthCalculator(balance))
    private val host = HostFactory.create(
        ProjectFiles.read(ProjectFiles.BALANCE_PATH),
        ProjectFiles.read(ProjectFiles.TEAMS_PATH),
        { ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)) },
        object : SaveStore {
            var text: String? = null
            override fun read(): String? = text
            override fun write(text: String) {
                this.text = text
            }
        },
        null,
    )
    private val league = runBlocking { host.loadStartingLeague() }

    private fun newGame() = host.newSession(league, league.teams.first().id, "테스트")

    @Test
    fun `성향은 선수마다 고정이고 글자로만 보이며 여러 모양이 섞여 있다`() {
        val session = newGame()
        val own = session.roster(RosterLevel.FIRST_TEAM)
        val grades = setOf("높음", "보통", "낮음")
        own.forEach { player ->
            val first = session.scout(player).personality
            assertEquals(first, session.scout(player).personality, "볼 때마다 성향이 바뀌었다")
            assertTrue(first.loyalty in grades && first.ambition in grades && first.professionalism in grades)
        }
        val archetypes = session.state.allPlayers().map { session.scout(it).personality.archetype }.toSet()
        assertTrue(archetypes.size >= 4, "성향 모양이 너무 적다: $archetypes")
    }

    @Test
    fun `한 주가 지나면 우리 선수만 만족도가 생기고 요인이 붙는다`() {
        val session = newGame()
        repeat(3) { session.advanceWeek(delegate = true) }
        val ours = session.state.playersOf(session.userTeamId).map { it.id }.toSet()
        assertEquals(ours, session.state.morale.keys, "우리 선수 전원·우리 선수만 만족도가 있어야 한다")
        assertTrue(session.state.morale.values.all { it.value in 0..100 })
        val starter = session.roster(RosterLevel.FIRST_TEAM).first()
        val view = assertNotNull(session.morale(starter))
        assertTrue(view.factors.any { it.label.contains("1군") || it.label.contains("주전") }, "${view.factors}")
        // 남의 팀 선수는 만족도가 없다
        val other = session.state.playersOf(league.teams.last().id).first()
        assertNull(session.morale(other))
    }

    @Test
    fun `같은 종류의 기억은 쌓이지 않고 덮어쓴다`() {
        val session = newGame()
        val player = session.roster(RosterLevel.FUTURES).first()
        val team = session.userTeamId
        morale.remember(session.state, team, player.id, MemorySlot.ROSTER, "calledUp", "올라왔다")
        val once = session.state.morale.getValue(player.id).value
        morale.remember(session.state, team, player.id, MemorySlot.ROSTER, "calledUp", "올라왔다")
        assertEquals(1, session.state.morale.getValue(player.id).memories.size)
        // 바로 반영되는 몫은 매번 들어가지만, 목표값을 움직이는 기억은 하나뿐이다
        assertTrue(session.state.morale.getValue(player.id).value >= once)
        morale.remember(session.state, team, player.id, MemorySlot.ROSTER, "userDemoted", "내려갔다")
        assertEquals(listOf("userDemoted"), session.state.morale.getValue(player.id).memories.map { it.key })
    }

    @Test
    fun `출장 약속은 지키면 고마워하고 어기면 크게 실망한다`() {
        val session = newGame()
        val team = session.userTeamId
        val candidates = session.roster(RosterLevel.FUTURES).filter { session.eligibilityProblem(it) == null }
        val kept = candidates[0]
        val broken = candidates[1]
        val deadline = session.week
        morale.promise(session.state, team, kept.id, PromiseKind.PLAYING_TIME, deadline)
        morale.promise(session.state, team, broken.id, PromiseKind.PLAYING_TIME, deadline)
        val down = session.swapOutCandidates(kept).first { it.id != broken.id }
        assertTrue(session.swap(kept, down))
        session.advanceWeek(delegate = true)

        // 엔진이 주 시작에 엔트리를 정리해 [kept] 를 내렸을 수 있다 — 약속은 주 끝에 본다. 단장이 고른 선수는 보호된다
        val keptMorale = session.state.morale.getValue(kept.id)
        assertNull(keptMorale.promise)
        assertTrue(keptMorale.memories.any { it.key == "promiseKept" }, "${keptMorale.memories}")
        val brokenMorale = session.state.morale.getValue(broken.id)
        if (session.player(broken.id).rosterLevel == RosterLevel.FUTURES) {
            assertTrue(brokenMorale.memories.any { it.key == "promiseBroken" }, "${brokenMorale.memories}")
        }
        val said = session.state.inbox.all().filter { it.category == InboxCategory.PLAYER }.map { it.text }
        assertTrue(said.any { it.startsWith(kept.registeredName) }, "선수가 한마디도 안 했다: $said")
        assertTrue(Messages.thread(session, Sender.PLAYERS).isNotEmpty(), "선수단 대화가 비었다")
    }

    @Test
    fun `만족도가 폼 중심과 다년계약 요구액을 바꾸고 바닥이면 협상을 거부한다`() {
        val session = newGame()
        val player = session.extensionCandidates().first()
        val state = session.state

        state.morale[player.id] = PlayerMorale(95)
        val happyCenter = assertNotNull(morale.formCenter(state, player))
        val happyDemand = session.extensionTerms(player.id).demand
        state.morale[player.id] = PlayerMorale(30)
        val sadCenter = assertNotNull(morale.formCenter(state, player))
        val sadDemand = session.extensionTerms(player.id).demand
        assertTrue(happyCenter > 50.0 && sadCenter < 50.0, "만족 $happyCenter · 불만 $sadCenter")
        assertTrue(happyDemand <= sadDemand, "만족한 선수가 더 비싸게 부른다: $happyDemand > $sadDemand")

        state.morale[player.id] = PlayerMorale(5)
        assertTrue(session.extensionBlockedReason(player.id)?.contains("만족도") == true, "${session.extensionBlockedReason(player.id)}")
    }

    @Test
    fun `불만이 쌓이면 선수가 직접 이적을 요청하고 시장에 내놓으면 이적 희망이 된다`() {
        val session = newGame()
        var request: baseballgm.events.Incident? = null
        var guard = 0
        while (request == null && guard++ < 12) {
            // 계속 불만인 선수단을 만든다 (몇 주째 바닥)
            session.state.playersOf(session.userTeamId).forEach { session.state.morale[it.id] = PlayerMorale(5, lowWeeks = 5) }
            session.advanceWeek()
            while (session.pendingIncident != null || session.weekPaused) {
                val incident = session.pendingIncident
                if (incident?.kind == IncidentKind.TRADE_REQUEST) {
                    request = incident
                    break
                }
                if (incident != null) session.delegateIncident()
                session.advanceWeek()
            }
        }
        val incident = assertNotNull(request, "이적 요청이 오지 않았다")
        assertTrue(incident.kind.fromPlayer)
        val player = session.player(assertNotNull(incident.playerId))
        assertTrue(incident.options.any { it.id == "list" } && incident.options.any { it.id == "refuse" })

        session.resolveIncident("list")
        val view = assertNotNull(session.morale(player))
        assertTrue(view.transferListed, "시장에 내놓았는데 이적 희망 표시가 없다")
        assertTrue(Messages.thread(session, Sender.PLAYERS).any { it.lines.first().contains("이적 요청") })
    }

    @Test
    fun `만족도는 저장하고 다시 열어도 그대로다`() {
        val session = newGame()
        repeat(4) { session.advanceWeek(delegate = true) }
        val player = session.roster(RosterLevel.FIRST_TEAM).first()
        morale.remember(session.state, session.userTeamId, player.id, MemorySlot.CONTROVERSY, "defended", "감싸 줬다")
        val before = session.state.morale.toMap()

        val data = assertNotNull(session.saveData())
        val restored = host.resume(assertNotNull(SaveGameCodec.parseOrNull(SaveGameCodec.encode(data))))
        assertEquals(before, restored.state.morale.toMap())
        assertEquals(session.morale(player)?.factors, restored.morale(restored.player(player.id))?.factors)
    }
}
