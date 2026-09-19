package baseballgm.stats

import baseballgm.model.PlayerId
import baseballgm.model.TeamId

/**
 * 시즌 누적 기록 (docs/04 기록 저장 방식).
 *
 * 타석 단위 로그는 남기지 않고 **선수별 누적 카운트만** 좌우 분리로 쌓는다.
 * 비율 지표(타율·평균자책)는 볼 때 계산한다. 현재 시즌의 경기별 박스스코어는 주간 결산·최근 경기
 * 표시를 위해 팀별로 최근 것만 들고 있는다.
 */
class SeasonStats(private val recentGamesPerTeam: Int = DEFAULT_RECENT_GAMES) {

    private val batting = mutableMapOf<PlayerId, PlayerBatting>()
    private val pitching = mutableMapOf<PlayerId, PlayerPitching>()
    private val futuresBatting = mutableMapOf<PlayerId, BattingLine>()
    private val futuresPitching = mutableMapOf<PlayerId, PitchingLine>()
    private val recentBoxScores = mutableMapOf<TeamId, MutableList<BoxScore>>()

    fun add(box: BoxScore) {
        listOf(box.home, box.away).forEach { team ->
            team.batting.forEach { (id, line) -> batting[id] = (batting[id] ?: PlayerBatting()) + line }
            team.pitching.forEach { (id, line) -> pitching[id] = (pitching[id] ?: PlayerPitching()) + line }
            val recent = recentBoxScores.getOrPut(team.teamId) { mutableListOf() }
            recent += box
            while (recent.size > recentGamesPerTeam) recent.removeAt(0)
        }
    }

    fun addFutures(playerId: PlayerId, line: BattingLine) {
        futuresBatting[playerId] = (futuresBatting[playerId] ?: BattingLine.EMPTY) + line
    }

    fun addFutures(playerId: PlayerId, line: PitchingLine) {
        futuresPitching[playerId] = (futuresPitching[playerId] ?: PitchingLine.EMPTY) + line
    }

    fun battingOf(playerId: PlayerId): PlayerBatting = batting[playerId] ?: PlayerBatting()

    fun pitchingOf(playerId: PlayerId): PlayerPitching = pitching[playerId] ?: PlayerPitching()

    fun futuresBattingOf(playerId: PlayerId): BattingLine = futuresBatting[playerId] ?: BattingLine.EMPTY

    fun futuresPitchingOf(playerId: PlayerId): PitchingLine = futuresPitching[playerId] ?: PitchingLine.EMPTY

    fun allBatting(): Map<PlayerId, PlayerBatting> = batting.toMap()

    fun allPitching(): Map<PlayerId, PlayerPitching> = pitching.toMap()

    fun allFuturesBatting(): Map<PlayerId, BattingLine> = futuresBatting.toMap()

    fun recentGames(teamId: TeamId): List<BoxScore> = recentBoxScores[teamId].orEmpty()

    /** 규정 타석 이상인 타자 순위. [minimum] 은 호출하는 쪽에서 정한다 (시즌 중에는 경기 수에 비례). */
    fun battingLeaders(minimumPa: Int, selector: (BattingLine) -> Double): List<Pair<PlayerId, BattingLine>> =
        batting.entries
            .map { it.key to it.value.total }
            .filter { it.second.plateAppearances >= minimumPa }
            .sortedByDescending { selector(it.second) }

    fun pitchingLeaders(minimumOuts: Int, selector: (PitchingLine) -> Double): List<Pair<PlayerId, PitchingLine>> =
        pitching.entries
            .map { it.key to it.value.total }
            .filter { it.second.outs >= minimumOuts }
            .sortedByDescending { selector(it.second) }

    private companion object {
        const val DEFAULT_RECENT_GAMES = 10
    }
}
