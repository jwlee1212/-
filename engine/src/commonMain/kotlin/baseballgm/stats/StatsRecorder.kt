package baseballgm.stats

import baseballgm.model.Hand
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.sim.GameEnded
import baseballgm.sim.GameEvent
import baseballgm.sim.GameStarted
import baseballgm.sim.HalfInningEnded
import baseballgm.sim.PaOutcome
import baseballgm.sim.PitcherDecisionsDecided
import baseballgm.sim.PlateAppearanceCompleted
import baseballgm.sim.ScoredRun
import baseballgm.sim.StolenBaseAttempted
import baseballgm.sim.WildPitchThrown

/**
 * 이벤트 스트림 → 박스스코어 (docs/05).
 *
 * 시뮬레이터가 만든 이벤트를 **읽기만** 해서 기록을 만든다. 시뮬레이터가 기록을 직접 쌓지 않는
 * 이유는, 문자 중계·검증기와 같은 스트림을 쓰게 해서 "관전한 경기와 건너뛴 경기의 기록이 다른"
 * 사고를 구조적으로 막기 위해서다 (불변 원칙 5).
 */
object StatsRecorder {

    fun record(events: List<GameEvent>): BoxScore {
        val start = events.filterIsInstance<GameStarted>().firstOrNull()
            ?: error("GameStarted 이벤트가 없다")
        val home = TeamAccumulator(start.homeTeam)
        val away = TeamAccumulator(start.awayTeam)
        home.markStarter(start.homeStarter)
        away.markStarter(start.awayStarter)

        var innings = 0
        var walkOff = false
        var tie = false
        var winningPitcher: PlayerId? = null
        var losingPitcher: PlayerId? = null
        var savePitcher: PlayerId? = null
        var holds: List<PlayerId> = emptyList()

        for (event in events) {
            when (event) {
                is GameStarted -> Unit

                is PlateAppearanceCompleted -> {
                    val batting = if (event.battingTeam == home.teamId) home else away
                    val fielding = if (event.fieldingTeam == home.teamId) home else away
                    batting.recordBatter(event)
                    fielding.recordPitcher(event)
                    batting.recordRuns(event.runs)
                    fielding.recordRunsAllowed(event.runs)
                    event.errorBy?.let { fielding.errors += 1 }
                    // 아웃을 잡은 것은 수비 팀이지만, 아웃된 주자는 공격 팀 소속이다.
                    // 주자 보존 등식은 공격 팀 기준이라 공격 팀에 쌓는다.
                    batting.runnersOutOnBase += event.runnersOutOnBase
                }

                is StolenBaseAttempted -> {
                    val batting = if (event.battingTeam == home.teamId) home else away
                    val fielding = if (event.battingTeam == home.teamId) away else home
                    batting.recordSteal(event)
                    if (!event.success) {
                        fielding.addOutsTo(event.pitcherId, 1)
                        batting.runnersOutOnBase += 1
                    }
                }

                is WildPitchThrown -> {
                    val fielding = if (event.fieldingTeam == home.teamId) home else away
                    val batting = if (event.fieldingTeam == home.teamId) away else home
                    batting.recordRuns(event.runs)
                    fielding.recordRunsAllowed(event.runs)
                }

                is HalfInningEnded -> {
                    val batting = if (event.battingTeam == home.teamId) home else away
                    val fielding = if (event.battingTeam == home.teamId) away else home
                    batting.addInningRuns(event.inning, event.runsScored)
                    batting.leftOnBase += event.leftOnBase
                    fielding.halfInningOuts += event.outsRecorded
                }

                is GameEnded -> {
                    innings = event.innings
                    walkOff = event.walkOff
                    tie = event.tie
                }

                is PitcherDecisionsDecided -> {
                    winningPitcher = event.winningPitcher
                    losingPitcher = event.losingPitcher
                    savePitcher = event.savePitcher
                    holds = event.holdPitchers
                }

                else -> Unit
            }
        }

        winningPitcher?.let { home.addDecision(it, wins = 1); away.addDecision(it, wins = 1) }
        losingPitcher?.let { home.addDecision(it, losses = 1); away.addDecision(it, losses = 1) }
        savePitcher?.let { home.addDecision(it, saves = 1); away.addDecision(it, saves = 1) }
        holds.forEach { home.addDecision(it, holds = 1); away.addDecision(it, holds = 1) }

        return BoxScore(
            home = home.toBoxScore(innings),
            away = away.toBoxScore(innings),
            innings = innings,
            walkOff = walkOff,
            tie = tie,
            winningPitcher = winningPitcher,
            losingPitcher = losingPitcher,
            savePitcher = savePitcher,
            holdPitchers = holds,
        )
    }

    /** 팀 하나의 기록을 모으는 상자. 이벤트를 순서대로 먹인다. */
    private class TeamAccumulator(val teamId: TeamId) {
        val batting = mutableMapOf<PlayerId, PlayerBatting>()
        val pitching = mutableMapOf<PlayerId, PlayerPitching>()
        val inningRuns = mutableListOf<Int>()
        val halfInningOuts = mutableListOf<Int>()
        var leftOnBase = 0
        var runnersOutOnBase = 0
        var errors = 0

        fun markStarter(pitcherId: PlayerId) {
            pitching[pitcherId] = PlayerPitching(unsplit = PitchingLine(gamesStarted = 1, games = 1))
        }

        fun recordBatter(event: PlateAppearanceCompleted) {
            val hand = event.pitcherHand
            val line = battingLineFor(event)
            update(event.batterId, hand) { it + line }
        }

        /** 득점은 상대 투수의 손과 무관하므로 좌우 칸이 아니라 unsplit 에 쌓는다. */
        fun recordRuns(runs: List<ScoredRun>) {
            runs.forEach { run ->
                val current = batting[run.runnerId] ?: PlayerBatting()
                batting[run.runnerId] = current.copy(unsplit = current.unsplit + BattingLine(runs = 1))
            }
        }

        fun recordSteal(event: StolenBaseAttempted) {
            val current = batting[event.runnerId] ?: PlayerBatting()
            val add = if (event.success) BattingLine(stolenBases = 1) else BattingLine(caughtStealing = 1)
            batting[event.runnerId] = current.copy(unsplit = current.unsplit + add)
        }

        fun recordPitcher(event: PlateAppearanceCompleted) {
            val line = pitchingLineFor(event)
            val current = pitching[event.pitcherId] ?: PlayerPitching()
            val withGame = if (current.unsplit.games == 0) {
                current.copy(unsplit = current.unsplit + PitchingLine(games = 1))
            } else {
                current
            }
            pitching[event.pitcherId] = when (event.batterHand) {
                Hand.LEFT -> withGame.copy(vsLeft = withGame.vsLeft + line)
                else -> withGame.copy(vsRight = withGame.vsRight + line)
            }
        }

        fun recordRunsAllowed(runs: List<ScoredRun>) {
            runs.forEach { run ->
                val current = pitching[run.responsiblePitcherId] ?: PlayerPitching()
                pitching[run.responsiblePitcherId] = current.copy(
                    unsplit = current.unsplit + PitchingLine(runs = 1, earnedRuns = if (run.earned) 1 else 0),
                )
            }
        }

        fun addOutsTo(pitcherId: PlayerId, outs: Int) {
            val current = pitching[pitcherId] ?: PlayerPitching()
            pitching[pitcherId] = current.copy(unsplit = current.unsplit + PitchingLine(outs = outs))
        }

        fun addDecision(pitcherId: PlayerId, wins: Int = 0, losses: Int = 0, saves: Int = 0, holds: Int = 0) {
            val current = pitching[pitcherId] ?: return
            pitching[pitcherId] = current.copy(
                unsplit = current.unsplit + PitchingLine(wins = wins, losses = losses, saves = saves, holds = holds),
            )
        }

        fun addInningRuns(inning: Int, runs: Int) {
            while (inningRuns.size < inning) inningRuns += 0
            inningRuns[inning - 1] = inningRuns[inning - 1] + runs
        }

        fun toBoxScore(innings: Int): TeamBoxScore {
            while (inningRuns.size < innings) inningRuns += 0
            return TeamBoxScore(
                teamId = teamId,
                batting = batting.toMap(),
                pitching = pitching,
                inningRuns = inningRuns.toList(),
                halfInningOuts = halfInningOuts.toList(),
                leftOnBase = leftOnBase,
                runnersOutOnBase = runnersOutOnBase,
                errors = errors,
            )
        }

        private fun update(playerId: PlayerId, pitcherHand: Hand, transform: (BattingLine) -> BattingLine) {
            val current = batting[playerId] ?: PlayerBatting()
            batting[playerId] = when (pitcherHand) {
                Hand.LEFT -> current.copy(vsLeft = transform(current.vsLeft))
                else -> current.copy(vsRight = transform(current.vsRight))
            }
        }
    }

    private fun battingLineFor(event: PlateAppearanceCompleted): BattingLine {
        val outcome = event.outcome
        return BattingLine(
            plateAppearances = 1,
            atBats = if (outcome.isAtBat) 1 else 0,
            hits = if (outcome.isHit) 1 else 0,
            doubles = if (outcome == PaOutcome.DOUBLE) 1 else 0,
            triples = if (outcome == PaOutcome.TRIPLE) 1 else 0,
            homeRuns = if (outcome == PaOutcome.HOME_RUN) 1 else 0,
            rbi = event.rbi,
            walks = if (outcome.isWalk) 1 else 0,
            intentionalWalks = if (outcome == PaOutcome.INTENTIONAL_WALK) 1 else 0,
            hitByPitch = if (outcome == PaOutcome.HIT_BY_PITCH) 1 else 0,
            strikeouts = if (outcome.isStrikeout) 1 else 0,
            sacFlies = if (outcome == PaOutcome.SAC_FLY) 1 else 0,
            sacBunts = if (outcome == PaOutcome.SAC_BUNT) 1 else 0,
            catcherInterference = if (outcome == PaOutcome.CATCHER_INTERFERENCE) 1 else 0,
            reachedOnError = if (outcome == PaOutcome.REACHED_ON_ERROR) 1 else 0,
            fieldersChoice = if (outcome == PaOutcome.FIELDERS_CHOICE) 1 else 0,
            strikeoutReached = if (outcome == PaOutcome.STRIKEOUT_REACHED) 1 else 0,
            groundOuts = if (outcome == PaOutcome.GROUND_OUT) 1 else 0,
            flyOuts = if (outcome == PaOutcome.FLY_OUT || outcome == PaOutcome.SAC_FLY) 1 else 0,
            lineOuts = if (outcome == PaOutcome.LINE_OUT) 1 else 0,
            doublePlays = if (outcome == PaOutcome.DOUBLE_PLAY) 1 else 0,
        )
    }

    private fun pitchingLineFor(event: PlateAppearanceCompleted): PitchingLine = PitchingLine(
        outs = event.outsRecorded,
        battersFaced = 1,
        hits = if (event.outcome.isHit) 1 else 0,
        homeRuns = if (event.outcome == PaOutcome.HOME_RUN) 1 else 0,
        walks = if (event.outcome.isWalk) 1 else 0,
        hitByPitch = if (event.outcome == PaOutcome.HIT_BY_PITCH) 1 else 0,
        strikeouts = if (event.outcome.isStrikeout) 1 else 0,
        pitches = event.pitches,
    )
}
