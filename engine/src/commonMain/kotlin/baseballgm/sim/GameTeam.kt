package baseballgm.sim

import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.tactics.DirectiveSheet

/**
 * 투수 가용 상태 (docs/06 ④ 연투 제한, 최근 7일 투구수).
 *
 * 경기 밖(주간 루프, M4)에서 채워 넣는다. 시뮬레이터는 이 값을 규칙표의 제한과 비교만 한다.
 */
data class PitcherAvailability(
    /** 부상·말소 등으로 아예 못 던지는 투수 */
    val unavailable: Set<PlayerId> = emptySet(),
    /** 어제까지 며칠 연속 등판했는지 */
    val consecutiveDays: Map<PlayerId, Int> = emptyMap(),
    /** 최근 7일 투구수 */
    val pitchesLast7Days: Map<PlayerId, Int> = emptyMap(),
) {
    companion object {
        val ALL_READY: PitcherAvailability = PitcherAvailability()
    }
}

/**
 * 경기에 나서는 한 팀.
 *
 * 라인업을 미리 받지 않고 **규칙표와 선수단**을 받는다. 상대 선발의 손을 보고 좌완용/우완용
 * 라인업 중 하나를 고르는 것은 시뮬레이터의 일이다 (docs/06 ①).
 */
data class GameTeam(
    val teamId: TeamId,
    val roster: Map<PlayerId, Player>,
    val directives: DirectiveSheet,
    val startingPitcher: PlayerId,
    val availability: PitcherAvailability = PitcherAvailability.ALL_READY,
) {
    fun batter(id: PlayerId): Batter = roster[id] as? Batter ?: error("$id 는 타자가 아니다")

    fun pitcher(id: PlayerId): Pitcher = roster[id] as? Pitcher ?: error("$id 는 투수가 아니다")

    fun batterOrNull(id: PlayerId): Batter? = roster[id] as? Batter

    fun pitcherOrNull(id: PlayerId): Pitcher? = roster[id] as? Pitcher
}
