package baseballgm.app

import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.stats.BatterMetrics
import baseballgm.stats.BattingLine
import baseballgm.stats.PitcherMetrics
import baseballgm.stats.PitchingLine
import baseballgm.util.fixed
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 기록실 데이터 (2026-10-04, 유저 요청 "팀 내 스탯 순위를 볼 수 있으면 좋겠고, 스탯을 한 눈에 보기가 어렵다").
 *
 * 화면과 분리했다 ([WeekReveal] 처럼). 표·순위·백분위는 전부 공개 기록(1군 성적)에서만 나온다 —
 * 능력치·잠재력은 쓰지 않아서 타 팀 선수에게 써도 정보 은닉(불변 원칙 4)과 상관없다.
 */

/** 팀 기록 보기: 타격 / 투구 */
enum class TeamView(val label: String) { BATTING("타격"), PITCHING("투구") }

/**
 * 한 팀의 시즌 합계 + 세이버 지표.
 * @param estimated 옛 세이브라 지금 소속 선수 기록으로 더한 추정인가
 */
class TeamStats(
    val teamId: baseballgm.model.TeamId,
    val name: String,
    val totals: baseballgm.stats.TeamTotals,
    val batting: BatterMetrics,
    val pitching: PitcherMetrics,
    val runsAllowed: Int,
    val estimated: Boolean,
)

/** 타자 한 명의 시즌 기록 묶음 */
class BatterStats(val player: Player, val line: BattingLine, val metrics: BatterMetrics, val war: Double)

/** 투수 한 명의 시즌 기록 묶음 */
class PitcherStats(val player: Player, val line: PitchingLine, val metrics: PitcherMetrics, val war: Double)

/**
 * 기록표 열 하나.
 *
 * @param lowerIsBetter 평균자책·삼진% 처럼 낮을수록 좋은 지표. 정렬 기본 방향과 백분위 방향이 뒤집힌다
 * @param rate 비율 지표 (타율·OPS·평균자책 등). 표본이 적은 선수는 순위·백분위에서 뺀다
 * @param width 표 칸 폭 (dp). 크기라 간격 토큰 대상이 아니다
 */
class StatColumn<T>(
    val header: String,
    val lowerIsBetter: Boolean = false,
    val rate: Boolean = true,
    val width: Int = 44,
    val value: (T) -> Double,
    val format: (T) -> String,
)

enum class StatScope(val label: String) { LEAGUE("리그 전체"), TEAM("우리 팀") }

/** 기본 기록 / 세이버 지표 (docs/16 §3 "기록: 기본 탭 / 세이버 탭") */
enum class StatView(val label: String) { BASIC("기본"), SABER("세이버") }

/** 기록표 포지션 거르기 (타자는 주 포지션, 투수는 보직). 공개 정보라 원칙 4 와 무관 */
enum class StatPosition(val label: String, val pitcher: Boolean, private val codes: Set<String>) {
    ALL_BATTERS("전체", false, emptySet()),
    C("C", false, setOf("C")),
    FIRST("1B", false, setOf("1B")),
    SECOND("2B", false, setOf("2B")),
    THIRD("3B", false, setOf("3B")),
    SS("SS", false, setOf("SS")),
    INFIELD("내야", false, setOf("1B", "2B", "3B", "SS")),
    LF("LF", false, setOf("LF")),
    CF("CF", false, setOf("CF")),
    RF("RF", false, setOf("RF")),
    OUTFIELD("외야", false, setOf("LF", "CF", "RF")),
    DH("DH", false, setOf("DH")),
    ALL_PITCHERS("전체", true, emptySet()),
    STARTER("선발", true, setOf("SP")),
    BULLPEN("불펜", true, setOf("RP", "CL")),
    CLOSER("마무리", true, setOf("CL")),
    ;

    fun matches(player: Player): Boolean = codes.isEmpty() || codeOf(player) in codes

    companion object {
        fun forPitchers(pitchers: Boolean): List<StatPosition> = entries.filter { it.pitcher == pitchers }

        fun codeOf(player: Player): String = when (player) {
            is Batter -> player.primaryPosition.label
            is Pitcher -> when (player.role) {
                baseballgm.model.PitcherRole.STARTER -> "SP"
                baseballgm.model.PitcherRole.RELIEVER -> "RP"
                baseballgm.model.PitcherRole.CLOSER -> "CL"
            }
        }
    }
}

/** 최소 표본 (타석·이닝) */
enum class SampleMode(val label: String) {
    QUALIFIED("규정 이상"),
    HALF("규정 절반"),
    CUSTOM("직접"),
    ALL("전체"),
}

/**
 * 기록표 필터 (2026-10-04, 유저 요청 "유격수 OPS, 일정 타석 이상 필터링").
 * @param custom [SampleMode.CUSTOM] 일 때 최소 타석, 투수는 최소 **아웃**(이닝 × 3)
 */
data class StatFilter(
    val position: StatPosition,
    val sample: SampleMode,
    val custom: Int = 0,
) {
    /** 기본값과 다른 조건 수 (필터 막대 머리줄) */
    fun activeCount(defaultSample: SampleMode): Int =
        (if (position != StatPosition.ALL_BATTERS && position != StatPosition.ALL_PITCHERS) 1 else 0) + (if (sample != defaultSample) 1 else 0)

    companion object {
        fun default(pitchers: Boolean, scope: StatScope) = StatFilter(
            position = if (pitchers) StatPosition.ALL_PITCHERS else StatPosition.ALL_BATTERS,
            // 리그 전체는 규정 이상이 기본, 우리 팀은 전원 (팀 내 순위)
            sample = if (scope == StatScope.LEAGUE) SampleMode.QUALIFIED else SampleMode.ALL,
        )
    }
}

object StatBoard {

    /**
     * 순위·백분위에 넣는 최소 표본. 규정(타석 = 팀 경기 × 3.1, 이닝 = 팀 경기 × 1)의 몇 배인가.
     * 투수는 불펜도 비교하려고 규정의 1/3 — 화면 표시 기준이라 코드 상수 (임시 결정)
     */
    const val SAMPLE_SHARE_BATTER = 0.5
    const val SAMPLE_SHARE_PITCHER = 1.0 / 3.0

    /** 리그 전체에서 규정 미달까지 보일 때 너무 길어지지 않게 */
    const val UNQUALIFIED_LIMIT = 100

    /** 백분위를 낼 만큼 비교할 선수가 있어야 한다 (시즌 초) */
    const val MIN_POPULATION = 10

    fun batters(session: GameSession): List<BatterStats> =
        session.state.allPlayers()
            .filter { it is Batter && it.teamId != null }
            .mapNotNull { player ->
                val line = session.batting(player.id)
                if (line.plateAppearances == 0) return@mapNotNull null
                BatterStats(player, line, session.batterMetrics(player), session.war(player).war)
            }

    fun pitchers(session: GameSession): List<PitcherStats> =
        session.state.allPlayers()
            .filter { it is Pitcher && it.teamId != null }
            .mapNotNull { player ->
                val line = session.pitching(player.id)
                if (line.outs == 0) return@mapNotNull null
                PitcherStats(player, line, session.pitcherMetrics(player), session.war(player).war)
            }

    fun batterSample(session: GameSession): Int = max(1, (session.qualifyingPa() * SAMPLE_SHARE_BATTER).roundToInt())

    fun pitcherSample(session: GameSession): Int = max(1, (session.qualifyingOuts() * SAMPLE_SHARE_PITCHER).roundToInt())

    // ---------- 표 ----------

    fun battingColumns(view: StatView): List<StatColumn<BatterStats>> = when (view) {
        StatView.BASIC -> listOf(
            StatColumn("타석", rate = false, value = { it.line.plateAppearances.toDouble() }, format = { "${it.line.plateAppearances}" }),
            StatColumn("타율", width = 48, value = { it.line.battingAverage }, format = { it.line.battingAverage.fixed(3) }),
            StatColumn("출루", width = 48, value = { it.line.onBasePercentage }, format = { it.line.onBasePercentage.fixed(3) }),
            StatColumn("장타", width = 48, value = { it.line.sluggingPercentage }, format = { it.line.sluggingPercentage.fixed(3) }),
            StatColumn("OPS", width = 48, value = { it.line.ops }, format = { it.line.ops.fixed(3) }),
            StatColumn("안타", rate = false, value = { it.line.hits.toDouble() }, format = { "${it.line.hits}" }),
            StatColumn("홈런", rate = false, value = { it.line.homeRuns.toDouble() }, format = { "${it.line.homeRuns}" }),
            StatColumn("타점", rate = false, value = { it.line.rbi.toDouble() }, format = { "${it.line.rbi}" }),
            StatColumn("득점", rate = false, value = { it.line.runs.toDouble() }, format = { "${it.line.runs}" }),
            StatColumn("도루", rate = false, value = { it.line.stolenBases.toDouble() }, format = { "${it.line.stolenBases}" }),
            StatColumn("볼넷", rate = false, value = { it.line.walks.toDouble() }, format = { "${it.line.walks}" }),
            StatColumn("삼진", rate = false, lowerIsBetter = true, value = { it.line.strikeouts.toDouble() }, format = { "${it.line.strikeouts}" }),
            StatColumn("WAR", rate = false, value = { it.war }, format = { it.war.fixed(1) }),
        )
        StatView.SABER -> listOf(
            StatColumn("타석", rate = false, value = { it.line.plateAppearances.toDouble() }, format = { "${it.line.plateAppearances}" }),
            StatColumn("wOBA", width = 48, value = { it.metrics.woba }, format = { it.metrics.woba.fixed(3) }),
            StatColumn("wRC+", value = { it.metrics.wrcPlus }, format = { it.metrics.wrcPlus.fixed(0) }),
            StatColumn("ISO", width = 48, value = { it.metrics.iso }, format = { it.metrics.iso.fixed(3) }),
            StatColumn("BABIP", width = 52, value = { it.metrics.babip }, format = { it.metrics.babip.fixed(3) }),
            StatColumn("볼넷%", width = 48, value = { it.metrics.walkRate }, format = { percent(it.metrics.walkRate) }),
            StatColumn("삼진%", width = 48, lowerIsBetter = true, value = { it.metrics.strikeoutRate }, format = { percent(it.metrics.strikeoutRate) }),
            StatColumn("WAR", rate = false, value = { it.war }, format = { it.war.fixed(1) }),
        )
    }

    fun pitchingColumns(view: StatView): List<StatColumn<PitcherStats>> = when (view) {
        StatView.BASIC -> listOf(
            StatColumn("경기", rate = false, value = { it.line.games.toDouble() }, format = { "${it.line.games}" }),
            StatColumn("이닝", rate = false, width = 52, value = { it.line.outs.toDouble() }, format = { it.line.inningsText() }),
            StatColumn("평균자책", lowerIsBetter = true, width = 56, value = { it.line.era }, format = { it.line.era.fixed(2) }),
            StatColumn("승", rate = false, width = 36, value = { it.line.wins.toDouble() }, format = { "${it.line.wins}" }),
            StatColumn("패", rate = false, width = 36, lowerIsBetter = true, value = { it.line.losses.toDouble() }, format = { "${it.line.losses}" }),
            StatColumn("세", rate = false, width = 36, value = { it.line.saves.toDouble() }, format = { "${it.line.saves}" }),
            StatColumn("홀", rate = false, width = 36, value = { it.line.holds.toDouble() }, format = { "${it.line.holds}" }),
            StatColumn("탈삼진", rate = false, width = 52, value = { it.line.strikeouts.toDouble() }, format = { "${it.line.strikeouts}" }),
            StatColumn("볼넷", rate = false, lowerIsBetter = true, value = { it.line.walks.toDouble() }, format = { "${it.line.walks}" }),
            StatColumn("WHIP", width = 48, lowerIsBetter = true, value = { it.line.whip }, format = { it.line.whip.fixed(2) }),
            StatColumn("피안타율", width = 56, lowerIsBetter = true, value = { it.line.opponentAverage }, format = { it.line.opponentAverage.fixed(3) }),
            StatColumn("WAR", rate = false, value = { it.war }, format = { it.war.fixed(1) }),
        )
        StatView.SABER -> listOf(
            StatColumn("이닝", rate = false, width = 52, value = { it.line.outs.toDouble() }, format = { it.line.inningsText() }),
            StatColumn("FIP", lowerIsBetter = true, value = { it.metrics.fip }, format = { it.metrics.fip.fixed(2) }),
            StatColumn("ERA+", value = { it.metrics.eraPlus }, format = { it.metrics.eraPlus.fixed(0) }),
            StatColumn("K/9", value = { it.metrics.strikeoutsPer9 }, format = { it.metrics.strikeoutsPer9.fixed(1) }),
            StatColumn("BB/9", lowerIsBetter = true, value = { it.metrics.walksPer9 }, format = { it.metrics.walksPer9.fixed(1) }),
            StatColumn("HR/9", lowerIsBetter = true, value = { it.metrics.homeRunsPer9 }, format = { it.metrics.homeRunsPer9.fixed(1) }),
            StatColumn("K-BB%", width = 52, value = { it.metrics.strikeoutMinusWalkRate }, format = { percent(it.metrics.strikeoutMinusWalkRate) }),
            StatColumn("LOB%", width = 48, value = { it.metrics.leftOnBaseRate }, format = { percent(it.metrics.leftOnBaseRate) }),
            StatColumn("WAR", rate = false, value = { it.war }, format = { it.war.fixed(1) }),
        )
    }

    /** 처음 열었을 때 정렬할 열: 기본은 OPS·평균자책, 세이버는 WAR */
    fun defaultBattingSort(view: StatView): Int = battingColumns(view).indexOfFirst { it.header == if (view == StatView.BASIC) "OPS" else "WAR" }

    fun defaultPitchingSort(view: StatView): Int = pitchingColumns(view).indexOfFirst { it.header == if (view == StatView.BASIC) "평균자책" else "WAR" }

    /**
     * 표에 올릴 줄. 리그 전체는 기본이 규정 이상, 우리 팀은 1군 기록이 있는 전원.
     * 비율 지표로 정렬할 땐 표본이 적은 선수를 뒤로 보낸다 (3타석 1.000 이 1위가 되지 않게).
     */
    fun <T> table(
        rows: List<T>,
        column: StatColumn<T>,
        descending: Boolean,
        sampleOf: (T) -> Int,
        sample: Int,
        player: (T) -> Player,
    ): List<T> {
        val order = compareBy<T> { if (column.rate && sampleOf(it) < sample) 1 else 0 }
            .thenBy { if (descending) -column.value(it) else column.value(it) }
            .thenBy { player(it).id.value }
        return rows.sortedWith(order)
    }

    /** 필터의 최소 표본 (타석). 0 이면 1군 기록이 있는 전원 */
    fun minimumPa(session: GameSession, filter: StatFilter): Int = when (filter.sample) {
        SampleMode.QUALIFIED -> session.qualifyingPa()
        SampleMode.HALF -> batterSample(session)
        SampleMode.CUSTOM -> filter.custom
        SampleMode.ALL -> 0
    }

    /** 필터의 최소 표본 (아웃) */
    fun minimumOuts(session: GameSession, filter: StatFilter): Int = when (filter.sample) {
        SampleMode.QUALIFIED -> session.qualifyingOuts()
        SampleMode.HALF -> pitcherSample(session)
        SampleMode.CUSTOM -> filter.custom
        SampleMode.ALL -> 0
    }

    fun battingTable(
        session: GameSession,
        scope: StatScope,
        view: StatView,
        sortColumn: Int,
        descending: Boolean,
        filter: StatFilter = StatFilter.default(pitchers = false, scope),
    ): List<BatterStats> {
        val column = battingColumns(view)[sortColumn]
        val minimum = minimumPa(session, filter)
        val rows = batters(session)
            .filter { scope == StatScope.LEAGUE || it.player.teamId == session.userTeamId }
            .filter { filter.position.matches(it.player) && it.line.plateAppearances >= minimum }
        val sorted = table(rows, column, descending, { it.line.plateAppearances }, batterSample(session)) { it.player }
        return limited(sorted, scope, minimum < batterSample(session))
    }

    fun pitchingTable(
        session: GameSession,
        scope: StatScope,
        view: StatView,
        sortColumn: Int,
        descending: Boolean,
        filter: StatFilter = StatFilter.default(pitchers = true, scope),
    ): List<PitcherStats> {
        val column = pitchingColumns(view)[sortColumn]
        val minimum = minimumOuts(session, filter)
        val rows = pitchers(session)
            .filter { scope == StatScope.LEAGUE || it.player.teamId == session.userTeamId }
            .filter { filter.position.matches(it.player) && it.line.outs >= minimum }
        val sorted = table(rows, column, descending, { it.line.outs }, pitcherSample(session)) { it.player }
        return limited(sorted, scope, minimum < pitcherSample(session))
    }

    /** 리그 전체에서 표본을 낮게 잡으면 수백 명이 된다 — 위에서 [UNQUALIFIED_LIMIT] 명까지 */
    private fun <T> limited(rows: List<T>, scope: StatScope, small: Boolean): List<T> =
        if (scope == StatScope.LEAGUE && small) rows.take(UNQUALIFIED_LIMIT) else rows

    // ---------- 팀 기록 ----------

    /** 리그 전체 팀 합계 (팀 타율·팀 평균자책 …). 경기 수 0 인 팀도 넣는다 */
    fun teams(session: GameSession): List<TeamStats> {
        val stats = session.state.stats
        val constants = session.leagueConstants()
        val estimated = !stats.hasTeamTotals
        return session.league.teams.map { team ->
            val totals = if (estimated) estimatedTotals(session, team.id) else stats.teamTotalsOf(team.id)
            TeamStats(
                teamId = team.id,
                name = team.name,
                totals = totals,
                batting = session.sabermetrics.batter(totals.batting, constants, team.parkFactor),
                pitching = session.sabermetrics.pitcher(totals.pitching, constants, team.parkFactor),
                runsAllowed = session.record(team.id).runsAllowed,
                estimated = estimated,
            )
        }
    }

    /** 옛 세이브(팀 합계 없음): 지금 소속 선수 기록을 더한 추정. 트레이드한 선수는 새 팀에 붙는다 */
    private fun estimatedTotals(session: GameSession, teamId: baseballgm.model.TeamId): baseballgm.stats.TeamTotals {
        val players = session.state.allPlayers().filter { it.teamId == teamId }
        return baseballgm.stats.TeamTotals(
            games = session.record(teamId).games,
            batting = players.fold(BattingLine.EMPTY) { acc, p -> acc + session.batting(p.id) },
            pitching = players.fold(PitchingLine.EMPTY) { acc, p -> acc + session.pitching(p.id) },
        )
    }

    fun teamColumns(view: TeamView): List<StatColumn<TeamStats>> = when (view) {
        TeamView.BATTING -> listOf(
            StatColumn("타율", width = 48, value = { it.totals.batting.battingAverage }, format = { it.totals.batting.battingAverage.fixed(3) }),
            StatColumn("출루", width = 48, value = { it.totals.batting.onBasePercentage }, format = { it.totals.batting.onBasePercentage.fixed(3) }),
            StatColumn("장타", width = 48, value = { it.totals.batting.sluggingPercentage }, format = { it.totals.batting.sluggingPercentage.fixed(3) }),
            StatColumn("OPS", width = 48, value = { it.totals.batting.ops }, format = { it.totals.batting.ops.fixed(3) }),
            StatColumn("득점", value = { it.totals.batting.runs.toDouble() }, format = { "${it.totals.batting.runs}" }),
            StatColumn("홈런", value = { it.totals.batting.homeRuns.toDouble() }, format = { "${it.totals.batting.homeRuns}" }),
            StatColumn("안타", value = { it.totals.batting.hits.toDouble() }, format = { "${it.totals.batting.hits}" }),
            StatColumn("도루", value = { it.totals.batting.stolenBases.toDouble() }, format = { "${it.totals.batting.stolenBases}" }),
            StatColumn("볼넷", value = { it.totals.batting.walks.toDouble() }, format = { "${it.totals.batting.walks}" }),
            StatColumn("삼진", lowerIsBetter = true, value = { it.totals.batting.strikeouts.toDouble() }, format = { "${it.totals.batting.strikeouts}" }),
            StatColumn("wRC+", value = { it.batting.wrcPlus }, format = { it.batting.wrcPlus.fixed(0) }),
        )
        TeamView.PITCHING -> listOf(
            StatColumn("평균자책", lowerIsBetter = true, width = 56, value = { it.totals.pitching.era }, format = { it.totals.pitching.era.fixed(2) }),
            StatColumn("WHIP", width = 48, lowerIsBetter = true, value = { it.totals.pitching.whip }, format = { it.totals.pitching.whip.fixed(2) }),
            StatColumn("실점", lowerIsBetter = true, value = { it.runsAllowed.toDouble() }, format = { "${it.runsAllowed}" }),
            StatColumn("탈삼진", width = 52, value = { it.totals.pitching.strikeouts.toDouble() }, format = { "${it.totals.pitching.strikeouts}" }),
            StatColumn("볼넷", lowerIsBetter = true, value = { it.totals.pitching.walks.toDouble() }, format = { "${it.totals.pitching.walks}" }),
            StatColumn("피홈런", width = 52, lowerIsBetter = true, value = { it.totals.pitching.homeRuns.toDouble() }, format = { "${it.totals.pitching.homeRuns}" }),
            StatColumn("세이브", width = 52, value = { it.totals.pitching.saves.toDouble() }, format = { "${it.totals.pitching.saves}" }),
            StatColumn("FIP", lowerIsBetter = true, value = { it.pitching.fip }, format = { it.pitching.fip.fixed(2) }),
            StatColumn("ERA+", value = { it.pitching.eraPlus }, format = { it.pitching.eraPlus.fixed(0) }),
            StatColumn("실책", lowerIsBetter = true, value = { it.totals.errors.toDouble() }, format = { "${it.totals.errors}" }),
        )
    }

    fun teamTable(session: GameSession, view: TeamView, sortColumn: Int, descending: Boolean): List<TeamStats> {
        val column = teamColumns(view)[sortColumn]
        return teams(session).sortedWith(
            compareBy<TeamStats> { if (descending) -column.value(it) else column.value(it) }.thenBy { it.teamId.value },
        )
    }

    /**
     * 우리 팀의 팀 기록 한 칸: (값, 리그 순위). 좋은 쪽이 1위 (평균자책은 낮을수록).
     * 선수단 탭의 "팀 기록" 바로가기 설명줄에 쓴다.
     */
    fun teamRankOf(session: GameSession, view: TeamView, header: String): Pair<String, Int>? {
        val column = teamColumns(view).firstOrNull { it.header == header } ?: return null
        val all = teams(session)
        val mine = all.firstOrNull { it.teamId == session.userTeamId } ?: return null
        if (mine.totals.games == 0) return null
        val rank = all.count { other ->
            if (column.lowerIsBetter) column.value(other) < column.value(mine) else column.value(other) > column.value(mine)
        } + 1
        return column.format(mine) to rank
    }

    /** 처음 정렬: 타격은 OPS, 투구는 평균자책 */
    fun defaultTeamSort(view: TeamView): Int = teamColumns(view).indexOfFirst { it.header == if (view == TeamView.BATTING) "OPS" else "평균자책" }

    private fun percent(rate: Double): String = "${(rate * 100).fixed(1)}%"
}

// ---------- 선수 한 명: 한눈에 보기 ----------

/**
 * 기록 탭 맨 위 타일 하나: 값 + 팀 내 순위.
 * @param teamRank 팀 내 순위 (표본이 적으면 null)
 */
data class StatTile(val label: String, val value: String, val teamRank: Int?, val teamSize: Int)

/**
 * 리그 백분위 막대 한 줄 (Baseball Savant 식).
 * @param percentile 0~100. 이 선수보다 못한 비교 대상의 비율 — 100 에 가까울수록 좋다 (낮을수록 좋은 지표는 뒤집었다)
 */
data class PercentileBar(val label: String, val value: String, val percentile: Int) {
    /** "상위 N%" 의 N. 1 보다 작게 쓰지 않는다 */
    val topShare: Int get() = max(1, 100 - percentile)

    /** 가운데보다 좋으면 "상위 N%", 아니면 "하위 N%" — 못한 기록에 "상위 85%" 라고 쓰면 헷갈린다 */
    val rankText: String get() = if (percentile >= 50) "상위 $topShare%" else "하위 ${max(1, percentile)}%"
}

/**
 * 선수 상세 기록 탭의 머리 (한 줄 요약 → 핵심 숫자 → 상세 표 중 "핵심 숫자").
 *
 * @param percentiles 표본이 모자라거나 비교 대상이 적으면 null — 그 까닭은 [note]
 */
class PlayerStatSheet(
    val tiles: List<StatTile>,
    val percentiles: List<PercentileBar>?,
    val note: String?,
) {
    companion object {
        /** 1군 기록이 없으면 null */
        fun of(session: GameSession, player: Player): PlayerStatSheet? = when (player) {
            is Batter -> batter(session, player)
            is Pitcher -> pitcher(session, player)
        }

        private val BATTER_TILES = listOf("OPS", "홈런", "wRC+", "WAR")
        private val BATTER_BARS = listOf("OPS", "wRC+", "ISO", "볼넷%", "삼진%", "WAR")
        private val PITCHER_TILES = listOf("평균자책", "탈삼진", "FIP", "WAR")
        private val PITCHER_BARS = listOf("ERA+", "FIP", "K/9", "BB/9", "HR/9", "WAR")

        /** 기본·세이버 열에서 머리글로 찾는다 (WAR 처럼 둘 다 있으면 기본 쪽) */
        private fun <T> pick(columns: List<StatColumn<T>>, headers: List<String>): List<StatColumn<T>> =
            headers.map { header -> columns.first { it.header == header } }

        private fun batter(session: GameSession, player: Player): PlayerStatSheet? {
            val all = StatBoard.batters(session)
            val me = all.firstOrNull { it.player.id == player.id } ?: return null
            val sample = StatBoard.batterSample(session)
            val columns = StatBoard.battingColumns(StatView.BASIC) + StatBoard.battingColumns(StatView.SABER)
            val teammates = all.filter { it.player.teamId == player.teamId }
            val tiles = pick(columns, BATTER_TILES).map { tile(it, me, teammates, { row -> row.line.plateAppearances }, sample) }
            val pool = all.filter { it.line.plateAppearances >= sample }
            val (bars, note) = bars(pick(columns, BATTER_BARS), me, pool, me.line.plateAppearances >= sample, "${sample}타석")
            return PlayerStatSheet(tiles, bars, note)
        }

        private fun pitcher(session: GameSession, player: Player): PlayerStatSheet? {
            val all = StatBoard.pitchers(session)
            val me = all.firstOrNull { it.player.id == player.id } ?: return null
            val sample = StatBoard.pitcherSample(session)
            val columns = StatBoard.pitchingColumns(StatView.BASIC) + StatBoard.pitchingColumns(StatView.SABER)
            val teammates = all.filter { it.player.teamId == player.teamId }
            val tiles = pick(columns, PITCHER_TILES).map { tile(it, me, teammates, { row -> row.line.outs }, sample) }
            val pool = all.filter { it.line.outs >= sample }
            val innings = (sample / 3.0).roundToInt()
            val (bars, note) = bars(pick(columns, PITCHER_BARS), me, pool, me.line.outs >= sample, "${innings}이닝")
            return PlayerStatSheet(tiles, bars, note)
        }

        /** 팀 내 순위: 비율 지표는 표본을 채운 동료끼리, 누적 지표는 기록 있는 동료 전원 */
        private fun <T> tile(column: StatColumn<T>, me: T, teammates: List<T>, sampleOf: (T) -> Int, sample: Int): StatTile {
            val eligible = if (column.rate) teammates.filter { sampleOf(it) >= sample } else teammates
            val rank = if (me in eligible) {
                eligible.count { other -> better(column, other, me) } + 1
            } else {
                null
            }
            return StatTile(column.header, column.format(me), rank, eligible.size)
        }

        private fun <T> bars(columns: List<StatColumn<T>>, me: T, pool: List<T>, enough: Boolean, sampleText: String): Pair<List<PercentileBar>?, String?> = when {
            !enough -> null to "백분위는 $sampleText 부터 보여 줘요"
            pool.size < StatBoard.MIN_POPULATION -> null to "시즌 초라 비교할 선수가 아직 적어요"
            else -> columns.map { column -> PercentileBar(column.header, column.format(me), percentile(column, me, pool)) } to null
        }

        /** 나보다 못한 선수 비율. 같은 값은 절반만 센다 */
        fun <T> percentile(column: StatColumn<T>, me: T, pool: List<T>): Int {
            val others = pool.filter { it !== me }
            if (others.isEmpty()) return 50
            val worse = others.count { better(column, me, it) }
            val ties = others.count { column.value(it) == column.value(me) }
            return ((worse + ties / 2.0) / others.size * 100).roundToInt().coerceIn(0, 100)
        }

        private fun <T> better(column: StatColumn<T>, a: T, b: T): Boolean =
            if (column.lowerIsBetter) column.value(a) < column.value(b) else column.value(a) > column.value(b)
    }
}
