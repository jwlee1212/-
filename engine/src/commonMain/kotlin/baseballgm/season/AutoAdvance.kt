package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.model.TeamId
import kotlin.random.Random

/**
 * 자동 진행 멈춤 조건 (docs/07).
 *
 * 유저가 켜고 끌 수 있는 항목이다. **드래프트 주차에는 항상 멈춘다** (설정으로 끌 수 없다).
 */
data class AutoAdvanceOptions(
    /** 멈춤 조건을 판단할 기준 구단 (유저 팀). null 이면 리그 전체 일정만 본다 */
    val teamId: TeamId? = null,
    val stopOnInjury: Boolean = true,
    val stopOnLosingStreak: Boolean = true,
    val stopOnWinningStreak: Boolean = false,
    val stopAtAllStarBreak: Boolean = true,
    val stopAtTradeDeadline: Boolean = true,
)

data class AutoAdvanceResult(
    val reports: List<WeekReport>,
    /** 멈춘 이유. null 이면 요청한 주차를 다 진행했다 */
    val stoppedBy: String?,
) {
    val weeksPlayed: Int get() = reports.size
}

/**
 * 여러 주 자동 진행 (docs/07).
 *
 * 한 주씩 [WeekLoop] 을 돌리고, 매주 끝에 멈출 이유가 생겼는지 본다.
 * 멈춤 조건은 "유저가 결정을 내려야 하는 사건"이다 — 주전 부상, 연패, 올스타 브레이크, 드래프트.
 */
class AutoAdvance(
    balance: BalanceConfig,
    private val weekLoop: WeekLoop,
) {
    private val section = balance.section("autoAdvance")
    private val losingStreak = section.int("losingStreak")
    private val winningStreak = section.int("winningStreak")
    private val injuryWeeks = section.int("injuryWeeksThreshold")

    fun run(
        state: SeasonState,
        weeks: Int,
        options: AutoAdvanceOptions = AutoAdvanceOptions(),
        random: Random,
    ): AutoAdvanceResult {
        val reports = mutableListOf<WeekReport>()
        repeat(weeks) {
            if (state.isRegularSeasonOver) return AutoAdvanceResult(reports, "정규시즌 종료")
            val week = state.week
            // 드래프트 주차에는 항상 멈춘다 (docs/07)
            if (state.calendar.isDraftWeek(week) && reports.isNotEmpty()) {
                return AutoAdvanceResult(reports, "신인 드래프트 주간")
            }
            reports += weekLoop.playWeek(state, random)
            stopReason(state, week, options)?.let { return AutoAdvanceResult(reports, it) }
        }
        return AutoAdvanceResult(reports, null)
    }

    private fun stopReason(state: SeasonState, week: Int, options: AutoAdvanceOptions): String? {
        if (options.stopAtAllStarBreak && state.calendar.isAllStarBreakAfter(week)) return "올스타 브레이크"
        if (options.stopAtTradeDeadline && state.calendar.isTradeDeadline(week)) return "트레이드 마감"
        val teamId = options.teamId ?: return null

        val record = state.standings.record(teamId)
        if (options.stopOnLosingStreak && record.streak <= -losingStreak) return "${-record.streak}연패"
        if (options.stopOnWinningStreak && record.streak >= winningStreak) return "${record.streak}연승"
        if (options.stopOnInjury) {
            val injured = state.playersOf(teamId).firstOrNull { player ->
                val injury = player.condition.injury
                injury != null && injury.weeksRemaining >= injuryWeeks &&
                    state.inbox.ofWeek(week, teamId).any { it.text.contains(player.registeredName) }
            }
            if (injured != null) return "${injured.registeredName} 부상"
        }
        return null
    }
}
