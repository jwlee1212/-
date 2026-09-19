package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.model.RosterLevel

/** 캘리브레이션 결과 한 줄. */
data class CalibrationLine(
    val name: String,
    val value: Double,
    val target: ClosedFloatingPointRange<Double>,
    val format: String,
) {
    val inRange: Boolean get() = value in target

    fun text(): String {
        val mark = if (inRange) "OK " else "!! "
        return "$mark${name.padEnd(16)} ${format.format(value)}  목표 ${format.format(target.start)}~${format.format(target.endInclusive)}"
    }
}

data class CalibrationReport(
    val seasons: Int,
    val lines: List<CalibrationLine>,
    val extras: List<String>,
    val validationFailures: Int,
    val elapsedMillis: Long,
) {
    val allInRange: Boolean get() = lines.all { it.inRange }

    fun text(): String = buildString {
        appendLine("=== 캘리브레이션 ($seasons 시즌, ${elapsedMillis}ms) ===")
        lines.forEach { appendLine(it.text()) }
        extras.forEach { appendLine("   $it") }
        appendLine(if (validationFailures == 0) "박스스코어 검증 실패 0건" else "검증 실패 $validationFailures 건")
    }
}

/**
 * 캘리브레이터 (docs/04, M4 도구).
 *
 * 여러 시즌을 돌려 리그 평균을 목표와 비교한다. 한 시즌만 보면 운 때문에 흔들려서
 * 변환표를 잘못 조정하게 되므로, 여러 시즌 평균으로 판단한다.
 */
class Calibrator(private val balance: BalanceConfig, private val league: League) {

    fun run(seasons: Int, seed: Long, onSeason: ((Int) -> Unit)? = null): CalibrationReport {
        val runner = SeasonRunner(balance, league)
        val totals = LeagueTotals()
        var failures = 0
        var bestWinPct = 0.0
        var worstWinPct = 0.0
        var injuries = 0
        var seasonEndingInjuries = 0
        var fatigueSum = 0.0
        var fatigueCount = 0
        var rosterMoves = 0

        val started = System.currentTimeMillis()
        repeat(seasons) { index ->
            val result = runner.playSeason(seed + index)
            failures += result.validationProblems.size
            result.reports.forEach { report -> report.games.forEach { totals.add(it) } }

            val ranked = result.state.standings.ranked()
            bestWinPct += ranked.first().winPct
            worstWinPct += ranked.last().winPct

            result.reports.forEach { report ->
                injuries += report.messages.count { it.category == baseballgm.season.InboxCategory.INJURY && it.text.contains("부상 (") }
                rosterMoves += report.messages.count { it.category == baseballgm.season.InboxCategory.ROSTER }
            }
            seasonEndingInjuries += result.state.allPlayers().count {
                it.condition.injury?.severity == baseballgm.model.InjurySeverity.SEASON_ENDING
            }
            result.state.allPlayers().filter { it.rosterLevel == RosterLevel.FIRST_TEAM }.forEach {
                fatigueSum += it.condition.fatigue
                fatigueCount++
            }
            onSeason?.invoke(index + 1)
        }
        val elapsed = System.currentTimeMillis() - started

        val best = bestWinPct / seasons
        val worst = worstWinPct / seasons
        val lines = totals.calibrationLines(balance) + listOf(
            CalibrationLine("최고 승률 팀", best, balance.doubleRange("leagueTargets.bestTeamWinPct"), "%.3f"),
            CalibrationLine("최저 승률 팀", worst, balance.doubleRange("leagueTargets.worstTeamWinPct"), "%.3f"),
        )
        val extras = totals.extraLines() + listOf(
            "시즌당 부상 ${"%.1f".format(injuries.toDouble() / seasons)}건 " +
                "(시즌 아웃 ${"%.1f".format(seasonEndingInjuries.toDouble() / seasons)}건) · " +
                "엔트리 등록 ${"%.1f".format(rosterMoves.toDouble() / seasons)}회",
            "시즌 종료 시점 1군 평균 피로도 ${"%.1f".format(if (fatigueCount == 0) 0.0 else fatigueSum / fatigueCount)}",
        )
        return CalibrationReport(seasons, lines, extras, failures, elapsed)
    }

    /** 검증만 빠르게 돌린다 (M4 완료 기준: 1,000시즌 검증 실패 0건). */
    fun soak(seasons: Int, seed: Long, onSeason: ((Int, Int) -> Unit)? = null): Pair<Int, Long> {
        val runner = SeasonRunner(balance, league)
        var failures = 0
        val started = System.currentTimeMillis()
        repeat(seasons) { index ->
            val result = runner.playSeason(seed + index)
            failures += result.validationProblems.size
            onSeason?.invoke(index + 1, failures)
        }
        return failures to (System.currentTimeMillis() - started)
    }
}
