package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.ServiceKind
import baseballgm.season.ForeignService
import baseballgm.season.withCondition
import baseballgm.season.InboxCategory
import baseballgm.season.Offseason
import baseballgm.season.SeasonState
import baseballgm.season.WeekLoop
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 외국인·군 복무·국제대회가 실제 리그에서 도는지 확인한다 (M8). */
class ForeignMilitaryTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val strength = StrengthCalculator(balance)
    private val maxForeign = balance.int("foreignPlayers.maxPerTeam")

    private fun playWeeks(seed: Long, weeks: Int): SeasonState {
        val state = SeasonRunner(balance, league).newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(seed)
        repeat(weeks) { if (!state.isRegularSeasonOver) loop.playWeek(state, random, validate = false) }
        return state
    }

    private fun offseason(state: SeasonState, seed: Long) = Offseason(balance, strength).run(
        state = state,
        random = Random(seed),
        rookieSupplier = RookieFactory(balance, strength, league),
        prospectSupplier = ProspectFactory(
            balance, strength, seed, nextPlayerIdNumber(league.players, league.draftPool.prospects),
        ),
        foreignSupplier = ForeignFactory(
            balance, strength, seed, nextPlayerIdNumber(league.players, league.draftPool.prospects) + 500,
        ),
    )

    // ---------- 외국인 ----------

    @Test
    fun `리그 데이터에 외국인 시장이 들어 있다`() {
        assertEquals(balance.int("foreignPlayers.poolSizePerSeason"), league.foreignPool.candidates.size)
        assertTrue(league.foreignPool.candidates.all { it.player.teamId == null })
        assertTrue(league.foreignPool.candidates.all { it.player.origin == Origin.FOREIGN })
        assertTrue(league.foreignPool.candidates.all { it.askingSalary <= balance.double("foreignPlayers.newContractMaxTotal") })
        assertTrue(league.foreignPool.candidates.map { it.originLeague }.distinct().size >= 3, "출신 리그가 다양하지 않다")
    }

    @Test
    fun `모든 구단이 외국인 규정을 지킨다`() {
        league.teams.forEach { team ->
            val foreigners = league.foreignersOf(team.id)
            assertEquals(maxForeign, foreigners.size, "${team.id} 외국인 ${foreigners.size}명")
            val pitchers = foreigners.count { it is baseballgm.model.Pitcher }
            assertTrue(
                pitchers <= balance.int("foreignPlayers.maxSameType") &&
                    foreigners.size - pitchers <= balance.int("foreignPlayers.maxSameType"),
                "${team.id} 가 한쪽으로 쏠렸다 (투수 $pitchers)",
            )
        }
    }

    @Test
    fun `적응 감점이 붙고 시즌이 갈수록 줄어든다`() {
        val opening = playWeeks(51L, weeks = 2)
        val full = playWeeks(51L, weeks = 24)

        val early = opening.allPlayers().filter { it.isForeign && it.condition.adaptationPenalty > 0.0 }
        assertTrue(early.isNotEmpty(), "적응 감점을 받은 외국인이 하나도 없다")
        assertTrue(early.any { it.condition.adaptationPenalty > 5.0 }, "감점이 큰 선수가 없다")

        // 첫 시즌 선수는 시즌이 지나며 감점이 줄어든다
        val firstSeason = early.filter { it.debutSeason == league.season }
        firstSeason.forEach { player ->
            val late = full.player(player.id).condition.adaptationPenalty
            assertTrue(
                late < player.condition.adaptationPenalty,
                "${player.registeredName} 감점이 시즌 끝까지 그대로다 ($late)",
            )
        }
    }

    @Test
    fun `적응 감점은 실제 성적을 끌어내린다`() {
        // 같은 경기를 같은 시드로 두 번 돌린다. 다른 것은 **적응 감점 하나뿐**이라,
        // 차이가 나면 그것은 적응 때문이다
        val pitcher = league.players.filterIsInstance<baseballgm.model.Pitcher>()
            .first { it.isForeign && it.role == baseballgm.model.PitcherRole.STARTER }
        val teamId = pitcher.teamId!!
        val opponent = league.teams.first { it.id != teamId }.id

        fun leagueWith(penalty: Double) = league.copy(
            players = league.players.map {
                if (it.id == pitcher.id) it.withCondition(it.condition.copy(adaptationPenalty = penalty)) else it
            },
        )

        fun runsAllowed(penalty: Double): Int = (1L..24L).sumOf { seed ->
            val target = leagueWith(penalty)
            val runner = GameRunner(balance, target)
            val managerAI = baseballgm.tactics.ManagerAI(balance, strength)
            val sheet = managerAI.buildSheet(
                baseballgm.tactics.DirectivePreset.STANDARD.tendencies(),
                target.playersOf(teamId),
                target.season,
            )
            val awaySheet = managerAI.buildSheet(
                baseballgm.tactics.DirectivePreset.STANDARD.tendencies(),
                target.playersOf(opponent),
                target.season,
            )
            val game = runner.playWithSheets(
                homeId = teamId,
                awayId = opponent,
                homeSheet = sheet,
                awaySheet = awaySheet,
                homeStarter = pitcher.id,
                seed = seed,
            )
            game.box.home.pitching[pitcher.id]?.total?.runs ?: 0
        }

        val settled = runsAllowed(0.0)
        val struggling = runsAllowed(12.0)
        assertTrue(
            struggling > settled,
            "적응 감점 12점을 줬는데 실점이 늘지 않았다 ($settled → $struggling)",
        )
    }

    @Test
    fun `시즌 중 외국인을 교체할 수 있고 횟수 제한이 걸린다`() {
        val state = playWeeks(52L, weeks = 5)
        val service = ForeignService(balance, strength)
        val teamId = league.teams.first().id
        val allowed = service.replacementsLeft(state, teamId)
        assertEquals(balance.int("foreignPlayers.inSeasonReplacements"), allowed)

        repeat(allowed) { index ->
            val outgoing = state.playersOf(teamId).first { it.isForeign }
            val candidate = state.foreignPool().candidates.first { it.isPitcher == (outgoing is baseballgm.model.Pitcher) }
            val problems = service.problemsForReplacement(state, teamId, outgoing.id, candidate, candidate.askingSalary)
            assertTrue(problems.isEmpty(), "${index + 1}번째 교체가 막혔다: $problems")

            val before = state.playersOf(teamId).size
            service.replace(state, teamId, outgoing.id, candidate, candidate.askingSalary)
            assertEquals(before, state.playersOf(teamId).size, "인원이 달라졌다")
            assertTrue(state.playersOf(teamId).none { it.id == outgoing.id }, "내보낸 선수가 남아 있다")
            assertTrue(state.playersOf(teamId).any { it.id == candidate.id }, "새 선수가 안 들어왔다")
            assertTrue(state.foreignPool().byId(candidate.id) == null, "계약한 선수가 시장에 남아 있다")
        }

        // 횟수를 다 쓰면 막힌다
        val outgoing = state.playersOf(teamId).first { it.isForeign }
        val candidate = state.foreignPool().candidates.first()
        val problems = service.problemsForReplacement(state, teamId, outgoing.id, candidate, candidate.askingSalary)
        assertTrue(problems.any { it.contains("횟수") }, problems.toString())
    }

    @Test
    fun `외국인 자리가 비어 있으면 아무도 내보내지 않고 영입한다`() {
        val teamId = league.teams.first().id
        val service = ForeignService(balance, strength)

        // 꽉 찬 상태에서는 내보낼 선수 없이 영입할 수 없다
        val full = SeasonRunner(balance, league).newSeason()
        val anyCandidate = full.foreignPool().candidates.first()
        val fullProblems = service.problemsForReplacement(full, teamId, null, anyCandidate, anyCandidate.askingSalary)
        assertTrue(fullProblems.any { it.contains("명까지만") }, fullProblems.toString())

        // 외국인 한 명이 빠진 채로 시즌을 시작한다 (스토브리그에서 못 채운 경우)
        val leaving = league.foreignersOf(teamId).first()
        val short = league.copy(players = league.players.filterNot { it.id == leaving.id })
        val state = SeasonRunner(balance, short).newSeason()
        assertTrue(service.hasOpenSlot(state, teamId))

        val candidate = state.foreignPool().candidates.first { it.isPitcher == (leaving is baseballgm.model.Pitcher) }
        val problems = service.problemsForReplacement(state, teamId, null, candidate, candidate.askingSalary)
        assertTrue(problems.isEmpty(), "빈 자리 영입이 막혔다: $problems")

        val fundsBefore = state.funds[teamId] ?: 0.0
        val before = state.playersOf(teamId).size
        val result = service.replace(state, teamId, null, candidate, candidate.askingSalary)
        assertEquals(before + 1, state.playersOf(teamId).size, "인원이 한 명 늘어야 한다")
        assertEquals(0.0, result.buyout, "내보낸 선수가 없으니 잔여 연봉도 없다")
        assertEquals(fundsBefore, state.funds[teamId] ?: 0.0)
        assertEquals(balance.int("foreignPlayers.inSeasonReplacements") - 1, service.replacementsLeft(state, teamId))
    }

    @Test
    fun `교체 마감이 지나면 바꿀 수 없다`() {
        val deadline = balance.int("foreignPlayers.replacementDeadlineWeek")
        val state = playWeeks(53L, weeks = deadline + 1)
        val service = ForeignService(balance, strength)
        val teamId = league.teams.first().id
        val outgoing = state.playersOf(teamId).first { it.isForeign }
        val candidate = state.foreignPool().candidates.first()
        val problems = service.problemsForReplacement(state, teamId, outgoing.id, candidate, candidate.askingSalary)
        assertTrue(problems.any { it.contains("마감") }, problems.toString())
    }

    @Test
    fun `스토브리그에 외국인이 재계약하거나 떠나고 빈 자리가 채워진다`() {
        val state = playWeeks(54L, weeks = 24)
        val (next, report) = offseason(state, 54L)

        assertTrue(report.foreignSignings.isNotEmpty(), "외국인 계약이 하나도 없다")
        next.teams.forEach { team ->
            val foreigners = next.foreignersOf(team.id)
            assertTrue(foreigners.size <= maxForeign, "${team.id} 외국인 ${foreigners.size}명")
            val pitchers = foreigners.count { it is baseballgm.model.Pitcher }
            assertTrue(pitchers <= balance.int("foreignPlayers.maxSameType"))
        }
        // 떠난 선수는 리그에 없다
        report.foreignDepartures.forEach { departure ->
            assertTrue(next.players.none { it.id == departure.playerId }, "떠난 외국인이 리그에 남아 있다")
            assertTrue(departure.reason.isNotBlank())
        }
        // 새 시장이 열린다
        assertEquals(next.season, next.foreignPool.season)
        assertTrue(next.foreignPool.candidates.isNotEmpty())
    }

    // ---------- 군 복무 ----------

    @Test
    fun `상무와 현역이 갈리고 기한 알림이 온다`() {
        val state = playWeeks(55L, weeks = 24)
        val (next, report) = offseason(state, 55L)

        assertTrue(report.enlistments.isNotEmpty(), "입대자가 없다")
        assertTrue(
            report.enlistments.any { it.kind == ServiceKind.SANGMU } &&
                report.enlistments.any { it.kind == ServiceKind.ACTIVE_DUTY },
            "상무와 현역이 갈리지 않았다",
        )
        report.enlistments.forEach { enlistment ->
            val player = next.players.firstOrNull { it.id == enlistment.playerId } ?: return@forEach
            val military = player.military
            assertTrue(military is MilitaryStatus.Serving, "${player.name} 이 입대하지 않았다")
            assertEquals(enlistment.kind, military.kind)
            assertTrue(military.returnSeason > next.season, "복귀 시즌이 ${military.returnSeason}")
        }
        assertTrue(report.enlistmentWarnings.isNotEmpty(), "기한 임박 알림이 없다")
    }

    @Test
    fun `복무를 마치면 시즌 중에 복귀한다`() {
        // 첫 시즌 복무 중인 선수 중 이번 시즌에 복귀 예정인 선수를 찾는다
        val returning = league.players.filter { player ->
            val military = player.military
            military is MilitaryStatus.Serving && military.returnSeason == league.season
        }
        if (returning.isEmpty()) return

        val latest = returning.maxOf { (it.military as MilitaryStatus.Serving).returnWeek }
        val state = playWeeks(56L, weeks = minOf(latest + 1, 24))
        returning.forEach { player ->
            val military = player.military as MilitaryStatus.Serving
            if (military.returnWeek <= state.week) {
                assertEquals(
                    MilitaryStatus.Completed,
                    state.player(player.id).military,
                    "${player.name} 이 복귀 주차가 지났는데 아직 복무 중이다",
                )
            }
        }
        assertTrue(
            state.inbox.all().any { it.text.contains("제대") },
            "제대 알림이 없다",
        )
    }

    // ---------- 국제대회 ----------

    @Test
    fun `첫 시즌 아시안게임이 열리고 선수들이 결장한다`() {
        val startWeek = balance.int("internationalTournament.asianGames.startWeek")
        val state = playWeeks(57L, weeks = startWeek)
        val result = assertNotNull(state.tournamentResult, "아시안게임이 열리지 않았다")

        assertEquals(league.season, result.season)
        assertTrue(result.squad.isNotEmpty())
        assertTrue(result.scores.isNotEmpty())
        // 대회 주간에는 차출 선수가 빠진다
        assertTrue(result.squad.all { state.isOnInternationalDuty(it.playerId, startWeek) })
        assertTrue(
            state.inbox.all().any { it.category == InboxCategory.NATIONAL_TEAM },
            "대표팀 알림이 없다",
        )
    }

    @Test
    fun `대회가 끝나면 차출이 풀리고 경기 수가 맞는다`() {
        val state = playWeeks(58L, weeks = 24)
        val result = assertNotNull(state.tournamentResult)
        val endWeek = balance.int("internationalTournament.asianGames.startWeek") +
            balance.int("internationalTournament.asianGames.weeksMissed") - 1

        assertTrue(result.squad.none { state.isOnInternationalDuty(it.playerId, endWeek + 1) })
        league.teams.forEach { team ->
            assertEquals(
                balance.int("schedule.gamesPerTeam"),
                state.standings.record(team.id).games,
                "${team.id} 경기 수가 모자란다 (대회 결장 때문에 경기가 누락됐나)",
            )
        }
    }

    @Test
    fun `금메달이면 병역 특례가 적용된다`() {
        // 금메달이 나오는 시드를 찾아 면제가 실제로 붙는지 본다
        val startWeek = balance.int("internationalTournament.asianGames.startWeek")
        val golden = (60L..80L).asSequence()
            .map { playWeeks(it, weeks = startWeek) }
            .firstOrNull { it.tournamentResult?.medal == baseballgm.events.Medal.GOLD }
            ?: return

        val result = golden.tournamentResult!!
        assertTrue(result.exempted.isNotEmpty(), "금메달인데 특례가 없다")
        result.exempted.forEach { playerId ->
            assertEquals(
                MilitaryStatus.Exempt,
                golden.player(playerId).military,
                "특례 대상인데 상태가 바뀌지 않았다",
            )
        }
    }

    @Test
    fun `리그 환경이 스토브리그마다 발표된다`() {
        val state = playWeeks(59L, weeks = 24)
        val (next, report) = offseason(state, 59L)
        assertNotNull(report.environmentAnnouncement, "리그 환경 발표가 없다")
        assertTrue(report.environmentAnnouncement!!.isNotBlank())
        assertTrue(next.environment.homeRunMultiplier in 0.8..1.25)
    }
}
