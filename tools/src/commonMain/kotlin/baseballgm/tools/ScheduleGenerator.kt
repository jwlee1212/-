package baseballgm.tools

import baseballgm.league.Schedule
import baseballgm.league.ScheduleRules
import baseballgm.league.ScheduledGame
import baseballgm.model.TeamId
import kotlin.random.Random

/**
 * 정규시즌 일정표 생성 (docs/01, 07).
 *
 * 10팀이 상대별 16경기(3연전 4회 + 2연전 2회), 화~일 6경기씩 24주를 치른다.
 * 숫자가 정확히 맞아떨어진다:
 * - 3연전: 45개 팀 조합 × 4회 = 180시리즈. 한 주에 두 시리즈(화~목, 금~일)씩 = 10시리즈 → **18주**
 * - 2연전: 45 × 2 = 90시리즈. 한 주에 세 시리즈(화수·목금·토일)씩 = 15시리즈 → **6주**
 *
 * 매 주의 대진은 **원형 스케줄(circle method)** 로 만든 9라운드를 쓴다. 9라운드 한 바퀴가
 * 모든 팀 조합을 정확히 한 번씩 덮으므로, 네 바퀴 돌면 모든 조합이 3연전을 4번,
 * 두 바퀴 돌면 2연전을 2번 갖는다. 홈·원정은 바퀴마다 뒤집어서 8:8로 맞춘다.
 */
class ScheduleGenerator(private val rules: ScheduleRules) {

    fun generate(season: Int, teamIds: List<TeamId>, random: Random): Schedule {
        require(teamIds.size % 2 == 0) { "팀 수가 짝수여야 한다" }
        val order = teamIds.shuffled(random)
        val rounds = roundRobinRounds(order.size)

        val threeGameWeeks = rules.weeks - rules.twoGameWeeksAtSeasonEnd
        val threeGameSlots = slotsFor(rules.threeGameSeriesPerOpponent, rounds.size)
        val twoGameSlots = slotsFor(rules.twoGameSeriesPerOpponent, rounds.size)

        val threeGameWeekPlan = assignToWeeks(threeGameSlots, threeGameWeeks, SLOTS_PER_THREE_GAME_WEEK, random)
        val twoGameWeekPlan = assignToWeeks(twoGameSlots, rules.twoGameWeeksAtSeasonEnd, SLOTS_PER_TWO_GAME_WEEK, random)

        val games = mutableListOf<ScheduledGame>()
        var seriesId = 1

        threeGameWeekPlan.forEachIndexed { weekIndex, slots ->
            val week = weekIndex + 1
            slots.forEachIndexed { slotIndex, slot ->
                val firstDay = slotIndex * THREE_GAME_LENGTH
                rounds[slot.round].forEach { (a, b) ->
                    val (home, away) = homeAway(order[a], order[b], slot.cycle)
                    repeat(THREE_GAME_LENGTH) { day ->
                        games += ScheduledGame(week, firstDay + day, home, away, seriesId)
                    }
                    seriesId++
                }
            }
        }

        twoGameWeekPlan.forEachIndexed { weekIndex, slots ->
            val week = threeGameWeeks + weekIndex + 1
            slots.forEachIndexed { slotIndex, slot ->
                val firstDay = slotIndex * TWO_GAME_LENGTH
                rounds[slot.round].forEach { (a, b) ->
                    val (home, away) = homeAway(order[a], order[b], slot.cycle)
                    repeat(TWO_GAME_LENGTH) { day ->
                        games += ScheduledGame(week, firstDay + day, home, away, seriesId)
                    }
                    seriesId++
                }
            }
        }

        return Schedule(season, games.sortedWith(compareBy({ it.week }, { it.day }, { it.home.value })))
    }

    /** 바퀴(cycle)가 홀수면 홈·원정을 뒤집어 맞대결 홈경기를 반씩 나눈다. */
    private fun homeAway(a: TeamId, b: TeamId, cycle: Int): Pair<TeamId, TeamId> =
        if (cycle % 2 == 0) a to b else b to a

    private fun slotsFor(cycles: Int, roundCount: Int): List<Slot> =
        (0 until cycles).flatMap { cycle -> (0 until roundCount).map { round -> Slot(round, cycle) } }

    /**
     * 시리즈 슬롯을 주차에 배분한다. 한 주 안에서는 **서로 다른 라운드**만 묶는다.
     * 같은 라운드를 한 주에 두 번 넣으면 같은 상대를 한 주에 두 번 만나게 된다.
     */
    private fun assignToWeeks(slots: List<Slot>, weeks: Int, perWeek: Int, random: Random): List<List<Slot>> {
        require(slots.size == weeks * perWeek) {
            "슬롯 ${slots.size}개를 ${weeks}주 × ${perWeek}슬롯에 넣을 수 없다"
        }
        repeat(MAX_SHUFFLE_ATTEMPTS) {
            val pool = slots.shuffled(random).toMutableList()
            val plan = mutableListOf<List<Slot>>()
            var failed = false
            repeat(weeks) {
                val week = mutableListOf<Slot>()
                repeat(perWeek) {
                    val index = pool.indexOfFirst { candidate -> week.none { it.round == candidate.round } }
                    if (index < 0) {
                        failed = true
                        return@repeat
                    }
                    week += pool.removeAt(index)
                }
                if (failed) return@repeat
                plan += week
            }
            if (!failed && plan.size == weeks) return plan
        }
        error("일정 배분에 실패했다")
    }

    /**
     * 원형 스케줄: 한 팀을 고정하고 나머지를 돌려서 n−1 라운드를 만든다.
     * 각 라운드는 모든 팀을 한 번씩 쓰는 짝짓기이고, n−1 라운드를 모으면 모든 조합이 정확히 한 번 나온다.
     */
    private fun roundRobinRounds(teamCount: Int): List<List<Pair<Int, Int>>> {
        val rotating = (1 until teamCount).toMutableList()
        return (0 until teamCount - 1).map {
            val pairs = mutableListOf(0 to rotating[0])
            for (index in 1 until rotating.size / 2 + 1) {
                val left = rotating[index]
                val right = rotating[rotating.size - index]
                if (left != right) pairs += left to right
            }
            rotating.add(0, rotating.removeAt(rotating.size - 1))
            pairs
        }
    }

    private data class Slot(val round: Int, val cycle: Int)

    companion object {
        private const val THREE_GAME_LENGTH = 3
        private const val TWO_GAME_LENGTH = 2
        private const val SLOTS_PER_THREE_GAME_WEEK = 2
        private const val SLOTS_PER_TWO_GAME_WEEK = 3
        private const val MAX_SHUFFLE_ATTEMPTS = 200
    }
}
