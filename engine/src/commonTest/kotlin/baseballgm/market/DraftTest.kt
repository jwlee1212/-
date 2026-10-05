package baseballgm.market

import baseballgm.model.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 드래프트 진행 (docs/10). */
class DraftTest {

    private val teams = listOf("AAA", "BBB", "CCC").map { TeamId(it) }
    private val order = teams.mapIndexed { index, team -> team to index + 1 }.toMap()
    private val draft = Draft(DRAFT_BALANCE)
    private val pool = DraftPool(
        season = TEST_SEASON,
        prospects = (1..20).map { prospect("P%02d".format(it), rating = 40 + it, potential = 60 + it) },
    )

    private fun rights() = DraftRights.initial(TEST_SEASON, teams, rounds = 3, years = 2)

    @Test
    fun `순번표는 라운드마다 전년도 역순으로 반복된다`() {
        val state = draft.prepare(TEST_SEASON, order, rights(), pool)
        assertEquals(9, state.order.size, "3라운드 × 3팀")
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), state.order.map { it.overallPick })
        // 각 라운드는 같은 순서로 다시 돈다
        assertEquals(teams, state.order.filter { it.round == 1 }.map { it.ownerTeam })
        assertEquals(teams, state.order.filter { it.round == 3 }.map { it.ownerTeam })
    }

    @Test
    fun `트레이드된 지명권은 새 주인이 지명한다`() {
        val traded = rights().let { current ->
            current.transferred(current.find(TEST_SEASON, 1, teams[0])!!, teams[2])
        }
        val state = draft.prepare(TEST_SEASON, order, traded, pool)
        val first = state.order.first()
        assertEquals(teams[2], first.ownerTeam, "지명권을 받은 팀이 지명해야 한다")
        assertEquals(teams[0], first.originalTeam, "순번은 원소속팀 성적으로 정해진다")
        assertTrue(first.isTraded)
    }

    @Test
    fun `계약금은 앞 순번일수록 크다`() {
        val total = 110
        val first = draft.signingBonus(1, total)
        val tenth = draft.signingBonus(10, total)
        val last = draft.signingBonus(total, total)
        assertTrue(first > tenth, "1순위 $first 가 10순위 $tenth 보다 커야 한다")
        assertTrue(tenth > last)
        assertEquals(5.0, first, 0.01)
        assertEquals(0.1, last, 0.01)
    }

    @Test
    fun `지명한 선수는 풀에서 사라지고 다시 뽑을 수 없다`() {
        val state = draft.prepare(TEST_SEASON, order, rights(), pool)
        val target = pool.prospects.first()
        draft.select(state, target.id)

        assertFalse(state.isAvailable(target.id), "지명된 선수가 아직 풀에 있다")
        assertEquals(1, state.selections.size)
        assertEquals(pool.prospects.size - 1, state.availableProspects().size)
        assertEquals(teams[0], state.selections.first().teamId)
    }

    @Test
    fun `모든 순번을 쓰면 드래프트가 끝난다`() {
        val state = draft.prepare(TEST_SEASON, order, rights(), pool)
        repeat(state.order.size) { draft.select(state, state.availableProspects().first().id) }
        assertTrue(state.isComplete)
        val result = state.result()
        assertEquals(9, result.selections.size)
        assertEquals(pool.prospects.size - 9, result.undrafted.size, "나머지는 육성선수 대상이 된다")
    }
}
