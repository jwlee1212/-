package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.scouting.ScoutingAccuracy
import baseballgm.scouting.ScoutingService
import baseballgm.season.Offseason
import baseballgm.season.SeasonState
import baseballgm.season.WeekLoop
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 스카우트·드래프트가 실제 리그 데이터 위에서 도는지 확인한다 (M6). */
class DraftIntegrationTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val strength = StrengthCalculator(balance)
    private val draftWeek = balance.int("season.draftWeek")
    private val rounds = balance.int("draft.rounds")

    /** 드래프트 주차까지만 돌린다 (한 시즌 전부를 돌릴 필요가 없다). */
    private fun playUntilDraftDone(seed: Long, target: baseballgm.league.League = league): SeasonState {
        val state = SeasonRunner(balance, target).newSeason()
        val loop = WeekLoop(balance, target)
        val random = Random(seed)
        while (state.week <= draftWeek) loop.playWeek(state, random, validate = false)
        return state
    }

    /**
     * 바깥에서는 잠재력의 진짜 값을 읽을 수 없다 (불변 원칙 4 — 컴파일 단계에서 막힌다).
     * 그래서 "정확" 정확도로 본 등급과 현재 능력치로 진짜 상위 유망주를 추린다.
     */
    private fun topProspectsByGrade(count: Int): Set<baseballgm.model.PlayerId> =
        league.draftPool.prospects
            .map { it to baseballgm.scouting.ScoutingView.of(it.player, ScoutingAccuracy.OWN_TEAM, league.season, baseballgm.scouting.PotentialScale.from(balance)) }
            .sortedWith(
                compareByDescending<Pair<baseballgm.market.DraftProspect, baseballgm.scouting.ScoutedPlayer>> {
                    it.second.potentialHigh.ordinal
                }.thenByDescending { it.second.overall.center },
            )
            .take(count)
            .map { it.first.id }
            .toSet()

    @Test
    fun `리그 데이터에 드래프트 풀과 지명권이 들어 있다`() {
        assertEquals(balance.int("draft.poolSize"), league.draftPool.prospects.size)
        assertEquals(league.season, league.draftPool.season)
        assertTrue(league.draftPool.highSchoolCount > 0 && league.draftPool.collegeCount > 0)
        assertTrue(league.draftPool.prospects.all { it.player.teamId == null }, "드래프트 풀 선수는 무소속이다")
        assertEquals(
            league.teams.size * rounds * balance.int("draft.tradeablePickYears"),
            league.draftRights.picks.size,
        )
    }

    @Test
    fun `시즌 중 트레이드로 넘긴 지명권은 새 주인이 쓴다`() {
        // 2026-10-04 버그: 드래프트가 개막 때 지명권 소유를 읽어서, 시즌 중 넘긴 1라운드 지명권을 원래 팀이 썼다
        val state = SeasonRunner(balance, league).newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(41L)
        loop.playWeek(state, random, validate = false)
        val (from, to) = league.teams[0].id to league.teams[1].id
        val pick = assertNotNull(state.draftRights.find(league.season, 1, from))
        baseballgm.season.TradeService(balance, strength).apply(
            state,
            baseballgm.market.TradeProposal(proposer = from, partner = to, fromProposer = baseballgm.market.TradePackage(picks = listOf(pick))),
        )
        while (state.week <= draftWeek) loop.playWeek(state, random, validate = false)

        val result = assertNotNull(state.draftResult)
        val firstRound = result.selections.filter { it.round == 1 }
        assertTrue(firstRound.none { it.teamId == from }, "지명권을 넘긴 팀이 1라운드에 지명했다")
        assertEquals(2, firstRound.count { it.teamId == to }, "받은 팀이 1라운드에 두 번 지명하지 않았다")
    }

    @Test
    fun `드래프트 주차에 지명이 끝난다`() {
        val state = playUntilDraftDone(31L)
        val result = assertNotNull(state.draftResult, "드래프트 결과가 없다")

        assertEquals(league.teams.size * rounds, result.selections.size)
        assertEquals(result.selections.size, result.selections.map { it.playerId }.toSet().size, "같은 선수가 두 번 지명됐다")
        // 시즌 중 AI 끼리 지명권을 주고받을 수 있다 — 구단별 지명 수는 그 시점 지명권 수와 같아야 한다 (2026-10-05 견고하게)
        league.teams.forEach { team ->
            val owned = state.draftRights.ofOwner(team.id).count { it.season == league.season }
            assertEquals(owned, result.selections.count { it.teamId == team.id }, "${team.id} 지명 수가 지명권 수와 다르다")
        }
        assertTrue(result.undrafted.isNotEmpty(), "미지명 선수가 없으면 육성선수 계약이 불가능하다")
    }

    @Test
    fun `꼴찌가 1순위를 받는다`() {
        val state = playUntilDraftDone(32L)
        val firstPick = state.draftResult!!.selections.first()
        val worstLastSeason = league.teams.minBy { it.draftPick }
        assertEquals(worstLastSeason.id, firstPick.originalTeam, "전년도 최하위가 1순위가 아니다")
        assertEquals(1, firstPick.overallPick)
    }

    @Test
    fun `트레이드된 지명권은 받은 팀이 행사한다`() {
        val giver = league.teams.first().id
        val taker = league.teams.last().id
        val original = league.draftRights.find(league.season, 1, giver)!!
        val traded = league.copy(draftRights = league.draftRights.transferred(original, taker))

        val state = playUntilDraftDone(7L, traded)
        val result = assertNotNull(state.draftResult)
        val firstRound = result.selections.first { it.round == 1 && it.originalTeam == giver }
        // 넘긴 지명권은 그 시점 주인이 행사한다 (시즌 중 AI 트레이드로 또 옮겨 갔을 수도 있다)
        val owner = state.draftRights.find(league.season, 1, giver)!!.ownerTeam
        assertEquals(owner, firstRound.teamId)
        assertTrue(owner != giver, "넘긴 1라운드 지명권을 원래 팀이 썼다")
        listOf(taker, giver).forEach { team ->
            assertEquals(state.draftRights.ofOwner(team).count { it.season == league.season }, result.selections.count { it.teamId == team })
        }
    }

    @Test
    fun `집중 관찰을 이어 가면 리포트 범위가 좁아진다`() {
        val state = playUntilDraftDone(33L)
        val service = ScoutingService(balance)
        val team = league.teams.first().id
        val department = state.scoutingOf(team)

        // 드래프트가 끝나면 슬롯은 비지만 관찰 주차 기록은 남는다 — 이력으로 찾는다
        assertTrue(department.activeFocus().none { league.draftPool.byId(it) != null }, "드래프트가 끝났는데 풀 선수가 슬롯에 남았다")
        val watched = league.draftPool.prospects.maxByOrNull { department.weeksOn(it.id) }
            ?.takeIf { department.weeksOn(it.id) > 0 }
        assertNotNull(watched, "AI 구단이 집중 관찰을 하지 않았다")
        val ignored = league.draftPool.prospects.first { department.weeksOn(it.id) == 0 }

        val watchedWidth = service.precisionFor(department, watched.player, team).halfWidth
        val ignoredWidth = service.precisionFor(department, ignored.player, team).halfWidth
        assertTrue(department.weeksOn(watched.id) >= 10, "관찰 주차가 ${department.weeksOn(watched.id)}주뿐이다")
        assertTrue(watchedWidth < ignoredWidth, "집중 관찰($watchedWidth)이 미관찰($ignoredWidth)보다 정확하지 않다")
    }

    @Test
    fun `스카우트 소식이 알림함에 올라온다`() {
        val state = playUntilDraftDone(34L)
        val team = league.teams.first().id
        val news = state.inbox.all().filter {
            it.teamId == team && it.category == baseballgm.season.InboxCategory.SCOUTING
        }
        assertTrue(news.isNotEmpty(), "스카우트 소식이 하나도 없다")
        val picks = state.inbox.all().filter { it.category == baseballgm.season.InboxCategory.DRAFT }
        assertEquals(league.teams.size * rounds, picks.size)
    }

    @Test
    fun `AI 편향 때문에 상위 유망주가 뒤 순번까지 남는다`() {
        val state = playUntilDraftDone(35L)
        val result = state.draftResult!!
        val trueTop = topProspectsByGrade(league.teams.size)

        val pickedLate = result.selections.filter { it.playerId in trueTop && it.round > 1 }
        assertTrue(
            pickedLate.isNotEmpty(),
            "잠재력 상위 10명이 모두 1라운드에 뽑혔다 — AI 가 진짜 값을 보고 있는 것 아닌가",
        )
    }

    @Test
    fun `지명 선수는 다음 시즌 2군으로 입단하고 계약금이 운용 자금에서 나간다`() {
        val result = SeasonRunner(balance, league).playSeason(36L)
        val fundsBefore = league.teams.associate { it.id to it.operatingFunds }
        // FA 시장이 돌면 보상금·계약금이 섞이므로, 여기서는 드래프트 계약금만 보려고 꺼 둔다
        val (next, report) = Offseason(balance, strength).run(
            state = result.state,
            random = Random(36L),
            rookieSupplier = RookieFactory(balance, strength, league),
            autoFreeAgency = false,
        )

        assertEquals(league.teams.size * rounds, report.drafted.size)
        report.drafted.forEach { id ->
            val player = next.player(id)
            assertEquals(RosterLevel.FUTURES, player.rosterLevel, "${player.name} 이 1군에서 시작한다")
            assertEquals(next.season, player.debutSeason)
            assertNotNull(player.teamId)
            assertEquals(balance.double("minimumSalary.value"), player.contract.salary, 0.001)
        }

        // 계약금은 재정 결산의 지출에 들어가고, 결산 결과가 다음 시즌 운용 자금이 된다 (docs/13)
        val spender: TeamId = result.state.draftResult!!.selections.first().teamId
        val bonus = result.state.draftResult!!.selections.filter { it.teamId == spender }.sumOf { it.signingBonus }
        assertTrue(bonus > 0.0)

        val finance = assertNotNull(report.review?.of(spender)?.finance, "재정 결산이 없다")
        assertEquals(bonus, finance.expenses.signingBonus, 0.01, "계약금이 지출에 안 들어갔다")
        assertEquals(fundsBefore.getValue(spender), finance.fundsBefore, 0.01)
        assertEquals(finance.fundsAfter, next.team(spender).operatingFunds, 0.01)
    }

    @Test
    fun `같은 시드면 같은 드래프트 결과가 나온다`() {
        val first = playUntilDraftDone(37L).draftResult!!
        val second = playUntilDraftDone(37L).draftResult!!
        assertEquals(first.selections, second.selections)
    }
}
