package baseballgm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 계약 종류. 연봉 산정과 FA 자격 판정에 쓴다. */
@Serializable
enum class ContractType {
    /** 신인 계약 (저연차). */
    @SerialName("rookie") ROOKIE,

    /** 일반 계약 (연봉 협상 대상). */
    @SerialName("standard") STANDARD,

    /** FA 계약. */
    @SerialName("freeAgent") FREE_AGENT,

    /** 외국인 선수 계약 (1년 단위). */
    @SerialName("foreign") FOREIGN,
}

/**
 * 선수 계약 (금액 단위: 억원).
 *
 * @param salary 이번 시즌 연봉
 * @param yearsRemaining 이번 시즌을 포함해 남은 계약 연수
 * @param signingBonusRemaining 아직 지급하지 않은 계약금 (운용 자금에서 나간다)
 * @param seasonsToFreeAgency FA 취득까지 남은 시즌 수. 0 이면 이번 시즌 후 FA
 * @param serviceSeasons 지금까지 쌓은 FA 인정 시즌 수 (군 복무 기간은 불인정, docs/12)
 */
@Serializable
data class Contract(
    val salary: Double,
    val yearsRemaining: Int,
    val signingBonusRemaining: Double = 0.0,
    val seasonsToFreeAgency: Int,
    val serviceSeasons: Int,
    val type: ContractType,
) {
    /** 이번 시즌이 끝나면 계약이 끝나는가. */
    val expiresThisSeason: Boolean get() = yearsRemaining <= 1
}
