package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.league.TeamRecord
import baseballgm.season.PostseasonResult
import baseballgm.season.PostseasonRound
import kotlin.math.roundToInt

/** 팬심을 흔드는 사건 (docs/13). */
enum class FanEvent(val configKey: String, val label: String) {
    BIG_FREE_AGENT_SIGNED("bigFreeAgentSigned", "대형 FA 영입"),
    FRANCHISE_STAR_TRADED("franchiseStarTraded", "프랜차이즈 스타 트레이드"),
    FRANCHISE_STAR_RELEASED("franchiseStarReleased", "프랜차이즈 스타 방출"),
    STAR_FREE_AGENT_LOST("starFreeAgentLost", "스타 FA 유출"),
    ROOKIE_STAR_EMERGED("rookieStarEmerged", "신인 스타 등장"),
    LEGEND_RETIRED("legendRetired", "레전드 은퇴식"),
    MANAGER_CHURN("managerChurn", "잦은 감독 교체"),
    RIVAL_SERIES_WIN("rivalSeriesWin", "라이벌전 우세"),
    RIVAL_SERIES_LOSS("rivalSeriesLoss", "라이벌전 열세"),
}

/**
 * 팬심 0~100 (docs/13).
 *
 * 승패로는 **천천히** 움직이고 사건으로는 **크게** 흔들린다. 프랜차이즈 스타를 트레이드하면
 * 한 번에 12점이 빠지는데, 그걸 승리로 메우려면 여러 주가 걸린다 — 팬심이 트레이드 결정의
 * 실제 비용이 되게 하려는 배치다.
 *
 * 구단별 변화 폭(`Team.fanVolatility`)이 곱해진다. 부산 타이드는 1.5배로 더 뜨겁고 더 차갑다.
 */
class FanSentiment(balance: BalanceConfig) {

    private val section = balance.section("fanSentiment")
    private val events = section.section("events")
    private val postseasonBonus = section.section("postseasonBonus")
    private val minimum = section.int("min")
    private val maximum = section.int("max")

    val franchiseStarOverall: Double = section.double("franchiseStarOverall")
    val bigFreeAgentSalary: Double = section.double("bigFreeAgentSalary")

    /** 한 주의 성적 반영. 이긴 만큼 오르고 진 만큼 내린다. */
    fun afterWeek(current: Int, weekWins: Int, weekLosses: Int, streak: Int, volatility: Double): Int {
        val fromResults = (weekWins - weekLosses) * section.double("weeklyWinWeight")
        val fromStreak = when {
            streak >= STREAK_THRESHOLD -> section.double("streakBonus") * streak
            streak <= -STREAK_THRESHOLD -> section.double("streakBonus") * streak
            else -> 0.0
        }
        // 매주 평균으로 조금 돌아온다 — 없으면 24주 동안 쌓여서 전 구단이 양 극단으로 몰린다
        val regression = (NEUTRAL_FAN - current) * section.double("weeklyRegression")
        return shift(current, (fromResults + fromStreak) * volatility + regression)
    }

    /** 시즌 결산. 승률·포스트시즌·하위권이 반영되고 평균으로 조금 돌아온다. */
    fun afterSeason(
        current: Int,
        record: TeamRecord,
        rank: Int,
        teamCount: Int,
        postseason: PostseasonResult?,
        volatility: Double,
    ): Int {
        val fromWinPct = (record.winPct - NEUTRAL) * section.double("seasonWinPctWeight") * 2
        val reached = postseason?.reachedRound(record.teamId)
        val fromPostseason = when {
            postseason?.champion == record.teamId -> postseasonBonus.double("champion")
            reached == PostseasonRound.KOREAN_SERIES -> postseasonBonus.double("koreanSeries")
            reached != null -> postseasonBonus.double("reached")
            else -> 0.0
        }
        val bottom = if (rank > teamCount - BOTTOM_TEAMS) -section.double("bottomPenalty") else 0.0
        val regression = (NEUTRAL_FAN - current) * section.double("regressionToMean")
        return shift(current, (fromWinPct + fromPostseason + bottom) * volatility + regression)
    }

    /** 사건 한 건. 구단 변화 폭이 곱해진다 */
    fun onEvent(current: Int, event: FanEvent, volatility: Double): Int =
        shift(current, events.double(event.configKey) * volatility)

    /** 돌발 이벤트 답변처럼 정해진 크기만큼 움직인다. 구단 변화 폭이 곱해진다 */
    fun nudge(current: Int, delta: Double, volatility: Double): Int = shift(current, delta * volatility)

    fun deltaOf(event: FanEvent): Double = events.double(event.configKey)

    fun label(fanSupport: Int): String = when {
        fanSupport >= 80 -> "열광"
        fanSupport >= 65 -> "우호적"
        fanSupport >= 45 -> "보통"
        fanSupport >= 30 -> "실망"
        else -> "등 돌림"
    }

    private fun shift(current: Int, delta: Double): Int =
        (current + delta).roundToInt().coerceIn(minimum, maximum)

    private companion object {
        const val NEUTRAL = 0.5
        const val NEUTRAL_FAN = 50.0
        const val STREAK_THRESHOLD = 4
        const val BOTTOM_TEAMS = 2
    }
}
