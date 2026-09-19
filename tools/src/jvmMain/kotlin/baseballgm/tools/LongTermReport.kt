package baseballgm.tools

import baseballgm.io.BalanceConfig
import kotlin.math.abs

/**
 * 장기 밸런스 판정 (CLAUDE.md §7, docs/15 M5 완료 기준).
 *
 * - 주전 평균 능력치 60±3 유지
 * - 90+ 능력치 수가 안정 (0으로 사라지거나 폭발하지 않음)
 * - 드래프트 유입과 은퇴 유출의 균형 (선수 수 유지)
 */
fun longTermReport(summaries: List<SeasonSummary>, balance: BalanceConfig): String {
    if (summaries.isEmpty()) return "시즌이 없다"
    val first = summaries.first()
    val last = summaries.last()
    val target = balance.double("ratingScale.starterAverage")
    val tolerance = STARTER_TOLERANCE

    val starterOk = abs(last.starterAverage - target) <= tolerance
    val rosterOk = abs(last.playerCount - first.playerCount) <= ROSTER_TOLERANCE
    val eliteOk = last.eliteRatings in 1..ELITE_LIMIT
    val ageOk = last.averageAge in AGE_RANGE
    val validationOk = summaries.all { it.validationFailures == 0 }
    val retired = summaries.sumOf { it.offseason.retired.size }
    val rookies = summaries.sumOf { it.offseason.rookies.size }
    val flowOk = abs(retired - rookies) <= (retired * FLOW_TOLERANCE).toInt() + FLOW_SLACK

    fun mark(ok: Boolean) = if (ok) "OK " else "!! "

    return buildString {
        appendLine("=== 장기 밸런스 (${summaries.size}시즌) ===")
        appendLine(
            "${mark(starterOk)}주전 평균 능력치   ${"%.1f".format(last.starterAverage)}  " +
                "목표 ${"%.0f".format(target)}±${tolerance.toInt()} (시작 ${"%.1f".format(first.starterAverage)})",
        )
        appendLine(
            "${mark(eliteOk)}90+ 능력치 수      ${last.eliteRatings}개  " +
                "(시작 ${first.eliteRatings}개, 최대 ${summaries.maxOf { it.eliteRatings }}개)",
        )
        appendLine(
            "${mark(rosterOk)}선수 수           ${last.playerCount}명  (시작 ${first.playerCount}명)",
        )
        appendLine(
            "${mark(flowOk)}유입·유출 균형     은퇴 ${retired}명 vs 신인 ${rookies}명",
        )
        appendLine(
            "${mark(ageOk)}평균 나이         ${"%.1f".format(last.averageAge)}세  (시작 ${"%.1f".format(first.averageAge)}세)",
        )
        appendLine("${mark(validationOk)}박스스코어 검증    실패 ${summaries.sumOf { it.validationFailures }}건")
        appendLine(
            "   각성 ${summaries.sumOf { it.offseason.awakened.size }}명 · " +
                "급노쇠 ${summaries.sumOf { it.offseason.collapsed.size }}명 · " +
                "코치 전향 ${summaries.sumOf { it.offseason.newCoachCandidates.size }}명 · " +
                "입대 ${summaries.sumOf { it.offseason.enlisted.size }}명",
        )
        appendLine(
            "   승률 범위 최근 시즌 ${"%.3f".format(last.bestWinPct)} ~ ${"%.3f".format(last.worstWinPct)}",
        )
    }
}

private const val STARTER_TOLERANCE = 3.0
private const val ROSTER_TOLERANCE = 30
private const val ELITE_LIMIT = 60
private val AGE_RANGE = 22.0..31.0
private const val FLOW_TOLERANCE = 0.15
private const val FLOW_SLACK = 20
