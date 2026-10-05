package baseballgm.events

import baseballgm.league.StrengthCalculator
import baseballgm.market.DRAFT_BALANCE
import baseballgm.market.TEST_SEASON
import baseballgm.market.rosterFor
import baseballgm.market.testLeague
import baseballgm.market.testTeam
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Player
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 국제대회와 병역 특례 (docs/12). */
class InternationalTournamentTest {

    private val strength = StrengthCalculator(DRAFT_BALANCE)
    private val tournaments = InternationalTournament(DRAFT_BALANCE, strength)

    /** 미필 선수를 섞은 리그. 나이를 흩어 놓아 연령 제한과 와일드카드가 갈리게 한다 */
    private val league = run {
        val teams = (1..4).map { testTeam("T0$it", pick = it) }
        val players = teams.flatMapIndexed { index, team ->
            rosterFor(team.id.value, rating = 55 + index * 6).mapIndexed { slot, player ->
                val age = 20 + (slot * 3) % 16
                player.aged(age).let { aged ->
                    if (age < 28) aged.unfulfilled() else aged
                }
            }
        }
        testLeague(teams = teams, players = players)
    }

    @Test
    fun `아시안게임은 4년마다 열린다`() {
        assertEquals(TournamentKind.ASIAN_GAMES, tournaments.rulesFor(2026)?.kind)
        assertEquals(null, tournaments.rulesFor(2027))
        assertEquals(TournamentKind.ASIAN_GAMES, tournaments.rulesFor(2030)?.kind)
        assertEquals(TournamentKind.OLYMPICS, tournaments.rulesFor(2028)?.kind)
    }

    @Test
    fun `첫 시즌에 아시안게임이 있다`() {
        val rules = assertNotNull(tournaments.rulesFor(TEST_SEASON), "2026 시즌에 대회가 없다")
        assertEquals(ExemptionRule.GOLD, rules.exemption)
        assertEquals(25, rules.ageLimit)
        assertTrue(rules.missesWeek(rules.startWeek))
        assertTrue(rules.missesWeek(rules.endWeek))
        assertFalse(rules.missesWeek(rules.endWeek + 1))
    }

    @Test
    fun `대표는 연령 제한과 팀당 인원을 지켜 뽑는다`() {
        val rules = tournaments.rulesFor(TEST_SEASON)!!
        val squad = tournaments.selectSquad(league, rules)

        assertTrue(squad.isNotEmpty())
        assertTrue(squad.size <= rules.squadSize)
        assertEquals(squad.size, squad.map { it.playerId }.toSet().size, "같은 선수가 두 번 뽑혔다")
        squad.groupBy { it.teamId }.forEach { (teamId, members) ->
            assertTrue(members.size <= rules.maxPerTeam, "$teamId 에서 ${members.size}명이 뽑혔다")
        }
        assertTrue(squad.count { it.wildcard } <= rules.wildcards, "와일드카드가 너무 많다")
        squad.filterNot { it.wildcard }.forEach { member ->
            assertTrue(
                league.player(member.playerId).ageIn(TEST_SEASON) <= rules.ageLimit,
                "연령 제한을 넘은 선수가 와일드카드 없이 뽑혔다",
            )
        }
    }

    @Test
    fun `외국인과 복무 중인 선수는 대표가 될 수 없다`() {
        val withForeign = league.copy(
            players = league.players.map { player ->
                when (player.id.value) {
                    "T01-B0" -> player.foreign()
                    "T01-B1" -> player.serving()
                    else -> player
                }
            },
        )
        val squad = tournaments.selectSquad(withForeign, tournaments.rulesFor(TEST_SEASON)!!)
        assertTrue(squad.none { it.playerId.value == "T01-B0" }, "외국인이 대표로 뽑혔다")
        assertTrue(squad.none { it.playerId.value == "T01-B1" }, "복무 중인 선수가 대표로 뽑혔다")
    }

    @Test
    fun `전력이 높으면 금메달 확률이 올라간다`() {
        val rules = tournaments.rulesFor(TEST_SEASON)!!
        val squad = tournaments.selectSquad(league, rules)
        val golds = (1..300).count { tournaments.play(league, rules, squad, Random(it)).medal == Medal.GOLD }

        val strongLeague = league.copy(players = league.players.map { it.boosted() })
        val strongSquad = tournaments.selectSquad(strongLeague, rules)
        val strongGolds = (1..300).count {
            tournaments.play(strongLeague, rules, strongSquad, Random(it)).medal == Medal.GOLD
        }

        assertTrue(
            tournaments.squadStrength(strongLeague, strongSquad) > tournaments.squadStrength(league, squad),
        )
        assertTrue(strongGolds > golds, "강한 대표팀이 금메달 ${strongGolds}회, 약한 팀이 ${golds}회")
    }

    @Test
    fun `금메달이면 미필 대표가 병역 면제를 받는다`() {
        val rules = tournaments.rulesFor(TEST_SEASON)!!
        val squad = tournaments.selectSquad(league, rules)
        val golden = (1..400).map { tournaments.play(league, rules, squad, Random(it)) }
            .first { it.medal == Medal.GOLD }

        val unfulfilledInSquad = squad.count { league.player(it.playerId).military is MilitaryStatus.Unfulfilled }
        assertEquals(unfulfilledInSquad, golden.exempted.size, "금메달인데 면제 인원이 다르다")
        golden.exempted.forEach {
            assertTrue(league.player(it).military is MilitaryStatus.Unfulfilled, "군필 선수가 면제 목록에 있다")
        }
        assertTrue(golden.summary().contains("병역 특례"))
    }

    @Test
    fun `메달을 못 따면 특례가 없다`() {
        val rules = tournaments.rulesFor(TEST_SEASON)!!
        val squad = tournaments.selectSquad(league, rules)
        val noMedal = (1..400).map { tournaments.play(league, rules, squad, Random(it)) }
            .first { it.medal == Medal.NONE }
        assertTrue(noMedal.exempted.isEmpty())
        assertTrue(noMedal.scores.isNotEmpty(), "스코어는 남아야 한다")
    }

    @Test
    fun `올림픽은 메달만 따도 특례를 준다`() {
        val rules = assertNotNull(tournaments.rulesFor(2028))
        assertEquals(ExemptionRule.MEDAL, rules.exemption)
        assertEquals(0, rules.ageLimit, "올림픽은 연령 제한이 없다")

        val olympicLeague = league.copy(season = 2028)
        val squad = tournaments.selectSquad(olympicLeague, rules)
        val bronze = (1..400).map { tournaments.play(olympicLeague, rules, squad, Random(it)) }
            .first { it.medal == Medal.BRONZE }
        assertTrue(bronze.exempted.isNotEmpty(), "동메달인데 특례가 없다")
    }
}

private fun Player.aged(age: Int): Player = when (this) {
    is baseballgm.model.Batter -> copy(birthYear = TEST_SEASON - age)
    is baseballgm.model.Pitcher -> copy(birthYear = TEST_SEASON - age)
}

private fun Player.unfulfilled(): Player = when (this) {
    is baseballgm.model.Batter -> copy(military = MilitaryStatus.Unfulfilled(28))
    is baseballgm.model.Pitcher -> copy(military = MilitaryStatus.Unfulfilled(28))
}

private fun Player.serving(): Player = when (this) {
    is baseballgm.model.Batter -> copy(
        military = MilitaryStatus.Serving(baseballgm.model.ServiceKind.SANGMU, TEST_SEASON + 1, 10),
    )
    is baseballgm.model.Pitcher -> copy(
        military = MilitaryStatus.Serving(baseballgm.model.ServiceKind.SANGMU, TEST_SEASON + 1, 10),
    )
}

private fun Player.foreign(): Player = when (this) {
    is baseballgm.model.Batter -> copy(origin = Origin.FOREIGN)
    is baseballgm.model.Pitcher -> copy(origin = Origin.FOREIGN)
}

private fun Player.boosted(): Player = when (this) {
    is baseballgm.model.Batter -> copy(
        ratings = baseballgm.model.BatterRatings(88, 88, 88, 88, 88),
    )
    is baseballgm.model.Pitcher -> copy(ratings = baseballgm.model.PitcherRatings(88, 88, 88, 88))
}
