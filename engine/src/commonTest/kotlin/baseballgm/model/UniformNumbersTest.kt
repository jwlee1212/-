package baseballgm.model

import baseballgm.market.rosterFor
import baseballgm.market.testBatter
import baseballgm.market.testLeague
import baseballgm.season.SeasonCalendar
import baseballgm.season.SeasonState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 등번호 배정 (2026-10-02) */
class UniformNumbersTest {

    private val teamA = TeamId("AAA")
    private val teamB = TeamId("BBB")

    private fun numbersOf(players: List<Player>, team: TeamId) = players.filter { it.teamId == team }.map { it.uniformNumber }

    @Test
    fun `번호가 없는 선수단에 1~99 의 겹치지 않는 번호를 준다`() {
        val players = UniformNumbers.assign(rosterFor("AAA") + rosterFor("BBB"))
        listOf(teamA, teamB).forEach { team ->
            val numbers = numbersOf(players, team)
            assertTrue(numbers.all { it in UniformNumbers.MIN..UniformNumbers.MAX })
            assertEquals(numbers.size, numbers.toSet().size, "$team 안에서 번호가 겹친다")
        }
    }

    @Test
    fun `같은 선수단이면 언제 배정해도 같은 번호 - 난수 없음`() {
        val roster = rosterFor("AAA")
        assertEquals(UniformNumbers.assign(roster), UniformNumbers.assign(roster))
        // 입력 순서가 달라도 같다
        assertEquals(
            UniformNumbers.assign(roster).associate { it.id to it.uniformNumber },
            UniformNumbers.assign(roster.reversed()).associate { it.id to it.uniformNumber },
        )
    }

    @Test
    fun `이미 있는 번호는 지키고 소속 없는 선수는 건드리지 않는다`() {
        val roster = UniformNumbers.assign(rosterFor("AAA"))
        assertEquals(roster, UniformNumbers.assign(roster))
        val freeAgent = testBatter("FA-1")
        assertEquals(0, UniformNumbers.assign(listOf(freeAgent)).single().uniformNumber)
    }

    @Test
    fun `번호가 겹치는 이적생은 바꿔 달고 원래 있던 선수가 번호를 지킨다`() {
        val roster = UniformNumbers.assign(rosterFor("AAA"))
        val veteran = roster.first()
        val newcomer = testBatter("ZZZ-new", teamId = teamA).withUniformNumber(veteran.uniformNumber)
        val after = UniformNumbers.assign(roster + newcomer, newcomers = setOf(newcomer.id))
        assertEquals(veteran.uniformNumber, after.first { it.id == veteran.id }.uniformNumber)
        assertNotEquals(veteran.uniformNumber, after.first { it.id == newcomer.id }.uniformNumber)
        // 혼자 들어올 때 쓰는 함수도 같은 규칙
        assertNotEquals(veteran.uniformNumber, UniformNumbers.forNewcomer(newcomer, roster).uniformNumber)
        val free = testBatter("ZZZ-free", teamId = teamA).withUniformNumber(100)
        assertEquals(100, UniformNumbers.forNewcomer(free, roster).uniformNumber, "비어 있는 번호는 그대로")
    }

    @Test
    fun `99 명이 넘으면 100번대로 넘어간다`() {
        val big = (1..105).map { testBatter("AAA-$it", teamId = teamA) }
        val numbers = numbersOf(UniformNumbers.assign(big), teamA)
        assertEquals(105, numbers.toSet().size)
        assertTrue(numbers.count { it > UniformNumbers.MAX } == 6)
    }

    @Test
    fun `시즌 상태가 만들어지면 번호가 채워지고 트레이드로 옮긴 선수도 겹치지 않는다`() {
        val league = testLeague(players = rosterFor("AAA") + rosterFor("BBB"))
        val state = SeasonState(league, SeasonCalendar(regularSeasonWeeks = 1, allStarBreakAfterWeek = 1, tradeDeadlineWeek = 1, draftWeek = 1, gamesPerWeek = 6))
        assertTrue(state.allPlayers().filter { it.teamId != null }.all { it.uniformNumber > 0 })
        val moving = state.playersOf(teamB).first().id
        state.movePlayer(moving, teamA)
        val numbers = state.playersOf(teamA).map { it.uniformNumber }
        assertEquals(numbers.size, numbers.toSet().size)
    }
}
