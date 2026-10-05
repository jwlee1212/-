package baseballgm.market

import baseballgm.league.StrengthCalculator
import baseballgm.model.TeamId
import baseballgm.scouting.ScoutingBudget
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** AI 지명 편향 (docs/10 "AI 편향 때문에 저평가 선수가 뒤 순번까지 남는다"). */
class DraftAITest {

    private val budget = ScoutingBudget(DRAFT_BALANCE)
    private val ai = DraftAI(DRAFT_BALANCE, PositionNeed(DRAFT_BALANCE, StrengthCalculator(DRAFT_BALANCE)), budget)
    private val teams = (1..10).map { TeamId("T%02d".format(it)) }
    private val precision = budget.amateurPrecision(3)

    /** 고졸 야수·대졸 투수를 섞은 풀. 잠재력이 조금씩 다르다 */
    private val pool = (1..30).map { index ->
        prospect(
            id = "P%02d".format(index),
            rating = 35 + index % 7,
            potential = 55 + index % 25,
            pitcher = index % 2 == 0,
            highSchool = index % 3 != 0,
        )
    }

    @Test
    fun `구단마다 지명 성향이 다르다`() {
        val biases = teams.map { ai.biasOf(it) }
        assertTrue(biases.map { it.highSchool }.distinct().size > 1, "고졸 선호가 전부 같다")
        assertTrue(biases.map { it.pitcher }.distinct().size > 1, "투수 선호가 전부 같다")
        assertTrue(biases.map { it.readiness }.distinct().size > 1, "즉시전력 선호가 전부 같다")
        assertTrue(biases.all { it.highSchool in 0.88..1.12 })
    }

    @Test
    fun `같은 구단은 몇 번을 평가해도 같은 값을 준다`() {
        val target = pool.first()
        val first = ai.evaluate(target, teams[0], emptyList(), precision, TEST_SEASON)
        repeat(5) {
            assertTrue(first == ai.evaluate(target, teams[0], emptyList(), precision, TEST_SEASON))
        }
    }

    @Test
    fun `팀마다 최고 후보가 갈린다`() {
        val tops = teams.map { team ->
            ai.rank(pool, team, emptyList(), TEST_SEASON) { precision }.first().first.id
        }
        assertTrue(tops.distinct().size > 1, "모든 팀이 같은 선수를 1순위로 본다 — 편향이 동작하지 않는다")
    }

    @Test
    fun `진짜 최고 유망주가 1순위로 뽑히지 않는 경우가 있다`() {
        // 잠재력이 가장 높은 선수를 진짜 1순위로 두고, 10개 팀이 한 명씩 뽑아 본다
        val best = pool.maxBy { it.player.hidden.potential.values.average() }
        val missedByTeams = teams.count { team ->
            ai.choose(pool, team, emptyList(), TEST_SEASON, Random(1)) { precision }.id != best.id
        }
        assertTrue(missedByTeams > 0, "모든 팀이 진짜 최고 유망주를 알아본다 — 스카우트 오차가 동작하지 않는다")
    }

    @Test
    fun `포지션이 비면 그 자리를 먼저 채운다`() {
        val team = teams[0]
        // 선발 투수가 넘치는 팀은 투수 후보의 가치가 떨어진다
        val pitcherRich = (1..6).map { testPitcher("SP$it", rating = 70, age = 27, teamId = team) }
        val pitcherProspect = pool.first { it.isPitcher }

        val emptyRoster = ai.evaluate(pitcherProspect, team, emptyList(), precision, TEST_SEASON)
        val fullRoster = ai.evaluate(pitcherProspect, team, pitcherRich, precision, TEST_SEASON)
        assertTrue(fullRoster < emptyRoster, "선발이 넘치는데 투수 가치가 그대로다")
    }
}
