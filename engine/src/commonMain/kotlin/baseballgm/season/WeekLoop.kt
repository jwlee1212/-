package baseballgm.season

import baseballgm.condition.FatigueModel
import baseballgm.development.AgingCurves
import baseballgm.development.GrowthModel
import baseballgm.condition.FormModel
import baseballgm.condition.InjuryModel
import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.MedicalRole
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.sim.GameRules
import baseballgm.sim.GameSimulator
import baseballgm.sim.GameTeam
import baseballgm.sim.RatingTables
import baseballgm.stats.BoxScore
import baseballgm.stats.BoxScoreValidator
import baseballgm.stats.StatsRecorder
import baseballgm.tactics.DirectiveSheet
import baseballgm.tactics.ManagerAI
import kotlin.random.Random

/** 관전할 수 있게 남겨 둔 경기 (docs/07 문자 중계). */
data class WatchedGame(val box: BoxScore, val events: List<baseballgm.sim.GameEvent>)

/** 한 주가 끝난 뒤의 결산 (docs/07 주 결산). */
data class WeekReport(
    val week: Int,
    val games: List<BoxScore>,
    val messages: List<InboxMessage>,
    val validationProblems: List<String>,
    val allStarBreak: Boolean,
    /** 주요 장면 문자 중계 (docs/07). 관전 여부와 상관없이 이벤트 스트림에서 뽑는다 */
    val highlights: List<String> = emptyList(),
    /** 관전용으로 남겨 둔 경기. [highlightTeam] 을 지정했을 때만 채워진다 */
    val watched: List<WatchedGame> = emptyList(),
)

/**
 * 주간 루프 (docs/07).
 *
 * 한 주 = 화~일 6경기. 경기를 치르고, 컨디션을 갱신하고, 기록을 쌓고, 결산을 만든다.
 *
 * 라인업은 **매일 다시 짠다.** 화요일에 다친 선수가 수요일에 나오면 안 되고, 피로가 쌓인 포수는
 * 규칙표의 휴식 기준에 걸려 저절로 빠져야 하기 때문이다 (docs/06 ①).
 */
class WeekLoop(
    private val balance: BalanceConfig,
    private val league: League,
) {
    private val tables = RatingTables(balance)
    private val strength = StrengthCalculator(balance)
    private val managerAI = ManagerAI(balance, strength)
    private val simulator = GameSimulator(balance, tables, GameRules.regularSeason(balance), league.environment)
    private val fatigueModel = FatigueModel(balance, tables)
    private val injuryModel = InjuryModel(balance)
    private val formModel = FormModel(balance)
    private val rosterManager = RosterManager(balance)
    private val futuresLeague = FuturesLeague(balance, tables)
    private val growthModel = GrowthModel(balance, AgingCurves(balance))
    private val growthContexts = GrowthContextResolver(balance)
    /** 시즌 중 성장은 월 1회 = 24주를 6번으로 나눈다 (docs/09). */
    private val monthlyGrowthInterval =
        balance.int("season.regularSeasonWeeks") / balance.int("growth.monthlyGrowthCount")
    private val autoStop = balance.section("autoAdvance")
    private val commentary = baseballgm.text.CommentaryRenderer { id -> league.player(id).registeredName }

    /**
     * 한 주를 진행한다. 검증에 실패한 경기가 있으면 [WeekReport.validationProblems] 에 담긴다.
     *
     * @param highlightTeam 하이라이트를 뽑을 구단 (유저 팀). null 이면 뽑지 않는다 —
     *   720경기 전부의 중계 문장을 들고 있을 이유가 없다
     */
    fun playWeek(
        state: SeasonState,
        random: Random,
        validate: Boolean = true,
        highlightTeam: TeamId? = null,
    ): WeekReport {
        val week = state.week
        val games = mutableListOf<BoxScore>()
        val problems = mutableListOf<String>()
        val highlights = mutableListOf<String>()
        val watched = mutableListOf<WatchedGame>()

        applyGmDirections(state)
        manageRosters(state, week)

        for (day in 0 until DAYS_IN_WEEK) {
            val sheets = buildSheets(state)
            val playedToday = mutableSetOf<PlayerId>()
            league.schedule.gamesInWeek(week).filter { it.day == day }.forEach { scheduled ->
                val watching = highlightTeam != null &&
                    (scheduled.home == highlightTeam || scheduled.away == highlightTeam)
                val (box, events) = playGame(state, sheets, scheduled.home, scheduled.away, week, day, random, playedToday)
                games += box
                if (validate) problems += BoxScoreValidator.validate(box)
                if (watching) {
                    highlights += commentary.highlights(events, HIGHLIGHTS_PER_GAME)
                    watched += WatchedGame(box, events)
                }
            }
            applyDailyRecovery(state, playedToday, random)
        }

        finishWeek(state, week, games, random)
        val messages = state.inbox.ofWeek(week)
        val allStarBreak = state.calendar.isAllStarBreakAfter(week)
        state.week = week + 1
        return WeekReport(
            week = week,
            games = games,
            messages = messages,
            validationProblems = problems,
            allStarBreak = allStarBreak,
            highlights = highlights.takeLast(WEEK_HIGHLIGHTS),
            watched = watched,
        )
    }

    // ---------- 경기 ----------

    private fun playGame(
        state: SeasonState,
        sheets: Map<TeamId, DirectiveSheet>,
        homeId: TeamId,
        awayId: TeamId,
        week: Int,
        day: Int,
        random: Random,
        playedToday: MutableSet<PlayerId>,
    ): Pair<BoxScore, List<baseballgm.sim.GameEvent>> {
        val absoluteDay = state.absoluteDay(week, day)
        val home = gameTeam(state, sheets.getValue(homeId), homeId, absoluteDay)
        val away = gameTeam(state, sheets.getValue(awayId), awayId, absoluteDay)
        val events = simulator.simulate(home, away, league.team(homeId).parkFactor, random)
        val box = StatsRecorder.record(events)

        state.stats.add(box)
        state.standings = state.standings.withResult(box)
        applyGameEffects(state, box, absoluteDay, week, random, playedToday)
        return box to events
    }

    private fun gameTeam(
        state: SeasonState,
        sheet: DirectiveSheet,
        teamId: TeamId,
        absoluteDay: Int,
    ): GameTeam {
        val roster = state.playersOf(teamId)
        val starters = sheet.rotation.starters
        val starter = starters[state.nextRotationIndex(teamId).mod(starters.size)]
        val pitcherIds = roster.filterIsInstance<Pitcher>().map { it.id }
        val unavailable = roster
            .filter { it.rosterLevel != RosterLevel.FIRST_TEAM || it.condition.isInjured || !it.military.isAvailable }
            .map { it.id }
            .toSet()
        return GameTeam(
            teamId = teamId,
            roster = roster.associateBy { it.id },
            directives = sheet,
            startingPitcher = starter,
            availability = state.usage.availabilityFor(pitcherIds, absoluteDay, unavailable),
        )
    }

    private fun buildSheets(state: SeasonState): Map<TeamId, DirectiveSheet> =
        league.teams.associate { team ->
            val base = managerAI.buildSheet(state.tendenciesOf(team.id), state.playersOf(team.id), state.season)
            team.id to managerAI.applyPolicy(base, state.policyOf(team.id))
        }

    /**
     * 단장 방침을 감독 성향에 섞는다 (docs/06 방침 반영률).
     * 한 번에 목표값까지 가지 않고 매주 조금씩 움직이므로, 계속 같은 방침을 유지하면 점점 가까워진다.
     */
    private fun applyGmDirections(state: SeasonState) {
        state.gmDirections.forEach { (teamId, requested) ->
            state.managerTendencies[teamId] =
                managerAI.blendTendencies(state.tendenciesOf(teamId), requested)
        }
    }

    // ---------- 경기 뒤 컨디션 ----------

    /** 경기에 나선 선수의 피로를 올리고 부상을 판정한다 (docs/08). */
    private fun applyGameEffects(
        state: SeasonState,
        box: BoxScore,
        absoluteDay: Int,
        week: Int,
        random: Random,
        playedToday: MutableSet<PlayerId>,
    ) {
        listOf(box.home, box.away).forEach { team ->
            val doctorGrade = medicalGrade(team.teamId, MedicalRole.TEAM_DOCTOR)

            team.pitching.forEach { (pitcherId, line) ->
                val pitches = line.total.pitches
                if (pitches <= 0 && line.total.outs <= 0) return@forEach
                val pitcher = state.player(pitcherId)
                val consecutive = state.usage.consecutiveDays(pitcherId, absoluteDay)
                state.usage.record(pitcherId, absoluteDay, pitches)
                playedToday += pitcherId
                val updated = pitcher.withCondition(
                    pitcher.condition.copy(
                        fatigue = fatigueModel.afterPitching(pitcher.condition.fatigue, pitches, consecutive),
                    ),
                )
                state.update(updated)
                rollInjury(state, updated, week, doctorGrade, random)
            }

            team.batting.forEach { (batterId, line) ->
                if (line.total.plateAppearances <= 0) return@forEach
                val batter = state.player(batterId)
                playedToday += batterId
                val position = (batter as? Batter)?.primaryPosition ?: Position.DESIGNATED_HITTER
                val updated = batter.withCondition(
                    batter.condition.copy(fatigue = fatigueModel.afterPlaying(batter.condition.fatigue, position)),
                )
                state.update(updated)
                rollInjury(state, updated, week, doctorGrade, random)
            }
        }
    }

    private fun rollInjury(state: SeasonState, player: Player, week: Int, doctorGrade: Int, random: Random) {
        val injury = injuryModel.roll(player, state.season, doctorGrade, random) ?: return
        state.update(player.withCondition(player.condition.copy(injury = injury)))
        state.inbox.add(
            week = week,
            category = InboxCategory.INJURY,
            teamId = player.teamId,
            text = "${player.registeredName} ${injury.part} 부상 (${injury.severity.label()}, ${injury.weeksRemaining}주)",
        )
    }

    /** 하루가 끝났다. 전원 피로 회복 + 1군 선수 폼 변화. */
    private fun applyDailyRecovery(state: SeasonState, playedToday: Set<PlayerId>, random: Random) {
        league.teams.forEach { team ->
            val conditioning = medicalGrade(team.id, MedicalRole.CONDITIONING_COACH)
            state.playersOf(team.id).forEach { player ->
                val played = player.id in playedToday
                var condition = player.condition.copy(
                    fatigue = fatigueModel.recover(player, state.season, played, conditioning),
                )
                if (player.rosterLevel == RosterLevel.FIRST_TEAM) {
                    condition = condition.copy(form = formModel.next(player, random))
                }
                state.update(player.withCondition(condition))
            }
        }
    }

    // ---------- 주 결산 ----------

    private fun finishWeek(state: SeasonState, week: Int, games: List<BoxScore>, random: Random) {
        advanceInjuries(state, week, random)
        if (week % monthlyGrowthInterval == 0) applyMonthlyGrowth(state, random)
        runFuturesLeague(state, random)
        if (state.calendar.isAllStarBreakAfter(week)) applyAllStarBreak(state)
        addWeeklyMessages(state, week, games)
    }

    /** 부상 주차를 줄이고, 다 나은 선수는 영구 하락을 확정한 뒤 재활에 들어간다. */
    private fun advanceInjuries(state: SeasonState, week: Int, random: Random) {
        state.allPlayers().forEach { player ->
            val injury = player.condition.injury
            if (injury == null) {
                if (player.condition.relapseRiskWeeks > 0) {
                    state.update(
                        player.withCondition(
                            player.condition.copy(relapseRiskWeeks = player.condition.relapseRiskWeeks - 1),
                        ),
                    )
                }
                return@forEach
            }
            val next = injuryModel.advanceWeek(injury)
            if (next != null) {
                state.update(player.withCondition(player.condition.copy(injury = next)))
                return@forEach
            }
            // 완치: 영구 하락 판정 후 재발 위험 기간에 들어간다
            val doctorGrade = player.teamId?.let { medicalGrade(it, MedicalRole.TEAM_DOCTOR) } ?: 0
            val outcome = injuryModel.applyPermanentLoss(player, injury, state.season, doctorGrade, random)
            val healed = outcome.changedPlayer.withCondition(
                outcome.changedPlayer.condition.copy(injury = null, relapseRiskWeeks = injury.relapseRiskWeeks),
            )
            state.update(healed)
            val lossText = if (outcome.permanentLoss.isEmpty()) {
                ""
            } else {
                " (${outcome.permanentLoss.entries.joinToString { "${it.key.label} -${it.value}" }})"
            }
            state.inbox.add(
                week = week,
                category = InboxCategory.RETURN,
                teamId = player.teamId,
                text = "${player.registeredName} ${injury.part} 부상 회복$lossText",
            )
        }
    }

    /**
     * 시즌 중 월 1회 성장 (docs/09). 25세 이하만, 연간 성장량의 40%를 여섯 번에 나눠 받는다.
     * 시즌 중에 유망주가 눈에 띄게 크는 맛을 내기 위한 장치다.
     */
    private fun applyMonthlyGrowth(state: SeasonState, random: Random) {
        state.allPlayers().forEach { player ->
            val result = growthModel.monthly(player, state.season, growthContexts.contextOf(state, player), random)
            if (result.changes.isNotEmpty()) state.update(result.player)
        }
    }

    /** 2군은 경기를 돌리지 않고 추정 성적만 만든다 (docs/07). */
    private fun runFuturesLeague(state: SeasonState, random: Random) {
        state.allPlayers().filter { it.rosterLevel == RosterLevel.FUTURES && !it.condition.isInjured }
            .forEach { player ->
                when (player) {
                    is Batter -> state.stats.addFutures(player.id, futuresLeague.weeklyBatting(player, random))
                    is Pitcher -> state.stats.addFutures(player.id, futuresLeague.weeklyPitching(player, random))
                }
                // 2군 선수의 폼은 주 단위로만 움직이고, 말소 직후에는 평균으로 빠르게 돌아온다
                state.update(
                    player.withCondition(
                        player.condition.copy(form = formModel.afterDemotion(player.condition.form)),
                    ),
                )
            }
    }

    private fun applyAllStarBreak(state: SeasonState) {
        state.allPlayers().forEach { player ->
            state.update(
                player.withCondition(
                    player.condition.copy(fatigue = fatigueModel.afterAllStarBreak(player.condition.fatigue)),
                ),
            )
        }
    }

    private fun manageRosters(state: SeasonState, week: Int) {
        league.teams.forEach { team ->
            val moves = rosterManager.manage(
                teamId = team.id,
                roster = state.playersOf(team.id),
                week = week,
                state = state.roster,
                rank = { strength.overallOf(it) },
                onChange = { state.update(it) },
            )
            moves.forEach { move ->
                state.inbox.add(
                    week = week,
                    category = if (move.promoted) InboxCategory.ROSTER else InboxCategory.INJURY,
                    teamId = team.id,
                    text = "${state.player(move.playerId).registeredName} ${if (move.promoted) "1군 등록" else "1군 말소"} — ${move.reason}",
                )
            }
        }
    }

    /** 결과 나열로 끝내지 않고 다음 주 결정거리를 만든다 (docs/07). */
    private fun addWeeklyMessages(state: SeasonState, week: Int, games: List<BoxScore>) {
        val losingStreakLimit = autoStop.int("losingStreak")
        val winningStreakLimit = autoStop.int("winningStreak")
        league.teams.forEach { team ->
            val record = state.standings.record(team.id)
            if (record.streak <= -losingStreakLimit) {
                state.inbox.add(week, InboxCategory.STREAK, team.id, "${team.name} ${-record.streak}연패 — 팬심이 흔들린다")
            }
            if (record.streak >= winningStreakLimit) {
                state.inbox.add(week, InboxCategory.STREAK, team.id, "${team.name} ${record.streak}연승")
            }
            // 2군 호성적: 콜업 후보를 알린다
            state.futuresOf(team.id)
                .filterIsInstance<Batter>()
                .maxByOrNull { state.stats.futuresBattingOf(it.id).ops }
                ?.let { best ->
                    val line = state.stats.futuresBattingOf(best.id)
                    if (line.plateAppearances >= FUTURES_REPORT_PA && line.ops >= FUTURES_REPORT_OPS) {
                        state.inbox.add(
                            week,
                            InboxCategory.FUTURES,
                            team.id,
                            "2군 ${best.registeredName} OPS ${"%.3f".format(line.ops)} (${line.plateAppearances}타석)",
                        )
                    }
                }
        }
        if (state.calendar.isAllStarBreakAfter(week)) {
            state.inbox.add(week, InboxCategory.SCHEDULE, null, "올스타 브레이크 — 전반기 결산, 피로 회복")
        }
        if (state.calendar.isTradeDeadline(week)) {
            state.inbox.add(week, InboxCategory.SCHEDULE, null, "이번 주가 트레이드 마감 주간이다")
        }
        if (state.calendar.isDraftWeek(week)) {
            state.inbox.add(week, InboxCategory.SCHEDULE, null, "신인 드래프트 주간")
        }
    }

    private fun medicalGrade(teamId: TeamId, role: MedicalRole): Int =
        league.medicalStaffOf(teamId).firstOrNull { it.role == role }?.grade ?: 0

    private fun baseballgm.model.InjurySeverity.label(): String = when (this) {
        baseballgm.model.InjurySeverity.MINOR -> "경미"
        baseballgm.model.InjurySeverity.MODERATE -> "중간"
        baseballgm.model.InjurySeverity.MAJOR -> "중상"
        baseballgm.model.InjurySeverity.SEASON_ENDING -> "시즌 아웃"
    }

    private companion object {
        const val DAYS_IN_WEEK = 6
        const val FUTURES_REPORT_PA = 40
        const val FUTURES_REPORT_OPS = 0.900
        const val HIGHLIGHTS_PER_GAME = 2
        const val WEEK_HIGHLIGHTS = 5
    }
}
