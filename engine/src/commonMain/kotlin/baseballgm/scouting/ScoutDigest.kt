package baseballgm.scouting

import baseballgm.market.DraftPolicy
import baseballgm.model.PlayerId
import kotlinx.serialization.Serializable

/**
 * 스카우트팀 정기 리포트 (유저 요청 2026-10-04).
 *
 * 추천을 늘 띄워 두면 "추천을 따라가는" 게임이 된다. 그래서 스카우트팀이 **4주마다 + 드래프트 직전**에만 보고하고,
 * 단장은 받은 보고서를 읽고 판단한다. 보고서는 **받은 시점 그대로 고정**이다 — 이름·확률·이유를 값으로 담아 두어
 * 다음 보고 전까지 숫자가 바뀌지 않는다 (지금 상태로 다시 계산하지 않는다).
 */
@Serializable
data class ScoutDigest(
    val season: Int,
    val week: Int,
    /** 드래프트 직전 최종 리포트인가 */
    val final: Boolean,
    /** 보고 당시 추천 기준 */
    val policy: DraftPolicy,
    /** 스카우트팀장 한 줄 의견 */
    val headline: String,
    /** 남은 우리 순번별 노려볼 후보 */
    val rounds: List<DigestRound>,
    /** 지난 보고 이후 평가가 크게 오른 선수 */
    val risers: List<DigestMove>,
    /** 지난 보고 이후 평가가 크게 내린 선수 */
    val fallers: List<DigestMove>,
    /** 이번에 관찰을 마친 선수 (더 봐도 정확도가 오르지 않는다) */
    val completed: List<DigestName>,
    /** 다음 보고에서 오르내림을 재기 위한 평가값 (선수별, 우리 리포트 기준) */
    val estimates: Map<PlayerId, Double>,
    /** 관찰을 마친 선수 전체 — 다음 보고에서 "이번에 새로 마친 선수"만 고르려고 */
    val completedIds: List<PlayerId>,
)

@Serializable
data class DigestRound(val round: Int, val overallPick: Int, val picks: List<DigestPick>)

@Serializable
data class DigestPick(
    val playerId: PlayerId,
    val name: String,
    val position: String,
    /** 당시 리포트의 종합 범위 ("32~52") */
    val overall: String,
    val potential: String,
    /** 당시 계산한 "우리 차례까지 남아 있을 확률" (0~1) */
    val availability: Double,
    val reasons: List<String>,
)

@Serializable
data class DigestMove(
    val playerId: PlayerId,
    val name: String,
    val position: String,
    /** 평가 변화 (능력치 점수) */
    val delta: Double,
    val potential: String,
)

@Serializable
data class DigestName(val playerId: PlayerId, val name: String, val position: String, val potential: String)
