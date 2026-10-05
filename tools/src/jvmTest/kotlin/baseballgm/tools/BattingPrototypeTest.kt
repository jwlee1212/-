package baseballgm.tools

import baseballgm.batting.AtBat
import baseballgm.batting.AtBatContext
import baseballgm.batting.AtBatResult
import baseballgm.batting.BatterSkills
import baseballgm.batting.BattingConfig
import baseballgm.batting.ContactQuality
import baseballgm.batting.PitchCall
import baseballgm.batting.PitchGenerator
import baseballgm.batting.SwingJudge
import baseballgm.model.Hand
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.sim.BattedBallType
import baseballgm.sim.Half
import baseballgm.sim.PaOutcome
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 타격 프로토타입 (실제 `config/balance.json` 의 battingPrototype 으로 돈다) */
class BattingPrototypeTest {
    private val config = BattingConfig.from(ProjectFiles.loadBalanceConfig())
    private val judge = SwingJudge(config)
    private val average = BatterSkills(50, 50, 50)

    @Test
    fun `능력치가 판정 폭과 구종이 보이는 시점을 바꾼다`() {
        assertTrue(config.solidWindowMs(100) > config.solidWindowMs(0))
        assertTrue(config.solidWindowMs(50) < config.weakWindowMs(50))
        assertTrue(config.weakWindowMs(50) < config.foulWindowMs(50))
        assertTrue(config.revealFraction(100) < config.revealFraction(0), "선구안이 높으면 더 일찍 보인다")
    }

    @Test
    fun `타이밍 차이로 정타·약한 타구·파울·헛스윙이 갈린다`() {
        val pitch = strikePitch()
        assertEquals(ContactQuality.SOLID, judge.quality(pitch, average, 0.0))
        assertEquals(ContactQuality.WEAK, judge.quality(pitch, average, config.solidWindowMs(50) + 1))
        assertEquals(ContactQuality.FOUL, judge.quality(pitch, average, -(config.weakWindowMs(50) + 1)))
        assertEquals(ContactQuality.MISS, judge.quality(pitch, average, config.foulWindowMs(50) + 1))
    }

    @Test
    fun `존 밖 공은 같은 타이밍이어도 판정이 나쁘다`() {
        val ball = generateSequence(0) { it + 1 }.map { PitchGenerator(config).next(average, Random(it)) }.first { !it.isStrike }
        val diff = config.solidWindowMs(50) * 0.9
        assertEquals(ContactQuality.SOLID, judge.quality(strikePitch(), average, diff))
        assertTrue(judge.quality(ball, average, diff) != ContactQuality.SOLID)
    }

    @Test
    fun `일찍 치면 당겨치고 늦게 치면 밀어친다`() {
        val pitch = strikePitch()
        val early = judge.contact(pitch, average, -config.solidWindowMs(50) * 0.8, Random(1))
        val late = judge.contact(pitch, average, config.solidWindowMs(50) * 0.8, Random(1))
        assertTrue(early.angleDeg < 0 && late.angleDeg > 0)
    }

    @Test
    fun `파워가 높을수록 정타 거리가 멀다`() {
        val pitch = strikePitch()
        fun avgDistance(power: Int) = (0 until 500).map {
            judge.contact(pitch, BatterSkills(50, power, 50), 0.0, Random(it)).distanceM
        }.average()
        val weak = avgDistance(10)
        val strong = avgDistance(90)
        assertTrue(strong > weak + 30, "파워 10: $weak m, 파워 90: $strong m")
    }

    @Test
    fun `공은 휘더라도 마지막엔 목표 지점에 도착한다`() {
        val pitch = PitchGenerator(config).next(average, Random(3))
        val (x, y) = pitch.zonePositionAt(1.0)
        assertTrue(abs(x - pitch.targetX) < 1e-9 && abs(y - pitch.targetY) < 1e-9)
    }

    @Test
    fun `안 치면 볼넷이나 삼진으로 끝나고 카운트가 맞는다`() {
        repeat(200) { seed ->
            val atBat = AtBat(config, average, Random(seed))
            while (!atBat.isOver) {
                atBat.nextPitch()
                atBat.take()
            }
            val result = atBat.result!!
            when (result.outcome) {
                PaOutcome.WALK -> assertEquals(4, atBat.balls)
                PaOutcome.STRIKEOUT -> assertEquals(3, atBat.strikes)
                else -> error("안 쳤는데 ${result.outcome}")
            }
            assertEquals(atBat.balls + atBat.strikes, result.pitches)
        }
    }

    @Test
    fun `2스트라이크 이후 파울은 삼진이 아니다`() {
        val atBat = AtBat(config, average, Random(7))
        var fouls = 0
        while (!atBat.isOver && fouls < 10) {
            val pitch = atBat.nextPitch()
            if (atBat.strikes < 2) {
                // 존 밖이어도 확실한 헛스윙
                atBat.swing(pitch.flightMs + config.foulWindowMs(50) * 3)
            } else {
                val factor = if (pitch.isStrike) 1.0 else config.outOfZoneWindowFactor
                val foulDiff = (config.weakWindowMs(50) + config.foulWindowMs(50)) / 2 * factor
                val outcome = atBat.swing(pitch.flightMs + config.inputLatencyMs + foulDiff)
                assertEquals(PitchCall.FOUL, outcome.call)
                fouls++
            }
        }
        assertEquals(2, atBat.strikes)
        assertTrue(!atBat.isOver)
    }

    @Test
    fun `같은 시드와 같은 입력이면 같은 결과`() {
        fun play(seed: Int): List<String> {
            val atBat = AtBat(config, average, Random(seed))
            val log = mutableListOf<String>()
            while (!atBat.isOver) {
                val pitch = atBat.nextPitch()
                log += atBat.swing(pitch.flightMs + 10).toString()
            }
            return log
        }
        assertEquals(play(42), play(42))
    }

    @Test
    fun `타석 결과를 엔진 타석 결과와 경기 이벤트로 바꿀 수 있다`() {
        val ctx = AtBatContext(1, Half.TOP, TeamId("A"), TeamId("B"), PlayerId("me"), Hand.RIGHT, PlayerId("p"), Hand.LEFT, 0)
        val homer = AtBatResult(PaOutcome.HOME_RUN, BattedBallType.FLY, 3, null)
        assertEquals(PaOutcome.HOME_RUN, homer.toPlateAppearanceResult().outcome)
        val event = homer.toEmptyBasesEvent(ctx)
        assertEquals(1, event.rbi)
        assertEquals(1, event.runs.size)
        assertEquals(0, event.outsRecorded)
        assertEquals(1, AtBatResult(PaOutcome.GROUND_OUT, BattedBallType.GROUND, 1, null).toEmptyBasesEvent(ctx).outsRecorded)
    }

    private fun strikePitch() =
        generateSequence(0) { it + 1 }.map { PitchGenerator(config).next(average, Random(it)) }.first { it.isStrike }
}
