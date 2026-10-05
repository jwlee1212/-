package baseballgm.management

import baseballgm.league.StrengthCalculator
import baseballgm.league.TeamRecord
import baseballgm.market.DRAFT_BALANCE
import baseballgm.market.TEST_SEASON
import baseballgm.market.rosterFor
import baseballgm.market.testLeague
import baseballgm.market.testTeam
import baseballgm.model.TeamId
import baseballgm.season.PostseasonResult
import baseballgm.season.PostseasonRound
import baseballgm.season.SeriesResult
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val AAA = TeamId("AAA")
private val BBB = TeamId("BBB")

private fun record(teamId: TeamId, wins: Int, losses: Int) =
    TeamRecord(teamId = teamId, wins = wins, losses = losses, ties = 144 - wins - losses)

/** 우승까지 올라간 포스트시즌 결과. */
private fun postseasonWith(champion: TeamId, runnerUp: TeamId, rounds: Map<PostseasonRound, Pair<TeamId, TeamId>>) =
    PostseasonResult(
        season = TEST_SEASON,
        participants = listOf(champion, runnerUp),
        series = rounds.map { (round, teams) ->
            SeriesResult(round, teams.first, teams.second, 3, 1, 0, teams.first)
        },
        champion = champion,
        runnerUp = runnerUp,
    )

/** 재정 (docs/13). */
class FinanceTest {

    private val strength = StrengthCalculator(DRAFT_BALANCE)
    private val finance = Finance(DRAFT_BALANCE, strength)
    /** 연봉 총액이 100억쯤 되는 팀 (실제 리그와 비슷한 규모) */
    private val league = testLeague(
        teams = listOf(testTeam("AAA"), testTeam("BBB", pick = 2)),
        players = rosterFor("AAA", salary = 4.5) + rosterFor("BBB", salary = 4.5),
    )

    @Test
    fun `이기고 팬심이 높으면 관중이 늘어난다`() {
        val good = finance.attendanceRate(record(AAA, 90, 50), fanSupport = 80, madePostseason = true)
        val bad = finance.attendanceRate(record(AAA, 50, 90), fanSupport = 25, madePostseason = false)
        assertTrue(good > bad, "$good vs $bad")
        assertTrue(good <= 1.30 && bad >= 0.45, "관중률이 범위를 벗어났다 ($good / $bad)")
    }

    @Test
    fun `포스트시즌 수입은 치른 시리즈만 센다`() {
        // 1위 팀은 한국시리즈만 치른다 — 와일드카드·준PO 몫을 받아서는 안 된다
        val topSeed = postseasonWith(AAA, BBB, mapOf(PostseasonRound.KOREAN_SERIES to (AAA to BBB)))
        val longRun = postseasonWith(
            AAA,
            BBB,
            mapOf(
                PostseasonRound.WILDCARD to (AAA to BBB),
                PostseasonRound.SEMI_PLAYOFF to (AAA to BBB),
                PostseasonRound.PLAYOFF to (AAA to BBB),
                PostseasonRound.KOREAN_SERIES to (AAA to BBB),
            ),
        )
        val short = finance.revenueOf(league, AAA, record(AAA, 85, 55), 60, 60, topSeed).postseason
        val full = finance.revenueOf(league, AAA, record(AAA, 85, 55), 60, 60, longRun).postseason
        assertTrue(full > short, "5위로 올라간 팀이 1위보다 배분을 더 받아야 한다 ($full vs $short)")
        assertTrue(short > 0.0)
    }

    @Test
    fun `자체 수입만으로는 대체로 적자다`() {
        val report = finance.report(
            league = league,
            teamId = AAA,
            season = TEST_SEASON,
            record = record(AAA, 72, 72),
            fanSupport = 50,
            ownerTrust = 60,
            postseason = null,
            signingBonus = 12.0,
            scouting = 2.2,
            penalties = 0.0,
            fundsBefore = 30.0,
        )
        val ownRevenue = report.revenue.total - report.revenue.parentSupport
        assertTrue(ownRevenue < report.expenses.total, "자체 수입 $ownRevenue 이 지출 ${report.expenses.total} 보다 크다")
        assertTrue(report.revenue.parentSupport > 0.0, "모기업 지원이 없다")
        assertTrue(report.expenses.payroll > 0.0)
        assertTrue(report.line().contains("억"))
    }

    @Test
    fun `결산 후 운용 자금은 수지만큼 움직인다`() {
        val report = finance.report(
            league = league, teamId = AAA, season = TEST_SEASON,
            record = record(AAA, 90, 54), fanSupport = 70, ownerTrust = 70, postseason = null,
            signingBonus = 0.0, scouting = 0.0, penalties = 0.0, fundsBefore = 40.0,
        )
        assertEquals(40.0 + report.revenue.total - report.expenses.total, report.fundsAfter, 0.02)
    }
}

/** 팬심 (docs/13). */
class FanSentimentTest {

    private val fans = FanSentiment(DRAFT_BALANCE)

    @Test
    fun `주간 변화는 작고 사건은 크다`() {
        val weekly = abs(fans.afterWeek(50, weekWins = 6, weekLosses = 0, streak = 6, volatility = 1.0) - 50)
        val event = abs(fans.onEvent(50, FanEvent.FRANCHISE_STAR_TRADED, volatility = 1.0) - 50)
        assertTrue(weekly <= 5, "한 주에 ${weekly}점이 움직였다")
        assertTrue(event > weekly * 2, "사건($event)이 주간($weekly)보다 크지 않다")
    }

    @Test
    fun `구단별 변화 폭이 곱해진다`() {
        val calm = fans.onEvent(50, FanEvent.FRANCHISE_STAR_TRADED, volatility = 1.0)
        val hot = fans.onEvent(50, FanEvent.FRANCHISE_STAR_TRADED, volatility = 1.5)
        assertTrue(hot < calm, "변화 폭 1.5배 구단이 덜 흔들렸다 ($hot vs $calm)")
    }

    @Test
    fun `우승하면 오르고 하위권이면 내린다`() {
        val champion = fans.afterSeason(
            current = 50, record = record(AAA, 90, 54), rank = 1, teamCount = 10,
            postseason = postseasonWith(AAA, BBB, mapOf(PostseasonRound.KOREAN_SERIES to (AAA to BBB))),
            volatility = 1.0,
        )
        val bottom = fans.afterSeason(
            current = 50, record = record(AAA, 50, 94), rank = 10, teamCount = 10,
            postseason = null, volatility = 1.0,
        )
        assertTrue(champion > 50, "우승했는데 팬심이 안 올랐다 ($champion)")
        assertTrue(bottom < 50, "꼴찌인데 팬심이 안 내렸다 ($bottom)")
    }

    @Test
    fun `팬심은 범위를 벗어나지 않는다`() {
        var value = 50
        repeat(60) { value = fans.onEvent(value, FanEvent.FRANCHISE_STAR_RELEASED, 1.5) }
        assertTrue(value >= 5, "팬심이 $value")
        var high = 50
        repeat(60) { high = fans.onEvent(high, FanEvent.BIG_FREE_AGENT_SIGNED, 1.5) }
        assertTrue(high <= 98, "팬심이 $high")
    }
}

/** 구단주 신뢰도 (docs/13). */
class OwnerTrustTest {

    private val trust = OwnerTrust(DRAFT_BALANCE)
    private val strength = StrengthCalculator(DRAFT_BALANCE)
    private val finance = Finance(DRAFT_BALANCE, strength)
    private val league = testLeague(
        teams = listOf(testTeam("AAA"), testTeam("BBB", pick = 2)),
        players = rosterFor("AAA") + rosterFor("BBB"),
    )

    private fun report(deficit: Double) = FinanceReport(
        teamId = AAA,
        season = TEST_SEASON,
        revenue = Revenue(50.0, 25.0, 15.0, 0.0, 20.0),
        expenses = Expenses(110.0 + deficit - 110.0 + (50.0 + 25.0 + 15.0 + 20.0) + deficit - deficit, 0.0, 0.0, 0.0, 0.0),
        attendanceRate = 0.8,
        allowedDeficit = 35.0,
        fundsBefore = 30.0,
        fundsAfter = 30.0,
    )

    private fun evaluate(outcome: GoalOutcome, deficit: Double = 0.0, misses: Int = 0, patience: Double = 1.0) =
        trust.evaluate(
            teamId = AAA,
            goal = SeasonGoal(GoalKind.REACH_POSTSEASON, "포스트시즌 진출"),
            outcome = outcome,
            trustBefore = 60,
            finance = report(deficit),
            fanChange = 0,
            consecutiveMisses = misses,
            patience = patience,
        )

    @Test
    fun `목표 문구를 목표로 옮긴다`() {
        assertEquals(GoalKind.REACH_KOREAN_SERIES, SeasonGoal.fromOwnerGoal("한국시리즈 진출").kind)
        assertEquals(GoalKind.REACH_PLAYOFF, SeasonGoal.fromOwnerGoal("플레이오프 진출").kind)
        assertEquals(GoalKind.WINNING_RECORD, SeasonGoal.fromOwnerGoal("승률 5할 이상, 25세 이하 주전 2명").kind)
        assertEquals(GoalKind.AVOID_DEFICIT, SeasonGoal.fromOwnerGoal("적자 30% 축소").kind)
        assertEquals(GoalKind.AVOID_LAST_PLACE, SeasonGoal.fromOwnerGoal("탈꼴찌, 1순위 지명자 1군 데뷔").kind)
    }

    @Test
    fun `달성하면 오르고 미달하면 내린다`() {
        assertTrue(evaluate(GoalOutcome.EXCEEDED).delta > evaluate(GoalOutcome.MET).delta)
        assertTrue(evaluate(GoalOutcome.MET).delta > 0)
        assertTrue(evaluate(GoalOutcome.MISSED).delta < 0)
        assertTrue(evaluate(GoalOutcome.FAR_MISSED).delta < evaluate(GoalOutcome.MISSED).delta)
    }

    @Test
    fun `적자가 허용을 넘으면 깎인다`() {
        val clean = evaluate(GoalOutcome.MET, deficit = 0.0).trustAfter
        val overspent = evaluate(GoalOutcome.MET, deficit = 80.0).trustAfter
        assertTrue(overspent < clean, "적자를 크게 냈는데 신뢰도가 같다 ($overspent vs $clean)")
    }

    @Test
    fun `2년 연속 미달은 크게 깎인다`() {
        val once = evaluate(GoalOutcome.MISSED, misses = 0).trustAfter
        val twice = evaluate(GoalOutcome.MISSED, misses = 1).trustAfter
        assertTrue(twice < once, "$twice vs $once")
        assertEquals(2, evaluate(GoalOutcome.MISSED, misses = 1).consecutiveMisses)
        assertEquals(0, evaluate(GoalOutcome.MET, misses = 1).consecutiveMisses, "달성하면 연속 기록이 사라진다")
    }

    @Test
    fun `난이도가 낮으면 구단주가 인내한다`() {
        val easy = evaluate(GoalOutcome.FAR_MISSED, patience = 1.4).trustAfter
        val hard = evaluate(GoalOutcome.FAR_MISSED, patience = 0.75).trustAfter
        assertTrue(easy > hard, "쉬움 $easy 이 어려움 $hard 보다 낮다")
    }

    @Test
    fun `신뢰도가 바닥나면 해임된다`() {
        val evaluation = trust.evaluate(
            teamId = AAA,
            goal = SeasonGoal(GoalKind.REACH_POSTSEASON, "포스트시즌 진출"),
            outcome = GoalOutcome.FAR_MISSED,
            trustBefore = 25,
            finance = report(90.0),
            fanChange = -10,
            consecutiveMisses = 2,
            patience = 1.0,
        )
        assertTrue(evaluation.fired, "신뢰도 ${evaluation.trustAfter} 인데 해임되지 않았다")
        assertTrue(evaluation.message("구단 AAA").contains("해임"))
    }

    @Test
    fun `신뢰도가 낮으면 큰 계약에 승인이 필요하다`() {
        assertTrue(trust.needsApproval(trust = 30, salary = 10.0))
        assertFalse(trust.needsApproval(trust = 30, salary = 1.0), "작은 계약은 승인이 필요 없다")
        assertFalse(trust.needsApproval(trust = 80, salary = 10.0), "신뢰가 높으면 승인이 필요 없다")
        assertTrue(trust.warning(30) != null)
        assertEquals(null, trust.warning(70))
    }

    @Test
    fun `적자 목표는 재정으로 판정한다`() {
        val goal = SeasonGoal(GoalKind.AVOID_DEFICIT, "적자 30% 축소")
        val surplus = trust.outcomeOf(goal, AAA, record(AAA, 70, 74), 6, null, 10, report(-10.0))
        val blown = trust.outcomeOf(goal, AAA, record(AAA, 70, 74), 6, null, 10, report(90.0))
        assertEquals(GoalOutcome.EXCEEDED, surplus)
        assertEquals(GoalOutcome.FAR_MISSED, blown)
    }
}

/** 커리어와 업적 (docs/13, 14). */
class CareerTest {

    private val strength = StrengthCalculator(DRAFT_BALANCE)
    private val career = Career(DRAFT_BALANCE, strength)
    private val league = testLeague(
        teams = (1..4).map { testTeam("T0$it", pick = it) },
        players = (1..4).flatMap { rosterFor("T0$it", rating = 48 + it * 8) },
    )

    @Test
    fun `전력이 높으면 기대 승수도 높다`() {
        val strong = career.expectedWins(league, TeamId("T04"), 144)
        val weak = career.expectedWins(league, TeamId("T01"), 144)
        assertTrue(strong > weak, "$strong vs $weak")
        assertTrue(strong in 43.0..101.0 && weak in 43.0..101.0)
    }

    @Test
    fun `약팀에서 5강 가는 것이 강팀에서 우승하는 것보다 평판이 오른다`() {
        val weakTeam = TeamId("T01")
        val strongTeam = TeamId("T04")
        val weakPower = career.powerOf(league, weakTeam)
        val strongPower = career.powerOf(league, strongTeam)

        // 약팀이 기대를 20승 넘겨 5강에 갔다
        val underdog = career.reputationAfterSeason(
            current = 50,
            record = record(weakTeam, (career.expectedWins(league, weakTeam, 144) + 20).toInt(), 60),
            expectedWins = career.expectedWins(league, weakTeam, 144),
            postseason = postseasonWith(TeamId("T03"), weakTeam, mapOf(PostseasonRound.WILDCARD to (weakTeam to TeamId("T02")))),
            teamId = weakTeam,
            teamPower = weakPower,
        )
        // 강팀이 기대만큼 이기고 우승했다
        val favourite = career.reputationAfterSeason(
            current = 50,
            record = record(strongTeam, career.expectedWins(league, strongTeam, 144).toInt(), 50),
            expectedWins = career.expectedWins(league, strongTeam, 144),
            postseason = postseasonWith(strongTeam, TeamId("T03"), mapOf(PostseasonRound.KOREAN_SERIES to (strongTeam to TeamId("T03")))),
            teamId = strongTeam,
            teamPower = strongPower,
        )
        assertTrue(underdog > favourite, "약팀 5강 $underdog 이 강팀 우승 $favourite 보다 낮다")
    }

    @Test
    fun `해임되면 평판이 깎이고 해설위원 해에는 바닥이 있다`() {
        assertTrue(career.reputationAfterFiring(50) < 50)
        val low = career.reputationAfterCommentary(31)
        assertTrue(low >= 30, "해설위원 해에 평판이 $low 까지 떨어졌다")
        assertEquals(20, career.reputationAfterCommentary(20), "이미 바닥 아래면 더 깎지 않는다")
    }

    @Test
    fun `평판에 맞는 구단이 제안한다`() {
        val openings = listOf(TeamId("T01"), TeamId("T04"))
        val famous = career.offersFor(league, reputation = 95, openings = openings, trustOf = { 50 }, random = Random(1))
        val unknown = career.offersFor(league, reputation = 10, openings = openings, trustOf = { 50 }, random = Random(1))
        assertTrue(famous.isNotEmpty())
        assertTrue(unknown.isEmpty(), "평판 10 인데 제안이 왔다")

        // 해설위원으로 한 해를 보내면 약팀이라도 제안이 온다 (docs/13)
        val guaranteed = career.offersFor(
            league, reputation = 10, openings = openings, trustOf = { 50 }, random = Random(1), guaranteed = true,
        )
        assertTrue(guaranteed.isNotEmpty(), "해설위원 뒤에도 제안이 없다")
    }
}

/** 업적 (docs/14). */
class AchievementsTest {

    private fun careerWith(vararg seasons: CareerSeason) = CareerRecord(
        gmName = "테스트 단장",
        reputation = 50,
        seasons = seasons.toList(),
        championshipTeams = seasons.filter { it.champion }.mapNotNull { it.teamId },
    )

    private fun season(
        year: Int,
        teamId: TeamId = AAA,
        champion: Boolean = false,
        rank: Int = 5,
        reached: PostseasonRound? = null,
    ) = CareerSeason(
        season = year, teamId = teamId, teamName = teamId.value, wins = 80, losses = 60, ties = 4,
        rank = rank, reachedRound = reached ?: if (champion) PostseasonRound.KOREAN_SERIES else null,
        champion = champion,
    )

    private fun context(
        teamId: TeamId = AAA,
        payrollRank: Int = 5,
        rankWhenHired: Int = 5,
        seasonsWithTeam: Int = 1,
        lateRoundStar: Boolean = false,
    ) = AchievementContext(
        season = TEST_SEASON, teamId = teamId, payrollRank = payrollRank, teamCount = 10,
        rankWhenHired = rankWhenHired, seasonsWithTeam = seasonsWithTeam, lateRoundStar = lateRoundStar,
    )

    @Test
    fun `첫 우승과 왕조`() {
        val first = Achievements.newlyUnlocked(careerWith(season(2026, champion = true)), context())
        assertTrue(first.contains(Achievements.FIRST_TITLE))
        assertFalse(first.contains(Achievements.DYNASTY))

        val dynasty = Achievements.newlyUnlocked(
            careerWith(season(2026, champion = true), season(2027, champion = true), season(2028, champion = true))
                .copy(unlockedAchievements = listOf(Achievements.FIRST_TITLE.id)),
            context(seasonsWithTeam = 3),
        )
        assertTrue(dynasty.contains(Achievements.DYNASTY))
    }

    @Test
    fun `기적의 재건은 꼴찌 팀을 3년 안에 우승시켜야 한다`() {
        val miracle = Achievements.newlyUnlocked(
            careerWith(season(2026, rank = 10), season(2027, rank = 6), season(2028, champion = true)),
            context(rankWhenHired = 10, seasonsWithTeam = 3),
        )
        assertTrue(miracle.contains(Achievements.MIRACLE_REBUILD))

        val slow = Achievements.newlyUnlocked(
            careerWith(season(2028, champion = true)),
            context(rankWhenHired = 10, seasonsWithTeam = 5),
        )
        assertFalse(slow.contains(Achievements.MIRACLE_REBUILD), "5년이 걸렸는데 기적이 됐다")
    }

    @Test
    fun `짠돌이 단장은 연봉 최하위로 포스트시즌에 가야 한다`() {
        val cheap = Achievements.newlyUnlocked(
            careerWith(season(2026, reached = PostseasonRound.WILDCARD)),
            context(payrollRank = 10),
        )
        assertTrue(cheap.contains(Achievements.PENNY_PINCHER))

        val rich = Achievements.newlyUnlocked(
            careerWith(season(2026, reached = PostseasonRound.WILDCARD)),
            context(payrollRank = 1),
        )
        assertFalse(rich.contains(Achievements.PENNY_PINCHER))
    }

    @Test
    fun `전국구 단장은 서로 다른 세 구단에서 우승해야 한다`() {
        val record = careerWith(
            season(2026, teamId = TeamId("T1"), champion = true),
            season(2027, teamId = TeamId("T2"), champion = true),
            season(2028, teamId = TeamId("T3"), champion = true),
        )
        assertTrue(Achievements.newlyUnlocked(record, context(teamId = TeamId("T3"))).contains(Achievements.NATIONWIDE))

        val sameTeam = careerWith(
            season(2026, champion = true), season(2027, champion = true), season(2028, champion = true),
        )
        assertFalse(Achievements.newlyUnlocked(sameTeam, context()).contains(Achievements.NATIONWIDE))
    }

    @Test
    fun `이미 달성한 업적은 다시 나오지 않는다`() {
        val record = careerWith(season(2026, champion = true)).copy(
            unlockedAchievements = listOf(Achievements.FIRST_TITLE.id),
        )
        assertFalse(Achievements.newlyUnlocked(record, context()).contains(Achievements.FIRST_TITLE))
    }

    @Test
    fun `명예의 전당 등급은 성과에 따라 올라간다`() {
        val nobody = CareerRecord("단장", 30, seasons = listOf(season(2026)))
        val legend = CareerRecord(
            gmName = "단장",
            reputation = 95,
            seasons = (2026..2035).map { season(it, champion = it % 2 == 0) },
            unlockedAchievements = Achievements.all.map { it.id },
        )
        assertTrue(Achievements.hallOfFameGrade(legend).contains("전설"))
        assertEquals("짧은 커리어", Achievements.hallOfFameGrade(nobody))
    }
}

/** 스태프 시장 (docs/13). */
class StaffMarketTest {

    private val market = StaffMarket(DRAFT_BALANCE)

    @Test
    fun `등급이 높으면 연봉이 비싸다`() {
        assertTrue(market.salaryFor(5) > market.salaryFor(1))
        assertEquals(market.salaryFor(5), market.salaryFor(9), "범위를 넘는 등급은 양 끝으로 자른다")
    }

    @Test
    fun `경질하면 남은 계약만큼 위약금을 낸다`() {
        val long = market.firingCost(baseballgm.model.StaffContract(2.0, 3))
        val short = market.firingCost(baseballgm.model.StaffContract(2.0, 1))
        assertTrue(long > short, "$long vs $short")
        assertTrue(short > 0.0)
    }

    @Test
    fun `무직 스태프만 시장에 올라온다`() {
        val employed = baseballgm.model.Coach(
            id = baseballgm.model.StaffId("C001"), name = "소속코치", birthYear = 1975,
            role = baseballgm.model.CoachRole.BATTING, focus = baseballgm.model.CoachFocus.POWER,
            grade = 4, contract = baseballgm.model.StaffContract(2.0, 2), teamId = AAA,
        )
        val free = employed.copy(id = baseballgm.model.StaffId("C002"), name = "무직코치", teamId = null)
        val league = testLeague(
            teams = listOf(testTeam("AAA"), testTeam("BBB", pick = 2)),
            players = rosterFor("AAA") + rosterFor("BBB"),
        ).copy(coaches = listOf(employed, free))

        val state = market.open(league, Random(1))
        assertEquals(1, state.offers().size)
        assertEquals("무직코치", state.offers().first().name)
        assertTrue(state.offers().first().askingSalary > 0.0)
        assertTrue(state.offers().first().roleLabel.contains("타격"))
    }

    @Test
    fun `자금이 모자라면 고용할 수 없다`() {
        val free = baseballgm.model.Coach(
            id = baseballgm.model.StaffId("C002"), name = "무직코치", birthYear = 1975,
            role = baseballgm.model.CoachRole.PITCHING, focus = baseballgm.model.CoachFocus.STUFF,
            grade = 5, contract = baseballgm.model.StaffContract(3.0, 1), teamId = null,
        )
        val league = testLeague(
            teams = listOf(testTeam("AAA"), testTeam("BBB", pick = 2)),
            players = rosterFor("AAA") + rosterFor("BBB"),
        ).copy(coaches = listOf(free))

        val state = market.open(league, Random(1))
        assertEquals(null, market.hire(state, league, AAA, free.id, funds = 0.1))
        assertTrue(market.hire(state, league, AAA, free.id, funds = 50.0) != null)
        assertEquals(null, market.hire(state, league, AAA, free.id, funds = 50.0), "이미 나간 사람을 또 데려왔다")
    }
}
