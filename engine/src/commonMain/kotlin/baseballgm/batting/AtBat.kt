package baseballgm.batting

import baseballgm.model.Hand
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.sim.BattedBallType
import baseballgm.sim.Half
import baseballgm.sim.PaOutcome
import baseballgm.sim.PlateAppearanceCompleted
import baseballgm.sim.PlateAppearanceResult
import baseballgm.sim.ScoredRun
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/** 스윙 판정 등급. 타이밍 차이가 작을수록 위 등급 */
enum class ContactQuality { SOLID, WEAK, FOUL, MISS }

/**
 * 맞은 공의 궤적 요약 (화면 표시용).
 *
 * @param angleDeg 0 = 가운데, 음수 = 3루 쪽(일찍 친 당겨친 타구), 양수 = 1루 쪽(늦게 친 밀어친 타구)
 */
data class Contact(
    val quality: ContactQuality,
    val angleDeg: Double,
    val distanceM: Double,
    val battedBall: BattedBallType?,
)

/** 공 하나의 판정 종류 */
enum class PitchCall { CALLED_STRIKE, BALL, SWINGING_STRIKE, FOUL, IN_PLAY }

/**
 * 공 하나를 처리한 결과.
 *
 * @param timingDiffMs 스윙했다면 (탭 시각 − 도착 시각). 음수면 빨랐다
 * @param result 이 공으로 타석이 끝났다면 그 결과
 */
data class PitchOutcome(
    val call: PitchCall,
    val timingDiffMs: Double?,
    val contact: Contact?,
    val result: AtBatResult?,
)

/**
 * 타석 하나의 최종 결과. 엔진의 타석 결과 타입으로 바꿀 수 있다.
 * 실제 경기 연결은 아직 하지 않는다 (프로토타입).
 */
data class AtBatResult(
    val outcome: PaOutcome,
    val battedBall: BattedBallType?,
    val pitches: Int,
    val contact: Contact?,
) {
    /** 시뮬레이터가 쓰는 타석 결과. 베이스 상태 변화는 `BaseRunning` 이 맡는다 */
    fun toPlateAppearanceResult(): PlateAppearanceResult = PlateAppearanceResult(outcome, battedBall, pitches)

    /**
     * 경기 이벤트로 바꾼다. **주자 없는 상황을 가정한 구조 확인용이다.** 실제 경기에 붙일 때는
     * [toPlateAppearanceResult] 를 시뮬레이터에 넘겨 주루·타점·자책점을 정식으로 계산해야 한다.
     */
    fun toEmptyBasesEvent(ctx: AtBatContext): PlateAppearanceCompleted {
        val homeRun = outcome == PaOutcome.HOME_RUN
        return PlateAppearanceCompleted(
            inning = ctx.inning,
            half = ctx.half,
            battingTeam = ctx.battingTeam,
            fieldingTeam = ctx.fieldingTeam,
            batterId = ctx.batterId,
            batterHand = ctx.batterHand,
            pitcherId = ctx.pitcherId,
            pitcherHand = ctx.pitcherHand,
            outcome = outcome,
            battedBall = battedBall,
            pitches = pitches,
            rbi = if (homeRun) 1 else 0,
            outsRecorded = if (outcome.batterReaches) 0 else 1,
            runnersOutOnBase = 0,
            runs = if (homeRun) listOf(ScoredRun(ctx.batterId, ctx.pitcherId, earned = true)) else emptyList(),
            errorBy = null,
            outsBefore = ctx.outsBefore,
            basesBefore = 0,
        )
    }
}

/** 이벤트 변환에 필요한 경기 맥락 */
data class AtBatContext(
    val inning: Int,
    val half: Half,
    val battingTeam: TeamId,
    val fieldingTeam: TeamId,
    val batterId: PlayerId,
    val batterHand: Hand,
    val pitcherId: PlayerId,
    val pitcherHand: Hand,
    val outsBefore: Int,
)

/** 탭 타이밍 → 판정, 판정 → 타구, 타구 → 타석 결과. 화면과 무관한 순수 로직 */
class SwingJudge(private val config: BattingConfig) {

    /** 타이밍 차이로 등급을 정한다. 존 밖 공은 판정 폭이 좁아진다 */
    fun quality(pitch: Pitch, skills: BatterSkills, timingDiffMs: Double): ContactQuality {
        val factor = if (pitch.isStrike) 1.0 else config.outOfZoneWindowFactor
        val d = abs(timingDiffMs)
        return when {
            d <= config.solidWindowMs(skills.contact) * factor -> ContactQuality.SOLID
            d <= config.weakWindowMs(skills.contact) * factor -> ContactQuality.WEAK
            d <= config.foulWindowMs(skills.contact) * factor -> ContactQuality.FOUL
            else -> ContactQuality.MISS
        }
    }

    fun contact(pitch: Pitch, skills: BatterSkills, timingDiffMs: Double, random: Random): Contact {
        val factor = if (pitch.isStrike) 1.0 else config.outOfZoneWindowFactor
        val solid = config.solidWindowMs(skills.contact) * factor
        val weak = config.weakWindowMs(skills.contact) * factor
        val d = abs(timingDiffMs)
        val side = if (timingDiffMs == 0.0) 0.0 else timingDiffMs.sign
        return when (quality(pitch, skills, timingDiffMs)) {
            ContactQuality.SOLID -> {
                val q = 1.0 - d / solid
                val power = skills.power / 100.0
                val distance = config.solidBaseM +
                    config.solidPowerBonusM * power * ((1 - config.solidQualityShare) + config.solidQualityShare * q) +
                    config.solidJitterM * random.nextDouble(-1.0, 1.0)
                val fly = random.nextDouble() < config.solidFlyChance(skills.power)
                Contact(
                    ContactQuality.SOLID,
                    angleDeg = timingDiffMs / solid * config.solidMaxAngleDeg,
                    distanceM = distance,
                    battedBall = if (fly) BattedBallType.FLY else BattedBallType.LINE,
                )
            }
            ContactQuality.WEAK -> {
                val t = ((d - solid) / (weak - solid)).coerceIn(0.0, 1.0)
                val ground = random.nextDouble() < config.weakGroundChance
                Contact(
                    ContactQuality.WEAK,
                    angleDeg = side * (config.solidMaxAngleDeg + (config.weakMaxAngleDeg - config.solidMaxAngleDeg) * t),
                    distanceM = config.weakMinM + (config.weakMaxM - config.weakMinM) * random.nextDouble(),
                    battedBall = if (ground) BattedBallType.GROUND else BattedBallType.FLY,
                )
            }
            ContactQuality.FOUL -> Contact(ContactQuality.FOUL, side * config.foulAngleDeg, 0.0, null)
            ContactQuality.MISS -> Contact(ContactQuality.MISS, 0.0, 0.0, null)
        }
    }

    /** 인플레이 타구의 결과. 수비수 위치는 아직 없어서 거리·방향·확률로 단순하게 정한다 */
    fun outcomeOf(contact: Contact, random: Random): PaOutcome = when (contact.quality) {
        ContactQuality.SOLID -> solidOutcome(contact, random)
        ContactQuality.WEAK -> if (random.nextDouble() < config.weakHitChance) {
            PaOutcome.SINGLE
        } else if (contact.battedBall == BattedBallType.GROUND) {
            PaOutcome.GROUND_OUT
        } else {
            PaOutcome.FLY_OUT
        }
        else -> error("파울·헛스윙은 인플레이가 아니다")
    }

    private fun solidOutcome(contact: Contact, random: Random): PaOutcome {
        if (contact.distanceM >= config.fenceM) return PaOutcome.HOME_RUN
        val fly = contact.battedBall == BattedBallType.FLY
        if (contact.distanceM >= config.doubleMinM) {
            if (fly && random.nextDouble() < config.deepFlyOutChance) return PaOutcome.FLY_OUT
            val corner = abs(contact.angleDeg) >= config.tripleMinAngleDeg
            return if (corner && random.nextDouble() < config.tripleChance) PaOutcome.TRIPLE else PaOutcome.DOUBLE
        }
        return if (fly) {
            if (random.nextDouble() < config.shortFlyHitChance) PaOutcome.SINGLE else PaOutcome.FLY_OUT
        } else {
            if (random.nextDouble() < config.lineHitChance) PaOutcome.SINGLE else PaOutcome.LINE_OUT
        }
    }
}

/**
 * 타석 하나 (볼카운트 진행).
 *
 * 화면은 [nextPitch] 로 공을 받아 날리고, 유저가 탭하면 [swing], 탭 없이 공이 지나가면 [take] 를 부른다.
 * 4볼 → 볼넷, 3스트라이크 → 삼진, 2스트라이크 이후 파울은 카운트를 올리지 않는다.
 */
class AtBat(
    private val config: BattingConfig,
    skills: BatterSkills,
    private val random: Random,
) {
    /** 능력치. 바꾸면 다음 공부터 반영된다 (프로토타입 슬라이더용) */
    var skills: BatterSkills = skills

    private val generator = PitchGenerator(config)
    private val judge = SwingJudge(config)

    var balls = 0
        private set
    var strikes = 0
        private set
    var pitchCount = 0
        private set
    var current: Pitch? = null
        private set
    var result: AtBatResult? = null
        private set

    val isOver: Boolean get() = result != null

    fun nextPitch(): Pitch {
        check(!isOver) { "끝난 타석이다" }
        check(current == null) { "이전 공을 아직 처리하지 않았다" }
        return generator.next(skills, random).also { current = it }
    }

    /**
     * 스윙. [tapMs] 는 투수가 공을 놓은 순간부터 탭까지의 시간이다.
     * 입력 지연 보정([BattingConfig.inputLatencyMs])은 여기서 뺀다.
     */
    fun swing(tapMs: Double): PitchOutcome {
        val pitch = takeCurrent()
        val diff = tapMs - config.inputLatencyMs - pitch.flightMs
        val contact = judge.contact(pitch, skills, diff, random)
        return when (contact.quality) {
            ContactQuality.MISS -> strike(PitchCall.SWINGING_STRIKE, diff, contact)
            ContactQuality.FOUL -> {
                if (strikes < 2) strikes++
                PitchOutcome(PitchCall.FOUL, diff, contact, null)
            }
            else -> {
                val outcome = judge.outcomeOf(contact, random)
                finish(PitchOutcome(PitchCall.IN_PLAY, diff, contact, AtBatResult(outcome, contact.battedBall, pitchCount, contact)))
            }
        }
    }

    /** 치지 않음 */
    fun take(): PitchOutcome {
        val pitch = takeCurrent()
        if (pitch.isStrike) return strike(PitchCall.CALLED_STRIKE, null, null)
        balls++
        val result = if (balls >= 4) AtBatResult(PaOutcome.WALK, null, pitchCount, null) else null
        return finish(PitchOutcome(PitchCall.BALL, null, null, result))
    }

    private fun strike(call: PitchCall, diff: Double?, contact: Contact?): PitchOutcome {
        strikes++
        val result = if (strikes >= 3) AtBatResult(PaOutcome.STRIKEOUT, null, pitchCount, null) else null
        return finish(PitchOutcome(call, diff, contact, result))
    }

    private fun finish(outcome: PitchOutcome): PitchOutcome {
        result = outcome.result
        return outcome
    }

    private fun takeCurrent(): Pitch {
        val pitch = checkNotNull(current) { "던진 공이 없다" }
        current = null
        pitchCount++
        return pitch
    }
}
