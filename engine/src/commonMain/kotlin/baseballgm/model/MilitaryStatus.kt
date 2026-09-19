package baseballgm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 군 복무 상태 (docs/12).
 *
 * 복무 중인 선수는 엔트리·연봉 총액에서 빠지고, 복무 기간은 FA 연차로 인정되지 않는다.
 */
@Serializable
sealed class MilitaryStatus {

    /** 외국인 선수 등 해당 없음. */
    @Serializable
    @SerialName("notRequired")
    data object NotRequired : MilitaryStatus()

    /** 미필. [deadlineAge] 까지 입대해야 한다 (임시값, `balance.json` 의 `military.enlistDeadlineAge`). */
    @Serializable
    @SerialName("unfulfilled")
    data class Unfulfilled(val deadlineAge: Int) : MilitaryStatus()

    /** 복무 중. [returnSeason] 시즌 [returnWeek] 주차에 복귀한다. */
    @Serializable
    @SerialName("serving")
    data class Serving(
        val kind: ServiceKind,
        val returnSeason: Int,
        val returnWeek: Int,
    ) : MilitaryStatus()

    /** 군필. */
    @Serializable
    @SerialName("completed")
    data object Completed : MilitaryStatus()

    /** 병역 특례(국제대회 등)로 면제. */
    @Serializable
    @SerialName("exempt")
    data object Exempt : MilitaryStatus()

    /** 지금 뛸 수 있는 상태인가. */
    val isAvailable: Boolean get() = this !is Serving
}
