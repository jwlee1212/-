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
) {
    fun ageIn(season: Int): Int = season - birthYear
}
