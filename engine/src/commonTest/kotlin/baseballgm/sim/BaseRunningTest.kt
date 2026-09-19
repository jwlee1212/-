package baseballgm.sim

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

/** 확률을 0/1 로 고정한 설정. 진루 규칙 자체를 확인하기 위해 운을 없앤다. */
private fun balanceWith(
    singleFromSecond: Double = 1.0,
    singleFirstToThird: Double = 0.0,
    doubleFromFirst: Double = 1.0,
    extraBaseOut: Double = 0.0,
    groundOutFromThird: Double = 1.0,
    groundOutFromSecond: Double = 0.0,
): BalanceConfig = BalanceConfig.parse(
    """
    {
      "baseRunning": {
        "singleRunnerOnSecondScores": $singleFromSecond,
        "singleRunnerOnFirstToThird": $singleFirstToThird,
        "doubleRunnerOnFirstScores": $doubleFromFirst
      },
      "baseRunningExtra": {
        "outAttemptingExtraBase": $extraBaseOut,
        "groundOutScoresFromThird": $groundOutFromThird,
        "groundOutAdvancesFromSecond": $groundOutFromSecond
      },
      "ratingTables": {
        "batterSpeedToExtraBase": { "20": 0.0, "50": 0.0, "95": 0.0 }
      }
    }
    """.trimIndent(),
)

private fun testBatter(speed: Int = 50): Batter = Batter(
    id = PlayerId("B001"),
    name = "김타자",
    birthYear = 1999,
    throwsWith = Hand.RIGHT,
    bats = Hand.RIGHT,
    origin = Origin.HIGH_SCHOOL,
    debutSeason = 2020,
    teamId = TeamId("DSK"),
    rosterLevel = RosterLevel.FIRST_TEAM,
    contract = Contract(1.0, 1, 0.0, 3, 5, ContractType.STANDARD),
    military = MilitaryStatus.Completed,
    hidden = HiddenTraits(
        potential = Attribute.batterAttributes.associateWith { 70 },
        growthType = GrowthType.NORMAL,
        durability = 60,
        volatility = 50,
        platoonSplit = 30,
        scoutingNoiseSeed = 1,
    ),
    primaryPosition = Position.LEFT_FIELD,
    defenseFitness = mapOf(Position.LEFT_FIELD to 60),
    ratings = BatterRatings(60, 60, 60, speed, 60),
)

private fun runnerAt(name: String) = Runner(PlayerId(name), PlayerId("P001"))

class BaseRunningTest {

    private fun running(balance: BalanceConfig = balanceWith()) = BaseRunning(balance, RatingTables(balance))

    @Test
    fun `홈런은 주자와 타자가 모두 득점한다`() {
        val bases = BaseState()
        bases[1] = runnerAt("R1")
        bases[3] = runnerAt("R3")
        val result = running().resolve(
            PaOutcome.HOME_RUN, bases, testBatter(), runnerAt("BAT"), outsBefore = 0, random = Random(1),
        )
        assertEquals(3, result.scored.size)
        assertEquals(3, result.rbi)
        assertEquals(0, bases.count)
    }

    @Test
    fun `만루 볼넷은 밀어내기 1점이고 만루가 유지된다`() {
        val bases = BaseState()
        (1..3).forEach { bases[it] = runnerAt("R$it") }
        val result = running().resolve(
            PaOutcome.WALK, bases, testBatter(), runnerAt("BAT"), outsBefore = 1, random = Random(1),
        )
        assertEquals(1, result.scored.size)
        assertEquals(1, result.rbi)
        assertEquals(3, bases.count)
        assertEquals("BAT", bases[1]!!.playerId.value)
    }

    @Test
    fun `1루 주자만 있을 때 볼넷은 득점 없이 한 칸씩 민다`() {
        val bases = BaseState()
        bases[1] = runnerAt("R1")
        val result = running().resolve(
            PaOutcome.WALK, bases, testBatter(), runnerAt("BAT"), outsBefore = 0, random = Random(1),
        )
        assertTrue(result.scored.isEmpty())
        assertEquals("R1", bases[2]!!.playerId.value)
        assertEquals("BAT", bases[1]!!.playerId.value)
        assertTrue(bases.isEmpty(3))
    }

    @Test
    fun `병살은 타자와 1루 주자를 함께 잡는다`() {
        val bases = BaseState()
        bases[1] = runnerAt("R1")
        val result = running().resolve(
            PaOutcome.DOUBLE_PLAY, bases, testBatter(), runnerAt("BAT"), outsBefore = 0, random = Random(1),
        )
        assertEquals(1, result.batterOuts)
        assertEquals(1, result.runnersOut.size)
        assertEquals("R1", result.runnersOut.first().playerId.value)
        assertEquals(0, bases.count)
    }

    @Test
    fun `2아웃에서 병살이면 점수가 나지 않는다`() {
        val bases = BaseState()
        bases[1] = runnerAt("R1")
        bases[3] = runnerAt("R3")
        val result = running().resolve(
            PaOutcome.DOUBLE_PLAY, bases, testBatter(), runnerAt("BAT"), outsBefore = 1, random = Random(1),
        )
        assertTrue(result.scored.isEmpty(), "3아웃이 되는 병살에서 득점이 났다")
    }

    @Test
    fun `희생플라이는 3루 주자만 들어온다`() {
        val bases = BaseState()
        bases[2] = runnerAt("R2")
        bases[3] = runnerAt("R3")
        val result = running().resolve(
            PaOutcome.SAC_FLY, bases, testBatter(), runnerAt("BAT"), outsBefore = 0, random = Random(1),
        )
        assertEquals(1, result.scored.size)
        assertEquals("R3", result.scored.first().playerId.value)
        assertEquals(1, bases.count)
    }

    @Test
    fun `야수선택은 타자가 살고 선행 주자가 죽는다`() {
        val bases = BaseState()
        bases[1] = runnerAt("R1")
        val result = running().resolve(
            PaOutcome.FIELDERS_CHOICE, bases, testBatter(), runnerAt("BAT"), outsBefore = 0, random = Random(1),
        )
        assertEquals(0, result.batterOuts)
        assertEquals(1, result.runnersOut.size)
        assertEquals("BAT", bases[1]!!.playerId.value)
    }

    @Test
    fun `실책 출루는 타점을 주지 않는다`() {
        val bases = BaseState()
        bases[3] = runnerAt("R3")
        val result = running().resolve(
            PaOutcome.REACHED_ON_ERROR, bases, testBatter(), runnerAt("BAT"), outsBefore = 0, random = Random(1),
        )
        assertEquals(1, result.scored.size)
        assertEquals(0, result.rbi, "실책으로 들어온 점수에는 타점이 없다")
    }

    @Test
    fun `어떤 결과에서도 주자는 사라지지 않는다`() {
        // 주자 보존: (원래 주자 + 살아 나간 타자) = 득점 + 남은 주자 + 아웃된 주자
        // 이 불변식이 깨지면 박스스코어 검증(docs/05)이 통째로 무너진다
        val random = Random(20260302)
        val runningRules = running(balanceWith(singleFirstToThird = 0.5, extraBaseOut = 0.2, groundOutFromSecond = 0.5))
        for (trial in 0 until 5000) {
            val outcome = PaOutcome.entries[random.nextInt(PaOutcome.entries.size)]
            val outsBefore = random.nextInt(3)
            val bases = BaseState()
            var runnersBefore = 0
            (1..3).forEach { base ->
                if (random.nextBoolean()) {
                    bases[base] = runnerAt("R$base")
                    runnersBefore++
                }
            }
            val batterRunner = runnerAt("BAT")
            val result = runningRules.resolve(outcome, bases, testBatter(), batterRunner, outsBefore, random)

            val reached = runnersBefore + if (outcome.batterReaches) 1 else 0
            val accounted = result.scored.size + bases.count + result.runnersOut.size
            assertEquals(
                reached,
                accounted,
                "$outcome (아웃 $outsBefore, 주자 $runnersBefore): 출루 $reached != 득점 ${result.scored.size} + " +
                    "잔루 ${bases.count} + 주자아웃 ${result.runnersOut.size}",
            )
            assertTrue(result.rbi <= result.scored.size, "$outcome: 타점이 득점보다 많다")
        }
    }
}
