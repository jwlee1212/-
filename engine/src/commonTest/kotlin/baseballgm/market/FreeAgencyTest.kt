package baseballgm.market

import baseballgm.development.AgingCurves
import baseballgm.league.Standings
import baseballgm.league.StrengthCalculator
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.Position
import baseballgm.model.TeamId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FA 시장 (docs/11). */
class FreeAgencyTest {

    private val a = TeamId("AAA")
    private val b = TeamId("BBB")
    private val strength = StrengthCalculator(DRAFT_BALANCE)
    private val valuation = Valuation(DRAFT_BALANCE, AgingCurves(DRAFT_BALANCE))
    private val market = FreeAgencyMarket(
        DRAFT_BALANCE,
        valuation,
        MarketView(DRAFT_BALANCE, strength),
        PositionNeed(DRAFT_BALANCE, strength),
        TeamModeResolver(DRAFT_BALANCE, strength),
    )
    private val standings = Standings.empty(listOf(a, b))

    /** 두 팀 모두 유격수가 비어 있다 — 경쟁이 붙을 조건 */
    private val rosters = rosterFor("AAA").filterNot { it.id.value == "AAA-B4" } +
        rosterFor("BBB").filterNot { it.id.value == "BBB-B4" }

    private val star = testBatter("FA1", rating = 72, potential = 74, position = Position.SHORTSTOP, age = 29)
        .copy(contract = Contract(5.0, 0, 0.0, 0, 9, ContractType.STANDARD))

    private fun league(funds: Double = 60.0) = testLeague(
        teams = listOf(testTeam("AAA", funds), testTeam("BBB", funds, pick = 2)),
        players = rosters + star,
        freeAgents = listOf(star.id),
        faOrigins = mapOf(star.id to a),
    )

    @Test
    fun `FA 명단과 등급이 만들어진다`() {
        val state = market.open(league(), standings, Random(1))
        val agent = assertNotNull(state.agent(star.id))
        assertEquals(a, agent.previousTeam)
        assertTrue(agent.askingSalary > 0.0)
        assertTrue(agent.askingYears in 1..6)
    }

    @Test
    fun `원소속 구단 선수의 만족도가 높으면 잔류 조건을 더 좋게 본다`() {
        // 유저 구단(AAA) 선수만 만족도가 있다 (docs/13 선수 성향과 만족도)
        fun satisfactionWith(morale: Int): Double {
            val current = league().let {
                it.copy(
                    management = it.management.copy(
                        userTeam = a,
                        morale = mapOf(star.id to baseballgm.management.PlayerMorale(morale)),
                    ),
                )
            }
            val state = market.open(current, standings, Random(1))
            val agent = state.agent(star.id)!!
            val offer = ContractOffer(a, star.id, salary = agent.askingSalary * 0.9, years = agent.askingYears)
            return assertNotNull(market.negotiation(state, star.id, a, current, standings, hypothetical = offer)).satisfaction
        }
        val happy = satisfactionWith(95)
        val neutral = satisfactionWith(50)
        val unhappy = satisfactionWith(5)
        assertTrue(happy > neutral && neutral > unhappy, "만족 $happy · 보통 $neutral · 불만 $unhappy")
    }

    @Test
    fun `연봉 순위가 높으면 A등급이다`() {
        assertEquals(FaGrade.A, market.gradeOf(1))
        assertEquals(FaGrade.B, market.gradeOf(45))
        assertEquals(FaGrade.C, market.gradeOf(200))
    }

    @Test
    fun `여러 라운드를 돌리면 계약이 성사된다`() {
        val current = league()
        val state = market.open(current, standings, Random(1))
        repeat(market.rounds) { market.runRound(state, current, standings, Random(3)) }
        assertTrue(state.signings.isNotEmpty(), "아무도 계약하지 않았다")
        val signing = state.signings.first()
        assertTrue(signing.offer.salary > 0.0)
        assertTrue(signing.offer.years >= 1)
    }

    @Test
    fun `관심 구단이 많으면 희망 연봉이 오른다`() {
        val current = league()
        val state = market.open(current, standings, Random(1))
        val before = state.agent(star.id)!!.askingSalary
        // 계약이 성사되기 전 라운드에서 경쟁만 붙여 본다
        market.runRound(state, current, standings, Random(11))
        val agent = state.agent(star.id)
        if (agent != null && agent.interestedTeams >= 2) {
            assertTrue(agent.askingSalary >= before, "경쟁이 붙었는데 값이 내려갔다")
            assertTrue(agent.askingSalary <= state.offersFor(star.id).maxOf { it.salary } + 1e-9, "희망 연봉이 최고 제시액을 넘었다")
        }
    }

    @Test
    fun `아무도 안 붙으면 눈높이를 낮춘다`() {
        val poor = testLeague(
            teams = listOf(testTeam("AAA", funds = 0.0), testTeam("BBB", funds = 0.0, pick = 2)),
            players = rosters + star,
            freeAgents = listOf(star.id),
            faOrigins = mapOf(star.id to a),
        )
        val state = market.open(poor, standings, Random(1))
        val before = state.agent(star.id)!!.askingSalary
        market.runRound(state, poor, standings, Random(5))
        val after = state.agent(star.id)?.askingSalary
        if (after != null) assertTrue(after < before, "제안이 없는데 희망 연봉이 그대로다")
    }

    @Test
    fun `AI 는 가치 이상으로 지르지 않는다`() {
        val current = league()
        val maximum = market.maximumOffer(star, b, current, TeamMode.NEUTRAL, need = 1.3)
        val war = valuation.expectedWar(star, 72.0)
        assertTrue(maximum <= war * valuation.salaryPerWar * 1.3, "상한 $maximum 이 가치를 넘는다")
        assertTrue(maximum > 0.0)
    }

    @Test
    fun `A등급 영입에는 보상이 따른다`() {
        val current = league()
        val agent = market.open(current, standings, Random(1)).agent(star.id)!!.copy(grade = FaGrade.A)
        val compensation = assertNotNull(market.compensationFor(current, agent, b, star))
        assertEquals(a, compensation.toTeam)
        assertEquals(b, compensation.fromTeam)
        assertTrue(compensation.cash > 0.0)
    }

    @Test
    fun `원소속팀에 잔류하면 보상이 없다`() {
        val current = league()
        val agent = market.open(current, standings, Random(1)).agent(star.id)!!.copy(grade = FaGrade.A)
        assertEquals(null, market.compensationFor(current, agent, a, star))
    }

    @Test
    fun `역제안은 무엇이 아쉬운지 알려준다`() {
        val current = league()
        val state = market.open(current, standings, Random(1))
        val agent = state.agent(star.id)!!
        val lowball = ContractOffer(b, star.id, salary = agent.askingSalary * 0.5, years = agent.askingYears)
        val (counter, message) = market.counterProposal(state, lowball, star, current, standings)
        assertTrue(counter.salary > lowball.salary || counter.years > lowball.years)
        assertTrue(message.isNotBlank())
    }

    @Test
    fun `미계약 FA 는 헐값에 원소속팀에 남고 미계약 방출 선수는 떠난다`() {
        val released = testBatter("CUT", rating = 50, age = 31, position = Position.LEFT_FIELD)
            .copy(contract = Contract(2.0, 0, 0.0, 0, 9, ContractType.STANDARD))
        val current = testLeague(
            teams = listOf(testTeam("AAA", funds = 0.0), testTeam("BBB", funds = 0.0, pick = 2)),
            players = rosters + star + released,
            freeAgents = listOf(star.id, released.id),
            faOrigins = mapOf(star.id to a, released.id to b),
        )
        val state = market.open(current, standings, Random(1), released = setOf(released.id))
        val closed = market.close(state, current, Random(1))

        val leftover = closed.players.firstOrNull { it.id == star.id }
        assertNotNull(leftover, "미계약 FA 가 사라졌다")
        assertEquals(a, leftover.teamId)
        assertEquals(1, leftover.contract.yearsRemaining)
        assertTrue(leftover.contract.salary < star.contract.salary, "헐값 계약이 아니다")

        assertFalse(closed.players.any { it.id == released.id }, "미계약 방출 선수가 리그에 남아 있다")
        assertTrue(closed.freeAgentPool.isEmpty())
    }

    // ---------- 경쟁 입찰 (2026-10-03) ----------

    private val c = TeamId("CCC")

    /** 세 팀 모두 유격수가 비어 있다. 우리(CCC)는 유저 구단 역할 */
    private fun threeTeams(funds: Double = 80.0) = testLeague(
        teams = listOf(testTeam("AAA", funds), testTeam("BBB", funds, pick = 2), testTeam("CCC", funds, pick = 3)),
        players = rosters + rosterFor("CCC").filterNot { it.id.value == "CCC-B4" } + star,
        freeAgents = listOf(star.id),
        faOrigins = mapOf(star.id to a),
    )

    private val threeStandings = Standings.empty(listOf(a, b, c))

    @Test
    fun `개장하면 AI 구단이 먼저 조건을 내고 서로 값을 올린다`() {
        val current = threeTeams()
        val state = market.open(current, threeStandings, Random(4))
        val offers = state.offersFor(star.id)
        assertTrue(offers.isNotEmpty(), "아무 구단도 참전하지 않았다")
        assertEquals(offers.size, state.agent(star.id)!!.interestedTeams)
        assertTrue(state.historyOf(star.id).any { it.kind == FaBidKind.JOINED })
        if (offers.size >= 2) {
            // 뒤에 붙은 구단은 앞 구단보다 더 부른다
            assertTrue(offers.maxOf { it.salary } > offers.minOf { it.salary }, "경쟁이 붙었는데 값이 같다")
        }
    }

    @Test
    fun `유저 제안은 라운드가 지나도 남는다`() {
        val current = threeTeams()
        val state = market.open(current, threeStandings, Random(4))
        val agent = state.agent(star.id)!!
        state.putOffer(ContractOffer(c, star.id, salary = agent.askingSalary * 0.5, years = 1))
        market.runRound(state, current, threeStandings, Random(5), userTeam = c)
        if (state.agent(star.id) != null) assertNotNull(state.offerOf(c, star.id), "유저 제안이 사라졌다")
    }

    @Test
    fun `밀리면 얼마면 1순위인지 알려 주고 그 값을 내면 1순위가 된다`() {
        val current = threeTeams()
        val state = market.open(current, threeStandings, Random(4))
        val agent = state.agent(star.id)!!
        assertTrue(state.offersFor(star.id).isNotEmpty())
        state.putOffer(ContractOffer(c, star.id, salary = 0.5, years = agent.askingYears))

        val behind = assertNotNull(market.negotiation(state, star.id, c, current, threeStandings))
        assertEquals(FaStanding.OUTBID, behind.standing)
        assertTrue(behind.rivals.isNotEmpty())
        assertEquals(0.0, behind.ourSignChance)
        val target = assertNotNull(behind.salaryToLead, "1순위 금액을 못 구했다")

        state.putOffer(ContractOffer(c, star.id, salary = target, years = agent.askingYears))
        val ahead = assertNotNull(market.negotiation(state, star.id, c, current, threeStandings))
        assertTrue(ahead.standing == FaStanding.LEADING || ahead.standing == FaStanding.LEADING_WAITING, "${ahead.standing}")
        assertEquals(0.0, ahead.rivalSignChance)
    }

    @Test
    fun `유저가 상한보다 크게 부르면 AI 는 따라오다 철수한다`() {
        val current = threeTeams()
        val state = market.open(current, threeStandings, Random(4))
        val agent = state.agent(star.id)!!
        val huge = agent.askingSalary * 4
        state.putOffer(ContractOffer(c, star.id, salary = huge, years = agent.askingYears))
        market.runRound(state, current, threeStandings, Random(6), userTeam = c)

        val signed = state.signings.firstOrNull { it.playerId == star.id }
        if (signed != null) {
            assertEquals(c, signed.offer.teamId, "가장 후하게 불렀는데 다른 팀에 갔다")
        } else {
            assertTrue(state.offersFor(star.id).filter { it.teamId != c }.all { it.salary < huge }, "AI 가 가치 이상으로 따라왔다")
            assertTrue(state.historyOf(star.id).any { it.kind == FaBidKind.WITHDREW }, "밀린 AI 가 물러나지 않았다")
            assertEquals(FaStanding.LEADING, market.negotiation(state, star.id, c, current, threeStandings)!!.standing)
        }
    }

    @Test
    fun `마지막 라운드에서는 1순위 조건에 도장을 찍는다`() {
        val current = threeTeams()
        val state = market.open(current, threeStandings, Random(4))
        val agent = state.agent(star.id)!!
        state.putOffer(ContractOffer(c, star.id, salary = agent.askingSalary * 4, years = agent.askingYears))
        repeat(market.rounds) { if (state.agent(star.id) != null) market.runRound(state, current, threeStandings, Random(it.toLong()), userTeam = c) }
        assertEquals(c, state.signings.single { it.playerId == star.id }.offer.teamId)
    }

    @Test
    fun `같은 시드면 입찰 결과가 같다`() {
        fun run(): List<Pair<String, Double>> {
            val current = threeTeams()
            val state = market.open(current, threeStandings, Random(9))
            repeat(market.rounds) { market.runRound(state, current, threeStandings, Random(10L + it)) }
            return state.signings.map { it.offer.teamId.value to it.offer.salary }
        }
        assertEquals(run(), run())
    }

    // ---------- 최저 연봉 (2026-10-04) ----------

    /** 지난 시즌 WAR 4.0 을 기록한 스타 — 세 팀 리그, 다른 두 팀은 연봉 총액이 캡을 넘어 입찰하지 못한다 */
    private fun noCompetition(war: Double = 4.0): baseballgm.league.League {
        val capped = (rosterFor("AAA", salary = 10.0) + rosterFor("BBB", salary = 10.0))
            .filterNot { it.id.value == "AAA-B4" || it.id.value == "BBB-B4" }
        val base = testLeague(
            teams = listOf(testTeam("AAA", 0.0), testTeam("BBB", 0.0, pick = 2), testTeam("CCC", 80.0, pick = 3)),
            players = capped + rosterFor("CCC").filterNot { it.id.value == "CCC-B4" } + star,
            freeAgents = listOf(star.id),
            faOrigins = mapOf(star.id to a),
        )
        val line = baseballgm.league.CareerSeason(season = base.season - 1, team = a, name = star.registeredName, war = war)
        return base.copy(history = baseballgm.league.LeagueHistory(careers = mapOf(star.id to listOf(line))))
    }

    @Test
    fun `최저 연봉은 직전 시즌 WAR 로 정해지고 희망 연봉은 그 아래로 안 내려간다`() {
        val current = noCompetition()
        val state = market.open(current, threeStandings, Random(1), userTeam = c)
        val agent = state.agent(star.id)!!
        assertEquals(4.0, agent.lastWar)
        assertEquals(4.0 * valuation.salaryPerWar * DRAFT_BALANCE.double("faMarket.salaryFloorRateOfWar"), agent.salaryFloor, 0.051)
        repeat(market.rounds - 1) { market.runRound(state, current, threeStandings, Random(it.toLong()), userTeam = c) }
        state.agent(star.id)?.let { assertTrue(it.askingSalary >= it.salaryFloor - 1e-9, "희망 연봉이 최저 아래로 내려갔다") }
    }

    @Test
    fun `경쟁이 없어도 마지막 라운드에 최저 연봉 미만으로는 계약하지 않는다`() {
        val current = noCompetition()
        val state = market.open(current, threeStandings, Random(1), userTeam = c)
        val floor = state.agent(star.id)!!.salaryFloor
        state.putOffer(ContractOffer(c, star.id, salary = floor * 0.5, years = 1))
        assertEquals(FaStanding.BELOW_FLOOR, market.negotiation(state, star.id, c, current, threeStandings)!!.standing)
        repeat(market.rounds) { if (state.agent(star.id) != null) market.runRound(state, current, threeStandings, Random(it.toLong()), userTeam = c) }
        assertTrue(state.signings.none { it.playerId == star.id && it.offer.teamId == c }, "최저 연봉 미만 헐값 계약이 됐다")

        // 미계약 1년 계약도 최저 연봉 아래로는 안 간다
        val closed = market.close(state, current, Random(1))
        val after = closed.players.first { it.id == star.id }
        if (state.signings.none { it.playerId == star.id }) assertTrue(after.contract.salary >= floor - 0.051)
    }

    @Test
    fun `최저 연봉 이상이면 경쟁 없이 마지막 라운드에 계약된다`() {
        val current = noCompetition()
        val state = market.open(current, threeStandings, Random(1), userTeam = c)
        val floor = state.agent(star.id)!!.salaryFloor
        state.putOffer(ContractOffer(c, star.id, salary = floor, years = 1))
        repeat(market.rounds) { if (state.agent(star.id) != null) market.runRound(state, current, threeStandings, Random(it.toLong()), userTeam = c) }
        assertEquals(c, state.signings.single { it.playerId == star.id }.offer.teamId)
    }

    // ---------- 협상 테이블 (2026-10-04, FM식) ----------

    private val talks = FaTalks(DRAFT_BALANCE, market)

    @Test
    fun `에이전트 요구 조건대로 내면 경쟁 구단이 있어도 그 자리에서 도장을 찍는다`() {
        val current = threeTeams()
        val state = market.open(current, threeStandings, Random(4))
        assertTrue(state.offersFor(star.id).isNotEmpty(), "경쟁 구단이 붙어 있어야 한다")
        val demand = assertNotNull(talks.demand(state, star.id, c, current, threeStandings))
        assertTrue(demand.years >= 1 && demand.salary > 0.0 && demand.signingBonus > 0.0, "$demand")

        val result = talks.propose(state, demand.asOffer(c, star.id), current, threeStandings)
        assertEquals(FaTalkOutcome.SIGNED, result.outcome, result.message)
        assertEquals(c, state.signings.single { it.playerId == star.id }.offer.teamId)
        assertNull(state.agent(star.id), "도장 찍은 선수는 시장에서 빠진다")
    }

    @Test
    fun `조금 모자라면 역제안이 오고 그대로 내면 합의된다`() {
        val current = threeTeams()
        val state = market.open(current, threeStandings, Random(4))
        val demand = assertNotNull(talks.demand(state, star.id, c, current, threeStandings))
        val short = demand.asOffer(c, star.id).copy(salary = demand.salary * 0.9)

        val first = talks.propose(state, short, current, threeStandings)
        assertEquals(FaTalkOutcome.COUNTERED, first.outcome, first.message)
        assertEquals(talks.maxPatience - 1, first.patienceLeft)
        val counter = assertNotNull(first.counter)
        assertTrue(counter.salary > short.salary)
        assertEquals(c, state.offerOf(c, star.id)?.teamId, "모자란 제안도 시장에 남는다")

        assertEquals(FaTalkOutcome.SIGNED, talks.propose(state, counter, current, threeStandings).outcome)
        assertEquals(4, state.talksOf(star.id).size, "우리 말·에이전트 말이 번갈아 남는다")
    }

    @Test
    fun `터무니없는 제안은 인내심을 두 칸 깎고 바닥나면 그 라운드엔 협상이 끝난다`() {
        val current = threeTeams()
        val state = market.open(current, threeStandings, Random(4))
        val agent = state.agent(star.id)!!
        val lowball = ContractOffer(c, star.id, salary = agent.salaryFloor + 0.1, years = 1)
        val first = talks.propose(state, lowball, current, threeStandings)
        assertEquals(FaTalkOutcome.REJECTED, first.outcome, first.message)
        assertEquals(talks.maxPatience - 2, first.patienceLeft)
        talks.propose(state, lowball, current, threeStandings)
        assertEquals(FaTalkOutcome.BROKEN_OFF, talks.propose(state, lowball, current, threeStandings).outcome)

        // 다음 라운드엔 인내심이 다시 찬다
        market.runRound(state, current, threeStandings, Random(9), userTeam = c)
        if (state.agent(star.id) != null) assertEquals(talks.maxPatience, talks.patienceLeft(state, star.id))
    }

    @Test
    fun `옵션 조항은 달성 가능성만큼만 쳐 주고 연봉의 절반을 넘거나 같은 조항이 겹치면 받지 않는다`() {
        val current = league()
        val state = market.open(current, standings, Random(1))
        val agent = state.agent(star.id)!!
        val player = current.player(star.id)
        val guaranteed = ContractOffer(b, star.id, salary = agent.askingSalary, years = 3)
        // 지난 시즌 기록이 없는 선수 → 어떤 조항이든 가능성이 낮다: 보장 1억 > 옵션 1억
        val withOption = guaranteed.copy(salary = agent.askingSalary - 1.0, options = listOf(OptionClause(OptionKind.HOME_RUNS, 1.0)))
        assertTrue(
            market.score(player, guaranteed, current, standings, agent) > market.score(player, withOption, current, standings, agent),
            "보장 1억이 옵션 1억보다 좋아야 한다",
        )
        val tooMuch = guaranteed.copy(options = listOf(OptionClause(OptionKind.HOME_RUNS, guaranteed.salary)))
        assertEquals(FaTalkOutcome.REJECTED, talks.propose(state, tooMuch, current, standings).outcome)
        assertNull(state.offerOf(b, star.id), "규칙에 어긋난 제안은 시장에 남기지 않는다")
        val twice = guaranteed.copy(options = listOf(OptionClause(OptionKind.RBI, 0.5), OptionClause(OptionKind.RBI, 0.5)))
        assertEquals(FaTalkOutcome.REJECTED, talks.propose(state, twice, current, standings).outcome)
    }

    @Test
    fun `옵션 달성 판정과 지난 시즌 기록에 따른 가능성`() {
        val rules = OptionRules(DRAFT_BALANCE)
        val slugger = OptionStats(plateAppearances = 520, atBats = 460, hits = 140, homeRuns = 28, rbi = 90)
        assertTrue(rules.achieved(OptionKind.HOME_RUNS, slugger, madePostseason = false))
        assertTrue(rules.achieved(OptionKind.BATTING_AVERAGE, slugger, madePostseason = false), "타율 .304")
        assertTrue(!rules.achieved(OptionKind.STOLEN_BASES, slugger, madePostseason = false))
        assertTrue(rules.achieved(OptionKind.TEAM_POSTSEASON, slugger, madePostseason = true))
        // 표본이 모자란 타율은 달성으로 치지 않는다
        assertTrue(!rules.achieved(OptionKind.BATTING_AVERAGE, OptionStats(plateAppearances = 40, atBats = 36, hits = 15), madePostseason = false))
        val ace = OptionStats(outs = 540, earnedRuns = 55, wins = 12, games = 28)
        assertTrue(rules.achieved(OptionKind.ERA, ace, madePostseason = false), "평균자책 2.75")
        assertTrue(rules.achieved(OptionKind.INNINGS, ace, madePostseason = false))
        assertEquals(listOf("타율 .300 이상", "평균자책 3.50 이하"), listOf(rules.label(OptionKind.BATTING_AVERAGE), rules.label(OptionKind.ERA)))
        // 모든 조항이 이름을 갖는다 (기준 수치가 없는 팀 포스트시즌 포함)
        OptionKind.entries.forEach { assertTrue(rules.label(it).isNotBlank(), "$it") }
    }
}
