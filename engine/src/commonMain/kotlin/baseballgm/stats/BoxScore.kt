package baseballgm.stats

import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import kotlinx.serialization.Serializable

/**
 * 한 팀의 경기 기록.
 *
 * @param halfInningOuts 이 팀이 **수비한** 하프 이닝마다 잡은 아웃 수. 아웃 등식 검증에 쓴다
 * @param runnersOutOnBase 베이스에서 아웃된 주자 수 (병살의 선행 주자, 도루 실패 등)
 */
@Serializable
data class TeamBoxScore(
    val teamId: TeamId,
    val batting: Map<PlayerId, PlayerBatting>,
    val pitching: Map<PlayerId, PlayerPitching>,
    val inningRuns: List<Int>,
    val halfInningOuts: List<Int>,
    val leftOnBase: Int,
    val runnersOutOnBase: Int,
    val errors: Int,
) {
    val runs: Int get() = inningRuns.sum()

    val battingTotal: BattingLine get() = batting.values.fold(BattingLine.EMPTY) { acc, line -> acc + line.total }

    val pitchingTotal: PitchingLine get() = pitching.values.fold(PitchingLine.EMPTY) { acc, line -> acc + line.total }

    val hits: Int get() = battingTotal.hits

    val timesReachedBase: Int get() = battingTotal.timesReachedBase
}

/** 경기 하나의 박스스코어. 현재 시즌 경기별로 보관한다 (docs/04 기록 저장 방식). */
@Serializable
data class BoxScore(
    val home: TeamBoxScore,
    val away: TeamBoxScore,
    val innings: Int,
    val walkOff: Boolean,
    val tie: Boolean,
    val winningPitcher: PlayerId? = null,
    val losingPitcher: PlayerId? = null,
    val savePitcher: PlayerId? = null,
    val holdPitchers: List<PlayerId> = emptyList(),
) {
    val homeScore: Int get() = home.runs
    val awayScore: Int get() = away.runs
    val winner: TeamId? get() = when {
        tie -> null
        homeScore > awayScore -> home.teamId
        else -> away.teamId
    }

    fun line(): String =
        "${away.teamId} ${away.inningRuns.joinToString("")} = $awayScore / " +
            "${home.teamId} ${home.inningRuns.joinToString("")} = $homeScore"
}
