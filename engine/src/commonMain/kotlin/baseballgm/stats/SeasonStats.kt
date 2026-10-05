package baseballgm.stats

import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import kotlinx.serialization.Serializable

/** 시즌 기록 세이브 형태. 순서까지 그대로 담는다 (Map 은 넣은 순서를 지킨다) */
@Serializable
data class SeasonStatsSnapshot(
    val batting: Map<PlayerId, PlayerBatting> = emptyMap(),
    val pitching: Map<PlayerId, PlayerPitching> = emptyMap(),
    val futuresBatting: Map<PlayerId, BattingLine> = emptyMap(),
    val futuresPitching: Map<PlayerId, PitchingLine> = emptyMap(),
    val recentBoxScores: Map<TeamId, List<BoxScore>> = emptyMap(),
    /** 팀 합계 (2026-10-04). 그 전 세이브에는 없다 — 빈 채로 읽힌다 */
    val teamTotals: Map<TeamId, TeamTotals> = emptyMap(),
)

/**
 * 한 팀의 시즌 합계 (2026-10-04, 팀 타율·팀 평균자책 등).
 *
 * 선수별 기록을 지금 소속으로 더하면 트레이드로 옮긴 선수의 기록이 새 팀에 붙는다. 그래서 박스스코어를 쌓을 때
 * **그 경기에 뛴 팀** 쪽에 따로 더해 둔다.
 */
@Serializable
data class TeamTotals(
    val games: Int = 0,
    val batting: BattingLine = BattingLine.EMPTY,
    val pitching: PitchingLine = PitchingLine.EMPTY,
    val errors: Int = 0,
) {
    operator fun plus(team: TeamBoxScore): TeamTotals = TeamTotals(
        games = games + 1,
        batting = batting + team.battingTotal,
        pitching = pitching + team.pitchingTotal,
        errors = errors + team.errors,
    )
}

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
    private val teamTotals = mutableMapOf<TeamId, TeamTotals>()

    fun add(box: BoxScore) {
        listOf(box.home, box.away).forEach { team ->
            team.batting.forEach { (id, line) -> batting[id] = (batting[id] ?: PlayerBatting()) + line }
            team.pitching.forEach { (id, line) -> pitching[id] = (pitching[id] ?: PlayerPitching()) + line }
            teamTotals[team.teamId] = (teamTotals[team.teamId] ?: TeamTotals()) + team
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

    /** 팀 시즌 합계. 이 기능 전 세이브에서 이어 하면 불러온 뒤 경기부터만 쌓인다 — [hasTeamTotals] 로 구분한다 */
    fun teamTotalsOf(teamId: TeamId): TeamTotals = teamTotals[teamId] ?: TeamTotals()

    /** 팀 합계가 시즌 처음부터 쌓였나. 옛 세이브(합계 없음)에서 이어 하면 false */
    var hasTeamTotals: Boolean = true
        private set

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

    // ---------- 세이브 (docs/14) ----------

    internal fun export(): SeasonStatsSnapshot = SeasonStatsSnapshot(
        batting = batting.toMap(),
        pitching = pitching.toMap(),
        futuresBatting = futuresBatting.toMap(),
        futuresPitching = futuresPitching.toMap(),
        recentBoxScores = recentBoxScores.mapValues { it.value.toList() },
        teamTotals = if (hasTeamTotals) teamTotals.toMap() else emptyMap(),
    )

    internal fun import(snapshot: SeasonStatsSnapshot) {
        batting.clear(); batting.putAll(snapshot.batting)
        pitching.clear(); pitching.putAll(snapshot.pitching)
        futuresBatting.clear(); futuresBatting.putAll(snapshot.futuresBatting)
        futuresPitching.clear(); futuresPitching.putAll(snapshot.futuresPitching)
        recentBoxScores.clear()
        snapshot.recentBoxScores.forEach { (team, games) -> recentBoxScores[team] = games.toMutableList() }
        teamTotals.clear(); teamTotals.putAll(snapshot.teamTotals)
        // 기록은 있는데 팀 합계가 없으면 옛 세이브다. 이번 시즌 팀 합계는 선수 기록으로 추정해야 한다
        hasTeamTotals = snapshot.teamTotals.isNotEmpty() || snapshot.batting.isEmpty()
    }

    private companion object {
        const val DEFAULT_RECENT_GAMES = 10
    }
}
