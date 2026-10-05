package baseballgm.market

import baseballgm.development.AgingCurves
import baseballgm.io.BalanceConfig
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.PitcherRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val BALANCE: BalanceConfig = DRAFT_BALANCE

/** 가치 평가 (docs/11). */
class ValuationTest {

    private val valuation = Valuation(BALANCE, AgingCurves(BALANCE))

    private fun contract(salary: Double, years: Int) =
        Contract(salary, years, 0.0, 5, 5, ContractType.STANDARD)

    @Test
    fun `종합 능력치가 높을수록 기대 WAR 이 크다`() {
        val weak = testBatter("W", rating = 50, age = 27)
        val strong = testBatter("S", rating = 70, age = 27)
        assertTrue(valuation.expectedWar(strong, 70.0) > valuation.expectedWar(weak, 50.0))
        assertTrue(valuation.expectedWar(weak, 50.0) < 0.5, "50짜리 선수가 주전 값을 한다")
    }

    @Test
    fun `싸고 긴 계약은 자산이고 비싼 계약은 짐이다`() {
        val cheap = testBatter("C", rating = 68, age = 27).copy(contract = contract(1.0, 4))
        val overpaid = testBatter("O", rating = 68, age = 27).copy(contract = contract(18.0, 4))
        val estimate = RatingEstimate(68.0, 70.0)

        val cheapValue = valuation.valueOf(cheap, estimate, TEST_SEASON, TeamMode.NEUTRAL)
        val overpaidValue = valuation.valueOf(overpaid, estimate, TEST_SEASON, TeamMode.NEUTRAL)

        assertTrue(cheapValue.value > 0, "싸고 좋은 계약이 자산이 아니다 (${cheapValue.value})")
        assertTrue(overpaidValue.isBadContract, "비싼 계약이 짐이 아니다 (${overpaidValue.value})")
    }

    @Test
    fun `계약이 1년 남은 선수는 같은 기량이라도 가치가 낮다`() {
        val rental = testBatter("R", rating = 70, age = 27).copy(contract = contract(3.0, 1))
        val controlled = testBatter("K", rating = 70, age = 27).copy(contract = contract(3.0, 4))
        val estimate = RatingEstimate(70.0, 72.0)

        val rentalValue = valuation.valueOf(rental, estimate, TEST_SEASON, TeamMode.NEUTRAL).value
        val controlledValue = valuation.valueOf(controlled, estimate, TEST_SEASON, TeamMode.NEUTRAL).value
        assertTrue(controlledValue > rentalValue * 2, "$controlledValue vs $rentalValue")
    }

    @Test
    fun `우승 도전 팀은 올해를 리빌딩 팀은 나중을 크게 본다`() {
        val veteran = testBatter("V", rating = 72, age = 33).copy(contract = contract(6.0, 3))
        val youngster = testBatter("Y", rating = 55, potential = 78, age = 21).copy(contract = contract(0.5, 4))
        val vEstimate = RatingEstimate(72.0, 72.0)
        val yEstimate = RatingEstimate(55.0, 78.0)

        val contendVet = valuation.valueOf(veteran, vEstimate, TEST_SEASON, TeamMode.CONTEND).value
        val rebuildVet = valuation.valueOf(veteran, vEstimate, TEST_SEASON, TeamMode.REBUILD).value
        val contendKid = valuation.valueOf(youngster, yEstimate, TEST_SEASON, TeamMode.CONTEND).value
        val rebuildKid = valuation.valueOf(youngster, yEstimate, TEST_SEASON, TeamMode.REBUILD).value

        assertTrue(contendVet > rebuildVet, "우승 도전 팀이 베테랑을 더 높이 봐야 한다")
        assertTrue(rebuildKid > contendKid, "리빌딩 팀이 유망주를 더 높이 봐야 한다")
    }

    @Test
    fun `나이가 들면 기대 WAR 이 줄어든다`() {
        val old = testBatter("O", rating = 70, age = 34).copy(contract = contract(5.0, 4))
        val projection = valuation.project(old, RatingEstimate(70.0, 70.0), TEST_SEASON, years = 4)
        assertTrue(projection.first() > projection.last(), "34세 선수의 4년 뒤가 더 좋다: $projection")
    }

    @Test
    fun `어린 유망주는 잠재력으로 값이 매겨진다`() {
        val prospect = testBatter("P", rating = 42, potential = 80, age = 20).copy(contract = contract(0.3, 3))
        val estimate = RatingEstimate(42.0, 80.0)
        val plain = valuation.valueOf(prospect, estimate, TEST_SEASON, TeamMode.REBUILD).value
        val asProspect = valuation.prospectValue(prospect, estimate, TEST_SEASON, TeamMode.REBUILD).value
        assertTrue(asProspect > plain, "유망주 보정이 없다 ($asProspect vs $plain)")
        assertTrue(asProspect > 0)
    }

    @Test
    fun `불펜은 같은 능력치라도 가치가 낮다`() {
        val starter = testPitcher("SP", rating = 68, age = 27, role = PitcherRole.STARTER)
        val reliever = testPitcher("RP", rating = 68, age = 27, role = PitcherRole.RELIEVER)
        assertTrue(valuation.expectedWar(starter, 68.0) > valuation.expectedWar(reliever, 68.0))
    }

    @Test
    fun `계약 연수만큼만 본다`() {
        val player = testBatter("L", rating = 65, age = 26).copy(contract = contract(2.0, 2))
        val value = valuation.valueOf(player, RatingEstimate(65.0, 70.0), TEST_SEASON, TeamMode.NEUTRAL)
        assertEquals(2, value.projectedWar.size)
    }
}
