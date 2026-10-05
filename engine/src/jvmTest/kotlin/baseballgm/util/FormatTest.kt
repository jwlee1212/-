package baseballgm.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** [fixed] 가 JVM `String.format("%.Nf")` 와 같은 글자를 내는지 확인한다 (JVM 에서만 비교 가능). */
class FormatTest {

    @Test
    fun `대표 값은 String format 과 같다`() {
        val values = listOf(0.0, 0.333333, 1.0, 0.2995, 12.345, 2.5, 3.14159, 100.0, 0.0004, 7.96, 1234.5678)
        for (value in values) for (digits in 0..3) {
            assertEquals("%.${digits}f".format(value), value.fixed(digits), "$value, $digits 자리")
        }
    }

    @Test
    fun `타율 방어율 범위의 무작위 값도 같다`() {
        val random = Random(7)
        repeat(20_000) {
            val value = random.nextDouble(0.0, 30.0)
            val digits = random.nextInt(0, 4)
            assertEquals("%.${digits}f".format(value), value.fixed(digits), "$value, $digits 자리")
        }
    }

    @Test
    fun `음수와 특수값`() {
        assertEquals("-1.50", (-1.5).fixed(2))
        assertEquals("-12", (-12.4).fixed(0))
        assertEquals("0.0", (-0.01).fixed(1), "반올림해서 0 이 되면 부호를 떼다")
        assertEquals("NaN", Double.NaN.fixed(3))
    }
}
