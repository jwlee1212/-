package baseballgm.batting

import kotlin.math.abs
import kotlin.random.Random

/** 구종. 프로토타입은 두 가지만 */
enum class PitchType { FASTBALL, BREAKING }

/** 내 타자의 능력치 (0~100). 프로토타입에서는 화면 슬라이더가 정한다 */
data class BatterSkills(val contact: Int, val power: Int, val eye: Int)

/**
 * 공 하나.
 *
 * 좌표는 스트라이크존 기준: 가운데가 (0, 0), 존 가장자리가 ±1, y 는 아래가 +.
 * 공은 존 가운데를 향해 곧게 오다가 [breakStart] 이후 휘어서 마지막에 ([targetX], [targetY])에 도착한다.
 * 그래서 막판에 휘는 변화구는 일찍 판단하기 어렵다.
 *
 * @param revealFraction 비행 진행률이 이 값을 넘으면 구종이 화면에 드러난다 (선구안)
 */
data class Pitch(
    val type: PitchType,
    val label: String,
    val flightMs: Double,
    val targetX: Double,
    val targetY: Double,
    val breakX: Double,
    val breakY: Double,
    val breakStart: Double,
    val revealFraction: Double,
) {
    val isStrike: Boolean get() = abs(targetX) <= 1.0 && abs(targetY) <= 1.0

    /** 진행률 [progress](0=투수 손, 1=홈플레이트)에서 공의 존 좌표 */
    fun zonePositionAt(progress: Double): Pair<Double, Double> {
        val p = progress.coerceIn(0.0, 1.0)
        val bend = if (p <= breakStart) 0.0 else ((p - breakStart) / (1.0 - breakStart)).let { it * it }
        val x = (targetX - breakX) * p + breakX * bend
        val y = (targetY - breakY) * p + breakY * bend
        return x to y
    }

    fun isRevealedAt(progress: Double): Boolean = progress >= revealFraction
}

/** 투구 생성. 구종·속도·코스를 시드 난수로 정한다 (불변 원칙 2) */
class PitchGenerator(private val config: BattingConfig) {

    fun next(skills: BatterSkills, random: Random): Pitch {
        val type = pickType(random)
        val spec = config.pitches.getValue(type)
        val (x, y) = pickTarget(random)
        val side = if (random.nextBoolean()) 1.0 else -1.0
        return Pitch(
            type = type,
            label = spec.label,
            flightMs = spec.flightMs + spec.flightJitterMs * random.nextDouble(-1.0, 1.0),
            targetX = x,
            targetY = y,
            breakX = spec.breakX * side,
            breakY = spec.breakY,
            breakStart = config.breakStartFraction,
            revealFraction = config.revealFraction(skills.eye),
        )
    }

    private fun pickType(random: Random): PitchType {
        val total = config.pitches.values.sumOf { it.weight }
        var roll = random.nextDouble(total)
        for ((type, spec) in config.pitches) {
            roll -= spec.weight
            if (roll < 0) return type
        }
        return config.pitches.keys.last()
    }

    /** 스트라이크면 존 안쪽, 볼이면 한 축만 존 밖으로 벗어난 코스 */
    private fun pickTarget(random: Random): Pair<Double, Double> {
        if (random.nextDouble() < config.strikeChance) {
            return random.nextDouble(-0.9, 0.9) to random.nextDouble(-0.9, 0.9)
        }
        val off = random.nextDouble(config.ballOffsetMin, config.ballOffsetMax) * (if (random.nextBoolean()) 1 else -1)
        val inside = random.nextDouble(-1.0, 1.0)
        return if (random.nextBoolean()) off to inside else inside to off
    }
}
