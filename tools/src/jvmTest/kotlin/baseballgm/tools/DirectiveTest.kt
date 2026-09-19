package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.Hand
import baseballgm.model.ManagerTendencies
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.RosterLevel
import baseballgm.sim.GameRules
import baseballgm.sim.GameSimulator
import baseballgm.sim.GameTeam
import baseballgm.sim.PaOutcome
import baseballgm.sim.PitcherChanged
import baseballgm.sim.PlateAppearanceCompleted
import baseballgm.sim.PlayerSubstituted
import baseballgm.sim.RatingTables
import baseballgm.sim.SubstitutionKind
import baseballgm.stats.BoxScoreValidator
import baseballgm.stats.StatsRecorder
import baseballgm.tactics.BullpenRole
import baseballgm.tactics.DirectivePreset
import baseballgm.tactics.ManagerAI
import baseballgm.tactics.TacticSliders
import baseballgm.tactics.WeeklyPolicy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 사전 지시 규칙표와 감독 AI (M3) 검증. */
class DirectiveTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val strength = StrengthCalculator(balance)
    private val managerAI = ManagerAI(balance, strength)
    private val runner = GameRunner(balance, league)
    private val homeId = league.teams[0].id
    private val awayId = league.teams[1].id

    private fun sheetOf(tendencies: ManagerTendencies, teamId: baseballgm.model.TeamId = homeId) =
        managerAI.buildSheet(tendencies, league.playersOf(teamId), league.season)

    /**
     * 두 팀 각각의 규칙표를 같은 방식으로 손본다.
     * 규칙표는 그 팀 선수 id 를 담고 있어서, 한 팀 것을 다른 팀에 넘기면 안 된다.
     */
    private fun bothSheets(
        tendencies: ManagerTendencies = DirectivePreset.STANDARD.tendencies(),
        transform: (baseballgm.tactics.DirectiveSheet) -> baseballgm.tactics.DirectiveSheet = { it },
    ): Pair<baseballgm.tactics.DirectiveSheet, baseballgm.tactics.DirectiveSheet> =
        transform(sheetOf(tendencies, homeId)) to transform(sheetOf(tendencies, awayId))

    // ---------- 감독 성향 → 규칙표 ----------

    @Test
    fun `인내심 있는 감독일수록 선발을 늦게 내린다`() {
        val patient = sheetOf(DirectivePreset.STANDARD.tendencies().copy(starterPatience = 95))
        val quick = sheetOf(DirectivePreset.STANDARD.tendencies().copy(starterPatience = 5))
        assertTrue(
            patient.starterHook.pitchLimit > quick.starterHook.pitchLimit,
            "${patient.starterHook.pitchLimit} vs ${quick.starterHook.pitchLimit}",
        )
        assertTrue(patient.starterHook.runsAllowedLimit >= quick.starterHook.runsAllowedLimit)
        assertTrue(quick.starterHook.pullOnThirdTimeThroughOrder, "인내심 낮은 감독은 세 번째 타순에서 내린다")
        assertTrue(!patient.starterHook.pullOnThirdTimeThroughOrder)
    }

    @Test
    fun `불펜 혹사 성향이 높으면 연투 제한이 느슨하고 필승조가 넓다`() {
        val heavy = sheetOf(DirectivePreset.STANDARD.tendencies().copy(bullpenAggression = 95))
        val careful = sheetOf(DirectivePreset.STANDARD.tendencies().copy(bullpenAggression = 5))
        assertTrue(heavy.bullpen.maxConsecutiveDays >= careful.bullpen.maxConsecutiveDays)
        assertTrue(heavy.bullpen.maxPitchesLast7Days > careful.bullpen.maxPitchesLast7Days)
        assertTrue(
            heavy.bullpen.candidates(BullpenRole.HIGH_LEVERAGE).size >
                careful.bullpen.candidates(BullpenRole.HIGH_LEVERAGE).size,
        )
    }

    @Test
    fun `작전 성향이 슬라이더로 옮겨진다`() {
        val smallBall = sheetOf(DirectivePreset.SMALL_BALL.tendencies())
        val aggressive = sheetOf(DirectivePreset.AGGRESSIVE.tendencies())
        assertTrue(smallBall.tactics.bunt > aggressive.tactics.bunt)
        assertTrue(aggressive.tactics.steal >= smallBall.tactics.steal)
        assertTrue(smallBall.tactics.bunt in TacticSliders.RANGE)
    }

    @Test
    fun `플래툰 활용이 높은 감독은 좌완용과 우완용 라인업이 다르다`() {
        val platoon = sheetOf(DirectivePreset.STANDARD.tendencies().copy(platoonUsage = 100))
        val flat = sheetOf(DirectivePreset.STANDARD.tendencies().copy(platoonUsage = 1))
        val platoonDiff = platoon.lineupVsLeft.playerIds.toSet() - platoon.lineupVsRight.playerIds.toSet()
        val flatDiff = flat.lineupVsLeft.playerIds.toSet() - flat.lineupVsRight.playerIds.toSet()
        assertTrue(platoonDiff.isNotEmpty(), "좌우 라인업이 완전히 같다")
        assertTrue(platoonDiff.size >= flatDiff.size)
    }

    @Test
    fun `모든 규칙표는 9자리 라인업과 5인 로테이션을 갖춘다`() {
        DirectivePreset.entries.forEach { preset ->
            league.teams.forEach { team ->
                val sheet = managerAI.buildSheet(preset.tendencies(), league.playersOf(team.id), league.season)
                assertEquals(9, sheet.lineupVsLeft.slots.size, "${team.id} ${preset.label}")
                assertEquals(9, sheet.lineupVsLeft.playerIds.toSet().size, "라인업에 같은 선수가 두 번 있다")
                assertEquals(
                    baseballgm.model.Position.entries.toSet(),
                    sheet.lineupVsLeft.slots.map { it.position }.toSet(),
                    "포지션이 비었다",
                )
                assertEquals(5, sheet.rotation.starters.size)
                assertTrue(sheet.bullpen.candidates(BullpenRole.CLOSER).isNotEmpty())
            }
        }
    }

    @Test
    fun `단장 방침은 감독 성향을 일부만 움직인다`() {
        val current = DirectivePreset.STANDARD.tendencies().copy(buntPreference = 20)
        val requested = current.copy(buntPreference = 100)
        val blended = managerAI.blendTendencies(current, requested, adoptionRate = 0.5)
        assertEquals(60, blended.buntPreference, "반영률 50%면 20 → 60")
        assertEquals(current.stealAggression, blended.stealAggression, "지시하지 않은 항목은 그대로다")
    }

    @Test
    fun `주간 방침이 제한을 풀거나 조인다`() {
        val base = sheetOf(DirectivePreset.STANDARD.tendencies())
        val allOut = managerAI.applyPolicy(base, WeeklyPolicy.ALL_OUT)
        val protect = managerAI.applyPolicy(base, WeeklyPolicy.PROTECT)
        assertTrue(allOut.starterHook.pitchLimit > base.starterHook.pitchLimit)
        assertTrue(protect.starterHook.pitchLimit < base.starterHook.pitchLimit)
        assertTrue(allOut.bullpen.maxConsecutiveDays > protect.bullpen.maxConsecutiveDays)
        assertTrue(
            allOut.bullpen.candidates(BullpenRole.HIGH_LEVERAGE).size >
                base.bullpen.candidates(BullpenRole.HIGH_LEVERAGE).size,
        )
    }

    // ---------- 경기에서 규칙대로 도는가 ----------

    @Test
    fun `투구수 한계에 걸리면 선발이 내려간다`() {
        val (shortHome, shortAway) = bothSheets {
            it.copy(starterHook = it.starterHook.copy(pitchLimit = 45, runsAllowedLimit = 99))
        }
        val (longHome, longAway) = bothSheets {
            it.copy(starterHook = it.starterHook.copy(pitchLimit = 200, runsAllowedLimit = 99))
        }

        val short = runner.playWithSheets(homeId, awayId, shortHome, shortAway, seed = 4L)
        val long = runner.playWithSheets(homeId, awayId, longHome, longAway, seed = 4L)

        val shortStarterPitches = starterPitches(short)
        val longStarterPitches = starterPitches(long)
        assertTrue(shortStarterPitches < longStarterPitches, "$shortStarterPitches vs $longStarterPitches")
        assertTrue(shortStarterPitches < 70, "투구수 한계 45인데 $shortStarterPitches 구를 던졌다")
        assertTrue(
            short.events.filterIsInstance<PitcherChanged>().size >
                long.events.filterIsInstance<PitcherChanged>().size,
        )
    }

    @Test
    fun `실점 한계에 걸려도 선발이 내려간다`() {
        val (tightHome, tightAway) = bothSheets {
            it.copy(starterHook = it.starterHook.copy(pitchLimit = 200, runsAllowedLimit = 2))
        }
        var pulled = 0
        var games = 0
        repeat(20) { index ->
            val played = runner.playWithSheets(homeId, awayId, tightHome, tightAway, seed = 300L + index)
            games++
            val starter = played.events.filterIsInstance<baseballgm.sim.GameStarted>().first().homeStarter
            val runs = played.box.home.pitching[starter]?.total?.runs ?: 0
            val changed = played.events.filterIsInstance<PitcherChanged>().any { it.leavingPitcherId == starter }
            if (runs >= 2) {
                assertTrue(changed, "선발이 ${runs}실점했는데 안 내려갔다")
                pulled++
            }
        }
        assertTrue(pulled > 0, "$games 경기에서 실점 한계에 걸린 경기가 없다")
    }

    @Test
    fun `9회 접전 리드면 마무리가 나온다`() {
        val (homeSheet, awaySheet) = bothSheets()
        val closer = homeSheet.bullpen.candidates(BullpenRole.CLOSER).first()
        var closerAppearances = 0
        var saveChances = 0
        repeat(40) { index ->
            val played = runner.playWithSheets(homeId, awayId, homeSheet, awaySheet, seed = 700L + index)
            val box = played.box
            val homeLead = box.homeScore - box.awayScore
            if (homeLead in 1..3) {
                saveChances++
                if (box.home.pitching.containsKey(closer)) closerAppearances++
            }
        }
        assertTrue(saveChances > 0, "세이브 상황이 한 번도 없었다")
        assertTrue(
            closerAppearances >= saveChances * 0.6,
            "세이브 상황 $saveChances 번 중 마무리 등판 $closerAppearances 번",
        )
    }

    @Test
    fun `대타 조건을 풀면 대타가 나오고 막으면 안 나온다`() {
        val eager = sheetOf(DirectivePreset.STANDARD.tendencies(), homeId).let { sheet ->
            sheet.copy(
                substitution = sheet.substitution.copy(
                    pinchHitFromInning = 1,
                    pinchHitMaxDeficit = 99,
                    pinchHitRatingGap = 0,
                    pinchHitOnlyInScoringPosition = false,
                    pinchHitPlatoonOnly = false,
                ),
            )
        }
        val never = sheetOf(DirectivePreset.STANDARD.tendencies(), awayId).let { sheet ->
            sheet.copy(substitution = sheet.substitution.copy(pinchHitFromInning = 99))
        }

        val withPinch = runner.playWithSheets(homeId, awayId, eager, never, seed = 21L)
        val pinchHits = withPinch.events.filterIsInstance<PlayerSubstituted>()
            .filter { it.kind == SubstitutionKind.PINCH_HITTER }
        assertTrue(pinchHits.any { it.teamId == homeId }, "대타 조건을 다 풀었는데 대타가 없다")
        assertTrue(pinchHits.none { it.teamId == awayId }, "대타를 막았는데 나왔다")
        BoxScoreValidator.validateOrThrow(withPinch.box)
    }

    @Test
    fun `번트 슬라이더가 희생번트 수를 바꾼다`() {
        val (buntHome, buntAway) = bothSheets { it.copy(tactics = it.tactics.copy(bunt = 5)) }
        val (calmHome, calmAway) = bothSheets { it.copy(tactics = it.tactics.copy(bunt = 1)) }
        var buntsWithSlider5 = 0
        var buntsWithSlider1 = 0
        repeat(30) { index ->
            buntsWithSlider5 += countOutcome(
                runner.playWithSheets(homeId, awayId, buntHome, buntAway, seed = 900L + index),
                PaOutcome.SAC_BUNT,
            )
            buntsWithSlider1 += countOutcome(
                runner.playWithSheets(homeId, awayId, calmHome, calmAway, seed = 900L + index),
                PaOutcome.SAC_BUNT,
            )
        }
        assertTrue(buntsWithSlider5 > buntsWithSlider1, "번트 5: $buntsWithSlider5, 번트 1: $buntsWithSlider1")
        assertTrue(buntsWithSlider5 > 0)
    }

    @Test
    fun `도루 슬라이더가 도루 시도를 바꾼다`() {
        val (runHome, runAway) = bothSheets { it.copy(tactics = it.tactics.copy(steal = 5)) }
        val (stillHome, stillAway) = bothSheets { it.copy(tactics = it.tactics.copy(steal = 1)) }
        var attemptsHigh = 0
        var attemptsLow = 0
        repeat(30) { index ->
            attemptsHigh += runner.playWithSheets(homeId, awayId, runHome, runAway, seed = 950L + index)
                .events.filterIsInstance<baseballgm.sim.StolenBaseAttempted>().count { it.battingTeam == homeId }
            attemptsLow += runner.playWithSheets(homeId, awayId, stillHome, stillAway, seed = 950L + index)
                .events.filterIsInstance<baseballgm.sim.StolenBaseAttempted>().count { it.battingTeam == homeId }
        }
        assertTrue(attemptsHigh > attemptsLow, "도루 5: $attemptsHigh, 도루 1: $attemptsLow")
    }

    // ---------- 예외 처리 (docs/06) ----------

    @Test
    fun `투수가 고갈돼도 11회 연장 경기가 끝난다`() {
        // 투수를 3명만 남긴 선수단으로, 투구수 한계까지 아주 낮게 잡아 고갈시킨다
        val tables = RatingTables(balance)
        val simulator = GameSimulator(balance, tables, GameRules.regularSeason(balance))
        val rules = GameRules.regularSeason(balance)

        fun thinTeam(teamId: baseballgm.model.TeamId): GameTeam {
            val roster = league.playersOf(teamId).filter { it.rosterLevel == RosterLevel.FIRST_TEAM }
            val batters = roster.filterIsInstance<Batter>().sortedByDescending { strength.overallOf(it) }.take(9)
            val pitchers = roster.filterIsInstance<Pitcher>().sortedByDescending { strength.overallOf(it) }.take(3)
            val thin: List<Player> = batters + pitchers
            val sheet = managerAI.buildSheet(DirectivePreset.STANDARD.tendencies(), thin, league.season)
            val exhausting = sheet.copy(
                starterHook = sheet.starterHook.copy(pitchLimit = 20, runsAllowedLimit = 1, fatigueLimit = 1),
            )
            return GameTeam(teamId, thin.associateBy { it.id }, exhausting, exhausting.rotation.starters.first())
        }

        var extraInningGames = 0
        repeat(40) { index ->
            val events = simulator.simulate(thinTeam(homeId), thinTeam(awayId), 1.0, Random(1000L + index))
            val box = StatsRecorder.record(events)
            BoxScoreValidator.validateOrThrow(box)
            assertTrue(box.innings <= rules.maxInnings)
            assertTrue(box.innings >= 9 || box.walkOff, "경기가 중간에 끝났다 (${box.innings}회)")
            if (box.innings > 9) extraInningGames++
            // 벤치가 비어도 교체 없이 진행되고, 마지막 투수가 계속 던진다
            val homePitchers = box.home.pitching.size
            assertTrue(homePitchers <= 3, "3명뿐인데 $homePitchers 명이 던졌다")
        }
        assertTrue(extraInningGames > 0, "40경기에서 연장이 한 번도 없었다 (고갈 상황을 못 만들었다)")
    }

    @Test
    fun `대타 자원이 없으면 교체 없이 진행한다`() {
        val roster = league.playersOf(homeId).filter { it.rosterLevel == RosterLevel.FIRST_TEAM }
        val batters = roster.filterIsInstance<Batter>().sortedByDescending { strength.overallOf(it) }.take(9)
        val pitchers = roster.filterIsInstance<Pitcher>().take(8)
        val thin: List<Player> = batters + pitchers
        val sheet = managerAI.buildSheet(DirectivePreset.STANDARD.tendencies(), thin, league.season)
        val eager = sheet.copy(
            substitution = sheet.substitution.copy(
                pinchHitFromInning = 1, pinchHitRatingGap = 0, pinchHitMaxDeficit = 99,
                pinchHitOnlyInScoringPosition = false, pinchHitPlatoonOnly = false,
            ),
        )
        val simulator = GameSimulator(balance, RatingTables(balance), GameRules.regularSeason(balance))
        val awaySheet = managerAI.buildSheet(DirectivePreset.STANDARD.tendencies(), league.playersOf(awayId), league.season)
        val events = simulator.simulate(
            GameTeam(homeId, thin.associateBy { it.id }, eager, eager.rotation.starters.first()),
            GameTeam(awayId, runner.rosterOf(awayId), awaySheet, awaySheet.rotation.starters.first()),
            1.0,
            Random(55L),
        )
        val box = StatsRecorder.record(events)
        BoxScoreValidator.validateOrThrow(box)
        assertTrue(
            events.filterIsInstance<PlayerSubstituted>().none { it.teamId == homeId },
            "벤치가 비었는데 교체가 일어났다",
        )
    }

    // ---------- 좌우 라인업이 실제로 쓰이는가 ----------

    @Test
    fun `상대 선발의 손에 따라 다른 라인업이 나간다`() {
        val sheet = sheetOf(DirectivePreset.STANDARD.tendencies().copy(platoonUsage = 100))
        val awaySheet = sheetOf(DirectivePreset.STANDARD.tendencies(), awayId)
        val lefty = awaySheet.rotation.starters.map { league.player(it) as Pitcher }
            .firstOrNull { it.throwsWith == Hand.LEFT }
        val righty = awaySheet.rotation.starters.map { league.player(it) as Pitcher }
            .firstOrNull { it.throwsWith == Hand.RIGHT }
        if (lefty == null || righty == null) return // 로테이션이 한쪽 손뿐이면 확인할 수 없다

        val vsLefty = runner.playWithSheets(homeId, awayId, sheet, awaySheet, awayStarter = lefty.id, seed = 31L)
        val vsRighty = runner.playWithSheets(homeId, awayId, sheet, awaySheet, awayStarter = righty.id, seed = 31L)

        fun starters(game: PlayedGame) = game.events.filterIsInstance<PlateAppearanceCompleted>()
            .filter { it.battingTeam == homeId }
            .map { it.batterId }
            .distinct()
            .take(9)
            .toSet()
        assertTrue(starters(vsLefty) != starters(vsRighty), "좌완·우완 선발에 같은 라인업이 나갔다")
        assertEquals(sheet.lineupVsLeft.playerIds.toSet(), starters(vsLefty).toSet())
    }

    private fun starterPitches(game: PlayedGame): Int {
        val starter = game.events.filterIsInstance<baseballgm.sim.GameStarted>().first().homeStarter
        return game.box.home.pitching[starter]?.total?.pitches ?: 0
    }

    private fun countOutcome(game: PlayedGame, outcome: PaOutcome): Int =
        game.events.filterIsInstance<PlateAppearanceCompleted>().count { it.outcome == outcome }
}
