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

    /** 비FA 다년계약 (docs/11). FA 가 되기 전에 우리 선수와 미리 맺은 장기 계약 */
    @SerialName("multiYear") MULTI_YEAR,
}

/**
 * 선수 계약 (금액 단위: 억원).
 *
 * @param salary 이번 시즌 연봉
 * @param yearsRemaining 이번 시즌을 포함해 남은 계약 연수
 * @param signingBonusRemaining 아직 지급하지 않은 계약금 (운용 자금에서 나간다)
 * @param seasonsToFreeAgency FA 취득까지 남은 시즌 수. 0 이면 이번 시즌 후 FA
 * @param serviceSeasons 지금까지 쌓은 FA 인정 시즌 수 (군 복무 기간은 불인정, docs/12)
 * @param nextSalary 다음 시즌부터 받을 연봉. 시즌 중에 다년계약을 맺으면 이번 시즌 연봉은 그대로 두고
 *   여기에 새 연봉을 적어 둔다. 스토브리그에서 계약이 한 해 넘어갈 때 [salary] 로 옮긴다
 */
@Serializable
data class Contract(
    val salary: Double,
    val yearsRemaining: Int,
    val signingBonusRemaining: Double = 0.0,
    val seasonsToFreeAgency: Int,
    val serviceSeasons: Int,
    val type: ContractType,
    val nextSalary: Double? = null,
    /**
     * 옵션 조항 (2026-10-04 FA 협상 테이블, 같은 날 "다양화 — WAR 기준 빼기"로 개정). 시즌이 끝나면 기준을 달성한 조항의 금액을
     * 운용 자금에서 따로 준다. 연봉 총액(캡)에는 들어가지 않는다. FA 계약에만 붙고 계약이 끝나면 사라진다
     */
    val options: List<baseballgm.market.OptionClause> = emptyList(),
    /** 다음 시즌부터 붙는 옵션 조항 (시즌 중 맺은 비FA 다년계약, [nextSalary] 와 함께 옮겨진다) */
    val nextOptions: List<baseballgm.market.OptionClause>? = null,
) {
    /** 이번 시즌이 끝나면 계약이 끝나는가. */
    val expiresThisSeason: Boolean get() = yearsRemaining <= 1
}
