package baseballgm.model

import kotlinx.serialization.Serializable

/** 모기업 (docs/01, 13). 이름이 아직 정해지지 않은 구단은 null 로 둔다. */
@Serializable
data class ParentCompany(
    val name: String,
    /** 연간 기본 지원금(억원). */
    val annualSupport: Double,
    /** 재정 상태 1~100. 지원금 변동과 모기업 이벤트에 쓴다 */
    val financialHealth: Int,
)

/**
 * 구단.
 *
 * 선수 명단을 들고 있지 않고, 선수 쪽이 [Player.teamId] 로 소속을 가리킨다.
 * 트레이드·FA 로 소속이 바뀔 때 양쪽 명단을 맞춰 고치는 실수를 없애려는 구조다.
 */
@Serializable
data class Team(
    val id: TeamId,
    val name: String,
    val city: String,
    val nickname: String,
    val parentCompany: ParentCompany?,
    /** strong / mid / weak */
    val tier: String,
    val keyword: String,
    /** large / medium / small */
    val marketSize: String,
    val parkFactor: Double,
    val fanVolatility: Double,
    val ownerGoal: String,
    /** 운용 자금(억원). 계약금·스태프 연봉·스카우트 예산의 재원 */
    val operatingFunds: Double,
    /** 이번 드래프트 지명 순번 */
    val draftPick: Int,
    /** 팬심 0~100 */
    val fanSupport: Int,
    /** 구단주 신뢰도 0~100 */
    val ownerTrust: Int,
    val managerId: StaffId?,
    val coachIds: List<StaffId>,
    val medicalStaffIds: List<StaffId>,
    val generalManagerId: StaffId?,
    /** 라이벌 구단 (docs/13 서사). 라이벌전 결과가 팬심·뉴스·비서 브리핑에 나온다 */
    val rival: TeamId? = null,
)
