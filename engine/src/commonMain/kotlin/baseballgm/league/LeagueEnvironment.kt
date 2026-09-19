package baseballgm.league

import kotlinx.serialization.Serializable

/**
 * 리그 환경 (docs/04). 시즌마다 이벤트로 바뀐다 (공인구 반발력, 스트라이크존 조정 등).
 * M2 에서는 기본값(변화 없음)으로 두고, 리그 환경 이벤트는 M8 에서 붙인다.
 *
 * @param homeRunMultiplier 홈런·장타 비율 배율
 * @param strikeoutDelta 삼진 비율에 더하는 값 (%p)
 * @param walkDelta 볼넷 비율에 더하는 값 (%p)
 */
@Serializable
data class LeagueEnvironment(
    val homeRunMultiplier: Double = 1.0,
    val strikeoutDelta: Double = 0.0,
    val walkDelta: Double = 0.0,
) {
    companion object {
        val NEUTRAL: LeagueEnvironment = LeagueEnvironment()
    }
}

/** 구장 (docs/04). 홈런·장타에 곱하는 계수. */
@Serializable
data class Park(val teamId: String, val factor: Double)
