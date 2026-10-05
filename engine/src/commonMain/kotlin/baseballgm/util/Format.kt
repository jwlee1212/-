package baseballgm.util

import kotlin.math.abs
import kotlin.math.floor

/**
 * 소수점 자리 고정 문자열. `"%.3f".format(x)` 의 멀티플랫폼판이다 —
 * `String.format` 은 JVM 전용이라 웹(Wasm)·iOS 에서 쓸 수 없다 (CLAUDE.md §2 폰 테스트).
 *
 * 반올림은 JVM 과 같은 "반 올림"(HALF_UP). 음수가 반올림으로 0 이 되면 "-0.0" 대신 "0.0" 을 쓴다.
 */
fun Double.fixed(decimals: Int): String {
    require(decimals in 0..9) { "소수 자리는 0~9: $decimals" }
    if (isNaN()) return "NaN"
    if (isInfinite()) return if (this > 0) "Infinity" else "-Infinity"

    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = floor(abs(this) * factor + 0.5).toLong()
    val whole = scaled / factor
    val fraction = scaled % factor
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return "$sign$whole"
    return "$sign$whole." + fraction.toString().padStart(decimals, '0')
}

fun Float.fixed(decimals: Int): String = toDouble().fixed(decimals)
