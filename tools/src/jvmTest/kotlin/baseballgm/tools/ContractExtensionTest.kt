package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.model.ContractType
import baseballgm.model.Player
import baseballgm.season.ContractExtensionService
import baseballgm.season.Offseason
import baseballgm.season.SeasonCalendar
import baseballgm.season.SeasonState
import baseballgm.season.WeekLoop
import baseballgm.util.Seeds
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 비FA 다년계약 (docs/11, 2026-10-03). */
class ContractExtensionTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val strength = StrengthCalculator(balance)
    private val service = ContractExtensionService(balance, strength)
    private val user = league.teams.first().id

    private fun freshState() = SeasonState.of(league, SeasonCalendar.from(balance), balance)

    /** 협상할 수 있는 우리 선수 중 FA 가 [near] 면 가장 가까운, 아니면 가장 먼 선수 */
    private fun candidate(state: SeasonState, near: Boolean): Player {
        val eligible = state.playersOf(user).filter { service.ineligibleReason(state, user, it) == null }
        assertTrue(eligible.isNotEmpty(), "협상할 수 있는 선수가 없다")
        return if (near) eligible.minBy { service.seasonsToFa(it) } else eligible.maxBy { service.seasonsToFa(it) }
    }

    private fun flex(state: SeasonState, player: Player) = Seeds.random(1L, state.season, Seeds.Phase.EXTENSION, player.id.value.hashCode().toLong())

    @Test
    fun `FA 가 멀수록 시장가 대비 싸게 묶인다`() {
        val state = freshState()
        val eligible = state.playersOf(user).filter { service.ineligibleReason(state, user, it) == null }
        val ratios = eligible.map { player ->
            val terms = service.terms(state, player)
            service.seasonsToFa(player) to terms.demand / terms.marketSalary
        }.filter { (_, ratio) -> ratio.isFinite() }
        val near = ratios.filter { it.first == 0 }.map { it.second }
        val far = ratios.filter { it.first >= 3 }.map { it.second }
        if (near.isNotEmpty() && far.isNotEmpty()) {
            assertTrue(far.average() < near.average(), "FA 가 먼 선수가 더 비싸다: 먼 ${far.average()} / 가까운 ${near.average()}")
        }
    }

    @Test
    fun `짧게 계약하면 연봉을 더 달라고 한다`() {
        val state = freshState()
        val player = candidate(state, near = false)
        val preferred = service.terms(state, player)
        if (preferred.years > preferred.minYears) {
            val short = service.terms(state, player, preferred.minYears)
            assertTrue(short.demand >= preferred.demand, "짧은 계약이 더 싸다")
        }
    }

    @Test
    fun `요구액을 주면 도장을 찍고 다음 시즌부터 새 연봉이다`() {
        val state = freshState()
        val player = candidate(state, near = false)
        val terms = service.terms(state, player)
        val funds = state.funds.getValue(user)
        val result = service.propose(state, user, player.id, terms.demand, terms.years, 1.0, flex(state, player))
        assertTrue(result.accepted, result.message)

        val signed = state.player(player.id)
        assertEquals(ContractType.MULTI_YEAR, signed.contract.type)
        assertEquals(player.contract.salary, signed.contract.salary, "이번 시즌 연봉이 바뀌었다")
        assertEquals(terms.demand, signed.contract.nextSalary)
        assertEquals(terms.years + 1, signed.contract.yearsRemaining)
        assertEquals(funds - 1.0, state.funds.getValue(user), 1e-9)
        // 한 시즌에 두 번은 안 된다
        assertNotNull(service.ineligibleReason(state, user, signed))
    }

    @Test
    fun `헐값을 반복하면 올 시즌 협상이 닫힌다`() {
        val state = freshState()
        val player = candidate(state, near = true)
        val terms = service.terms(state, player)
        // 요구의 절반은 터무니없는 제안이라 한 번에 인내심 두 칸 (2026-10-05 협상 테이블) → (maxTalks + 1) / 2 번이면 닫힌다
        repeat((service.maxTalks + 1) / 2) { attempt ->
            assertNull(service.ineligibleReason(state, user, state.player(player.id)), "${attempt}번째에 벌써 닫혔다")
            val result = service.propose(state, user, player.id, terms.demand * 0.5, terms.years, 0.0, flex(state, player))
            assertFalse(result.accepted)
        }
        assertNotNull(service.ineligibleReason(state, user, state.player(player.id)))
        // 닫힌 뒤엔 요구액을 줘도 안 된다
        assertFalse(service.propose(state, user, player.id, terms.demand, terms.years, 0.0, flex(state, player)).accepted)
    }

    @Test
    fun `양보 폭은 시즌·선수마다 고정이라 같은 조건엔 같은 답이 나온다`() {
        val a = freshState()
        val b = freshState()
        val player = candidate(a, near = true)
        val terms = service.terms(a, player)
        val offer = terms.demand * 0.95
        val first = service.propose(a, user, player.id, offer, terms.years, 0.0, flex(a, player))
        val second = service.propose(b, user, player.id, offer, terms.years, 0.0, flex(b, player))
        assertEquals(first.accepted, second.accepted)
    }

    @Test
    fun `다년계약한 FA 대상은 시장에 나가지 않고 새 연봉으로 남는다`() {
        val state = freshState()
        val loop = WeekLoop(balance, league)
        val random = Random(7)
        val player = candidate(state, near = true)
        val terms = service.terms(state, player)
        assertTrue(service.propose(state, user, player.id, terms.demand, terms.years, 0.0, flex(state, player)).accepted)
        while (!state.isRegularSeasonOver) loop.playWeek(state, random, validate = false)

        val (next, report) = Offseason(balance, strength).run(
            state, Seeds.random(7L, state.season, Seeds.Phase.OFFSEASON), RookieFactory(balance, strength, league),
            autoFreeAgency = true,
        )
        if (player.id in report.retired) return
        val after = assertNotNull(next.players.firstOrNull { it.id == player.id }, "선수가 사라졌다")
        assertEquals(user, after.teamId, "다년계약한 선수가 팀을 떠났다")
        assertEquals(terms.demand, after.contract.salary, "새 연봉이 적용되지 않았다")
        assertNull(after.contract.nextSalary)
        assertEquals(terms.years, after.contract.yearsRemaining)
        assertTrue(report.faSignings.none { it.playerId == player.id }, "다년계약한 선수가 FA 시장에 나갔다")
    }

    @Test
    fun `다년계약 요구 연봉은 언제나 FA 시장가보다 싸다`() {
        val state = freshState()
        val eligible = state.playersOf(user).filter { service.ineligibleReason(state, user, it) == null }
        assertTrue(eligible.isNotEmpty())
        eligible.forEach { player ->
            (2..6).forEach { years ->
                val terms = service.terms(state, player, years)
                assertTrue(
                    terms.demand <= terms.marketSalary * 0.95 + 0.1,
                    "${player.registeredName} ${years}년: 요구 ${terms.demand} > 시장가 ${terms.marketSalary}",
                )
            }
        }
    }
}
