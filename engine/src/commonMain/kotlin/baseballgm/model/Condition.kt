package baseballgm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 부상 단계 (docs/08). */
@Serializable
enum class InjurySeverity {
    @SerialName("minor") MINOR,
    @SerialName("moderate") MODERATE,
    @SerialName("major") MAJOR,
    @SerialName("seasonEnding") SEASON_ENDING,
}

/** 부상 상태. 부위는 복귀 후 떨어지는 능력치를 정한다 (M4에서 사용). */
@Serializable
data class Injury(
    val part: String,
    val severity: InjurySeverity,
    val weeksRemaining: Int,
    val relapseRiskWeeks: Int = 0,
)

/**
 * 컨디션 (docs/08). M1 에서는 자리만 만들고 기본값으로 둔다. 실제 변동은 M4 에서 붙인다.
 *
 * @param fatigue 피로도 0~100
 * @param form 폼 0~100. 50 이 보통이며 표시는 5단계로 변환한다
 * @param adaptationPenalty 적응 감점 (docs/12). 외국인 선수가 KBO 에 적응하지 못한 만큼 능력치에서 깎인다.
 *   숨김 수치인 적응력에서 나오지만 **감점 자체는 기록으로 드러나는 값**이라 컨디션에 둔다
 */
@Serializable
data class Condition(
    val fatigue: Int = 0,
    val form: Int = 50,
    val injury: Injury? = null,
    /** 복귀 후 재발 위험이 남은 주 수 (docs/08) */
    val relapseRiskWeeks: Int = 0,
    val adaptationPenalty: Double = 0.0,
) {
    val isInjured: Boolean get() = injury != null

    val relapseRisk: Boolean get() = relapseRiskWeeks > 0

    companion object {
        val HEALTHY: Condition = Condition()
    }
}
