package baseballgm.season

import baseballgm.model.PlayerId
import kotlinx.serialization.Serializable

/**
 * 스토브리그 계획 — 단장이 겨울에 내리는 결정 (2026-10-01, 진단 2번 "스토브리그를 결정의 시간으로").
 *
 * 포스트시즌이 끝나면 비서가 [OffseasonPreview] 로 결정거리를 브리핑하고, 단장은 여기에 답을 적는다.
 * 스토브리그([Offseason.run])는 **유저 구단에 대해서는 이 계획대로** 처리하고, AI 구단은 예전처럼 자동이다.
 *
 * 처음에는 비서 추천으로 채워진다([Offseason.defaultPlan]) — 아무것도 안 건드리고 시작해도 말이 되는 겨울이 된다.
 * 시즌 상태에 담겨 세이브에도 들어간다 (준비하다 앱을 닫아도 남는다).
 */
@Serializable
data class OffseasonPlan(
    /** 외국인 재계약: 선수 → 재계약하면 그 연봉, 안 하면 null */
    val foreign: Map<PlayerId, Double?> = emptyMap(),
    /** 연봉 재계약: 선수 → 합의 금액과 깎았는지. 미리보기에서 보여 준 금액 그대로 계약한다 */
    val salaries: Map<PlayerId, SalaryDecision> = emptyMap(),
    /** 방출 명단 */
    val releases: Set<PlayerId> = emptySet(),
    /** 단장이 확인을 마쳤는가. 마치기 전에는 진행 버튼이 준비 화면으로 보낸다 */
    val confirmed: Boolean = false,
)

/** 연봉 재계약 결정 하나 */
@Serializable
data class SalaryDecision(val salary: Double, val lowball: Boolean)

/** 외국인 재계약 건 하나 */
data class ForeignRenewal(
    val playerId: PlayerId,
    val war: Double,
    /** 재계약하려면 줘야 하는 연봉 (일본 구단 제안이 있으면 그 액수) */
    val asking: Double,
    val currentSalary: Double,
    /** 해외(일본) 구단이 데려가려 한다 */
    val outflow: Boolean,
    /** 비서 추천: 재계약 */
    val recommendResign: Boolean,
)

/** 연봉 재계약 건 하나 (계약이 끝나는 비FA 선수) */
data class SalaryCase(
    val playerId: PlayerId,
    val war: Double,
    val currentSalary: Double,
    /** 선수 요구액 = 전 시즌 WAR 시장가로 끌어간 값 (예전 자동 재계약 금액) */
    val demand: Double,
    /** 깎아서 제시할 때의 액수 */
    val lowball: Double,
)

/** 방출 검토 대상 한 명 */
data class ReleaseCandidate(
    val playerId: PlayerId,
    /** 보이는 정보(현재 능력 + 잠재력 등급 + 나이)로 매긴 남길 가치. 낮을수록 방출 후보 */
    val keepScore: Double,
    val recommended: Boolean,
)

/**
 * 스토브리그 결정거리 (비서 브리핑).
 *
 * 숫자는 **스토브리그가 실제로 쓰는 값과 같다** — 외국인 요구액은 선수마다 고정 시드로 미리 정하고,
 * 재계약하면 그 액수 그대로 계약한다. 연봉 요구액은 기록(WAR)으로 정해져 난수가 없다.
 */
data class OffseasonPreview(
    val foreign: List<ForeignRenewal>,
    val salaries: List<SalaryCase>,
    /** FA 자격을 얻어 시장에 나가는 우리 선수 */
    val freeAgents: List<PlayerId>,
    val releaseCandidates: List<ReleaseCandidate>,
    /** 다음 시즌 선수단 예상 (방출 전, 은퇴 제외) */
    val projectedRoster: Int,
    val targetRoster: Int,
    /** 유저 구단이 둘 수 있는 최대 인원 (넘으면 스토브리그가 가장 아래부터 자동 방출) */
    val maxRoster: Int,
)
