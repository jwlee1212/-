package baseballgm.scouting

import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.BatterRatings
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.GrowthType
import baseballgm.model.Hand
import baseballgm.model.HiddenTraits
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.PlayerId
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SEASON = 2026

private fun batter(
    contact: Int = 70,
    potential: Int = 78,
    noiseSeed: Int = 12345,
    growthType: GrowthType = GrowthType.NORMAL,
    durability: Int = 60,
): Batter = Batter(
    id = PlayerId("P0001"),
    name = "김테스트",
    birthYear = SEASON - 27,
    throwsWith = Hand.RIGHT,
    bats = Hand.RIGHT,
    origin = Origin.HIGH_SCHOOL,
    debutSeason = SEASON - 8,
    teamId = TeamId("DSK"),
    rosterLevel = RosterLevel.FIRST_TEAM,
    contract = Contract(2.0, 1, 0.0, 0, 8, ContractType.STANDARD),
    military = MilitaryStatus.Completed,
    hidden = HiddenTraits(
        potential = Attribute.batterAttributes.associateWith { potential },
        growthType = growthType,
        durability = durability,
        volatility = 50,
        platoonSplit = 30,
        scoutingNoiseSeed = noiseSeed,
    ),
    primaryPosition = Position.SHORTSTOP,
    defenseFitness = mapOf(Position.SHORTSTOP to 80),
    ratings = BatterRatings(contact = contact, power = 60, eye = 55, speed = 65, defense = 72),
)

class ScoutingViewTest {

    @Test
    fun `우리 팀 선수는 현재 능력치를 정확히 본다`() {
        val view = ScoutingView.of(batter(contact = 71), ScoutingAccuracy.OWN_TEAM, SEASON)
        val contact = view.ratings.getValue(Attribute.CONTACT)
        assertTrue(contact.isExact)
        assertEquals(71, contact.low)
    }

    @Test
    fun `정확도가 낮을수록 범위가 넓다`() {
        val player = batter()
        val widths = listOf(
            ScoutingAccuracy.HIGH, ScoutingAccuracy.MEDIUM, ScoutingAccuracy.LOW, ScoutingAccuracy.MINIMAL,
        ).map { accuracy ->
            val range = ScoutingView.of(player, accuracy, SEASON).ratings.getValue(Attribute.CONTACT)
            range.high - range.low
        }
        assertEquals(widths.sorted(), widths, "정확도가 낮아질수록 범위가 넓어져야 한다: $widths")
    }

    @Test
    fun `같은 선수를 여러 번 봐도 범위가 똑같다`() {
        // 볼 때마다 오차를 새로 뽑으면 여러 번 열어 평균 내는 꼼수가 생긴다 (docs/02 구현 규칙 1)
        val player = batter()
        val first = ScoutingView.of(player, ScoutingAccuracy.MEDIUM, SEASON)
        repeat(20) {
            val again = ScoutingView.of(player, ScoutingAccuracy.MEDIUM, SEASON)
            assertEquals(first.ratings, again.ratings)
            assertEquals(first.potentialLabel, again.potentialLabel)
            assertEquals(first.growthTypeGuess, again.growthTypeGuess)
        }
    }

    @Test
    fun `선수마다 오차 방향이 다르다`() {
        // 오차 씨앗이 다르면 범위 중심도 달라야 한다 (모두 같은 방향이면 보정해서 맞힐 수 있다)
        val centers = (1..40).map { seed ->
            val range = ScoutingView.of(batter(noiseSeed = seed * 7919), ScoutingAccuracy.MEDIUM, SEASON)
                .ratings.getValue(Attribute.CONTACT)
            (range.low + range.high) / 2.0
        }
        assertTrue(centers.toSet().size > 1, "모든 선수의 범위 중심이 같다")
        assertTrue(centers.any { it > 70 } && centers.any { it < 70 }, "오차가 한쪽으로만 치우쳐 있다")
    }

    @Test
    fun `진짜 값은 범위 안에 있지만 정중앙은 아닐 수 있다`() {
        val offCenter = (1..60).count { seed ->
            val range = ScoutingView.of(batter(noiseSeed = seed * 104729), ScoutingAccuracy.LOW, SEASON)
                .ratings.getValue(Attribute.CONTACT)
            assertTrue(70 in range.low..range.high, "진짜 값이 범위를 벗어났다: $range")
            (range.low + range.high) / 2.0 != 70.0
        }
        assertTrue(offCenter > 30, "대부분의 선수에서 진짜 값이 범위 한가운데 있다 ($offCenter/60)")
    }

    @Test
    fun `잠재력은 숫자가 아니라 등급으로만 나온다`() {
        val exact = ScoutingView.of(batter(potential = 88), ScoutingAccuracy.OWN_TEAM, SEASON)
        assertEquals(PotentialGrade.A, exact.potentialLow)
        assertEquals("A", exact.potentialLabel)

        val blurred = ScoutingView.of(batter(potential = 78), ScoutingAccuracy.MINIMAL, SEASON)
        assertTrue(blurred.potentialLow != blurred.potentialHigh, "정확도가 낮으면 등급도 범위여야 한다")
    }

    @Test
    fun `정확도가 낮으면 성장 타입 추정이 틀릴 수 있다`() {
        val wrong = (1..200).count { seed ->
            val player = batter(noiseSeed = seed * 65537, growthType = GrowthType.LATE)
            ScoutingView.of(player, ScoutingAccuracy.MINIMAL, SEASON).growthTypeGuess != GrowthType.LATE
        }
        assertTrue(wrong in 1..199, "아마추어 추정이 항상 맞거나 항상 틀린다 ($wrong/200)")

        val ownTeam = ScoutingView.of(batter(growthType = GrowthType.LATE), ScoutingAccuracy.OWN_TEAM, SEASON)
        assertEquals(GrowthType.LATE, ownTeam.growthTypeGuess)
    }

    @Test
    fun `내구도는 숫자가 아니라 코멘트로 나온다`() {
        val tough = ScoutingView.of(batter(durability = 85), ScoutingAccuracy.OWN_TEAM, SEASON)
        val fragile = ScoutingView.of(batter(durability = 30), ScoutingAccuracy.OWN_TEAM, SEASON)
        assertTrue(tough.durabilityComment != fragile.durabilityComment)
        assertTrue(tough.durabilityComment.isNotBlank())
    }
}
