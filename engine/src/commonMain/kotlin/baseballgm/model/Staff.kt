package baseballgm.model

import kotlinx.serialization.Serializable

/** 스태프 계약 (금액 단위: 억원, 운용 자금에서 지급). */
@Serializable
data class StaffContract(val salary: Double, val yearsRemaining: Int)

/**
 * 감독 성향 7종 (docs/06). 각 1~100.
 * 이 값이 사전 지시 규칙표(M3)의 기본값을 만든다. 단장 방침은 성향을 목표값 쪽으로 일부만 옮긴다.
 */
@Serializable
data class ManagerTendencies(
    /** 선발 인내심: 투구수·실점 한계 */
    val starterPatience: Int,
    /** 불펜 혹사: 연투 제한, 필승조 범위 */
    val bullpenAggression: Int,
    val buntPreference: Int,
    val stealAggression: Int,
    /** 플래툰 활용: 좌우 라인업 차이 정도 */
    val platoonUsage: Int,
    val prospectUsage: Int,
    val veteranTrust: Int,
)

/** 감독 (docs/06). 무직 후보는 [teamId] 가 null 이다. */
@Serializable
data class Manager(
    val id: StaffId,
    val name: String,
    val birthYear: Int,
    /** 선수 시절 포지션 표기. 표시용. */
    val playingBackground: String,
    val tendencies: ManagerTendencies,
    val specialty: ManagerSpecialty,
    /** 평판 1~100. 영입 협상·팬심에 영향 */
    val reputation: Int,
    val contract: StaffContract,
    val teamId: TeamId? = null,
) {
    fun ageIn(season: Int): Int = season - birthYear
}

/** 코치 (docs/09). 2군 감독은 [focus] 가 null 이고 25세 이하 전체에 보너스를 준다. */
@Serializable
data class Coach(
    val id: StaffId,
    val name: String,
    val birthYear: Int,
    val role: CoachRole,
    val focus: CoachFocus?,
    /** 등급 1~5. 재분배 배율의 크기를 정한다 */
    val grade: Int,
    val contract: StaffContract,
    val teamId: TeamId? = null,
) {
    fun ageIn(season: Int): Int = season - birthYear
}

/** 메디컬 스태프 (docs/13). */
@Serializable
data class MedicalStaff(
    val id: StaffId,
    val name: String,
    val birthYear: Int,
    val role: MedicalRole,
    /** 등급 1~5 */
    val grade: Int,
    val contract: StaffContract,
    val teamId: TeamId? = null,
) {
    fun ageIn(season: Int): Int = season - birthYear
}

/**
 * 단장 (docs/13). 유저도 AI 구단도 같은 타입이다.
 *
 * @param reputation 평판 0~100. 기대 대비 성과로 오르내린다
 * @param tradeAggression 거래 성향 1~100 (M7 트레이드 AI 가 쓴다)
 * @param isHuman 유저가 맡은 자리인지
 * @param style 트레이드 협상 성격 (2026-10-05). 없으면 단장 id 로 고정해서 정한다 ([tradeStyle])
 */
@Serializable
data class GeneralManager(
    val id: StaffId,
    val name: String,
    val birthYear: Int,
    val reputation: Int,
    val tradeAggression: Int,
    val teamId: TeamId? = null,
    val isHuman: Boolean = false,
    val style: GmStyle? = null,
) {
    fun ageIn(season: Int): Int = season - birthYear

    /** 트레이드 협상 성격. 데이터에 없으면 id 에서 결정적으로 (같은 단장 → 같은 성격) */
    fun tradeStyle(): GmStyle = style ?: GmStyle.entries[(id.value.hashCode() and Int.MAX_VALUE) % GmStyle.entries.size]
}

/**
 * AI 단장의 트레이드 협상 성격 (2026-10-05 유저 요청 "상대 단장 성격 차이"). 공개 정보 — 업계에 알려진 평판이다.
 * 가치 배율·요구 이익 폭은 balance.json `trade.gmStyles.<key>`
 */
@Serializable
enum class GmStyle(val key: String, val label: String, val description: String) {
    BALANCED("balanced", "무난형", "가치만 맞으면 받아요."),
    REBUILDER("rebuilder", "리빌딩 집착형", "유망주·지명권을 후하게 쳐 주고, 베테랑은 박하게 봐요."),
    WIN_NOW("winNow", "즉시 전력형", "지금 잘하는 베테랑을 원하고, 유망주·지명권은 덜 쳐 줘요."),
    HARD_BARGAINER("hardBargainer", "깐깐한 협상가", "이익을 크게 남겨야 받아요. 거절이 쌓이면 금방 등을 돌려요."),
}
