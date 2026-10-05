package baseballgm.app

import baseballgm.io.LeagueLoader
import baseballgm.market.DraftPolicy
import baseballgm.model.RosterLevel
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 스카우트팀 지명 추천과 자동 집중 관찰 (유저 요청 2026-10-03) */
class DraftAdviceTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val draftWeek = balance.int("season.draftWeek")

    private fun session(seed: Long = 4242L, team: Int = 0) = GameSession(balance, league, league.teams[team].id, seed = seed)

    @Test
    fun `추천은 같은 상황이면 같고 실제 드래프트 결과를 바꾸지 않는다`() {
        val looked = session()
        val plain = session()
        looked.advanceUntil(draftWeek, delegate = true)
        plain.advanceUntil(draftWeek, delegate = true)

        val first = looked.draftAdvice()
        assertTrue(first.isNotEmpty(), "추천이 비었다")
        assertEquals(balance.int("draft.rounds"), first.size, "라운드마다 추천이 있어야 한다")
        DraftPolicy.entries.forEach { looked.draftPolicy = it; looked.draftAdvice() }
        looked.draftPolicy = DraftPolicy.BALANCED
        assertEquals(first.map { r -> r.picks.map { it.prospect.id to it.availability } },
            looked.draftAdvice().map { r -> r.picks.map { it.prospect.id to it.availability } })

        looked.autoDraft()
        plain.autoDraft()
        assertEquals(plain.draftSelections(), looked.draftSelections(), "추천을 봤더니 드래프트 결과가 달라졌다")
    }

    @Test
    fun `지금 우리 차례면 첫 추천은 모두 지명 가능하고 뒤 순번일수록 확률이 낮다`() {
        val session = session()
        session.advanceUntil(draftWeek, delegate = true)
        session.openDraft()
        assertTrue(session.isDraftWeek, "드래프트 주차가 아니다")
        while (session.advanceDraftPick() != null) Unit
        val advice = session.draftAdvice()
        assertTrue(advice.first().picks.all { it.availability == 1.0 })
        assertTrue(advice.flatMap { it.picks }.all { it.availability >= balance.double("draftAdvisor.minAvailability") })
        // 이유는 최대 2개, 한 선수는 한 순번에만
        assertTrue(advice.flatMap { it.picks }.all { it.reasons.size <= 2 })
        val ids = advice.flatMap { it.picks }.map { it.prospect.id }
        assertEquals(ids.size, ids.toSet().size, "같은 선수가 여러 순번에 나온다")
    }

    @Test
    fun `추천 기준을 바꾸면 즉시전력은 현재 능력 잠재력 중시는 잠재력이 높은 쪽을 고른다`() {
        val session = session()
        session.advanceUntil(draftWeek, delegate = true)
        fun average(policy: DraftPolicy, pick: (baseballgm.scouting.ScoutReport) -> Double): Double {
            session.draftPolicy = policy
            return session.draftAdvice().take(3).flatMap { it.picks }.map { pick(session.prospectReport(it.prospect)) }.average()
        }
        val readyNow = average(DraftPolicy.READY) { it.scouted.overall.center }
        val potentialNow = average(DraftPolicy.POTENTIAL) { it.scouted.overall.center }
        val readyPot = average(DraftPolicy.READY) { it.potentialRange.center }
        val potentialPot = average(DraftPolicy.POTENTIAL) { it.potentialRange.center }
        assertTrue(readyNow > potentialNow, "즉시전력 현재 $readyNow vs 잠재력 중시 $potentialNow")
        assertTrue(potentialPot > readyPot, "잠재력 중시 잠재 $potentialPot vs 즉시전력 $readyPot")
    }

    /**
     * 남을 확률 보정: 드래프트 직전에 낸 확률과 실제로 남아 있었는지를 비교한다.
     * 우리는 매 순번 그 순번 추천 1순위(없으면 우리 평가 1위)를 뽑는다 — 모의 드래프트 가정과 같다.
     */
    @Test
    fun `남을 확률이 실제 드래프트와 대체로 맞는다`() {
        val bins = Array(5) { DoubleArray(3) } // [예측 합, 실제 합, 개수]
        var brier = 0.0
        var count = 0
        for (run in 0 until 30) {
            val session = session(seed = 1000L + run * 37, team = run % league.teams.size)
            session.advanceUntil(draftWeek, delegate = true)
            val advice = session.draftAdvice()
            session.openDraft()
            advice.forEach { round ->
                while (!session.isMyDraftTurn && !session.draftDone) {
                    checkNotNull(session.advanceDraftPick()) { "우리 차례도 아닌데 지명이 멈췄다 (${session.week}주차)" }
                }
                if (session.draftDone) return@forEach
                val available = session.availableProspects().map { it.id }.toSet()
                round.picks.forEach { pick ->
                    val actual = if (pick.prospect.id in available) 1.0 else 0.0
                    val bin = (pick.availability * 5).toInt().coerceAtMost(4)
                    bins[bin][0] += pick.availability; bins[bin][1] += actual; bins[bin][2] += 1.0
                    brier += (pick.availability - actual).let { it * it }
                    count++
                }
                val choice = round.picks.firstOrNull { it.prospect.id in available }?.prospect
                    ?: session.draftBoard(limit = 1).first()
                session.draftPlayer(choice)
            }
        }
        val lines = bins.mapIndexed { i, b ->
            "${i * 20}~${i * 20 + 20}%: n=${b[2].toInt()} 예측 ${"%.2f".format(b[0] / b[2])} 실제 ${"%.2f".format(b[1] / b[2])}"
        }
        println("ADVICE calibration brier=${"%.3f".format(brier / count)} n=$count\n" + lines.joinToString("\n"))
        // 화면에 나가는 확률은 보정표(draftAdvisor.availabilityCalibration)를 거친 값이다
        val gap = bins.filter { it[2] >= 15 }.map { kotlin.math.abs(it[0] / it[2] - it[1] / it[2]) }
        assertTrue(gap.all { it <= 0.2 }, "구간별 예측과 실제 차이가 크다: ${lines.joinToString(" / ")}")
    }

    @Test
    fun `자동 관찰은 빈 슬롯을 채우고 다 본 선수는 빼며 직접 붙이면 자리를 비켜 준다`() {
        val session = session()
        assertTrue(session.autoFocus, "자동 관찰은 기본으로 켜져 있다")
        session.advanceWeek(delegate = true)
        assertEquals(session.focusSlots, session.focusUsed, "첫 주에 슬롯이 다 차야 한다")

        // 직접 붙이면 자동 관찰 선수가 자리를 비켜 준다
        val outsider = session.draftBoard(limit = Int.MAX_VALUE).first { !session.isFocused(it) }
        assertTrue(session.toggleFocus(outsider))
        assertTrue(session.isFocused(outsider) && !session.isAutoFocused(outsider))
        assertEquals(session.focusSlots, session.focusUsed)

        // 몇 주 지나면 다 본 선수가 빠지고 다른 선수로 채워진다
        val firstBatch = session.focusedProspects().map { it.id }.toSet()
        repeat(draftWeek - 3) { if (!session.isDraftWeek) session.advanceWeek(delegate = true) }
        val now = session.focusedProspects()
        assertTrue(now.none { session.isFullyScouted(it) && session.isAutoFocused(it) }, "다 본 자동 관찰 선수가 남아 있다")
        assertTrue(now.any { it.id !in firstBatch }, "새로 채운 선수가 없다")
    }

    @Test
    fun `자동 관찰을 끄면 슬롯을 건드리지 않는다`() {
        val session = session()
        session.autoFocus = false
        repeat(3) { session.advanceWeek(delegate = true) }
        assertEquals(0, session.focusUsed)
        session.autoFocus = true
        assertEquals(session.focusSlots, session.focusUsed, "켜는 즉시 채워야 한다")
    }

    @Test
    fun `지명 자동 진행은 우리 차례에도 멈추지 않고 기준대로 고르며 같은 조건이면 결과가 같다`() {
        fun run(policy: DraftPolicy): GameSession = session().also { s ->
            s.advanceUntil(draftWeek, delegate = true)
            s.draftPolicy = policy
            s.openDraft()
            var guard = 0
            while (s.advanceDraftPickAuto() != null && guard++ < 500) Unit
        }
        val first = run(DraftPolicy.BALANCED)
        assertTrue(first.draftDone, "자동 진행이 끝나지 않았다")
        assertEquals(balance.int("draft.rounds"), first.myDraftPicks().size, "우리 지명이 빠졌다")
        assertEquals(first.draftSelections(), run(DraftPolicy.BALANCED).draftSelections(), "같은 조건인데 결과가 다르다")

        // 한 번에 끝내기(autoDraft)도 한 장씩 자동 진행과 같은 결과
        val instant = session().also { it.advanceUntil(draftWeek, delegate = true); it.autoDraft() }
        assertEquals(first.draftSelections(), instant.draftSelections())

        // 기준을 바꾸면 우리 지명이 달라진다: 즉시전력은 현재 능력, 잠재력 중시는 잠재력이 높은 쪽
        val ready = run(DraftPolicy.READY)
        val potential = run(DraftPolicy.POTENTIAL)
        fun average(s: GameSession, pick: (baseballgm.scouting.ScoutReport) -> Double) =
            s.myDraftPicks().map { sel -> pick(s.prospectReport(s.league.draftPool.byId(sel.playerId)!!)) }.average()
        val readyNow = average(ready) { it.scouted.overall.center }
        val potentialNow = average(potential) { it.scouted.overall.center }
        val readyPot = average(ready) { it.potentialRange.center }
        val potentialPot = average(potential) { it.potentialRange.center }
        println("AUTOPICK ready now=$readyNow pot=$readyPot / potential now=$potentialNow pot=$potentialPot")
        assertTrue(readyNow > potentialNow, "즉시전력 현재 $readyNow vs 잠재력 중시 $potentialNow")
        assertTrue(potentialPot > readyPot, "잠재력 중시 잠재 $potentialPot vs 즉시전력 $readyPot")
    }
}
