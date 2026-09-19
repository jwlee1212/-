package baseballgm.development

import baseballgm.io.BalanceConfig
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.BatterRatings
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
import baseballgm.model.TeamId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val BALANCE = BalanceConfig.parse(
    """
    {
      "awakening": {
        "chance": 0.5,
        "maxAge": 25,
        "lateBloomerMultiplier": 2.0,
        "potentialGain": [5, 10],
        "collapse": { "fromAge": 33, "chance": 0.5, "extraDeclineMultiplier": 2.4 }
      },
      "growth": {
        "annualGapClosure": [0.3, 0.3],
        "peakAges": { "early": [24, 28], "normal": [27, 31], "late": [29, 33] }
      },
      "aging": {
        "baseDeclinePerYear": 1.6,
        "extraDeclinePerYearOver": { "age": 34, "amount": 0.5 },
        "attributeMultiplier": {
          "contact": 1.0, "power": 0.6, "eye": 0.25, "speed": 1.7, "defense": 1.4,
          "stuff": 1.3, "control": 0.35, "groundball": 0.6, "stamina": 1.0
        }
      },
      "attributeGrowthSpeed": {
        "contact": 1.0, "power": 1.25, "eye": 1.1, "speed": 0.55, "defense": 0.8,
        "stuff": 1.0, "control": 1.2, "groundball": 0.9, "stamina": 0.9
      }
    }
    """.trimIndent(),
)

private const val SEASON = 2026

private fun subject(age: Int, growthType: GrowthType = GrowthType.NORMAL, rating: Int = 60): Batter = Batter(
    id = PlayerId("P0001"),
    name = "최각성",
    birthYear = SEASON - age,
    throwsWith = Hand.RIGHT,
    bats = Hand.RIGHT,
    origin = Origin.HIGH_SCHOOL,
    debutSeason = SEASON - 3,
    teamId = TeamId("DSK"),
    rosterLevel = RosterLevel.FIRST_TEAM,
    contract = Contract(1.0, 2, 0.0, 4, 3, ContractType.STANDARD),
    military = MilitaryStatus.Completed,
    hidden = HiddenTraits(
        potential = Attribute.batterAttributes.associateWith { 75 },
        growthType = growthType,
        durability = 60,
        volatility = 50,
        platoonSplit = 30,
        scoutingNoiseSeed = 11,
    ),
    primaryPosition = Position.CENTER_FIELD,
    defenseFitness = mapOf(Position.CENTER_FIELD to 70),
    ratings = BatterRatings(rating, rating, rating, rating, rating),
)

class AwakeningTest {

    private val model = AwakeningModel(BALANCE, AgingCurves(BALANCE))

    @Test
    fun `각성하면 현재치가 아니라 잠재력이 오른다`() {
        val young = subject(age = 22)
        var result = model.check(young, SEASON, Random(1))
        var attempts = 0
        while (!result.awakened && attempts < 20) {
            result = model.check(young, SEASON, Random(attempts + 2))
            attempts++
        }
        assertTrue(result.awakened, "각성이 한 번도 안 나왔다")
        assertEquals(young.ratingsMap(), result.player.ratingsMap(), "각성은 현재 능력치를 바꾸지 않는다")
        assertTrue(
            result.player.potentialOf(Attribute.CONTACT) > young.potentialOf(Attribute.CONTACT),
            "잠재력이 올라야 한다",
        )
    }

    @Test
    fun `나이가 많으면 각성하지 않는다`() {
        val veteran = subject(age = 30)
        repeat(20) { assertTrue(!model.check(veteran, SEASON, Random(it)).awakened) }
    }

    @Test
    fun `급노쇠는 33세 이상에게만 오고 능력치를 크게 깎는다`() {
        val young = subject(age = 28)
        repeat(20) { assertTrue(!model.check(young, SEASON, Random(it)).collapsed) }

        val old = subject(age = 35, rating = 70)
        var result = model.check(old, SEASON, Random(1))
        var attempts = 0
        while (!result.collapsed && attempts < 20) {
            result = model.check(old, SEASON, Random(attempts + 2))
            attempts++
        }
        assertTrue(result.collapsed, "급노쇠가 한 번도 안 나왔다")
        assertTrue(result.player.rating(Attribute.SPEED) < old.rating(Attribute.SPEED))
        assertTrue(
            old.rating(Attribute.SPEED) - result.player.rating(Attribute.SPEED) >
                old.rating(Attribute.EYE) - result.player.rating(Attribute.EYE),
            "주루가 선구안보다 크게 떨어져야 한다",
        )
    }

    @Test
    fun `대기만성형이 더 자주 각성한다`() {
        var lateCount = 0
        var normalCount = 0
        repeat(200) { seed ->
            if (model.check(subject(22, GrowthType.LATE), SEASON, Random(seed)).awakened) lateCount++
            if (model.check(subject(22, GrowthType.NORMAL), SEASON, Random(seed)).awakened) normalCount++
        }
        assertTrue(lateCount > normalCount, "대기만성 $lateCount vs 일반 $normalCount")
    }
}
