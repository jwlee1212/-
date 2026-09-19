package baseballgm.sim

import baseballgm.io.BalanceConfig
import baseballgm.io.ConfigException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val SAMPLE = BalanceConfig.parse(
    """
    {
      "ratingTables": {
        "status": "draft",
        "batterContactToK": { "20": 0.30, "50": 0.20, "80": 0.12 },
        "pitcherStaminaToPitchLimit": { "20": 40, "50": 85, "80": 105 }
      }
    }
    """.trimIndent(),
)

class RatingTablesTest {

    private val tables = RatingTables(SAMPLE)

    @Test
    fun `앵커 값은 그대로 나온다`() {
        assertEquals(0.30, tables.value("batterContactToK", 20))
        assertEquals(0.20, tables.value("batterContactToK", 50))
        assertEquals(0.12, tables.value("batterContactToK", 80))
    }

    @Test
    fun `사이값은 선형 보간이다`() {
        assertEquals(0.25, tables.value("batterContactToK", 35), absoluteTolerance = 1e-9)
        assertEquals(0.16, tables.value("batterContactToK", 65), absoluteTolerance = 1e-9)
    }

    @Test
    fun `표 밖은 양 끝 값으로 고정된다`() {
        assertEquals(0.30, tables.value("batterContactToK", 1))
        assertEquals(0.12, tables.value("batterContactToK", 100))
    }

    @Test
    fun `없는 표를 부르면 바로 예외를 던진다`() {
        val failure = assertFailsWith<ConfigException> { tables.value("없는표", 50) }
        assertTrue(failure.message!!.contains("없는표"))
    }

    @Test
    fun `status 는 표로 읽지 않는다`() {
        assertTrue("status" !in tables.tableNames)
        assertEquals(setOf("batterContactToK", "pitcherStaminaToPitchLimit"), tables.tableNames)
    }
}
