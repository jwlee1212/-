package baseballgm.market

import baseballgm.development.AgingCurves
import baseballgm.league.Standings
import baseballgm.league.StrengthCalculator
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.PitcherRole
import baseballgm.model.Position
import baseballgm.model.TeamId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 트레이드 규칙과 AI 판정 (docs/11). */
class TradeTest {

    private val a = TeamId("AAA")
    private val b = TeamId("BBB")
    private val strength = StrengthCalculator(DRAFT_BALANCE)
    private val valuation = Valuation(DRAFT_BALANCE, AgingCurves(DRAFT_BALANCE))
    private val marketView = MarketView(DRAFT_BALANCE, strength)
    private val positionNeed = PositionNeed(DRAFT_BALANCE, strength)
    private val modeResolver = TeamModeResolver(DRAFT_BALANCE, strength)
    private val ai = TradeAI(DRAFT_BALANCE, valuation, marketView, positionNeed, modeResolver)
    private val rules = TradeRules(DRAFT_BALANCE)
    private val standings = Standings.empty(listOf(a, b))

    private val star = testBatter(
        "STAR", rating = 75, potential = 78, position = Position.SHORTSTOP, age = 27, teamId = a,
    ).copy(contract = Contract(3.0, 4, 0.0, 5, 5, ContractType.STANDARD))

    private val filler = testBatter(
        "FILL", rating = 48, potential = 52, position = Position.LEFT_FIELD, age = 30, teamId = b,
    ).copy(contract = Contract(1.0, 2, 0.0, 3, 8, ContractType.STANDARD))

    private val league = testLeague(players = rosterFor("AAA") + rosterFor("BBB") + star + filler)

    private fun swap(give: String, take: String) = TradeProposal(
        proposer = b,
        partner = a,
        fromProposer = TradePackage(playerIds = listOf(baseballgm.model.PlayerId(give))),
        fromPartner = TradePackage(playerIds = listOf(baseballgm.model.PlayerId(take))),
    )

    @Test
    fun `헐값에 좋은 선수를 달라는 제안은 거절한다`() {
        val verdict = ai.judge(league, standings, swap("FILL", "STAR"), a, "normal")
        assertFalse(verdict.accepted, "스타를 잡선수와 바꿔줬다 (${verdict.incomingValue} vs ${verdict.requiredValue})")
        assertTrue(verdict.shortfall > 0)
    }

    @Test
    fun `난이도가 높을수록 요구치가 커진다`() {
        val proposal = swap("FILL", "STAR")
        val easy = ai.judge(league, standings, proposal, a, "easy").requiredValue
        val hard = ai.judge(league, standings, proposal, a, "hard").requiredValue
        assertTrue(hard > easy, "어려움 $hard 이 쉬움 $easy 보다 크지 않다")
    }

    @Test
    fun `악성 계약을 떠넘기는 제안은 받는 쪽이 거절한다`() {
        val burden = testBatter("BAD", rating = 55, age = 34, teamId = b, position = Position.FIRST_BASE)
            .copy(contract = Contract(20.0, 4, 0.0, 0, 12, ContractType.FREE_AGENT))
        val withBurden = testLeague(players = rosterFor("AAA") + rosterFor("BBB") + star + burden)

        val dump = TradeProposal(
            proposer = b,
            partner = a,
            fromProposer = TradePackage(playerIds = listOf(burden.id)),
            fromPartner = TradePackage(),
        )
        val verdict = ai.judge(withBurden, standings, dump, a, "normal")
        assertFalse(verdict.accepted, "공짜로 준다니까 악성 계약을 받았다")
        assertTrue(verdict.incomingValue < 0, "악성 계약의 가치가 음수가 아니다 (${verdict.incomingValue})")
    }

    @Test
    fun `지명권도 저울에 올라간다`() {
        val pick = league.draftRights.find(TEST_SEASON, 1, b)!!
        val mode = TeamMode.NEUTRAL
        val first = ai.pickValueOf(league, pick, mode)
        val late = ai.pickValueOf(league, league.draftRights.find(TEST_SEASON, 3, b)!!, mode)
        assertTrue(first > late, "1라운드가 3라운드보다 싸다")

        val rebuildValue = ai.pickValueOf(league, pick, TeamMode.REBUILD)
        val contendValue = ai.pickValueOf(league, pick, TeamMode.CONTEND)
        assertTrue(rebuildValue > contendValue, "리빌딩 팀이 지명권을 더 높이 봐야 한다")
    }

    @Test
    fun `외국인 보유 한도를 넘기는 거래는 막힌다`() {
        val foreigners = (1..3).map {
            testPitcher("F$it", rating = 65, age = 30, teamId = a, role = PitcherRole.STARTER)
                .copy(origin = baseballgm.model.Origin.FOREIGN)
        }
        val incoming = testPitcher("F4", rating = 65, age = 30, teamId = b, role = PitcherRole.STARTER)
            .copy(origin = baseballgm.model.Origin.FOREIGN)
        val withForeigners = testLeague(players = rosterFor("AAA") + rosterFor("BBB") + foreigners + incoming)

        val proposal = TradeProposal(
            proposer = b,
            partner = a,
            fromProposer = TradePackage(playerIds = listOf(incoming.id)),
            fromPartner = TradePackage(playerIds = listOf(baseballgm.model.PlayerId("AAA-B0"))),
        )
        val problems = rules.problems(withForeigners, proposal, week = 5, draftDone = false, history = emptyList())
        assertTrue(problems.any { it.contains("외국인") }, problems.toString())
    }

    @Test
    fun `받은 선수를 곧바로 되팔 수 없다`() {
        val history = listOf(
            TradeRecord(
                season = TEST_SEASON,
                week = 3,
                teamA = a,
                teamB = b,
                playersToA = listOf(filler.id),
                playersToB = emptyList(),
            ),
        )
        val moved = league.copy(
            players = league.players.map { if (it.id == filler.id) it.movedForTest(a) else it },
        )
        val backAgain = TradeProposal(
            proposer = a,
            partner = b,
            fromProposer = TradePackage(playerIds = listOf(filler.id)),
            fromPartner = TradePackage(playerIds = listOf(baseballgm.model.PlayerId("BBB-B1"))),
        )
        val problems = rules.problems(moved, backAgain, week = 5, draftDone = false, history = history)
        assertTrue(problems.any { it.contains("되팔") }, problems.toString())
    }

    @Test
    fun `거래가 성사되면 소속과 지명권과 자금이 한꺼번에 바뀐다`() {
        val pick = league.draftRights.find(TEST_SEASON + 1, 2, b)!!
        val proposal = TradeProposal(
            proposer = b,
            partner = a,
            fromProposer = TradePackage(playerIds = listOf(filler.id), picks = listOf(pick), cash = 3.0),
            fromPartner = TradePackage(playerIds = listOf(star.id)),
        )
        val outcome = TradeExecutor.apply(league, proposal, week = 5)

        assertEquals(a, outcome.league.player(filler.id).teamId)
        assertEquals(b, outcome.league.player(star.id).teamId)
        assertEquals(a, outcome.league.draftRights.find(TEST_SEASON + 1, 2, b)!!.ownerTeam)
        assertEquals(43.0, outcome.league.team(a).operatingFunds, 0.001)
        assertEquals(37.0, outcome.league.team(b).operatingFunds, 0.001)
        assertEquals(1, outcome.league.tradeHistory.size)
    }

    @Test
    fun `협상 피로도가 쌓이면 협상을 거부한다`() {
        val fatigue = NegotiationFatigue()
        repeat(rules.rejectionsBeforeRefusal) {
            assertFalse(fatigue.refuses(b, a, week = 5), "아직 거부하면 안 된다")
            fatigue.recordRejection(b, a, week = 5, rules.rejectionsBeforeRefusal, rules.refusalWeeks)
        }
        assertTrue(fatigue.refuses(b, a, week = 5), "반복 거절인데 계속 받아 준다")
        assertFalse(fatigue.refuses(b, a, week = 5 + rules.refusalWeeks), "기간이 지나면 풀려야 한다")

        fatigue.recordAcceptance(b, a)
        assertFalse(fatigue.refuses(b, a, week = 5))
    }

    @Test
    fun `AI 끼리도 서로 이득인 거래만 만든다`() {
        val proposal = ai.findTrade(league, standings, a, b, "normal", Random(7)) ?: return
        assertTrue(ai.judge(league, standings, proposal, a, "normal").accepted)
        assertTrue(ai.judge(league, standings, proposal, b, "normal").accepted)
    }

    // ---------- 트레이드 가치 개편 (2026-10-04) ----------

    private fun neutralValue(player: baseballgm.model.Player): TradeValue {
        val estimate = marketView.estimate(player, player.teamId)
        return valuation.tradeValue(player, estimate, TEST_SEASON, TeamSituation(TeamMode.NEUTRAL, 1.0, valuation.baseSalaryWeight))
    }

    @Test
    fun `연봉이 비싼 주전도 가치가 양수이고 싼 벤치 선수보다 비싸다`() {
        // 예전 식(WAR 시장가 − 연봉)에선 비싼 주전이 리그 최저 가치였다
        val pricey = star.copy(contract = Contract(15.0, 4, 0.0, 0, 9, ContractType.FREE_AGENT))
        val bench = testBatter("BENCH", rating = 58, potential = 60, position = Position.SHORTSTOP, age = 28, teamId = a)
            .copy(rosterLevel = baseballgm.model.RosterLevel.FIRST_TEAM, contract = Contract(1.0, 1, 0.0, 3, 5, ContractType.STANDARD))
        val priceyValue = neutralValue(pricey).baseValue
        assertTrue(priceyValue > 0.0, "비싼 주전의 가치가 음수다 ($priceyValue)")
        assertTrue(priceyValue > neutralValue(bench).baseValue, "비싼 주전($priceyValue)이 벤치 선수보다 싸다")
    }

    @Test
    fun `비FA 선수는 1년 계약이어도 FA 까지 보유하고 곧 FA 면 렌탈이다`() {
        val held = star.copy(contract = Contract(3.0, 1, 0.0, 4, 3, ContractType.STANDARD))
        val rental = star.copy(contract = Contract(3.0, 1, 0.0, 0, 8, ContractType.STANDARD))
        assertEquals(5, valuation.controlYears(held))
        assertEquals(1, valuation.controlYears(rental))
        assertEquals(1, valuation.controlYears(star.copy(contract = Contract(3.0, 1, 0.0, 0, 0, ContractType.FOREIGN))))
        assertTrue(neutralValue(held).baseValue > neutralValue(rental).baseValue * 2, "보유 기간이 길어도 가치가 비슷하다")
    }

    @Test
    fun `잠재력만 큰 유망주는 완성된 주전보다 싸다`() {
        val prospect = testBatter("PROS", rating = 55, potential = 90, position = Position.SHORTSTOP, age = 20, teamId = a)
        val prospectValue = neutralValue(prospect).baseValue
        val starValue = neutralValue(star).baseValue
        assertTrue(prospectValue > 0.0)
        assertTrue(prospectValue < starValue, "유망주 $prospectValue 가 주전 $starValue 보다 비싸다")
    }

    @Test
    fun `구멍 난 자리를 메우는 제안은 그 자리 선수를 노리고 양쪽 다 납득한다`() {
        // AAA 는 유격수가 리그 하위 수준, BBB 는 주전급 유격수가 둘
        val weakShortstop = rosterFor("AAA").map {
            if (it is baseballgm.model.Batter && it.primaryPosition == Position.SHORTSTOP) {
                testBatter(it.id.value, rating = 42, position = Position.SHORTSTOP, age = 27, teamId = a)
                    .copy(rosterLevel = baseballgm.model.RosterLevel.FIRST_TEAM, contract = it.contract)
            } else {
                it
            }
        }
        val target = testBatter("BSS", rating = 68, potential = 70, position = Position.SHORTSTOP, age = 28, teamId = b)
            .copy(rosterLevel = baseballgm.model.RosterLevel.FIRST_TEAM, contract = Contract(3.0, 1, 0.0, 3, 5, ContractType.STANDARD))
        val prospects = (1..4).map {
            testBatter("APR$it", rating = 52, potential = 78, position = Position.CENTER_FIELD, age = 21, teamId = a)
        }
        val needy = testLeague(players = weakShortstop + prospects + rosterFor("BBB") + target)

        val offer = ai.buildOffer(needy, standings, proposer = a, partner = b, difficulty = "normal", random = Random(3), partnerIsAi = false)
        val made = kotlin.test.assertNotNull(offer, "구멍 난 자리가 있는데 제안을 못 만들었다")
        assertEquals(TradeMotive.NEED, made.reason?.motive)
        assertEquals(Position.SHORTSTOP, (needy.player(made.fromPartner.playerIds.single()) as baseballgm.model.Batter).primaryPosition)
        assertTrue(ai.judge(needy, standings, made, a, "normal").accepted, "제안한 AI 가 스스로 손해다")
        assertTrue(ai.fairnessFor(needy, standings, made, b) >= 1.0, "받는 쪽 눈에 공정하지 않다")
    }

    // ---------- 트레이드 협상 확장 (2026-10-05) ----------

    private fun withGm(base: baseballgm.league.League, team: TeamId, style: baseballgm.model.GmStyle) = base.copy(
        generalManagers = base.generalManagers + baseballgm.model.GeneralManager(
            id = baseballgm.model.StaffId("G-${team.value}"), name = "테스트", birthYear = 1980, reputation = 50,
            tradeAggression = 50, teamId = team, style = style,
        ),
    )

    @Test
    fun `깐깐한 협상가는 이익을 더 남겨야 받는다`() {
        val proposal = swap("FILL", "STAR")
        val balanced = ai.judge(withGm(league, a, baseballgm.model.GmStyle.BALANCED), standings, proposal, a, "normal").requiredValue
        val hard = ai.judge(withGm(league, a, baseballgm.model.GmStyle.HARD_BARGAINER), standings, proposal, a, "normal").requiredValue
        assertTrue(hard > balanced, "깐깐한 협상가 요구 $hard ≤ 무난형 $balanced")
    }

    @Test
    fun `리빌딩 집착형은 유망주를 즉시 전력형보다 비싸게 본다`() {
        val prospect = testBatter("PROS", rating = 50, potential = 80, position = Position.CENTER_FIELD, age = 20, teamId = b)
        val withProspect = testLeague(players = rosterFor("AAA") + rosterFor("BBB") + star + filler + prospect)
        val pack = TradePackage(playerIds = listOf(prospect.id))
        val rebuilder = ai.packageValue(withGm(withProspect, a, baseballgm.model.GmStyle.REBUILDER), standings, a, pack, incoming = true)
        val winNow = ai.packageValue(withGm(withProspect, a, baseballgm.model.GmStyle.WIN_NOW), standings, a, pack, incoming = true)
        assertTrue(rebuilder > winNow, "리빌딩 $rebuilder ≤ 즉시 전력 $winNow")
    }

    @Test
    fun `연봉 보조는 받는 쪽 가치를 올리고 거래 뒤 연봉 총액에 반영된다`() {
        val plain = TradeProposal(proposer = a, partner = b, fromProposer = TradePackage(playerIds = listOf(star.id)))
        val retained = plain.copy(fromProposer = plain.fromProposer.copy(retained = mapOf(star.id to 1.5)))
        val before = ai.packageValue(league, standings, b, plain.fromProposer, incoming = true)
        val after = ai.packageValue(league, standings, b, retained.fromProposer, incoming = true)
        assertTrue(after > before)
        val retainProblems = rules.problems(league, retained, 10, false, emptyList())
        assertTrue(retainProblems.none { it.contains("연봉 보조") }, "$retainProblems")
        val tooMuch = plain.copy(fromProposer = plain.fromProposer.copy(retained = mapOf(star.id to star.contract.salary)))
        assertTrue(rules.problems(league, tooMuch, 10, false, emptyList()).any { it.contains("연봉 보조") })

        val payrollA = league.payrollOf(a)
        val payrollB = league.payrollOf(b)
        val traded = TradeExecutor.apply(league, retained, 10).league
        assertEquals(payrollA - star.contract.salary + 1.5, traded.payrollOf(a), 1e-6)
        assertEquals(payrollB + star.contract.salary - 1.5, traded.payrollOf(b), 1e-6)
    }

    @Test
    fun `핵심 선수를 달라면 같은 자리 다른 선수로 바꾼 역제안을 낸다`() {
        val proposal = swap("FILL", "STAR")
        assertFalse(ai.judge(league, standings, proposal, a, "normal").accepted)
        val counter = ai.counterOffer(league, standings, proposal, a, "normal", legal = { true }) { it.value }
        if (counter != null) {
            assertFalse(star.id in counter.proposal.fromPartner.playerIds, "핵심 선수가 역제안에 남았다")
            assertTrue(counter.notes.isNotEmpty())
            assertTrue(ai.judge(league, standings, counter.proposal, a, "normal").accepted, "역제안인데 자기가 거절한다")
        }
    }
}

private fun baseballgm.model.Player.movedForTest(teamId: TeamId): baseballgm.model.Player = when (this) {
    is baseballgm.model.Batter -> copy(teamId = teamId)
    is baseballgm.model.Pitcher -> copy(teamId = teamId)
}
