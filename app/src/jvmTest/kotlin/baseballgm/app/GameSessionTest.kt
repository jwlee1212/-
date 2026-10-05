package baseballgm.app

import baseballgm.io.LeagueLoader
import baseballgm.model.ManagerTendencies
import baseballgm.model.RosterLevel
import baseballgm.tactics.WeeklyPolicy
import baseballgm.text.CommentaryRenderer
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 화면이 쓰는 경로를 화면 없이 검사한다.
 *
 * Compose 화면 자체는 사람이 눈으로 보는 게 빠르지만, **화면이 부르는 함수가 터지지 않는지**는
 * 테스트로 잡을 수 있다. 특히 정보 은닉(타 팀 선수는 범위로만)이 지켜지는지를 여기서 확인한다.
 */
class GameSessionTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val userTeam = league.teams.first { it.id.value == "SWR" }.id

    private fun session() = GameSession(
        balance,
        league,
        userTeam,
        rookieSupplier = { current ->
            baseballgm.tools.RookieFactory(balance, baseballgm.league.StrengthCalculator(balance), current)
        },
        prospectSupplier = { current ->
            baseballgm.tools.ProspectFactory(
                balance = balance,
                strength = baseballgm.league.StrengthCalculator(balance),
                seed = 4242L,
                startingIdNumber = baseballgm.tools.nextPlayerIdNumber(current.players, current.draftPool.prospects),
            )
        },
        foreignSupplier = { current ->
            baseballgm.tools.ForeignFactory(
                balance = balance,
                strength = baseballgm.league.StrengthCalculator(balance),
                seed = 909L,
                startingIdNumber = baseballgm.tools.nextPlayerIdNumber(current.players, current.draftPool.prospects) +
                    current.draftPool.prospects.size,
            )
        },
        seed = 4242L,
    )

    @Test
    fun `주 진행과 화면 조회가 끝까지 돈다`() {
        val session = session()
        repeat(4) { session.advanceWeek(delegate = true) }

        assertEquals(5, session.week)
        assertTrue(session.record().games > 0)
        assertTrue(session.rank() in 1..10)
        assertTrue(session.roster(RosterLevel.FIRST_TEAM).isNotEmpty())
        assertTrue(session.roster(RosterLevel.FUTURES).isNotEmpty())
        assertTrue(session.teamsRanked().size == league.teams.size)
        assertTrue(session.lastReport != null)
        assertTrue(session.weekSchedule().isNotEmpty())
        assertTrue(session.calendarLabel().isNotBlank())
    }

    @Test
    fun `기록실 순위가 나온다`() {
        val session = session()
        repeat(6) { session.advanceWeek(delegate = true) }
        assertTrue(session.battingLeaders().isNotEmpty(), "타율 순위가 비었다")
        assertTrue(session.homeRunLeaders().isNotEmpty())
        assertTrue(session.eraLeaders().isNotEmpty(), "평균자책 순위가 비었다")
        assertTrue(session.winLeaders().isNotEmpty())
        // 화면은 선수의 소속 구단 이름을 찾는다 — 무소속이면 터진다
        session.battingLeaders().forEach { (player, _) ->
            assertTrue(player.teamId != null, "${player.name} 의 소속이 없다")
        }
    }

    @Test
    fun `우리 팀은 정확한 값 타 팀은 범위로 본다`() {
        val session = session()
        val own = session.roster(RosterLevel.FIRST_TEAM).first()
        val other = league.players.first { it.teamId != userTeam }

        assertTrue(session.accuracyFor(own).isExact, "우리 팀 선수인데 범위로 본다")
        val ownView = session.scout(own)
        assertTrue(ownView.ratings.values.all { it.isExact }, "우리 팀 선수인데 범위로 나온다")

        val otherView = session.scout(other)
        assertTrue(otherView.ratings.values.none { it.isExact }, "타 팀 선수인데 정확한 값이 나온다")
        assertTrue(otherView.potentialLabel.isNotBlank())
    }

    @Test
    fun `관전용 경기와 중계 문장이 만들어진다`() {
        val session = session()
        session.advanceWeek(delegate = true)
        val watched = session.lastReport!!.watched
        assertEquals(6, watched.size, "한 주는 6경기다")
        assertTrue(watched.all { it.box.home.teamId == userTeam || it.box.away.teamId == userTeam })

        val renderer = CommentaryRenderer { session.player(it).registeredName }
        val lines = renderer.render(watched.first().events)
        assertTrue(lines.size > 50, "중계 문장이 ${lines.size}줄뿐이다")
        assertTrue(lines.first().contains("경기 시작"))
        assertTrue(lines.last().contains("경기 종료"))
    }

    @Test
    fun `주간 방침과 단장 방침이 규칙표에 반영된다`() {
        val session = session()
        val before = session.sheet().starterHook.pitchLimit

        session.setPolicy(WeeklyPolicy.ALL_OUT)
        assertTrue(session.sheet().starterHook.pitchLimit > before, "총력전인데 투구수 한계가 그대로다")

        session.setPolicy(WeeklyPolicy.PROTECT)
        assertTrue(session.sheet().starterHook.pitchLimit < before, "선수 보호인데 한계가 그대로다")

        // 단장 방침: 번트를 많이 대라고 지시하면 감독 성향이 매주 조금씩 끌려온다
        session.setPolicy(WeeklyPolicy.NORMAL)
        val startBunt = session.tendencies().buntPreference
        session.setDirection(session.tendencies().copy(buntPreference = 100))
        repeat(3) { session.advanceWeek(delegate = true) }
        val afterBunt = session.tendencies().buntPreference
        assertTrue(afterBunt > startBunt, "방침을 줬는데 성향이 안 움직였다 ($startBunt → $afterBunt)")
        assertTrue(afterBunt < 100, "한 번에 목표값까지 가면 안 된다 (반영률)")
    }

    @Test
    fun `자동 진행은 드래프트 주차에서 멈춘다`() {
        val session = session()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)

        assertTrue(!session.seasonOver, "드래프트를 건너뛰고 시즌이 끝났다")
        assertEquals(balance.int("season.draftWeek"), session.week)
        assertTrue(session.isDraftWeek && !session.draftDone)

        // 내 차례까지 AI 가 지명하고 멈춘다
        assertTrue(!session.advanceDraft(), "우리 차례에서 멈추지 않았다")
        assertEquals(userTeam, session.draftSlot()!!.ownerTeam)
        val best = session.draftBoard(limit = 1).first()
        session.draftPlayer(best)
        assertTrue(session.myDraftPicks().any { it.playerId == best.id })

        session.autoDraft()
        assertTrue(session.draftDone)
        assertEquals(balance.int("draft.rounds"), session.myDraftPicks().size)
    }

    @Test
    fun `시즌 끝까지 자동 진행할 수 있다`() {
        val session = session()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.autoDraft()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        assertTrue(session.seasonOver)
        assertEquals(balance.int("schedule.gamesPerTeam"), session.record().games)
        assertTrue(session.battingLeaders().isNotEmpty())
    }

    @Test
    fun `단장 방침 없이도 감독 성향은 그대로 유지된다`() {
        val session = session()
        val before = session.tendencies()
        repeat(3) { session.advanceWeek(delegate = true) }
        assertEquals(before, session.tendencies(), "방침을 안 줬는데 성향이 변했다")
    }

    @Test
    fun `시즌이 끝나면 스토브리그를 거쳐 다음 시즌으로 간다`() {
        val session = session()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.autoDraft()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        assertTrue(session.draftDone, "드래프트가 열리지 않았다")
        val seasonBefore = session.league.season
        val playersBefore = session.league.players.size

        session.startNextSeason()
        assertTrue(session.inFreeAgency, "스토브리그가 FA 시장 앞에서 멈추지 않았다")
        assertTrue(session.faAgents().isNotEmpty(), "FA 가 한 명도 안 나왔다")
        session.skipFreeAgency()
        assertTrue(!session.inFreeAgency)

        assertEquals(seasonBefore + 1, session.league.season)
        assertEquals(1, session.week)
        assertTrue(!session.seasonOver)
        assertEquals(0, session.record().games, "새 시즌 성적은 0부터 시작한다")
        val offseason = session.lastOffseason!!
        assertTrue(offseason.retired.isNotEmpty(), "은퇴한 선수가 없다")
        assertTrue(offseason.drafted.isNotEmpty(), "드래프트 입단 선수가 없다")
        assertTrue(session.league.players.size in (playersBefore - 30)..(playersBefore + 30))

        // 새 시즌도 정상적으로 굴러간다
        session.advanceWeek(delegate = true)
        assertTrue(session.record().games > 0)
    }

    @Test
    fun `부상 선수도 화면에서 볼 수 있다`() {
        val session = session()
        repeat(8) { session.advanceWeek(delegate = true) }
        val injured = session.state.allPlayers().filter { it.condition.isInjured }
        assertTrue(injured.isNotEmpty(), "8주 동안 부상자가 한 명도 없다")
        injured.take(5).forEach { player ->
            val view = session.scout(player)
            assertTrue(view.name.isNotBlank())
            assertTrue(session.formLabel(player).isNotBlank())
        }
    }

    // ---------- M7 ----------

    @Test
    fun `세이버 지표와 WAR 이 화면 경로로 나온다`() {
        val session = session()
        repeat(6) { session.advanceWeek(delegate = true) }

        val constants = session.leagueConstants()
        assertTrue(constants.runsPerWin > 0.0)

        val batters = session.warLeaders(limit = 5, pitchers = false)
        val pitchers = session.warLeaders(limit = 5, pitchers = true)
        assertTrue(batters.isNotEmpty() && pitchers.isNotEmpty())
        assertTrue(batters.first().second.war >= batters.last().second.war, "WAR 순 정렬이 아니다")

        val batter = batters.first().first
        val metrics = session.batterMetrics(batter)
        assertTrue(metrics.wrcPlus > 0.0, "wRC+ 가 ${metrics.wrcPlus}")
        assertTrue(session.pitcherMetrics(pitchers.first().first).fip > 0.0)
    }

    @Test
    fun `트레이드는 미리 보기와 제안이 나뉜다`() {
        val session = session()
        repeat(4) { session.advanceWeek(delegate = true) }

        val partner = league.teams.first { it.id != userTeam }.id
        val mine = session.roster(RosterLevel.FIRST_TEAM).last()
        // 능력치 1위가 아니라 "상대가 내주기 가장 아까운 선수"를 고른다 —
        // 나이 많고 비싼 선수는 AI 가 오히려 내보내고 싶어 하기 때문이다 (악성 계약)
        val theirs = session.state.playersOf(partner).maxByOrNull { player ->
            session.previewTrade(session.buildProposal(partner, emptyList(), listOf(player.id))).outgoingValue
        }!!

        val greedy = session.buildProposal(partner, listOf(mine.id), listOf(theirs.id))
        val preview = session.previewTrade(greedy)
        assertTrue(!preview.accepted, "잡선수로 주전을 데려왔다")
        assertTrue(preview.requiredValue > preview.incomingValue)

        // 미리 보기는 협상 피로도를 쌓지 않는다
        repeat(5) { session.previewTrade(greedy) }
        assertTrue(!session.previewTrade(greedy).reason.contains("협상하지 않겠다"))
    }

    @Test
    fun `연봉 계획은 지금 연봉 총액·운용 자금에서 시작하고 FA 조건을 넣으면 그만큼 캡 여유가 준다`() {
        val session = session()
        repeat(3) { session.advanceWeek(delegate = true) }
        val inSeason = session.financialPlan()
        assertEquals(session.league.season, inSeason.years.first().season)
        assertEquals(session.state.currentLeague().payrollOf(userTeam), inSeason.years.first().payroll, 0.05)
        assertEquals(session.state.funds.getValue(userTeam), session.currentFunds(), 1e-9)
        assertEquals(session.currentFunds(), inSeason.years.first().fundsStart, 0.01)

        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.autoDraft()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.startNextSeason()
        assertTrue(session.inFreeAgency)

        // FA 시장 중에는 다음 시즌부터 센다
        val base = session.financialPlan()
        assertEquals(session.league.season + 1, base.years.first().season)
        val target = session.faAgents().maxByOrNull { it.askingSalary }!!
        session.submitFaOffer(target, salary = 10.0, years = 2, signingBonus = 3.0)
        val withOffer = session.financialPlan(session.faOffersChange())
        assertEquals(base.years[0].capRoom - 10.0, withOffer.years[0].capRoom, 0.01)
        assertEquals(base.years[1].capRoom - 10.0, withOffer.years[1].capRoom, 0.01)
        assertEquals(base.years[0].fundsStart - 3.0, withOffer.years[0].fundsStart, 0.01)
        // 조건 창 미리 보기: 이 선수 조건만 갈아 끼운다
        val preview = session.financialPlan(
            session.faOffersChange(target.playerId, baseballgm.market.ContractOffer(userTeam, target.playerId, 4.0, 1, 0.0)),
        )
        assertEquals(base.years[0].capRoom - 4.0, preview.years[0].capRoom, 0.01)
        assertEquals(base.years[1].capRoom, preview.years[1].capRoom, 0.01)
    }

    @Test
    fun `FA 시장에서 직접 제안해 계약할 수 있다`() {
        val session = session()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.autoDraft()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.startNextSeason()

        assertTrue(session.inFreeAgency)
        val target = session.faAgents().maxByOrNull { it.askingSalary }!!
        // 희망 조건보다 후하게 부른다 — 다른 구단이 붙어도 이기도록
        session.submitFaOffer(target, salary = target.askingSalary * 2.0, years = target.askingYears)
        assertEquals(target.askingSalary * 2.0, session.faOffer(target)!!.salary, 0.001)
        assertTrue(session.faRumor(target).isNotBlank())
        // 협상 현황: 우리가 낸 선수는 현황 목록에 뜨고, 상태가 정해져 있다
        val negotiation = assertNotNull(session.faNegotiation(target))
        assertTrue(negotiation.standing != baseballgm.market.FaStanding.NO_OFFER)
        assertTrue(session.faMyNegotiations().any { it.first.playerId == target.playerId })

        session.skipFreeAgency()
        assertTrue(!session.inFreeAgency, "FA 시장이 닫히지 않았다")
        val signed = session.lastOffseason!!.faSignings.firstOrNull { it.playerId == target.playerId }
        assertTrue(signed != null, "후하게 불렀는데 계약이 안 됐다")
        assertEquals(userTeam, signed.offer.teamId, "우리가 데려오지 못했다")
        assertEquals(userTeam, session.player(target.playerId).teamId)
    }

    // ---------- M8 ----------

    @Test
    fun `외국인을 교체하고 다음 주를 진행해도 오류가 없다`() {
        val session = session()
        repeat(3) { session.advanceWeek(delegate = true) }

        // 1군 주전 외국인을 내보내야 라인업·로테이션에 남은 흔적이 드러난다
        val outgoing = session.foreigners().maxByOrNull { it.contract.salary }!!
        val candidate = session.foreignCandidates().first { it.isPitcher == (outgoing is baseballgm.model.Pitcher) }
        assertEquals(emptyList(), session.replaceForeign(outgoing, candidate))

        repeat(3) { session.advanceWeek(delegate = true) }
        session.advanceWeek(delegate = false)

        // 떠난 선수의 기록은 남는다 — 기록실·박스스코어가 이름을 찾다가 터지면 안 된다
        session.battingLeaders(500)
        session.homeRunLeaders(500)
        session.eraLeaders(500)
        session.winLeaders(500)
        val departed = session.player(outgoing.id)
        assertEquals(outgoing.registeredName, departed.registeredName)
        assertEquals(null, departed.teamId, "떠난 선수가 아직 팀에 묶여 있다")
        assertTrue(session.foreigners().none { it.id == outgoing.id })
    }

    @Test
    fun `외국인 시장과 적응 상태를 화면에서 볼 수 있다`() {
        val session = session()
        repeat(3) { session.advanceWeek(delegate = true) }

        val mine = session.foreigners()
        assertEquals(balance.int("foreignPlayers.maxPerTeam"), mine.size)
        mine.forEach { player ->
            assertTrue(session.adaptationLabel(player) != null, "${player.registeredName} 적응 문구가 없다")
            assertTrue(session.militaryLabel(player).isNotBlank())
        }

        val candidates = session.foreignCandidates()
        assertTrue(candidates.isNotEmpty())
        val candidate = candidates.first()
        assertTrue(session.convertedLine(candidate).text().isNotBlank(), "환산 기록이 비어 있다")
        assertTrue(session.foreignReport(candidate).scouted.ratings.values.none { it.isExact }, "외국인 후보가 정확히 보인다")

        // 집중 관찰 슬롯을 외국인에게도 쓸 수 있다 (docs/12)
        assertTrue(session.toggleFocus(candidate))
        assertTrue(session.isFocused(candidate))
    }

    @Test
    fun `시즌 중 외국인을 교체할 수 있다`() {
        val session = session()
        repeat(5) { session.advanceWeek(delegate = true) }

        val outgoing = session.foreigners().first()
        val candidate = session.foreignCandidates(pitchersOnly = outgoing is baseballgm.model.Pitcher).first()
        val before = session.foreignReplacementsLeft()
        assertTrue(session.foreignReplacementOpen)

        val problems = session.replaceForeign(outgoing, candidate)
        assertTrue(problems.isEmpty(), problems.toString())
        assertEquals(before - 1, session.foreignReplacementsLeft())
        assertTrue(session.foreigners().any { it.id == candidate.id }, "새 외국인이 안 들어왔다")
        assertTrue(session.foreigners().none { it.id == outgoing.id }, "내보낸 선수가 남아 있다")
    }

    @Test
    fun `국제대회 결과를 화면에서 볼 수 있다`() {
        val session = session()
        val startWeek = balance.int("internationalTournament.asianGames.startWeek")
        session.advanceUntil(startWeek, delegate = true)

        val result = session.tournamentResult()
        assertTrue(result != null, "${startWeek}주차가 지났는데 대회 결과가 없다")
        assertTrue(result.squad.isNotEmpty())
        assertTrue(result.medal.label.isNotBlank())
        // 차출된 우리 팀 선수는 결장 표시가 된다
        val mine = result.squad.filter { it.teamId == userTeam }
        mine.forEach { member ->
            assertTrue(session.isOnInternationalDuty(session.player(member.playerId)), "차출 표시가 없다")
        }
    }

    // ---------- M9 ----------

    @Test
    fun `포스트시즌을 치르고 우승팀이 나온다`() {
        val session = session()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.autoDraft()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        assertTrue(session.seasonOver)
        assertTrue(!session.postseasonDone)

        session.runPostseason()
        val result = session.postseason()
        assertTrue(result != null, "포스트시즌 결과가 없다")
        assertEquals(4, result.series.size)
        assertTrue(session.postseasonGames.isNotEmpty(), "경기 기록이 없다")
        assertTrue(result.series.last().round.label.contains("한국시리즈"))
    }

    @Test
    fun `구단 화면에서 재정과 구단주를 볼 수 있다`() {
        val session = session()
        repeat(6) { session.advanceWeek(delegate = true) }

        val finance = session.projectedFinance()
        assertTrue(finance.revenue.total > 0.0)
        assertTrue(finance.expenses.payroll > 0.0)
        assertTrue(finance.attendanceRate > 0.0)
        assertTrue(session.allowedDeficit() > 0.0)

        assertTrue(session.fanSupport() in 5..98)
        assertTrue(session.fanLabel().isNotBlank())
        assertTrue(session.ownerTrust() in 0..100)
        assertTrue(session.ownerTrustLabel().isNotBlank())
        assertTrue(session.seasonGoal().description.isNotBlank())
        assertTrue(session.expectedWins() > 0.0)
        assertTrue(session.staffSalary() > 0.0)
        assertTrue(session.coaches().isNotEmpty() && session.medicalStaff().isNotEmpty())
    }

    @Test
    fun `커리어 기록과 업적이 화면 경로로 나온다`() {
        val session = session()
        assertTrue(session.career() != null, "커리어가 시작되지 않았다")
        assertTrue(session.achievements().isNotEmpty())
        assertTrue(session.unlockedAchievements().isEmpty(), "시작부터 업적이 있다")
        assertTrue(session.hallOfFameGrade().isNotBlank())

        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.autoDraft()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.startNextSeason()
        session.skipFreeAgency()

        val career = session.career()!!
        assertEquals(1, career.seasons.size, "커리어에 시즌이 안 쌓였다")
        assertTrue(career.seasons.first().line().isNotBlank())
        assertTrue(session.finance() != null, "지난 시즌 결산이 없다")
    }

    @Test
    fun `드래프트 생중계로 한 장씩 넘겨도 한 번에 넘긴 것과 결과가 같다`() {
        val live = session()
        val batch = session()
        val draftWeek = balance.int("season.draftWeek")
        live.advanceUntil(draftWeek, delegate = true)
        batch.advanceUntil(draftWeek, delegate = true)

        live.openDraft()
        assertTrue(live.draftTotalPicks > 0, "순번표가 열리지 않았다")
        assertTrue(live.draftSelections().isEmpty(), "순번표만 열었는데 지명이 나왔다")
        var steps = 0
        while (live.advanceDraftPick() != null) steps++
        assertTrue(live.isMyDraftTurn, "생중계가 우리 차례에서 멈추지 않았다")
        assertEquals(steps, live.draftSelections().size)

        batch.advanceDraft()
        assertEquals(batch.draftSelections(), live.draftSelections(), "넘기는 방식에 따라 지명 결과가 달라졌다")

        // 우리 차례에는 한 장 넘기기가 아무것도 하지 않는다
        assertEquals(null, live.advanceDraftPick())
        assertEquals(steps, live.draftSelections().size)
    }

    @Test
    fun `집중 관찰을 바꾸면 그 값을 읽은 화면이 다시 그려진다`() {
        // 화면 맨 위에서만 revision 을 읽으면 하위 composable 이 건너뛰어져 값이 안 바뀌던 문제 (strong skipping).
        // 조회 함수가 읽는 스냅샷 상태에 revision 이 들어 있어야, 조회한 composable 이 스스로 다시 그려진다.
        val session = session()
        val prospect = session.draftBoard(limit = 1).first()

        val reads = mutableSetOf<Any>()
        androidx.compose.runtime.snapshots.Snapshot.observe(readObserver = { reads += it }) {
            session.isFocused(prospect)
            session.focusUsed
        }
        var changed: Set<Any> = emptySet()
        val handle = androidx.compose.runtime.snapshots.Snapshot.registerApplyObserver { objects, _ -> changed = objects }
        try {
            session.toggleFocus(prospect)
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }
        assertTrue(session.isFocused(prospect))
        assertTrue(changed.any { it in reads }, "관찰 상태를 읽은 쪽이 변경 알림을 받지 못한다")
    }

    @Test
    fun `집중 관찰 슬롯은 투자 단계와 상관없이 15개다`() {
        val session = session()
        assertEquals(balance.int("scouting.defaultLevel"), session.scoutingLevel)
        assertEquals(15, session.focusSlots)
        (1..5).forEach { level ->
            session.setScoutingLevel(level)
            assertEquals(15, session.focusSlots, "${level}단계 슬롯")
        }
        session.draftBoard(limit = 20).forEach { session.toggleFocus(it) }
        assertEquals(15, session.focusUsed, "슬롯보다 많이 붙었다")
    }

    @Test
    fun `필터는 스카우트 시선으로 포지션 잠재력 종합 계약 연봉을 거른다`() {
        val session = session()
        val players = session.roster(RosterLevel.FIRST_TEAM)
        val pitchersOnly = baseballgm.app.ui.PlayerFilter(position = baseballgm.app.ui.PositionFilter.SP)
        val starters = players.filter { pitchersOnly.matches(session.scout(it)) }
        assertTrue(starters.isNotEmpty())
        assertTrue(starters.all { session.positionLabel(it) == "SP" })

        val strong = baseballgm.app.ui.PlayerFilter(overall = baseballgm.app.ui.OverallFilter.OVER60)
        assertTrue(players.filter { strong.matches(session.scout(it)) }.all { session.scout(it).overall.center >= 60 })

        val rich = baseballgm.app.ui.PlayerFilter(salary = baseballgm.app.ui.SalaryFilter.OVER10)
        players.filter { rich.matches(session.scout(it), it.contract.salary, it.contract.yearsRemaining) }
            .forEach { assertTrue(it.contract.salary >= 10.0) }
        // 연봉을 모르는 목록(드래프트 풀)은 연봉 필터에 걸리지 않는다
        val prospect = session.draftBoard(limit = 1).first()
        assertTrue(!rich.matches(session.prospectReport(prospect).scouted))

        val expiring = baseballgm.app.ui.PlayerFilter(contract = baseballgm.app.ui.ContractFilter.EXPIRING)
        players.filter { expiring.matches(session.scout(it), it.contract.salary, it.contract.yearsRemaining) }
            .forEach { assertTrue(it.contract.yearsRemaining <= 1) }

        val potentialA = baseballgm.app.ui.PlayerFilter(potential = baseballgm.app.ui.PotentialFilter.A)
        session.draftBoard(limit = Int.MAX_VALUE).map { session.prospectReport(it).scouted }
            .filter { potentialA.matches(it) }
            .forEach { assertTrue(it.potentialHigh >= baseballgm.scouting.PotentialGrade.A) }
    }

    @Test
    fun `관찰하던 선수가 지명되면 슬롯이 바로 비고 관찰 이력은 남는다`() {
        val session = session()
        session.autoFocus = false // 자동 관찰(2026-10-03)이 빈 슬롯을 채우면 셈이 달라진다
        val watched = session.draftBoard(limit = 15)
        watched.forEach { session.toggleFocus(it) }
        session.advanceUntil(balance.int("season.draftWeek"), delegate = true)
        val weeksBefore = watched.associate { it.id to session.focusWeeks(it) }

        session.openDraft()
        while (session.advanceDraftPick() != null) Unit
        val taken = watched.filter { session.selectionOf(it) != null }
        assertTrue(taken.isNotEmpty(), "상위 15명 중 우리 차례 전에 뽑힌 선수가 없다")
        assertEquals(watched.size - taken.size, session.focusUsed, "지명된 선수가 슬롯을 차지한다")
        taken.forEach {
            assertTrue(!session.isFocused(it))
            assertEquals(weeksBefore.getValue(it.id), session.focusWeeks(it), "슬롯에서 빠지며 관찰 주차가 사라졌다")
            assertTrue(!session.toggleFocus(it), "이미 지명된 선수에 관찰이 붙었다")
        }
    }

    @Test
    fun `로스터 전력 요약 - 우리 팀은 정확한 값, 타 팀은 범위, 순위는 범위`() {
        val session = session()
        val current = session.currentStrength()
        val best = session.bestStrength()
        assertTrue(current.overall.isExact, "우리 팀 전력은 정확히 보여야 한다")
        assertTrue(best.overall.center >= current.overall.center - 1e-9, "베스트 전력이 현재보다 낮다")

        val board = session.powerBoard()
        assertEquals(10, board.rows.size)
        board.rows.filterNot { it.isViewer }.forEach {
            assertTrue(!it.strength.overall.isExact, "${it.teamId} 전력이 정확한 값으로 새어 나왔다")
        }
        assertEquals(current.overall, board.rows.single { it.isViewer }.strength.overall)
        val rank = board.viewerRank!!
        assertTrue(rank.first in 1..10 && rank.last in rank.first..10, "추정 순위 범위가 이상하다: $rank")
        // 범위가 리그 전체를 덮으면 쓸모가 없다. 시작 리그에서 4칸 이하로 좁혀져야 한다 (spreadFactor 점검)
        assertTrue(rank.last - rank.first <= 4, "추정 순위 범위가 너무 넓다: $rank")
    }

    @Test
    fun `포지션 뎁스 - 1군 뛸 수 있는 선수가 맨 앞이다`() {
        val session = session()
        baseballgm.season.RosterSlot.entries.forEach { slot ->
            val depth = session.depthOf(slot)
            assertTrue(depth.all { baseballgm.season.RosterSlot.of(it) == slot })
            val firstTeamIndex = depth.indexOfLast { it.rosterLevel == RosterLevel.FIRST_TEAM }
            val futuresIndex = depth.indexOfFirst { it.rosterLevel == RosterLevel.FUTURES }
            if (firstTeamIndex >= 0 && futuresIndex >= 0) assertTrue(firstTeamIndex < futuresIndex, "$slot 뎁스에서 2군이 1군보다 앞에 있다")
        }
    }

    @Test
    fun `시즌 중 비FA 다년계약을 맺을 수 있고 FA 시장 중엔 막힌다`() {
        val session = session()
        val candidate = session.extensionCandidates().firstOrNull() ?: return
        val terms = session.extensionTerms(candidate.id)
        val result = session.proposeExtension(candidate.id, terms.demand, terms.years)
        assertTrue(result.accepted, result.message)
        val signed = session.player(candidate.id)
        assertEquals(baseballgm.model.ContractType.MULTI_YEAR, signed.contract.type)
        assertEquals(terms.demand, signed.contract.nextSalary)
        assertTrue(session.extensionBlockedReason(candidate.id) != null, "같은 시즌에 또 협상할 수 있다")
    }

    @Test
    fun `드래프트 직후 우리 신인은 현재 능력치가 정확히 보이고 다른 팀 신인은 범위다`() {
        val session = session()
        session.advanceUntil(balance.int("season.regularSeasonWeeks"), delegate = true)
        session.autoDraft()
        assertTrue(session.draftDone)

        val rookies = session.myDraftClass()
        assertTrue(rookies.isNotEmpty(), "우리 신인이 없다")
        rookies.forEach { (selection, report) ->
            assertTrue(report.scouted.isExactView, "${selection.playerName} 능력치가 정확하지 않다")
            assertTrue(report.scouted.ratings.values.all { it.isExact })
            // 지명 전 예상은 스카우트 범위 그대로 남는다
            assertTrue(!assertNotNull(session.preDraftReport(selection.playerId)).scouted.isExactView)
        }
        val others = session.draftSelections().filter { it.teamId != userTeam }.take(5)
        others.forEach { selection ->
            val prospect = assertNotNull(session.league.draftPool.byId(selection.playerId))
            assertTrue(!session.prospectReport(prospect).scouted.isExactView, "다른 팀 신인이 정확히 보인다")
        }
    }

    @Test
    fun `지명한 신인은 다음 시즌 합류하면 신인으로 표시되고 그 다음 해엔 아니다`() {
        val session = session()
        val weeks = balance.int("season.regularSeasonWeeks")
        session.advanceUntil(weeks, delegate = true)
        session.autoDraft()
        val drafted = session.myDraftClass().map { it.first.playerId }
        assertTrue(drafted.isNotEmpty())
        session.advanceUntil(weeks, delegate = true)
        session.startNextSeason()
        session.skipFreeAgency()

        val joined = drafted.mapNotNull { id -> session.state.allPlayers().firstOrNull { it.id == id && it.teamId == userTeam } }
        assertTrue(joined.isNotEmpty(), "지명 선수가 합류하지 않았다")
        joined.forEach { assertTrue(session.isRookie(it), "${it.registeredName} 신인 표시가 없다") }
        // 지난 시즌부터 뛴 국내 선수는 신인이 아니다
        val veteran = session.state.playersOf(userTeam).first { !it.isForeign && it.debutSeason < session.league.season }
        assertTrue(!session.isRookie(veteran))
    }

    @Test
    fun `스토브리그엔 시장에 나간 우리 FA 가 로스터에서 빠지고 FA 시장엔 우리 팀 출신으로 표시된다`() {
        val session = session()
        val weeks = balance.int("season.regularSeasonWeeks")
        session.advanceUntil(weeks, delegate = true)
        session.autoDraft()
        val drafted = session.myDraftClass().map { it.first.playerId }.toSet()
        session.advanceUntil(weeks, delegate = true)
        session.startNextSeason()
        assertTrue(session.inFreeAgency)

        val ours = session.faAgents().filter { session.isOurFormer(it) }
        val squad = (session.roster(baseballgm.model.RosterLevel.FIRST_TEAM) + session.roster(baseballgm.model.RosterLevel.FUTURES)).map { it.id }.toSet()
        assertTrue(ours.none { it.playerId in squad }, "시장에 나간 우리 FA 가 로스터에 남아 있다")
        assertTrue(drafted.all { it in squad }, "드래프트 신인이 스토브리그 로스터에 없다")
        assertTrue(session.faAgents().filter { it.previousTeam != userTeam }.none { session.isOurFormer(it) })
        // 신인 선수 상세도 열린다 (시즌 상태에 없는 선수)
        drafted.firstOrNull()?.let { assertEquals(it, session.player(it).id) }

        // 다시 잡으면 로스터로 돌아온다
        val target = ours.maxByOrNull { it.askingSalary } ?: return
        session.submitFaOffer(target, target.askingSalary * 4, target.askingYears)
        repeat(session.faRounds()) {
            if (session.inFreeAgency && session.faSignings().none { it.playerId == target.playerId }) session.advanceFaRound()
        }
        if (session.inFreeAgency) {
            val back = (session.roster(baseballgm.model.RosterLevel.FIRST_TEAM) + session.roster(baseballgm.model.RosterLevel.FUTURES)).map { it.id }
            assertTrue(target.playerId in back, "다시 잡은 FA 가 로스터에 없다")
        }
    }
}
