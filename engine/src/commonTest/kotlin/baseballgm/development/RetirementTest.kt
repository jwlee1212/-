package baseballgm.development

import baseballgm.io.BalanceConfig
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.BatterRatings
import baseballgm.model.CoachRole
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.GrowthType
import baseballgm.model.Hand
import baseballgm.model.HiddenTraits
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.PlayerId
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.model.StaffId
import baseballgm.model.TeamId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val BALANCE = BalanceConfig.parse(
    """
    {
      "retirement": {
        "fromAge": 32,
        "chancePerYearOver": 0.055,
        "lowRatingThreshold": 48,
        "lowRatingChance": 0.30,
        "noContractChance": 0.18,
        "forcedAge": 44,
        "minimumAgeForLowRating": 29,
        "coachCandidateShare": 1.0,
        "coachCandidateMinRating": 58
      },
      "staffGeneration": { "coachSalary": { "1": 0.8, "2": 1.2, "3": 1.8, "4": 2.6, "5": 3.5 } }
    }
    """.trimIndent(),
)

private const val SEASON = 2026

private fun player(age: Int, yearsRemaining: Int = 2, power: Int = 70): Batter = Batter(
    id = PlayerId("P0001"),
    name = "박노장",
    birthYear = SEASON - age,
    throwsWith = Hand.RIGHT,
    bats = Hand.RIGHT,
    origin = Origin.HIGH_SCHOOL,
    debutSeason = SEASON - 12,
    teamId = TeamId("DSK"),
    rosterLevel = RosterLevel.FIRST_TEAM,
    contract = Contract(3.0, yearsRemaining, 0.0, 0, 12, ContractType.STANDARD),
    military = MilitaryStatus.Completed,
    hidden = HiddenTraits(
        potential = Attribute.batterAttributes.associateWith { 80 },
        growthType = GrowthType.NORMAL,
        durability = 55,
        volatility = 45,
        platoonSplit = 30,
        scoutingNoiseSeed = 3,
    ),
    primaryPosition = Position.FIRST_BASE,
    defenseFitness = mapOf(Position.FIRST_BASE to 60),
    ratings = BatterRatings(contact = 60, power = power, eye = 60, speed = 40, defense = 50),
)

class RetirementTest {

    private val model = RetirementModel(BALANCE)

    @Test
    fun `젊고 잘하는 선수는 은퇴하지 않는다`() {
        assertEquals(0.0, model.chanceOf(player(age = 27), SEASON, overall = 70.0, hasContract = true))
    }

    @Test
    fun `나이가 많을수록 은퇴 확률이 올라간다`() {
        val at33 = model.chanceOf(player(age = 33), SEASON, 65.0, hasContract = true)
        val at38 = model.chanceOf(player(age = 38), SEASON, 65.0, hasContract = true)
        assertTrue(at38 > at33 && at33 > 0.0, "$at33 → $at38")
    }

    @Test
    fun `계약이 없고 못하면 확률이 더 오른다`() {
        val base = model.chanceOf(player(age = 34), SEASON, 65.0, hasContract = true)
        val noContract = model.chanceOf(player(age = 34, yearsRemaining = 0), SEASON, 65.0, hasContract = false)
        val weak = model.chanceOf(player(age = 34), SEASON, 40.0, hasContract = true)
        assertTrue(noContract > base)
        assertTrue(weak > base)
    }

    @Test
    fun `일정 나이가 되면 반드시 은퇴한다`() {
        assertEquals(1.0, model.chanceOf(player(age = 44), SEASON, 80.0, hasContract = true))
        assertTrue(model.retires(player(age = 45), SEASON, 80.0, true, Random(1)))
    }

    @Test
    fun `어린 선수는 능력치가 낮아도 은퇴 확률이 붙지 않는다`() {
        assertEquals(0.0, model.chanceOf(player(age = 24), SEASON, 30.0, hasContract = true))
    }

    @Test
    fun `은퇴 선수는 선수 시절 유형을 물려받아 코치가 된다`() {
        val slugger = player(age = 38, power = 85)
        val coach = model.toCoach(slugger, overall = 75.0, StaffId("C999"), Random(1))
        assertNotNull(coach)
        assertEquals(CoachRole.BATTING, coach.role)
        assertEquals(Attribute.POWER, coach.focus?.attribute, "거포는 장타 중심 코치가 된다")
        assertEquals(slugger.name, coach.name)
        assertTrue(coach.teamId == null, "무직 후보로 시장에 나온다")
    }

    @Test
    fun `실력이 모자란 선수는 코치 후보가 되지 않는다`() {
        assertNull(model.toCoach(player(age = 38), overall = 50.0, StaffId("C999"), Random(1)))
    }
}
