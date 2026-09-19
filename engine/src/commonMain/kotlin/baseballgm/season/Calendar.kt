package baseballgm.season

import baseballgm.io.BalanceConfig

/**
 * 시즌 캘린더 (docs/07).
 *
 * 정규시즌은 24주이고 각 주에 화~일 6경기가 있다. 올스타 브레이크는 별도의 주차를 쓰지 않고
 * **12주차가 끝난 뒤** 한 번 쉬는 것으로 처리한다 (일정표가 24주 고정이라 주차 번호를 흔들지 않으려는 임시 결정).
 */
class SeasonCalendar(
    val regularSeasonWeeks: Int,
    val allStarBreakAfterWeek: Int,
    val tradeDeadlineWeek: Int,
    val draftWeek: Int,
    val gamesPerWeek: Int,
) {
    fun isAllStarBreakAfter(week: Int): Boolean = week == allStarBreakAfterWeek

    fun isTradeDeadline(week: Int): Boolean = week == tradeDeadlineWeek

    /** 드래프트 주차에는 자동 진행이 반드시 멈춘다 (docs/07). */
    fun isDraftWeek(week: Int): Boolean = week == draftWeek

    fun isFinalWeek(week: Int): Boolean = week == regularSeasonWeeks

    fun isFirstHalf(week: Int): Boolean = week <= allStarBreakAfterWeek

    fun label(week: Int): String = buildString {
        append(if (isFirstHalf(week)) "전반기" else "후반기")
        append(" ${week}주차")
        if (isAllStarBreakAfter(week)) append(" (올스타 브레이크)")
        if (isTradeDeadline(week)) append(" (트레이드 마감)")
        if (isDraftWeek(week)) append(" (신인 드래프트)")
    }

    companion object {
        fun from(balance: BalanceConfig): SeasonCalendar {
            val section = balance.section("season")
            return SeasonCalendar(
                regularSeasonWeeks = section.int("regularSeasonWeeks"),
                allStarBreakAfterWeek = section.int("allStarBreakAfterWeek"),
                tradeDeadlineWeek = section.int("tradeDeadlineWeek"),
                draftWeek = section.int("draftWeek"),
                gamesPerWeek = section.int("gamesPerWeek"),
            )
        }
    }
}
