package baseballgm.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 난수 도우미.
 *
 * 모든 난수는 시드를 받은 [Random] 인스턴스에서만 나온다 (불변 원칙 2).
 * 전역 난수(`Random.Default`)나 시간 기반 난수는 쓰지 않는다.
 */

/** 정규분포. Box-Muller 변환. */
fun Random.nextGaussian(mean: Double, sd: Double): Double {
    var u1 = 0.0
    while (u1 <= 0.0) u1 = nextDouble()
    val u2 = nextDouble()
    return mean + sd * sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}

/** 정규분포를 정수로, 그리고 범위 안으로 자른다. */
fun Random.nextGaussianInt(mean: Double, sd: Double, range: IntRange): Int =
    nextGaussian(mean, sd).roundToInt().coerceIn(range.first, range.last)

/** `[최소, 최대]` 실수 범위에서 균등 추출. */
fun Random.nextInRange(range: ClosedFloatingPointRange<Double>): Double =
    range.start + nextDouble() * (range.endInclusive - range.start)

/** `[최소, 최대]` 정수 범위에서 균등 추출 (최대 포함). */
fun Random.nextInRange(range: IntRange): Int = nextInt(range.first, range.last + 1)

/** 가중치 추출. 가중치 합이 1이 아니어도 된다. */
fun <T> Random.weightedPick(weights: Map<T, Double>): T {
    val total = weights.values.sum()
    var point = nextDouble() * total
    for ((item, weight) in weights) {
        point -= weight
        if (point <= 0.0) return item
    }
    return weights.keys.last()
}

/** 확률 [chance] 로 true. */
fun Random.chance(chance: Double): Boolean = nextDouble() < chance
