package baseballgm.development

import baseballgm.io.BalanceConfig
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.BatterRatings
import baseballgm.model.CoachFocus
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

/** 성장 계산에 필요한 부분만 담은 설정. */
private val BALANCE = BalanceConfig.parse(
    """
    {
      "growth": {
        "inSeasonShare": 0.40,
        "inSeasonMaxAge": 25,
        "monthlyGrowthCount": 6,
        "switchHitterPenalty": 0.92,
        "peakVariation": 1.0,
        "annualGapClosure": [0.30, 0.30],
        "growthTypeShare": { "early": 0.25, "normal": 0.55, "late": 0.20 },
        "peakAges": { "early": [24, 28], "normal": [27, 31], "late": [29, 33] },
        "experienceMultiplier": {
          "firstTeamStarter": 1.2, "futuresStarter": 1.0, "firstTeamBench": 0.7, "injured": 0.5,
          "sangmu": 1.0, "activeDuty": 0.0
        },
        "coachFocus": { "focused": 1.3, "others": 0.9 },
        "managerSpecialtyBonus": 0.10,
        "futuresManagerBonus": 0.10,
        "activeDutyDecline": 1.5
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

private fun batter(
    age: Int,
    rating: Int = 50,
    potential: Int = 80,
    growthType: GrowthType = GrowthType.NORMAL,
    bats: Hand = Hand.RIGHT,
): Batter = Batter(
    id = PlayerId("P0001"),
    name = "김성장",
    birthYear = SEASON - age,
    throwsWith = Hand.RIGHT,
    bats = bats,
    origin = Origin.HIGH_SCHOOL,
    debutSeason = SEASON - 2,
    teamId = TeamId("DSK"),
    rosterLevel = RosterLevel.FIRST_TEAM,
    contract = Contract(1.0, 2, 0.0, 5, 3, ContractType.STANDARD),
    military = MilitaryStatus.Completed,
    hidden = HiddenTraits(
        potential = Attribute.batterAttributes.associateWith { potential },
        growthType = growthType,
        durability = 60,
        volatility = 50,
        platoonSplit = 30,
        scoutingNoiseSeed = 7,
    ),
    primaryPosition = Position.SHORTSTOP,
    defenseFitness = mapOf(Position.SHORTSTOP to 70),
    ratings = BatterRatings(rating, rating, rating, rating, rating),
)

class GrowthTest {

    private val curves = AgingCurves(BALANCE)
    private val model = GrowthModel(BALANCE, curves)
    private val starter = GrowthContext(PlayingExperience.FIRST_TEAM_STARTER)

    @Test
    fun `나이로 성장기 전성기 하락기를 가른다`() {
        assertEquals(CareerPhase.GROWTH, curves.phaseOf(24, GrowthType.NORMAL))
        assertEquals(CareerPhase.PEAK, curves.phaseOf(29, GrowthType.NORMAL))
        assertEquals(CareerPhase.DECLINE, curves.phaseOf(33, GrowthType.NORMAL))
        // 대기만성형은 늦게까지 큰다
        assertEquals(CareerPhase.GROWTH, curves.phaseOf(28, GrowthType.LATE))
        assertEquals(CareerPhase.PEAK, curves.phaseOf(30, GrowthType.LATE))
    }

    @Test
    fun `성장기 선수는 잠재력 쪽으로 올라간다`() {
        val young = batter(age = 22, rating = 50, potential = 80)
        val result = model.afterSeason(young, SEASON, starter, Random(1))
        assertTrue(result.changes.isNotEmpty())
        result.changes.forEach { (attribute, delta) -> assertTrue(delta > 0, "$attribute 가 안 올랐다") }
        assertTrue(result.player.rating(Attribute.CONTACT) > 50)
    }

    @Test
    fun `잠재력을 넘지 않는다`() {
        var player = batter(age = 20, rating = 50, potential = 62)
        repeat(15) { player = model.afterSeason(player, SEASON, starter, Random(it)).player as Batter }
        Attribute.batterAttributes.forEach { assertTrue(player.rating(it) <= 62, "$it ${player.rating(it)}") }
    }

    @Test
    fun `능력치마다 성장 속도가 다르다`() {
        val young = batter(age = 21, rating = 50, potential = 85)
        val result = model.afterSeason(young, SEASON, starter, Random(3))
        val power = result.changes.getValue(Attribute.POWER)
        val speed = result.changes.getValue(Attribute.SPEED)
        assertTrue(power > speed, "파워($power)가 주루($speed)보다 빨리 커야 한다")
    }

    @Test
    fun `하락기에는 주루와 수비가 먼저 떨어진다`() {
        val old = batter(age = 36, rating = 70, potential = 80)
        val result = model.afterSeason(old, SEASON, starter, Random(5))
        val speed = result.changes.getValue(Attribute.SPEED)
        val eye = result.changes.getValue(Attribute.EYE)
        assertTrue(speed < 0 && eye <= 0)
        assertTrue(speed < eye, "주루($speed)가 선구안($eye)보다 크게 떨어져야 한다")
    }

    @Test
    fun `1군 주전이 벤치보다 빨리 큰다`() {
        val young = batter(age = 21, rating = 50, potential = 85)
        val asStarter = model.afterSeason(young, SEASON, GrowthContext(PlayingExperience.FIRST_TEAM_STARTER), Random(9))
        val asBench = model.afterSeason(young, SEASON, GrowthContext(PlayingExperience.FIRST_TEAM_BENCH), Random(9))
        assertTrue(
            asStarter.changes.values.sum() > asBench.changes.values.sum(),
            "주전 ${asStarter.changes.values.sum()} vs 벤치 ${asBench.changes.values.sum()}",
        )
    }

    @Test
    fun `현역 복무 중에는 성장이 멈추고 감각이 떨어진다`() {
        val young = batter(age = 22, rating = 60, potential = 85)
        val result = model.afterSeason(young, SEASON, GrowthContext(PlayingExperience.ACTIVE_DUTY), Random(11))
        assertTrue(result.changes.values.all { it <= 0 }, "현역 복무 중에 능력치가 올랐다")
    }

    @Test
    fun `코치는 총량을 늘리지 않고 재분배한다`() {
        val young = batter(age = 21, rating = 50, potential = 85)
        val noCoach = model.afterSeason(young, SEASON, starter, Random(13))
        val withCoach = model.afterSeason(
            young,
            SEASON,
            starter.copy(coaches = mapOf(AttributeGroup.BATTING to CoachAssignment(CoachFocus.POWER, grade = 5))),
            Random(13),
        )
        assertTrue(
            withCoach.changes.getValue(Attribute.POWER) > noCoach.changes.getValue(Attribute.POWER),
            "집중 능력치가 더 커야 한다",
        )
        assertTrue(
            withCoach.changes.getValue(Attribute.CONTACT) <= noCoach.changes.getValue(Attribute.CONTACT),
            "같은 그룹의 나머지는 줄어야 한다",
        )
    }

    @Test
    fun `양타 선수는 성장이 조금 느리다`() {
        val switch = batter(age = 21, rating = 50, potential = 85, bats = Hand.SWITCH)
        val normal = batter(age = 21, rating = 50, potential = 85, bats = Hand.RIGHT)
        val switchGrowth = model.afterSeason(switch, SEASON, starter, Random(17)).changes.values.sum()
        val normalGrowth = model.afterSeason(normal, SEASON, starter, Random(17)).changes.values.sum()
        assertTrue(switchGrowth <= normalGrowth, "양타 $switchGrowth vs 일반 $normalGrowth")
    }

    @Test
    fun `시즌 중 성장은 25세 이하만 조금씩 받는다`() {
        val young = batter(age = 23, rating = 50, potential = 85)
        val old = batter(age = 27, rating = 50, potential = 85)
        val monthly = model.monthly(young, SEASON, starter, Random(19))
        val annual = model.afterSeason(young, SEASON, starter, Random(19))
        assertTrue(monthly.changes.values.sum() <= annual.changes.values.sum())
        assertTrue(model.monthly(old, SEASON, starter, Random(19)).changes.isEmpty(), "26세 이상은 시즌 중 성장이 없다")
    }
}
