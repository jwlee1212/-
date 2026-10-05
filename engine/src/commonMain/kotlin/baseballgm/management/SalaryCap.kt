package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.model.TeamId

/** 소프트캡 제재 한 건 (docs/11). */
data class CapPenalty(
    val teamId: TeamId,
    val payroll: Double,
    val excess: Double,
    /** 몇 년 연속 초과인가 */
    val consecutive: Int,
    /** 제재금(억원). 운용 자금에서 나간다 */
    val fine: Double,
    /** 다음 드래프트 지명 순번이 밀리는가 */
    val draftPickDrop: Boolean,
) {
    fun message(teamName: String): String = buildString {
        append("$teamName 연봉 총액 ${format(payroll)}억 — 상한 ${format(payroll - excess)}억 초과")
        append(" (${consecutive}년 연속) · 제재금 ${format(fine)}억")
        if (draftPickDrop) append(" · 다음 드래프트 지명 순번 하락")
    }

    private fun format(value: Double): String = ((value * 10).toInt() / 10.0).toString()
}

/**
 * 소프트캡 (docs/11).
 *
 * 연봉 총액에 **상한이 아니라 벌칙**을 둔다. 돈으로 전력을 사는 길을 막지는 않되, 계속 넘으면
 * 제재가 무거워진다 — 1회 초과분 50%, 2년 연속 100% + 지명 순번 하락, 3년 연속 150%.
 *
 * 판정은 **시즌 종료 시점의 연봉 총액**으로 한다 (docs/11). 시즌 중 트레이드로 연봉을 주고받아도
 * 결국 시즌 끝의 명단으로 계산되므로, 마감 직전에 잠깐 줄이는 꼼수가 통하지 않는다.
 */
class SalaryCap(balance: BalanceConfig) {

    private val section = balance.section("softCap")
    val cap: Double = section.double("cap")
    private val penalties = section.sections("penalties")
    private val dropPlaces = section.intOrNull("draftPickDropPlaces") ?: DEFAULT_DROP

    fun payrollOf(league: League, teamId: TeamId): Double = league.payrollOf(teamId)

    fun isOver(league: League, teamId: TeamId): Boolean = payrollOf(league, teamId) > cap

    /**
     * 시즌 종료 판정.
     *
     * @param previousOverruns 지난 시즌까지의 연속 초과 횟수
     * @return 제재 목록과 갱신된 연속 초과 횟수
     */
    fun evaluate(league: League, previousOverruns: Map<TeamId, Int>): Pair<List<CapPenalty>, Map<TeamId, Int>> {
        val results = mutableListOf<CapPenalty>()
        val overruns = mutableMapOf<TeamId, Int>()

        league.teams.forEach { team ->
            val payroll = payrollOf(league, team.id)
            if (payroll <= cap) return@forEach
            val consecutive = (previousOverruns[team.id] ?: 0) + 1
            overruns[team.id] = consecutive

            results += CapPenalty(
                teamId = team.id,
                payroll = payroll,
                excess = payroll - cap,
                consecutive = consecutive,
                fine = fineFor(payroll, consecutive),
                draftPickDrop = ruleFor(consecutive).boolean("draftPickDrop"),
            )
        }
        return results to overruns
    }

    /** 연봉 총액 [payroll] 로 [consecutive] 년 연속 넘겼을 때의 제재금. 상한 아래면 0 */
    fun fineFor(payroll: Double, consecutive: Int): Double =
        if (payroll <= cap) 0.0 else (payroll - cap) * ruleFor(consecutive).double("rateOfExcess")

    private fun ruleFor(consecutive: Int) =
        penalties.lastOrNull { it.int("consecutive") <= consecutive } ?: penalties.first()

    /** 제재로 밀리는 지명 순번 칸 수. */
    fun draftPickDropPlaces(): Int = dropPlaces

    private companion object {
        const val DEFAULT_DROP = 5
    }
}
