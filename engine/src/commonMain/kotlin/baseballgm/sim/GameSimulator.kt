package baseballgm.sim

import baseballgm.io.BalanceConfig
import baseballgm.league.LeagueEnvironment
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.Hand
import baseballgm.model.Pitcher
import baseballgm.model.PlayerId
import baseballgm.model.Position
import baseballgm.model.TeamId
import baseballgm.tactics.BullpenRole
import baseballgm.tactics.LineupPlan
import baseballgm.tactics.LineupSlot
import baseballgm.util.chance
import kotlin.math.abs
import kotlin.random.Random

/**
 * 경기 시뮬레이터 (docs/05, 06).
 *
 * 경기 운영은 전부 **사전 지시 규칙표**(`DirectiveSheet`)를 읽어서 한다. 규칙표를 감독 AI 가 채웠는지
 * 유저가 채웠는지는 여기서 알 필요가 없다. 시뮬레이터는 이벤트 스트림만 만들고, 기록·검증·문자 중계가
 * 그것을 읽는다 (불변 원칙 5).
 *
 * **경기는 어떤 경우에도 멈추지 않는다.** 던질 투수가 없으면 제한을 하나씩 풀어 가며 올리고
 * (docs/06 예외 처리), 대타 자원이 없으면 교체 없이 진행한다.
 */
class GameSimulator(
    private val balance: BalanceConfig,
    private val tables: RatingTables,
    private val rules: GameRules,
    environment: LeagueEnvironment = LeagueEnvironment.NEUTRAL,
) {
    private val paSim = PlateAppearanceSim(balance, tables, environment)
    private val baseRunning = BaseRunning(balance, tables)
    private val strength = StrengthCalculator(balance)

    private val closerLeadMax = balance.int("gameFlow.closerLeadMax")
    private val leagueStealSuccess = balance.double("leagueAverages.stealSuccess")

    private val buntSuccess = balance.double("tactics.buntSuccessRate")
    private val buntForceOut = balance.double("tactics.buntFailureIsForceOut")
    private val buntMaxPower = balance.int("tactics.buntMaxBatterPower")
    private val buntFromInning = balance.int("tactics.buntFromInning")
    private val buntMaxMargin = balance.int("tactics.buntMaxMargin")
    private val ibbFromInning = balance.int("tactics.intentionalWalkFromInning")
    private val ibbMaxMargin = balance.int("tactics.intentionalWalkMaxMargin")
    private val ibbBatterRating = balance.double("tactics.intentionalWalkBatterRating")
    private val buntAttemptBySlider = balance.numericMap("tactics.buntAttemptBySlider")
    private val stealMultiplierBySlider = balance.numericMap("tactics.stealMultiplierBySlider")
    private val ibbBySlider = balance.numericMap("tactics.intentionalWalkBySlider")

    fun simulate(home: GameTeam, away: GameTeam, parkFactor: Double, random: Random): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val state = GameState(rules)
        // 상대 선발의 손을 보고 라인업 한 벌을 고른다 (docs/06 ①)
        val homeSide = SideState(home, isHome = true, opposingStarterHand = away.pitcher(away.startingPitcher).throwsWith)
        val awaySide = SideState(away, isHome = false, opposingStarterHand = home.pitcher(home.startingPitcher).throwsWith)
        baseRunning.runnerSpeedLookup = { runner ->
            ((home.roster[runner.playerId] ?: away.roster[runner.playerId]) as? Batter)
                ?.ratings?.speed ?: LEAGUE_AVERAGE_RATING
        }

        events += GameStarted(home.teamId, away.teamId, homeSide.currentPitcherId, awaySide.currentPitcherId)

        var walkOff = false
        var lastInning = 1
        outer@ for (inning in 1..rules.maxInnings) {
            lastInning = inning
            for (half in Half.entries) {
                state.inning = inning
                state.half = half
                if (half == Half.BOTTOM && inning >= rules.regulationInnings && state.homeScore > state.awayScore) {
                    break@outer
                }
                val batting = if (half == Half.TOP) awaySide else homeSide
                val fielding = if (half == Half.TOP) homeSide else awaySide
                walkOff = playHalfInning(state, batting, fielding, parkFactor, events, random)
                if (walkOff) break@outer
                if (half == Half.BOTTOM && inning >= rules.regulationInnings && state.homeScore != state.awayScore) {
                    break@outer
                }
            }
        }

        homeSide.closeAppearance(state.homeScore - state.awayScore)
        awaySide.closeAppearance(state.awayScore - state.homeScore)

        events += GameEnded(
            homeTeam = home.teamId,
            awayTeam = away.teamId,
            homeScore = state.homeScore,
            awayScore = state.awayScore,
            innings = lastInning,
            walkOff = walkOff,
            tie = state.homeScore == state.awayScore,
        )
        events += decidePitchers(state, homeSide, awaySide)
        return events
    }

    // ---------- 하프 이닝 ----------

    private fun playHalfInning(
        state: GameState,
        batting: SideState,
        fielding: SideState,
        parkFactor: Double,
        events: MutableList<GameEvent>,
        random: Random,
    ): Boolean {
        state.outs = 0
        state.virtualOuts = 0
        state.bases.clear()
        events += HalfInningStarted(state.inning, state.half, batting.team.teamId)

        changePitcherIfNeeded(state, fielding, batting, events, atInningStart = true)
        maybeDefensiveSubstitution(state, fielding, events)

        var runsThisHalf = 0
        var outsThisHalf = 0

        while (state.outs < BaseRunning.OUTS_PER_INNING) {
            val pitcher = fielding.currentPitcher()
            val between = playBetweenPitches(state, batting, fielding, pitcher, events, random)
            runsThisHalf += between.runs
            outsThisHalf += between.outs
            if (state.outs >= BaseRunning.OUTS_PER_INNING) break
            if (isWalkOff(state)) {
                events += HalfInningEnded(state.inning, state.half, batting.team.teamId, runsThisHalf, state.bases.count, outsThisHalf)
                return true
            }

            maybePinchRun(state, batting, events)
            maybePinchHit(state, batting, fielding, events)

            val batter = batting.currentBatter()
            val outsBefore = state.outs
            val basesBefore = state.bases.occupiedCode
            val virtualOutsBefore = state.virtualOuts

            val result = decidePlateAppearance(state, batting, fielding, batter, pitcher, parkFactor, random)
            batting.advanceBattingOrder()

            val batterRunner = Runner(
                playerId = batter.id,
                responsiblePitcherId = pitcher.id,
                reachedByError = result.outcome == PaOutcome.REACHED_ON_ERROR,
            )
            val advance = baseRunning.resolve(result.outcome, state.bases, batter, batterRunner, outsBefore, random)
            val outsRecorded = advance.batterOuts + advance.runnersOut.size

            state.outs += outsRecorded
            state.virtualOuts += outsRecorded
            if (result.outcome == PaOutcome.REACHED_ON_ERROR) state.virtualOuts += 1

            val runs = advance.scored.map { runner ->
                ScoredRun(
                    runnerId = runner.playerId,
                    responsiblePitcherId = runner.responsiblePitcherId,
                    earned = !runner.reachedByError && virtualOutsBefore < BaseRunning.OUTS_PER_INNING,
                )
            }
            applyRuns(state, batting, fielding, runs)
            runsThisHalf += runs.size
            outsThisHalf += outsRecorded
            fielding.currentAppearance().pitches += result.pitches
            fielding.currentAppearance().outs += outsRecorded
            fielding.currentAppearance().battersFaced += 1

            events += PlateAppearanceCompleted(
                inning = state.inning,
                half = state.half,
                battingTeam = batting.team.teamId,
                fieldingTeam = fielding.team.teamId,
                batterId = batter.id,
                batterHand = paSim.effectiveBatterHand(batter, pitcher),
                pitcherId = pitcher.id,
                pitcherHand = pitcher.throwsWith,
                outcome = result.outcome,
                battedBall = result.battedBall,
                pitches = result.pitches,
                rbi = advance.rbi,
                outsRecorded = outsRecorded,
                runnersOutOnBase = advance.runnersOut.size,
                runs = runs,
                errorBy = result.errorBy,
                outsBefore = outsBefore,
                basesBefore = basesBefore,
            )

            if (isWalkOff(state)) {
                events += HalfInningEnded(state.inning, state.half, batting.team.teamId, runsThisHalf, state.bases.count, outsThisHalf)
                return true
            }
            if (state.outs < BaseRunning.OUTS_PER_INNING) {
                changePitcherIfNeeded(state, fielding, batting, events, atInningStart = false)
            }
        }

        events += HalfInningEnded(
            inning = state.inning,
            half = state.half,
            battingTeam = batting.team.teamId,
            runsScored = runsThisHalf,
            leftOnBase = state.bases.count,
            outsRecorded = outsThisHalf,
        )
        return false
    }

    // ---------- 작전 (docs/06 ⑥) ----------

    /** 작전 지시를 먼저 보고, 해당 없으면 보통 타석을 굴린다. */
    private fun decidePlateAppearance(
        state: GameState,
        batting: SideState,
        fielding: SideState,
        batter: Batter,
        pitcher: Pitcher,
        parkFactor: Double,
        random: Random,
    ): PlateAppearanceResult {
        intentionalWalkResult(state, fielding, batter, random)?.let { return it }
        buntResult(state, batting, batter, random)?.let { return it }
        return paSim.simulate(
            batter = batter,
            pitcher = pitcher,
            defense = fielding.defenseContext(),
            basesCode = state.bases.occupiedCode,
            outs = state.outs,
            parkFactor = parkFactor,
            pitcherPitchCount = fielding.currentAppearance().pitches,
            random = random,
        )
    }

    /** 고의사구: 1루가 비고 득점권에 주자가 있는 접전 후반, 강타자를 거른다. */
    private fun intentionalWalkResult(
        state: GameState,
        fielding: SideState,
        batter: Batter,
        random: Random,
    ): PlateAppearanceResult? {
        val slider = fielding.team.directives.tactics.intentionalWalk
        if (state.inning < ibbFromInning) return null
        if (state.bases.isOccupied(1) || state.bases.count == 0) return null
        if (!state.bases.isOccupied(2) && !state.bases.isOccupied(3)) return null
        if (abs(state.scoreMargin) > ibbMaxMargin) return null
        if (strength.overallOf(batter) < ibbBatterRating) return null
        if (!random.chance(sliderValue(ibbBySlider, slider))) return null
        return PlateAppearanceResult(PaOutcome.INTENTIONAL_WALK, null, INTENTIONAL_WALK_PITCHES)
    }

    /** 희생번트: 주자를 보내야 하는 접전 상황에서 장타력이 낮은 타자에게 시킨다. */
    private fun buntResult(
        state: GameState,
        batting: SideState,
        batter: Batter,
        random: Random,
    ): PlateAppearanceResult? {
        val slider = batting.team.directives.tactics.bunt
        if (state.outs >= 2) return null
        if (state.inning < buntFromInning) return null
        if (abs(state.scoreMargin) > buntMaxMargin) return null
        if (!state.bases.isOccupied(1) && !state.bases.isOccupied(2)) return null
        if (state.bases.isOccupied(3)) return null
        if (batter.ratings.power > buntMaxPower) return null
        if (!random.chance(sliderValue(buntAttemptBySlider, slider))) return null

        return if (random.chance(buntSuccess)) {
            PlateAppearanceResult(PaOutcome.SAC_BUNT, BattedBallType.GROUND, BUNT_PITCHES)
        } else if (state.bases.isOccupied(1) && random.chance(buntForceOut)) {
            // 실패: 선행 주자가 잡힌다
            PlateAppearanceResult(PaOutcome.FIELDERS_CHOICE, BattedBallType.GROUND, BUNT_PITCHES)
        } else {
            // 실패: 떠서 잡힌다 (주자는 그대로)
            PlateAppearanceResult(PaOutcome.LINE_OUT, BattedBallType.LINE, BUNT_PITCHES)
        }
    }

    private data class BetweenResult(val runs: Int, val outs: Int)

    /** 타석 사이 사건: 폭투와 도루. 도루 시도는 작전 슬라이더가 배율로 작용한다. */
    private fun playBetweenPitches(
        state: GameState,
        batting: SideState,
        fielding: SideState,
        pitcher: Pitcher,
        events: MutableList<GameEvent>,
        random: Random,
    ): BetweenResult {
        var runs = 0
        var outs = 0
        if (state.bases.count == 0) return BetweenResult(0, 0)
        val defense = fielding.defenseContext()

        if (random.chance(tables.value("pitcherControlToWildPitch", pitcher.ratings.control.toDouble()))) {
            val virtualOutsBefore = state.virtualOuts
            val advance = baseRunning.onWildPitch(state.bases)
            val scored = advance.scored.map {
                ScoredRun(it.playerId, it.responsiblePitcherId, !it.reachedByError && virtualOutsBefore < BaseRunning.OUTS_PER_INNING)
            }
            applyRuns(state, batting, fielding, scored)
            runs += scored.size
            events += WildPitchThrown(state.inning, state.half, fielding.team.teamId, pitcher.id, scored)
        }

        val runner = state.bases[1]
        if (runner != null && state.bases.isEmpty(2) && state.outs < BaseRunning.OUTS_PER_INNING) {
            val speed = batting.team.batterOrNull(runner.playerId)?.ratings?.speed ?: LEAGUE_AVERAGE_RATING
            val multiplier = sliderValue(stealMultiplierBySlider, batting.team.directives.tactics.steal)
            if (random.chance(tables.value("speedToStealAttempt", speed) * multiplier)) {
                val successRate = Log5.rate(
                    tables.value("speedToStealSuccess", speed),
                    tables.value("catcherDefenseToStealSuccess", defense.catcherDefense),
                    leagueStealSuccess,
                )
                val success = random.chance(successRate)
                baseRunning.onSteal(state.bases, 1, success)
                if (!success) {
                    state.outs += 1
                    state.virtualOuts += 1
                    outs += 1
                    fielding.currentAppearance().outs += 1
                }
                events += StolenBaseAttempted(
                    inning = state.inning,
                    half = state.half,
                    battingTeam = batting.team.teamId,
                    runnerId = runner.playerId,
                    pitcherId = pitcher.id,
                    catcherId = fielding.catcherId(),
                    targetBase = 2,
                    success = success,
                )
            }
        }
        return BetweenResult(runs, outs)
    }

    // ---------- 선수 교체 (docs/06 ⑤) ----------

    /** 대타: 후반 접전에서 더 좋은 타자가 벤치에 있으면 바꾼다. 자원이 없으면 그냥 진행한다. */
    private fun maybePinchHit(
        state: GameState,
        batting: SideState,
        fielding: SideState,
        events: MutableList<GameEvent>,
    ) {
        val sub = batting.team.directives.substitution
        if (state.inning < sub.pinchHitFromInning) return
        val margin = state.scoreMargin
        if (margin < -sub.pinchHitMaxDeficit) return
        if (sub.pinchHitOnlyInScoringPosition && !state.bases.isOccupied(2) && !state.bases.isOccupied(3)) return

        val current = batting.currentBatter()
        val pitcherHand = fielding.currentPitcher().throwsWith
        val currentRating = strength.overallOf(current)

        val candidate = batting.benchBatters()
            .filter { !sub.pinchHitPlatoonOnly || hasPlatoonAdvantage(it, pitcherHand) }
            .maxByOrNull { strength.overallOf(it) }
            ?: return
        if (strength.overallOf(candidate) - currentRating < sub.pinchHitRatingGap) return

        batting.substitute(current.id, candidate.id)
        events += PlayerSubstituted(
            state.inning, state.half, batting.team.teamId, current.id, candidate.id, SubstitutionKind.PINCH_HITTER,
        )
    }

    /** 대주자: 후반 접전에서 느린 주자를 빠른 선수로 바꾼다. */
    private fun maybePinchRun(state: GameState, batting: SideState, events: MutableList<GameEvent>) {
        val sub = batting.team.directives.substitution
        if (state.inning < sub.pinchRunFromInning) return
        if (abs(state.scoreMargin) > sub.pinchRunMaxMargin) return
        if (state.bases.count == 0) return

        for (base in BaseState.BASE_COUNT downTo 1) {
            val runner = state.bases[base] ?: continue
            val currentRunner = batting.team.batterOrNull(runner.playerId) ?: continue
            if (!batting.isInLineup(currentRunner.id)) continue
            val candidate = batting.benchBatters().maxByOrNull { it.ratings.speed } ?: return
            if (candidate.ratings.speed - currentRunner.ratings.speed < sub.pinchRunSpeedGap) continue

            batting.substitute(currentRunner.id, candidate.id)
            state.bases[base] = runner.copy(playerId = candidate.id)
            events += PlayerSubstituted(
                state.inning, state.half, batting.team.teamId, currentRunner.id, candidate.id,
                SubstitutionKind.PINCH_RUNNER,
            )
            return
        }
    }

    /** 대수비: 리드한 후반에 수비가 약한 자리를 보강한다. */
    private fun maybeDefensiveSubstitution(state: GameState, fielding: SideState, events: MutableList<GameEvent>) {
        val sub = fielding.team.directives.substitution
        if (state.inning < sub.defensiveSubFromInning) return
        val lead = if (fielding.isHome) state.homeScore - state.awayScore else state.awayScore - state.homeScore
        if (lead < sub.defensiveSubMinLead) return

        for (slot in fielding.lineup()) {
            if (slot.position == Position.DESIGNATED_HITTER) continue
            val current = fielding.team.batterOrNull(slot.playerId) ?: continue
            val currentFitness = current.defenseFitness[slot.position] ?: continue
            val candidate = fielding.benchBatters()
                .maxByOrNull { it.defenseFitness[slot.position] ?: 0 } ?: return
            val candidateFitness = candidate.defenseFitness[slot.position] ?: 0
            if (candidateFitness - currentFitness < sub.defensiveSubDefenseGap) continue

            fielding.substitute(current.id, candidate.id)
            events += PlayerSubstituted(
                state.inning, state.half, fielding.team.teamId, current.id, candidate.id, SubstitutionKind.DEFENSIVE,
            )
            return
        }
    }

    private fun hasPlatoonAdvantage(batter: Batter, pitcherHand: Hand): Boolean = when (batter.bats) {
        Hand.SWITCH -> true
        else -> batter.bats != pitcherHand
    }

    // ---------- 투수 교체 (docs/06 ③④) ----------

    private fun changePitcherIfNeeded(
        state: GameState,
        fielding: SideState,
        batting: SideState,
        events: MutableList<GameEvent>,
        atInningStart: Boolean,
    ) {
        val current = fielding.currentPitcher()
        val appearance = fielding.currentAppearance()
        val sheet = fielding.team.directives
        val lead = if (fielding.isHome) state.homeScore - state.awayScore else state.awayScore - state.homeScore

        val shouldPull = if (appearance.isStarter) {
            // 먼저 충족되는 조건이 적용된다 (docs/06 ③)
            appearance.pitches >= sheet.starterHook.pitchLimit ||
                appearance.runs >= sheet.starterHook.runsAllowedLimit ||
                current.condition.fatigue >= sheet.starterHook.fatigueLimit ||
                (
                    sheet.starterHook.pullOnThirdTimeThroughOrder && atInningStart &&
                        appearance.battersFaced >= LineupPlan.SIZE * 2
                    )
        } else {
            appearance.pitches >= relieverPitchLimit(current) ||
                (atInningStart && appearance.outs >= BaseRunning.OUTS_PER_INNING)
        }

        val role = neededRole(state, fielding, batting, lead, atInningStart)
        val roleMismatch = atInningStart && !appearance.isStarter &&
            role == BullpenRole.CLOSER && !fielding.isRole(current.id, BullpenRole.CLOSER)

        if (!shouldPull && !roleMismatch) return

        val choice = fielding.chooseReliever(role) ?: return
        if (choice.pitcherId == current.id) return

        fielding.closeAppearance(lead)
        fielding.enterPitcher(choice.pitcherId, leadAtEntry = lead, runnersAtEntry = state.bases.count)
        events += PitcherChanged(state.inning, state.half, fielding.team.teamId, current.id, choice.pitcherId)
        if (choice.forced) {
            events += PitcherForcedIn(state.inning, state.half, fielding.team.teamId, choice.pitcherId)
        }
    }

    /**
     * 지금 상황에 맞는 불펜 역할 (docs/06 ④ 표).
     * 마무리(9회+ 3점 차 이내) → 셋업(8회) → 필승조(6~7회 접전) → 좌완 스페셜리스트 → 롱릴리프 → 추격조.
     */
    private fun neededRole(
        state: GameState,
        fielding: SideState,
        batting: SideState,
        lead: Int,
        atInningStart: Boolean,
    ): BullpenRole = when {
        state.inning >= rules.regulationInnings && lead in 1..closerLeadMax -> BullpenRole.CLOSER
        state.inning == rules.regulationInnings - 1 && lead in 1..closerLeadMax -> BullpenRole.SETUP
        !atInningStart && state.inning >= LATE_INNING && abs(lead) <= CLOSE_GAME_MARGIN &&
            batting.nextBatterBats() == Hand.LEFT -> BullpenRole.LEFTY_SPECIALIST
        state.inning in HIGH_LEVERAGE_INNINGS && lead >= 0 && abs(lead) <= CLOSE_GAME_MARGIN -> BullpenRole.HIGH_LEVERAGE
        state.inning < HIGH_LEVERAGE_INNINGS.first && fielding.currentAppearance().isStarter -> BullpenRole.LONG_RELIEF
        state.inning > rules.regulationInnings -> BullpenRole.LONG_RELIEF
        lead < 0 -> BullpenRole.MOP_UP
        else -> BullpenRole.HIGH_LEVERAGE
    }

    private fun relieverPitchLimit(pitcher: Pitcher): Int =
        (paSim.pitchLimitOf(pitcher) * RELIEVER_PITCH_SHARE).toInt().coerceAtLeast(MIN_RELIEVER_PITCHES)

    // ---------- 득점·승패 ----------

    private fun applyRuns(state: GameState, batting: SideState, fielding: SideState, runs: List<ScoredRun>) {
        if (runs.isEmpty()) return
        for (run in runs) {
            val before = state.homeScore - state.awayScore
            state.addRuns(1)
            val after = state.homeScore - state.awayScore
            fielding.recordRun(run)
            val battingIsHome = state.battingIsHome
            val tookLead = if (battingIsHome) before <= 0 && after > 0 else before >= 0 && after < 0
            if (tookLead) {
                winCandidate = batting.currentPitcherId
                lossCandidate = run.responsiblePitcherId
            }
        }
    }

    private fun isWalkOff(state: GameState): Boolean =
        state.half == Half.BOTTOM && state.inning >= rules.regulationInnings && state.homeScore > state.awayScore

    private var winCandidate: PlayerId? = null
    private var lossCandidate: PlayerId? = null

    private fun decidePitchers(state: GameState, home: SideState, away: SideState): PitcherDecisionsDecided {
        if (state.homeScore == state.awayScore) {
            winCandidate = null
            lossCandidate = null
            return PitcherDecisionsDecided(null, null, null, emptyList())
        }
        val winnerTeam = if (state.homeScore > state.awayScore) home else away
        val loserTeam = if (state.homeScore > state.awayScore) away else home

        var winner = winCandidate?.takeIf { id -> winnerTeam.appearances.any { it.pitcherId == id } }
            ?: winnerTeam.appearances.first().pitcherId
        val loser = lossCandidate?.takeIf { id -> loserTeam.appearances.any { it.pitcherId == id } }
            ?: loserTeam.appearances.first().pitcherId

        val winnerAppearance = winnerTeam.appearances.first { it.pitcherId == winner }
        if (winnerAppearance.isStarter && winnerAppearance.outs < STARTER_WIN_OUTS) {
            val best = winnerTeam.appearances.filter { !it.isStarter }
                .maxWithOrNull(compareBy({ it.outs }, { -it.runs }))
            if (best != null) winner = best.pitcherId
        }

        val last = winnerTeam.appearances.last()
        val save = when {
            last.pitcherId == winner -> null
            last.leadAtExit <= 0 -> null
            last.outs >= LONG_SAVE_OUTS -> last.pitcherId
            last.leadAtEntry in 1..closerLeadMax && last.outs >= BaseRunning.OUTS_PER_INNING -> last.pitcherId
            last.leadAtEntry in 1..(last.runnersAtEntry + 1) -> last.pitcherId
            else -> null
        }

        val holds = winnerTeam.appearances
            .filter {
                !it.isStarter && it.pitcherId != winner && it.pitcherId != save &&
                    it.leadAtEntry in 1..closerLeadMax && it.outs >= 1 && it.leadAtExit > 0
            }
            .map { it.pitcherId }

        return PitcherDecisionsDecided(winner, loser, save, holds)
    }

    private fun sliderValue(table: Map<Int, Double>, slider: Int): Double {
        table[slider]?.let { return it }
        val keys = table.keys.sorted()
        val low = keys.lastOrNull { it <= slider } ?: keys.first()
        val high = keys.firstOrNull { it >= slider } ?: keys.last()
        if (low == high) return table.getValue(low)
        val ratio = (slider - low).toDouble() / (high - low)
        return table.getValue(low) + (table.getValue(high) - table.getValue(low)) * ratio
    }

    // ---------- 팀 런타임 ----------

    private class Appearance(
        val pitcherId: PlayerId,
        val isStarter: Boolean,
        val leadAtEntry: Int,
        val runnersAtEntry: Int,
    ) {
        var outs: Int = 0
        var runs: Int = 0
        var pitches: Int = 0
        var battersFaced: Int = 0
        var leadAtExit: Int = 0
    }

    /** 한 투수를 올리기로 한 결정. [forced] 면 제한을 무시하고 올린 것이다. */
    private data class RelieverChoice(val pitcherId: PlayerId, val forced: Boolean)

    private inner class SideState(
        val team: GameTeam,
        val isHome: Boolean,
        opposingStarterHand: Hand,
    ) {
        private val lineup: MutableList<LineupSlot> =
            team.directives.lineupFor(opposingStarterHand).slots.toMutableList()
        private val bench: MutableList<PlayerId>
        private val usedPlayers = mutableSetOf<PlayerId>()
        private var battingIndex = 0
        val appearances = mutableListOf<Appearance>()
        var currentPitcherId: PlayerId = team.startingPitcher
            private set

        init {
            usedPlayers += lineup.map { it.playerId }
            usedPlayers += currentPitcherId
            // 벤치는 **1군에 등록돼 있고 지금 뛸 수 있는** 야수만이다.
            // 선수단 전체에서 고르면 2군 선수나 부상자가 대타로 나가 버린다.
            bench = team.roster.values
                .filterIsInstance<Batter>()
                .filter {
                    it.id !in usedPlayers &&
                        it.rosterLevel == baseballgm.model.RosterLevel.FIRST_TEAM &&
                        it.military.isAvailable &&
                        !it.condition.isInjured
                }
                .sortedByDescending { strength.overallOf(it) }
                .map { it.id }
                .toMutableList()
            appearances += Appearance(currentPitcherId, isStarter = true, leadAtEntry = 0, runnersAtEntry = 0)
        }

        fun lineup(): List<LineupSlot> = lineup

        fun currentPitcher(): Pitcher = team.pitcher(currentPitcherId)

        fun currentAppearance(): Appearance = appearances.last()

        fun currentBatter(): Batter = team.batter(lineup[battingIndex].playerId)

        fun advanceBattingOrder() {
            battingIndex = (battingIndex + 1) % LineupPlan.SIZE
        }

        fun nextBatterBats(): Hand = team.batterOrNull(lineup[battingIndex].playerId)?.bats ?: Hand.RIGHT

        fun isInLineup(playerId: PlayerId): Boolean = lineup.any { it.playerId == playerId }

        fun benchBatters(): List<Batter> = bench.mapNotNull { team.batterOrNull(it) }

        /** 교체: 나간 선수는 다시 못 들어온다. 수비 위치는 그대로 물려받는다. */
        fun substitute(leaving: PlayerId, entering: PlayerId) {
            val index = lineup.indexOfFirst { it.playerId == leaving }
            if (index < 0) return
            lineup[index] = lineup[index].copy(playerId = entering)
            bench -= entering
            usedPlayers += entering
        }

        fun recordRun(run: ScoredRun) {
            appearances.lastOrNull { it.pitcherId == run.responsiblePitcherId }?.let { it.runs += 1 }
        }

        fun closeAppearance(lead: Int) {
            appearances.last().leadAtExit = lead
        }

        fun enterPitcher(id: PlayerId, leadAtEntry: Int, runnersAtEntry: Int) {
            currentPitcherId = id
            usedPlayers += id
            appearances += Appearance(id, isStarter = false, leadAtEntry = leadAtEntry, runnersAtEntry = runnersAtEntry)
        }

        fun hasPitched(id: PlayerId): Boolean = appearances.any { it.pitcherId == id }

        fun isRole(id: PlayerId, role: BullpenRole): Boolean = team.directives.bullpen.candidates(role).contains(id)

        /**
         * 다음 투수를 고른다 (docs/06 예외 처리).
         *
         * ① 역할에 맞는 투수 → ② 규칙을 지킬 수 있는 아무 투수 → ③ 추격조 →
         * ④ 제한을 무시하고 아무나 (부상 위험을 감수한 무리한 등판) → ⑤ 아무도 없으면 교체하지 않는다.
         */
        fun chooseReliever(role: BullpenRole): RelieverChoice? {
            val plan = team.directives.bullpen
            val roleCandidates = plan.candidates(role).filter { canPitch(it, plan) }
            roleCandidates.firstOrNull()?.let { return RelieverChoice(it, forced = false) }

            val anyRested = plan.roles.values.flatten().distinct().filter { canPitch(it, plan) }
            anyRested.firstOrNull()?.let { return RelieverChoice(it, forced = false) }

            val mopUp = plan.candidates(BullpenRole.MOP_UP).firstOrNull { isFreshBody(it) }
            mopUp?.let { return RelieverChoice(it, forced = false) }

            // 제한 무시: 아직 안 던진 투수라면 누구든 올린다
            val forced = team.roster.values.filterIsInstance<Pitcher>()
                .map { it.id }
                .firstOrNull { isFreshBody(it) }
            return forced?.let { RelieverChoice(it, forced = true) }
        }

        private fun canPitch(id: PlayerId, plan: baseballgm.tactics.BullpenPlan): Boolean {
            if (!isFreshBody(id)) return false
            val availability = team.availability
            if ((availability.consecutiveDays[id] ?: 0) >= plan.maxConsecutiveDays) return false
            if ((availability.pitchesLast7Days[id] ?: 0) >= plan.maxPitchesLast7Days) return false
            return true
        }

        /** 이 경기에서 아직 안 던졌고, 부상·말소로 빠진 선수도 아닌가. */
        private fun isFreshBody(id: PlayerId): Boolean {
            if (hasPitched(id)) return false
            if (id in team.availability.unavailable) return false
            val pitcher = team.pitcherOrNull(id) ?: return false
            if (pitcher.condition.isInjured) return false
            if (id == team.startingPitcher) return false
            return true
        }

        fun catcherId(): PlayerId =
            lineup.firstOrNull { it.position == Position.CATCHER }?.playerId ?: lineup.first().playerId

        fun defenseContext(): DefenseContext {
            val infield = lineup.filter { it.position in INFIELD }.map { it.playerId }
            val outfield = lineup.filter { it.position in OUTFIELD }.map { it.playerId }
            return DefenseContext(
                infieldDefense = averageDefense(infield),
                outfieldDefense = averageDefense(outfield),
                catcherDefense = averageDefense(listOf(catcherId())),
                infielders = infield,
                outfielders = outfield,
            )
        }

        private fun averageDefense(ids: List<PlayerId>): Double {
            val values = ids.mapNotNull { team.batterOrNull(it)?.ratings?.defense }
            return if (values.isEmpty()) LEAGUE_AVERAGE_RATING.toDouble() else values.average()
        }
    }

    companion object {
        private const val LEAGUE_AVERAGE_RATING = 50
        private const val STARTER_WIN_OUTS = 15
        private const val LONG_SAVE_OUTS = 9
        private const val INTENTIONAL_WALK_PITCHES = 4
        private const val BUNT_PITCHES = 2
        private const val RELIEVER_PITCH_SHARE = 0.4
        private const val MIN_RELIEVER_PITCHES = 20
        private const val LATE_INNING = 7
        private const val CLOSE_GAME_MARGIN = 2
        private val HIGH_LEVERAGE_INNINGS = 6..7
        private val INFIELD = setOf(
            Position.CATCHER, Position.FIRST_BASE, Position.SECOND_BASE,
            Position.THIRD_BASE, Position.SHORTSTOP,
        )
        private val OUTFIELD = setOf(Position.LEFT_FIELD, Position.CENTER_FIELD, Position.RIGHT_FIELD)
    }
}
