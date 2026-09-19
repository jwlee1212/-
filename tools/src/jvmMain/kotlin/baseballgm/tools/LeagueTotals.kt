package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.sim.PitcherChanged
import baseballgm.sim.PitcherForcedIn
import baseballgm.sim.PlayerSubstituted
import baseballgm.sim.SubstitutionKind
import baseballgm.stats.BattingLine
import baseballgm.stats.BoxScore
import baseballgm.stats.PitchingLine

/**
 * 여러 경기 기록을 합쳐 리그 평균을 낸다 (docs/04 캘리브레이션 목표와 비교용).
 * M4 의 `Calibrator` 가 이 값을 보고 변환표를 조정하게 된다.
 */
class LeagueTotals {
    private var batting = BattingLine.EMPTY
    private var pitching = PitchingLine.EMPTY
    private var games = 0
    private var innings = 0
    private var extraInningGames = 0
    private var ties = 0
    private var walkOffs = 0
    private var errors = 0
    private var pitchingChanges = 0
    private var forcedPitchers = 0
    private val substitutions = mutableMapOf<SubstitutionKind, Int>()

    /** 규칙표가 실제로 작동하는지 보려면 박스스코어만으로는 부족해서 이벤트도 함께 센다. */
    fun add(played: PlayedGame) {
        add(played.box)
        played.events.forEach { event ->
            when (event) {
                is PitcherChanged -> pitchingChanges++
                is PitcherForcedIn -> forcedPitchers++
                is PlayerSubstituted -> substitutions[event.kind] = (substitutions[event.kind] ?: 0) + 1
                else -> Unit
            }
        }
    }

    fun add(box: BoxScore) {
        listOf(box.home, box.away).forEach { team ->
            batting += team.battingTotal
            pitching += team.pitchingTotal
            errors += team.errors
        }
        games++
        innings += box.innings
        if (box.innings > 9) extraInningGames++
        if (box.tie) ties++
        if (box.walkOff) walkOffs++
    }

    /** 경기당 팀 득점 (두 팀 합산을 2로 나눈 값). */
    private val runsPerTeamGame: Double get() = if (games == 0) 0.0 else pitching.runs.toDouble() / (games * 2)

    /** 캘리브레이션 목표와 비교할 지표들 (docs/04). */
    fun calibrationLines(balance: BalanceConfig): List<CalibrationLine> {
        val pa = batting.plateAppearances.toDouble()
        return listOf(
            CalibrationLine("리그 타율", batting.battingAverage, balance.doubleRange("leagueTargets.battingAverage"), "%.3f"),
            CalibrationLine("삼진 비율", batting.strikeouts / pa, balance.doubleRange("leagueTargets.kRate"), "%.3f"),
            CalibrationLine("볼넷 비율", batting.walks / pa, balance.doubleRange("leagueTargets.bbRate"), "%.3f"),
            CalibrationLine("경기당 팀 득점", runsPerTeamGame, balance.doubleRange("leagueTargets.runsPerTeamGame"), "%.2f"),
            CalibrationLine(
                "팀당 시즌 홈런",
                batting.homeRuns.toDouble() / (games * 2) * SEASON_GAMES,
                balance.doubleRange("leagueTargets.teamHomeRunsPerSeason"),
                "%.0f",
            ),
        )
    }

    /** 목표는 없지만 눈으로 확인할 값들. */
    fun extraLines(): List<String> = listOf(
        "출루율 ${"%.3f".format(batting.onBasePercentage)} 장타율 ${"%.3f".format(batting.sluggingPercentage)} " +
            "경기당 안타 ${"%.1f".format(batting.hits.toDouble() / games / 2)} 실책 ${"%.2f".format(errors.toDouble() / games)} " +
            "도루 ${"%.2f".format(batting.stolenBases.toDouble() / games)}",
        "평균자책 ${"%.2f".format(pitching.era)} WHIP ${"%.2f".format(pitching.whip)} " +
            "경기당 투구수 ${"%.0f".format(pitching.pitches.toDouble() / games / 2)} " +
            "평균 이닝 ${"%.2f".format(innings.toDouble() / games)} 무승부 ${"%.1f".format(ties.toDouble() / games * 100)}%",
        "희생번트 ${"%.2f".format(batting.sacBunts.toDouble() / games)} 고의사구 ${"%.2f".format(batting.intentionalWalks.toDouble() / games)} " +
            "병살 ${"%.2f".format(batting.doublePlays.toDouble() / games)} (경기당)",
    )

    fun report(balance: BalanceConfig): String {
        val pa = batting.plateAppearances.toDouble()
        val kRate = batting.strikeouts / pa
        val bbRate = batting.walks / pa
        val average = batting.battingAverage
        val homeRunsPerTeamSeason = batting.homeRuns.toDouble() / (games * 2) * SEASON_GAMES
        val babip = (batting.hits - batting.homeRuns).toDouble() /
            (batting.atBats - batting.strikeouts - batting.homeRuns + batting.sacFlies)

        fun line(name: String, value: Double, range: ClosedFloatingPointRange<Double>, format: String = "%.3f"): String {
            val mark = if (value in range) "OK " else "!! "
            return "$mark${name.padEnd(16)} ${format.format(value)}  목표 ${format.format(range.start)}~${format.format(range.endInclusive)}"
        }

        return buildString {
            appendLine("=== 리그 평균 ($games 경기) ===")
            appendLine(line("리그 타율", average, balance.doubleRange("leagueTargets.battingAverage")))
            appendLine(line("삼진 비율", kRate, balance.doubleRange("leagueTargets.kRate")))
            appendLine(line("볼넷 비율", bbRate, balance.doubleRange("leagueTargets.bbRate")))
            appendLine(line("경기당 팀 득점", runsPerTeamGame, balance.doubleRange("leagueTargets.runsPerTeamGame"), "%.2f"))
            appendLine(
                line(
                    "팀당 시즌 홈런",
                    homeRunsPerTeamSeason,
                    balance.doubleRange("leagueTargets.teamHomeRunsPerSeason"),
                    "%.0f",
                ),
            )
            appendLine("   출루율 ${"%.3f".format(batting.onBasePercentage)} 장타율 ${"%.3f".format(batting.sluggingPercentage)} BABIP ${"%.3f".format(babip)}")
            appendLine("   경기당 안타 ${"%.1f".format(batting.hits.toDouble() / games / 2)} 2루타 ${"%.1f".format(batting.doubles.toDouble() / games / 2)} 3루타 ${"%.2f".format(batting.triples.toDouble() / games / 2)}")
            appendLine("   경기당 실책 ${"%.2f".format(errors.toDouble() / games)} 도루 ${"%.2f".format(batting.stolenBases.toDouble() / games)} (성공률 ${"%.1f".format(stealRate() * 100)}%)")
            appendLine("   경기당 투구수 ${"%.0f".format(pitching.pitches.toDouble() / games / 2)} 평균자책 ${"%.2f".format(pitching.era)} WHIP ${"%.2f".format(pitching.whip)}")
            appendLine("   평균 이닝 ${"%.2f".format(innings.toDouble() / games)} · 연장 $extraInningGames · 무승부 $ties · 끝내기 $walkOffs")
            appendLine("   희생플라이 ${batting.sacFlies} 병살 ${batting.doublePlays} 실책출루 ${batting.reachedOnError} 낫아웃 ${batting.strikeoutReached} 타격방해 ${batting.catcherInterference}")
            appendLine("   [규칙표] 희생번트 ${batting.sacBunts} 고의사구 ${batting.intentionalWalks} · 경기당 투수교체 ${"%.2f".format(pitchingChanges.toDouble() / games / 2)} (무리한 등판 $forcedPitchers)")
            appendLine("   [규칙표] 대타 ${substitutions[SubstitutionKind.PINCH_HITTER] ?: 0} 대주자 ${substitutions[SubstitutionKind.PINCH_RUNNER] ?: 0} 대수비 ${substitutions[SubstitutionKind.DEFENSIVE] ?: 0}")
        }
    }

    private fun stealRate(): Double {
        val attempts = batting.stolenBases + batting.caughtStealing
        return if (attempts == 0) 0.0 else batting.stolenBases.toDouble() / attempts
    }

    private companion object {
        const val SEASON_GAMES = 144
    }
}
