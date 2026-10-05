package baseballgm.events

import baseballgm.condition.AdaptationModel
import baseballgm.league.StrengthCalculator
import baseballgm.market.DRAFT_BALANCE
import baseballgm.market.ForeignCandidate
import baseballgm.market.ForeignMarket
import baseballgm.market.ForeignRules
import baseballgm.market.MarketView
import baseballgm.market.PositionNeed
import baseballgm.market.TEST_SEASON
import baseballgm.market.TeamMode
import baseballgm.market.Valuation
import baseballgm.market.rosterFor
import baseballgm.market.testBatter
import baseballgm.market.testLeague
import baseballgm.market.testPitcher
import baseballgm.market.testTeam
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.PitcherRole
import baseballgm.model.Position
import baseballgm.model.ServiceKind
import baseballgm.model.TeamId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 외국인 적응력 (docs/12). */
class AdaptationTest {

    private val model = AdaptationModel(DRAFT_BALANCE)

    private fun foreigner(adaptability: Int, kboSeason: Int) = testPitcher(
        id = "F$adaptability$kboSeason",
        rating = 70,
        age = 29,
        origin = Origin.FOREIGN,
        adaptability = adaptability,
        debutSeason = TEST_SEASON - (kboSeason - 1),
    )

    @Test
    fun `국내 선수는 적응 감점이 없다`() {
        assertEquals(0.0, model.penaltyFor(testBatter("KR", age = 27), TEST_SEASON, week = 1))
    }

    @Test
    fun `적응력이 낮으면 감점이 크다`() {
        val poor = model.penaltyFor(foreigner(adaptability = 15, kboSeason = 1), TEST_SEASON, week = 1)
        val good = model.penaltyFor(foreigner(adaptability = 90, kboSeason = 1), TEST_SEASON, week = 1)
        assertTrue(poor > good, "$poor vs $good")
        assertTrue(poor > 10.0, "적응력 15인데 감점이 ${poor}뿐이다")
        assertTrue(good < 2.0, "적응력 90인데 감점이 ${good}이다")
    }

    @Test
    fun `첫 시즌에는 주차가 지나면서 감점이 줄어든다`() {
        val player = foreigner(adaptability = 20, kboSeason = 1)
        val opening = model.penaltyFor(player, TEST_SEASON, week = 1)
        val midSeason = model.penaltyFor(player, TEST_SEASON, week = 8)
        val late = model.penaltyFor(player, TEST_SEASON, week = 20)
        assertTrue(opening > midSeason && midSeason > late, "$opening / $midSeason / $late")
        // 첫 시즌에 완전히 사라지지는 않는다 — 그래야 교체 결정이 의미가 있다
        assertTrue(late > opening * 0.4, "첫 시즌에 감점이 거의 사라졌다 ($late)")
    }

    @Test
    fun `두 번째 시즌에는 감점이 크게 줄고 세 번째부터는 없다`() {
        val first = model.penaltyFor(foreigner(20, kboSeason = 1), TEST_SEASON, week = 24)
        val second = model.penaltyFor(foreigner(20, kboSeason = 2), TEST_SEASON, week = 1)
        val third = model.penaltyFor(foreigner(20, kboSeason = 3), TEST_SEASON, week = 1)
        assertTrue(second < first, "$second vs $first")
        assertEquals(0.0, third)
    }

    @Test
    fun `감점은 문구로만 보여준다`() {
        assertEquals("적응 완료", model.label(0.0))
        assertTrue(model.label(11.0).contains("적응하지 못"))
        assertTrue(model.label(5.0).contains("적응 중"))
    }
}

/** 외국인 보유 규칙과 시장 (docs/12). */
class ForeignMarketTest {

    private val strength = StrengthCalculator(DRAFT_BALANCE)
    private val rules = ForeignRules(DRAFT_BALANCE)
    private val market = ForeignMarket(
        DRAFT_BALANCE,
        Valuation(DRAFT_BALANCE, baseballgm.development.AgingCurves(DRAFT_BALANCE)),
        MarketView(DRAFT_BALANCE, strength),
        PositionNeed(DRAFT_BALANCE, strength),
    )
    private val team = TeamId("AAA")

    private fun foreignPitcher(id: String, rating: Int = 70) = testPitcher(
        id = id, rating = rating, age = 29, teamId = team, origin = Origin.FOREIGN, adaptability = 55,
    ).copy(contract = Contract(6.0, 1, 0.0, 0, 0, ContractType.FOREIGN))

    private fun foreignBatter(id: String, rating: Int = 70) = testBatter(
        id = id, rating = rating, age = 29, teamId = team, position = Position.FIRST_BASE,
    ).copy(origin = Origin.FOREIGN, contract = Contract(6.0, 1, 0.0, 0, 0, ContractType.FOREIGN))

    private fun candidate(id: String, pitcher: Boolean, asking: Double = 5.0) = ForeignCandidate(
        player = if (pitcher) {
            testPitcher(id, rating = 70, age = 29, origin = Origin.FOREIGN, adaptability = 50)
        } else {
            testBatter(id, rating = 70, age = 29, position = Position.RIGHT_FIELD).copy(origin = Origin.FOREIGN)
        },
        originLeague = "tripleA",
        originLabel = "미국 트리플A",
        askingSalary = asking,
    )

    @Test
    fun `외국인은 팀당 세 명까지다`() {
        val three = listOf(foreignPitcher("F1"), foreignPitcher("F2"), foreignBatter("F3"))
        val problems = rules.problemsForSigning(three, candidate("F4", pitcher = false).player, 5.0, true)
        assertTrue(problems.any { it.contains("3명까지만") }, problems.toString())
    }

    @Test
    fun `한쪽으로 세 명은 안 된다`() {
        val twoPitchers = listOf(foreignPitcher("F1"), foreignPitcher("F2"))
        val pitcherProblems = rules.problemsForSigning(twoPitchers, candidate("F3", pitcher = true).player, 5.0, true)
        assertTrue(pitcherProblems.any { it.contains("투수는") }, pitcherProblems.toString())

        // 타자 자리는 아직 비어 있다
        assertTrue(rules.problemsForSigning(twoPitchers, candidate("F3", pitcher = false).player, 5.0, true).isEmpty())
    }

    @Test
    fun `신규 계약에는 연봉 상한이 있고 재계약에는 없다`() {
        val over = rules.newContractMax + 2.0
        assertTrue(
            rules.problemsForSigning(emptyList(), candidate("F1", true).player, over, isNewContract = true)
                .any { it.contains("상한") },
        )
        assertTrue(
            rules.problemsForSigning(emptyList(), candidate("F1", true).player, over, isNewContract = false).isEmpty(),
            "재계약은 상한이 없어야 한다",
        )
    }

    @Test
    fun `KBO 환산 기록은 같은 선수면 항상 같다`() {
        val candidate = candidate("F1", pitcher = true)
        val first = market.convertedLine(candidate)
        repeat(3) { assertEquals(first, market.convertedLine(candidate)) }
        assertTrue(first.era > 0.0 && first.strikeoutsPer9 > 0.0)
        assertTrue(first.text().contains("평균자책"))
    }

    @Test
    fun `능력치가 높으면 환산 기록도 좋다`() {
        val ace = market.convertedLine(candidate("ACE", pitcher = true).let { it.copy(player = testPitcher("ACE", rating = 85, age = 29, origin = Origin.FOREIGN, adaptability = 50)) })
        val filler = market.convertedLine(candidate("FIL", pitcher = true).let { it.copy(player = testPitcher("FIL", rating = 45, age = 29, origin = Origin.FOREIGN, adaptability = 50)) })
        assertTrue(ace.era < filler.era, "${ace.era} vs ${filler.era}")
        assertTrue(ace.strikeoutsPer9 > filler.strikeoutsPer9)
    }

    @Test
    fun `재계약은 인상되고 해외 유출 제안은 그보다 크다`() {
        val player = foreignPitcher("F1")
        val random = Random(4)
        val reSign = market.reSignSalary(player, random)
        val japan = market.outflowOffer(player, random)
        assertTrue(reSign > player.contract.salary, "재계약이 삭감됐다")
        assertTrue(japan > player.contract.salary * 1.5, "일본 제안이 너무 작다 ($japan)")
    }

    @Test
    fun `신규 계약 상한을 넘겨 제안하지 않는다`() {
        val league = testLeague(
            teams = listOf(testTeam("AAA"), testTeam("BBB", pick = 2)),
            players = rosterFor("AAA") + rosterFor("BBB"),
        )
        val star = candidate("STAR", pitcher = true).copy(
            player = testPitcher("STAR", rating = 90, age = 28, origin = Origin.FOREIGN, adaptability = 70),
        )
        val maximum = market.maximumOffer(star, team, league, TeamMode.CONTEND)
        assertTrue(maximum <= rules.newContractMax, "상한 ${rules.newContractMax}억을 넘겼다 ($maximum)")
    }
}

/** 군 복무 (docs/12). */
class MilitaryServiceTest {

    private val service = MilitaryService(DRAFT_BALANCE)

    private fun unfulfilled(age: Int, id: String = "M$age") = testBatter(id, rating = 60, age = age)
        .copy(military = MilitaryStatus.Unfulfilled(service.deadlineAge))

    @Test
    fun `입대 기한에 닿으면 반드시 입대한다`() {
        assertTrue(service.shouldEnlist(unfulfilled(service.deadlineAge), TEST_SEASON, 70.0, Random(1)))
        assertFalse(service.shouldEnlist(unfulfilled(21), TEST_SEASON, 70.0, Random(1)))
    }

    @Test
    fun `외국인과 군필은 입대하지 않는다`() {
        val foreign = testPitcher("F", age = 30, origin = Origin.FOREIGN, adaptability = 50)
        assertFalse(service.shouldEnlist(foreign, TEST_SEASON, 70.0, Random(1)))
        val done = unfulfilled(29).copy(military = MilitaryStatus.Completed)
        assertFalse(service.shouldEnlist(done, TEST_SEASON, 70.0, Random(1)))
    }

    @Test
    fun `능력치가 높으면 상무에 붙는다`() {
        val good = (1..200).count {
            service.enlist(unfulfilled(28), TEST_SEASON, overall = 78.0, sangmuTaken = 0, random = Random(it))
                .kind == ServiceKind.SANGMU
        }
        val poor = (1..200).count {
            service.enlist(unfulfilled(28), TEST_SEASON, overall = 42.0, sangmuTaken = 0, random = Random(it))
                .kind == ServiceKind.SANGMU
        }
        assertTrue(good > poor * 2, "상무 합격 78점 ${good}회 vs 42점 ${poor}회")
        assertTrue(good > 150, "능력치 78인데 합격이 ${good}/200 뿐이다")
    }

    @Test
    fun `상무 정원이 차면 현역으로 간다`() {
        val enlistment = service.enlist(unfulfilled(28), TEST_SEASON, overall = 90.0, sangmuTaken = 99, random = Random(1))
        assertEquals(ServiceKind.ACTIVE_DUTY, enlistment.kind)
        assertTrue(enlistment.appliedToSangmu)
        assertTrue(enlistment.message("김테스트").contains("불합격"))
    }

    @Test
    fun `복무는 두 시즌 뒤 시즌 중에 끝난다`() {
        val (season, week) = service.returnPoint(TEST_SEASON)
        assertEquals(TEST_SEASON + 2, season, "18개월이면 두 시즌 뒤")
        assertTrue(week in 1..24, "복귀 주차 $week")

        val serving = unfulfilled(28).copy(military = MilitaryStatus.Serving(ServiceKind.SANGMU, season, week))
        assertFalse(service.dischargesNow(serving, season, week - 1), "복귀 주차 전에 제대했다")
        assertTrue(service.dischargesNow(serving, season, week), "복귀 주차인데 제대하지 않았다")
        assertTrue(service.dischargesNow(serving, season + 1, 1), "시즌이 지났는데 아직 복무 중이다")
    }

    @Test
    fun `기한이 다가오면 알림이 온다`() {
        assertNotNull(service.warningFor(unfulfilled(27), TEST_SEASON))
        assertEquals(null, service.warningFor(unfulfilled(22), TEST_SEASON))
        val warning = service.warningFor(unfulfilled(26), TEST_SEASON)!!
        assertTrue(warning.message("김테스트").contains("기한"))
    }
}
