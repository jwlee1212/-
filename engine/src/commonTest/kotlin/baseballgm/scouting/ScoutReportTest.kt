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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val REPORT_SEASON = 2026

private fun amateur(noiseSeed: Int = 4242, power: Int = 62): Batter = Batter(
    id = PlayerId("P9001"),
    name = "장유망",
    birthYear = REPORT_SEASON - 18,
    throwsWith = Hand.RIGHT,
    bats = Hand.LEFT,
    origin = Origin.HIGH_SCHOOL,
    debutSeason = REPORT_SEASON + 1,
    teamId = null,
    rosterLevel = RosterLevel.FUTURES,
    contract = Contract(0.3, 1, 0.0, 8, 0, ContractType.ROOKIE),
    military = MilitaryStatus.Unfulfilled(28),
    hidden = HiddenTraits(
        potential = Attribute.batterAttributes.associateWith { 80 },
        growthType = GrowthType.LATE,
        durability = 70,
        volatility = 55,
        platoonSplit = 40,
        scoutingNoiseSeed = noiseSeed,
    ),
    primaryPosition = Position.CENTER_FIELD,
    defenseFitness = mapOf(Position.CENTER_FIELD to 55),
    ratings = BatterRatings(contact = 45, power = power, eye = 40, speed = 58, defense = 50),
)

/** 스카우트 리포트 (docs/10 리포트 구성). */
class ScoutReportTest {

    private val minimal = ScoutingAccuracy.MINIMAL.precision

    private fun report(precision: ScoutingPrecision = minimal, player: Batter = amateur()) =
        ScoutReportWriter.write(
            player = player,
            precision = precision,
            season = REPORT_SEASON,
            focusWeeks = 5,
            school = "청림고",
            physique = Physique(184, 82),
            injuryHistory = listOf("고교 3학년 팔꿈치 통증 (수술 없음)"),
            scale = SCALE,
        )

    @Test
    fun `리포트에는 범위와 등급과 코멘트가 함께 들어간다`() {
        val report = report()
        assertTrue(report.scouted.ratings.values.none { it.isExact }, "아마추어인데 정확한 값이 보인다")
        assertTrue(report.potentialRange.low < report.potentialRange.high)
        assertTrue(report.comments.size >= 3, "코멘트가 ${report.comments.size}개뿐이다")
        assertEquals("청림고", report.school)
        assertEquals("184cm 82kg", report.physique.toString())
        assertTrue(report.injuryText.contains("팔꿈치"))
    }

    @Test
    fun `같은 선수를 몇 번 열어도 같은 리포트가 나온다`() {
        val first = report()
        repeat(3) { assertEquals(first, report()) }
    }

    @Test
    fun `정확도가 낮으면 더 봐야 한다는 단서가 붙는다`() {
        assertTrue(report().comments.any { it.contains("적어") || it.contains("컨디션") || it.contains("상대") })
        val sharp = report(ScoutingPrecision(3, 0, true, "높음"))
        assertTrue(sharp.comments.none { it.contains("본 횟수가 적어") })
        assertTrue(sharp.comments.none { it.contains("확신은 없다") }, "확신할 수 있는데 단서가 붙었다")
    }

    @Test
    fun `부상 이력이 없으면 특이사항 없음으로 나온다`() {
        val clean = ScoutReportWriter.write(amateur(), minimal, REPORT_SEASON, scale = SCALE)
        assertEquals("특이사항 없음", clean.injuryText)
    }

    @Test
    fun `주간 소식은 선수와 주차로 정해진다`() {
        val report = report()
        val news = ScoutNews.forProspect(report, week = 4)
        assertTrue(news.contains(report.scouted.name))
        assertEquals(news, ScoutNews.forProspect(report, week = 4))
    }
}
