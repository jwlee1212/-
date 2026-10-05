package baseballgm.management

import baseballgm.league.StrengthCalculator
import baseballgm.league.TeamRecord
import baseballgm.market.DRAFT_BALANCE
import baseballgm.market.TEST_SEASON
import baseballgm.market.rosterFor
import baseballgm.market.testBatter
import baseballgm.market.testLeague
import baseballgm.market.testTeam
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Player
import baseballgm.model.RosterLevel
import baseballgm.model.ServiceKind
import baseballgm.model.TeamId
import baseballgm.season.withContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val AAA = TeamId("AAA")

/** 연봉 계획 — 앞으로 몇 년의 캡 여유·운용 자금 (2026-10-04). */
class FinancialOutlookTest {

    private val finance = Finance(DRAFT_BALANCE, StrengthCalculator(DRAFT_BALANCE))
    private val outlook = FinancialOutlook(DRAFT_BALANCE, finance)

    /** 주전 21명(연 3억, 3년 남음, FA 5시즌 뒤) + 시험용 선수 */
    private fun leagueWith(vararg extra: Player, salary: Double = 3.0) = testLeague(
        teams = listOf(testTeam("AAA"), testTeam("BBB", pick = 2)),
        players = rosterFor("AAA", salary = salary) + rosterFor("BBB") + extra,
    )

    private fun player(id: String, contract: Contract, origin: Origin = Origin.HIGH_SCHOOL): Player =
        testBatter(id, teamId = AAA, origin = origin).copy(rosterLevel = RosterLevel.FIRST_TEAM).withContract(contract)

    private fun plan(league: baseballgm.league.League, change: OutlookChange = OutlookChange.NONE, funds: Double = 40.0) =
        outlook.project(league, AAA, funds, TeamRecord(AAA), fanSupport = 50, ownerTrust = 60, postseason = null, scoutingCost = 2.0, change = change)

    @Test
    fun `첫해 연봉 총액은 리그가 계산한 연봉 총액과 같다`() {
        val league = leagueWith(player("X", Contract(10.0, 2, 0.0, 1, 7, ContractType.FREE_AGENT)))
        assertEquals(league.payrollOf(AAA), plan(league).years.first().payroll, 0.01)
        assertEquals(4, plan(league).years.size)
        assertEquals(TEST_SEASON, plan(league).years.first().season)
    }

    @Test
    fun `FA 자격을 채우는 선수는 계약이 끝난 다음 해부터 빠진다`() {
        // 2년 남음, FA 까지 1시즌, 7시즌 뛰었음 → 첫 시즌 끝에 8시즌 자격 충족, 계약은 두 번째 시즌까지
        val league = leagueWith(player("X", Contract(10.0, 2, 0.0, 1, 7, ContractType.FREE_AGENT)))
        val row = plan(league).players.single { it.name == "타자X" }

        assertEquals(SalarySource.CONTRACT, row.cells[0]?.source)
        assertEquals(SalarySource.CONTRACT, row.cells[1]?.source)
        assertNull(row.cells[2], "FA 로 떠난 해에 연봉이 남아 있다")
        assertEquals(TEST_SEASON + 1, row.freeAgentAfter)
        assertTrue(plan(league).freeAgentsAfter(TEST_SEASON + 1).any { it.name == "타자X" })
    }

    @Test
    fun `FA 자격이 없으면 계약이 끝나도 지금 연봉으로 재계약한다고 본다`() {
        // 1년 남음, FA 까지 6시즌 → 4년 안에 FA 가 안 된다
        val league = leagueWith(player("Y", Contract(2.0, 1, 0.0, 6, 1, ContractType.STANDARD)))
        val row = plan(league).players.single { it.name == "타자Y" }

        assertEquals(SalarySource.CONTRACT, row.cells[0]?.source)
        (1..3).forEach { assertEquals(SalaryCell(2.0, SalarySource.ESTIMATE), row.cells[it]) }
        assertNull(row.freeAgentAfter)
    }

    @Test
    fun `외국인은 FA 가 없고 매년 재계약 예상으로 남는다`() {
        val league = leagueWith(player("F", Contract(8.0, 1, 0.0, 0, 0, ContractType.FOREIGN), origin = Origin.FOREIGN))
        val row = plan(league).players.single { it.name == "타자F" }
        assertNull(row.freeAgentAfter)
        assertEquals(SalarySource.ESTIMATE, row.cells[3]?.source)
    }

    @Test
    fun `다년계약으로 정한 다음 시즌 연봉은 다음 해부터 들어간다`() {
        val league = leagueWith(player("Z", Contract(3.0, 4, 0.0, 4, 3, ContractType.MULTI_YEAR, nextSalary = 9.0)))
        val row = plan(league).players.single { it.name == "타자Z" }
        assertEquals(3.0, row.cells[0]?.amount)
        assertEquals(9.0, row.cells[1]?.amount)
        assertEquals(9.0, row.cells[3]?.amount)
    }

    @Test
    fun `복무 중인 선수는 복귀하는 해부터 연봉에 들어간다`() {
        val serving = player("M", Contract(5.0, 4, 0.0, 6, 2, ContractType.STANDARD))
            .let { it as baseballgm.model.Batter }
            .copy(military = MilitaryStatus.Serving(ServiceKind.entries.first(), returnSeason = TEST_SEASON + 2, returnWeek = 1))
        val row = plan(leagueWith(serving)).players.single { it.name == "타자M" }
        assertNull(row.cells[0])
        assertNull(row.cells[1])
        assertEquals(5.0, row.cells[2]?.amount)
    }

    @Test
    fun `검토 중인 FA 계약은 그 기간만 캡 여유를 깎고 계약금은 지금 운용 자금에서 나간다`() {
        val league = leagueWith()
        val before = plan(league)
        val after = plan(league, OutlookChange(contracts = listOf(PlannedContract("새 FA", 12.0, 2, signingBonus = 10.0))))

        assertEquals(before.years[0].capRoom - 12.0, after.years[0].capRoom, 0.01)
        assertEquals(before.years[1].capRoom - 12.0, after.years[1].capRoom, 0.01)
        assertEquals(before.years[2].capRoom, after.years[2].capRoom, 0.01)
        assertEquals(before.years[0].fundsStart - 10.0, after.years[0].fundsStart, 0.01)
        assertEquals(12.0, after.years[0].planned, 0.01)
    }

    @Test
    fun `트레이드로 내보낸 선수의 연봉은 빠지고 받은 선수의 계약은 그대로 들어온다`() {
        val star = player("S", Contract(15.0, 3, 0.0, 5, 5, ContractType.FREE_AGENT))
        val incoming = testBatter("IN", teamId = TeamId("BBB")).withContract(Contract(4.0, 1, 0.0, 6, 1, ContractType.STANDARD))
        val league = leagueWith(star)
        val after = plan(league, OutlookChange(outgoing = setOf(star.id), incoming = listOf(incoming), cashOut = 3.0))
        val before = plan(league)

        assertEquals(before.years[0].payroll - 15.0 + 4.0, after.years[0].payroll, 0.01)
        assertEquals(SalarySource.PLANNED, after.players.single { it.name == "타자IN" }.cells[0]?.source)
        assertEquals(before.years[0].fundsStart - 3.0, after.years[0].fundsStart, 0.01)
    }

    @Test
    fun `상한을 계속 넘으면 해마다 제재금이 무거워진다`() {
        // 21명 × 7억 = 147억 → 상한 120억을 27억 넘는다
        val league = leagueWith(salary = 7.0)
        val years = plan(league).years
        val excess = years[0].payroll - years[0].cap
        assertEquals(excess * 0.5, years[0].capFine, 0.05)
        assertEquals(excess * 1.0, years[1].capFine, 0.05)
        assertEquals(excess * 1.5, years[2].capFine, 0.05)
    }

    @Test
    fun `해마다 연말 운용 자금이 다음 해 시작 자금이 되고 0 아래로 내려가지 않는다`() {
        val years = plan(leagueWith(salary = 7.0), funds = 5.0).years
        years.zipWithNext().forEach { (a, b) -> assertEquals(a.fundsEnd, b.fundsStart, 0.01) }
        assertTrue(years.all { it.fundsEnd >= 0.0 })
        assertNotNull(years.firstOrNull { it.coveredByOwner > 0.0 }, "적자가 큰데 모기업이 메운 해가 없다")
    }
}
