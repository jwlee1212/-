package baseballgm.management

import baseballgm.market.DRAFT_BALANCE
import baseballgm.market.rosterFor
import baseballgm.market.testLeague
import baseballgm.market.testTeam
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.Player
import baseballgm.model.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 소프트캡 제재 (docs/11). */
class SalaryCapTest {

    private val cap = SalaryCap(DRAFT_BALANCE)
    private val a = TeamId("AAA")
    private val b = TeamId("BBB")

    private fun leagueWithPayroll(teamId: String, salaryEach: Double) = testLeague(
        teams = listOf(testTeam("AAA"), testTeam("BBB", pick = 2)),
        players = rosterFor("AAA").map { it.paid(if (teamId == "AAA") salaryEach else 1.0) } +
            rosterFor("BBB").map { it.paid(if (teamId == "BBB") salaryEach else 1.0) },
    )

    @Test
    fun `상한 아래면 제재가 없다`() {
        val (penalties, overruns) = cap.evaluate(leagueWithPayroll("AAA", 2.0), emptyMap())
        assertTrue(penalties.isEmpty(), penalties.toString())
        assertTrue(overruns.isEmpty())
    }

    @Test
    fun `처음 넘기면 초과분의 절반을 낸다`() {
        val league = leagueWithPayroll("AAA", 7.0)
        val (penalties, overruns) = cap.evaluate(league, emptyMap())
        val penalty = penalties.single { it.teamId == a }

        assertEquals(1, penalty.consecutive)
        assertEquals(penalty.excess * 0.5, penalty.fine, 0.001)
        assertTrue(!penalty.draftPickDrop, "1회 초과인데 지명 순번이 밀린다")
        assertEquals(1, overruns[a])
    }

    @Test
    fun `2년 연속이면 제재가 무거워지고 지명 순번이 밀린다`() {
        val league = leagueWithPayroll("AAA", 7.0)
        val (penalties, overruns) = cap.evaluate(league, mapOf(a to 1))
        val penalty = penalties.single { it.teamId == a }

        assertEquals(2, penalty.consecutive)
        assertEquals(penalty.excess * 1.0, penalty.fine, 0.001)
        assertTrue(penalty.draftPickDrop)
        assertEquals(2, overruns[a])
        assertTrue(penalty.message("구단 AAA").contains("제재금"))
    }

    @Test
    fun `3년 연속이면 가장 무겁다`() {
        val league = leagueWithPayroll("AAA", 7.0)
        val (penalties, _) = cap.evaluate(league, mapOf(a to 2))
        assertEquals(penalties.single().excess * 1.5, penalties.single().fine, 0.001)
    }

    @Test
    fun `넘지 않은 구단의 연속 기록은 사라진다`() {
        val league = leagueWithPayroll("AAA", 2.0)
        val (_, overruns) = cap.evaluate(league, mapOf(b to 2))
        assertEquals(null, overruns[b], "안 넘겼는데 연속 초과가 남아 있다")
    }
}

private fun Player.paid(salary: Double): Player {
    val contract = Contract(salary, 3, 0.0, 5, 5, ContractType.STANDARD)
    return when (this) {
        is baseballgm.model.Batter -> copy(contract = contract)
        is baseballgm.model.Pitcher -> copy(contract = contract)
    }
}
