package baseballgm.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** balance.json 과 같은 모양의 축소판. 실제 파일 테스트는 tools 쪽에서 한다. */
private val SAMPLE = """
{
  "_note": "설명 키는 무시되어야 한다",
  "ratingScale": { "min": 1, "max": 100, "leagueAverage": 50 },
  "ratingTables": {
    "status": "draft",
    "batterContactToK": { "50": 0.19, "20": 0.30, "80": 0.11 }
  },
  "leagueTargets": { "status": "draft", "battingAverage": [0.260, 0.275] },
  "injury": { "status": "draft", "durationWeeks": { "minor": [1, 2] } },
  "roster": { "status": "verify", "firstTeamRegistered": 28, "foreignPlayers": 3 },
  "gameRules": { "regularSeason": { "maxInnings": 11, "tiesAllowed": true } },
  "softCap": {
    "status": "verify",
    "penalties": [
      { "consecutive": 1, "rateOfExcess": 0.5, "draftPickDrop": false },
      { "consecutive": 2, "rateOfExcess": 1.0, "draftPickDrop": true }
    ]
  },
  "difficulty": {
    "easy": { "ownerPatience": "high" },
    "hard": { "ownerPatience": "low" }
  }
}
"""

class BalanceConfigTest {

    private val config = BalanceConfig.parse(SAMPLE)

    @Test
    fun `경로로 값을 읽는다`() {
        assertEquals(1, config.int("ratingScale.min"))
        assertEquals(100, config.int("ratingScale.max"))
        assertEquals(0.19, config.double("ratingTables.batterContactToK.50"))
        assertEquals(true, config.boolean("gameRules.regularSeason.tiesAllowed"))
        assertEquals("verify", config.string("roster.status"))
    }

    @Test
    fun `정수도 실수로 읽을 수 있다`() {
        assertEquals(11.0, config.double("gameRules.regularSeason.maxInnings"))
    }

    @Test
    fun `범위는 최소 최대 두 값으로 읽는다`() {
        assertEquals(0.260..0.275, config.doubleRange("leagueTargets.battingAverage"))
        assertEquals(1..2, config.intRange("injury.durationWeeks.minor"))
    }

    @Test
    fun `숫자 키 표는 오름차순으로 돌려준다`() {
        val table = config.numericMap("ratingTables.batterContactToK")
        assertEquals(listOf(20, 50, 80), table.keys.toList())
        assertEquals(0.30, table[20])
    }

    @Test
    fun `객체 배열은 섹션 목록으로 읽는다`() {
        val penalties = config.sections("softCap.penalties")
        assertEquals(2, penalties.size)
        assertEquals(1.0, penalties[1].double("rateOfExcess"))
        assertTrue(penalties[1].boolean("draftPickDrop"))
    }

    @Test
    fun `이름이 정해지지 않은 객체 묶음도 읽는다`() {
        val difficulties = config.sectionMap("difficulty")
        assertEquals(setOf("easy", "hard"), difficulties.keys)
        assertEquals("high", difficulties.getValue("easy").string("ownerPatience"))
    }

    @Test
    fun `설명 키와 상태 키는 데이터에서 빠진다`() {
        assertTrue("_note" !in config.keys)
        assertTrue("ratingScale" in config.keys)
        assertTrue("status" !in config.numericMap("ratingTables.batterContactToK").keys.map { it.toString() })
    }

    @Test
    fun `값이 없으면 경로를 알려주며 예외를 던진다`() {
        val failure = assertFailsWith<ConfigException> { config.double("ratingScale.없는값") }
        assertTrue(failure.message!!.contains("ratingScale.없는값"), "메시지: ${failure.message}")
    }

    @Test
    fun `타입이 다르면 예외를 던진다`() {
        assertFailsWith<ConfigException> { config.int("ratingScale") }
        assertFailsWith<ConfigException> { config.string("ratingScale.min") }
        assertFailsWith<ConfigException> { config.doubleRange("ratingScale") }
    }

    @Test
    fun `선택 값은 없으면 null 이거나 기본값이다`() {
        assertNull(config.doubleOrNull("ratingScale.없는값"))
        assertEquals(50, config.intOrNull("ratingScale.leagueAverage"))
        assertEquals(false, config.booleanOr("gameRules.regularSeason.없는값", false))
    }

    @Test
    fun `확인이 필요한 섹션과 초안 섹션을 구분한다`() {
        assertEquals(listOf("roster", "softCap"), config.sectionsNeedingVerification())
        assertEquals(listOf("injury", "leagueTargets", "ratingTables"), config.draftSections())
    }

    @Test
    fun `깨진 JSON 은 파싱 단계에서 막는다`() {
        assertFailsWith<ConfigException> { BalanceConfig.parse("{ not json") }
        assertFailsWith<ConfigException> { BalanceConfig.parse("[1, 2, 3]") }
    }
}
