package baseballgm.scouting

import baseballgm.io.BalanceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 잠재력 등급 기준선 (balance.json scouting.potentialGrades, 2026-10-04 코드에서 옮김) */
class PotentialScaleTest {

    private fun balance(c: Int, b: Int, a: Int, s: Int) = BalanceConfig.parse(
        """{ "scouting": { "potentialGrades": { "C": $c, "B": $b, "A": $a, "S": $s, "edgeHalfBand": 4.0 } } }""",
    )

    @Test
    fun `설정에서 읽은 기준선으로 경계값을 가른다`() {
        val scale = PotentialScale.from(balance(54, 64, 76, 84))
        assertEquals(PotentialGrade.D, scale.gradeOf(53.9))
        assertEquals(PotentialGrade.C, scale.gradeOf(54.0))
        assertEquals(PotentialGrade.B, scale.gradeOf(64.0))
        assertEquals(PotentialGrade.A, scale.gradeOf(83.9))
        assertEquals(PotentialGrade.S, scale.gradeOf(84.0))
    }

    @Test
    fun `설정 숫자만 바꾸면 등급이 바뀐다`() {
        val strict = PotentialScale.from(balance(60, 70, 80, 90))
        assertEquals(PotentialGrade.D, strict.gradeOf(58.0))
        assertEquals(PotentialGrade.B, strict.gradeOf(78.0))
    }

    @Test
    fun `구간 가운데 값은 양 끝을 반폭으로 잡는다`() {
        val scale = PotentialScale.from(balance(54, 64, 76, 84))
        assertEquals(46.0, scale.midpoint(PotentialGrade.D)) // C 아래로 반폭 2개
        assertEquals(59.0, scale.midpoint(PotentialGrade.C))
        assertEquals(70.0, scale.midpoint(PotentialGrade.B))
        assertEquals(80.0, scale.midpoint(PotentialGrade.A))
        assertEquals(88.0, scale.midpoint(PotentialGrade.S)) // S 위로 반폭 1개
    }

    @Test
    fun `기준선 순서가 틀리면 받지 않는다`() {
        assertFailsWith<IllegalArgumentException> { PotentialScale.from(balance(64, 54, 76, 84)) }
        assertFailsWith<IllegalArgumentException> { PotentialScale.from(balance(54, 64, 84, 84)) }
    }
}
