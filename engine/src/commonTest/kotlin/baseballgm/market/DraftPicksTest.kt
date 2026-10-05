package baseballgm.market

import baseballgm.model.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 지명권 보유·트레이드 규칙 (docs/10 안전장치). */
class DraftPicksTest {

    private val teams = listOf("AAA", "BBB", "CCC").map { TeamId(it) }
    private val rules = DraftPickTradeRules(DRAFT_BALANCE)
    private val rights = DraftRights.initial(TEST_SEASON, teams, rounds = 3, years = 2)

    @Test
    fun `처음에는 모든 팀이 자기 지명권을 2년치 가진다`() {
        assertEquals(3 * 3 * 2, rights.picks.size)
        assertEquals(6, rights.ofOwner(teams[0]).size)
        assertTrue(rights.picks.none { it.isTraded })
    }

    @Test
    fun `올해와 내년 지명권까지만 거래할 수 있다`() {
        val nextYear = rights.find(TEST_SEASON + 1, 2, teams[0])!!
        assertTrue(rules.problems(rights, nextYear, teams[0], teams[1], TEST_SEASON, draftDone = false).isEmpty())

        val farFuture = DraftPickRight(TEST_SEASON + 2, 2, teams[0], teams[0])
        val problems = rules.problems(rights, farFuture, teams[0], teams[1], TEST_SEASON, draftDone = false)
        assertTrue(problems.any { it.contains("내년") }, problems.toString())
    }

    @Test
    fun `드래프트가 끝난 해의 지명권은 거래할 수 없다`() {
        val thisYear = rights.find(TEST_SEASON, 1, teams[0])!!
        assertTrue(rules.problems(rights, thisYear, teams[0], teams[1], TEST_SEASON, draftDone = false).isEmpty())
        assertTrue(
            rules.problems(rights, thisYear, teams[0], teams[1], TEST_SEASON, draftDone = true).isNotEmpty(),
        )
    }

    @Test
    fun `1라운드 지명권은 2년 연속 양도할 수 없다`() {
        val thisYearFirst = rights.find(TEST_SEASON, 1, teams[0])!!
        val afterTrade = rights.transferred(thisYearFirst, teams[1])

        val nextYearFirst = afterTrade.find(TEST_SEASON + 1, 1, teams[0])!!
        val problems = rules.problems(afterTrade, nextYearFirst, teams[0], teams[2], TEST_SEASON, draftDone = false)
        assertTrue(problems.any { it.contains("2년 연속") }, problems.toString())

        // 2라운드는 연속으로 넘겨도 된다
        val nextYearSecond = afterTrade.find(TEST_SEASON + 1, 2, teams[0])!!
        assertTrue(
            rules.problems(afterTrade, nextYearSecond, teams[0], teams[2], TEST_SEASON, draftDone = false).isEmpty(),
        )
    }

    @Test
    fun `남의 지명권은 넘길 수 없다`() {
        val other = rights.find(TEST_SEASON + 1, 1, teams[1])!!
        val problems = rules.problems(rights, other, teams[0], teams[2], TEST_SEASON, draftDone = false)
        assertTrue(problems.any { it.contains("가진 지명권이 아니다") }, problems.toString())
    }

    @Test
    fun `드래프트가 끝나면 지난 지명권이 빠지고 새 연도가 채워진다`() {
        val traded = rights.transferred(rights.find(TEST_SEASON + 1, 1, teams[0])!!, teams[1])
        val rolled = traded.rolledForward(TEST_SEASON, teams, rounds = 3, years = 2)

        assertTrue(rolled.ofSeason(TEST_SEASON).isEmpty(), "끝난 시즌 지명권이 남아 있다")
        assertEquals(3 * 3 * 2, rolled.picks.size)
        // 이미 트레이드된 다음 시즌 지명권은 주인이 유지된다
        assertEquals(teams[1], rolled.find(TEST_SEASON + 1, 1, teams[0])!!.ownerTeam)
        // 새로 생긴 연도는 원소속팀이 가진다
        assertEquals(teams[0], rolled.find(TEST_SEASON + 2, 1, teams[0])!!.ownerTeam)
    }

    @Test
    fun `지명권 가치는 앞 순번일수록 높고 미래 지명권은 할인된다`() {
        val value = DraftPickValue(DRAFT_BALANCE)
        assertTrue(value.ofOverallPick(1) > value.ofOverallPick(10))
        assertTrue(value.ofOverallPick(10) > value.ofOverallPick(30))

        val pick = DraftPickRight(TEST_SEASON + 1, 1, teams[0], teams[1])
        val now = value.of(pick, teams = 10, seasonsAhead = 0) { 1 }
        val nextYear = value.of(pick, teams = 10, seasonsAhead = 1) { 1 }
        assertTrue(nextYear < now, "미래 지명권은 할인돼야 한다")
    }
}
