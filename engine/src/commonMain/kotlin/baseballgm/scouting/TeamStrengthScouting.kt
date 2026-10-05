package baseballgm.scouting

import baseballgm.io.BalanceConfig
import baseballgm.league.OverallEstimate
import baseballgm.league.StrengthCalculator
import baseballgm.league.TeamStrengthRange
import baseballgm.model.Batter
import baseballgm.model.PitcherRole
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId

/** 리그 전력 비교표 한 줄 */
data class TeamPowerRow(val teamId: TeamId, val strength: TeamStrengthRange, val isViewer: Boolean)

/**
 * 리그 전력 비교 (로스터 화면, 2026-10-01).
 *
 * @param rows 추정 중심이 높은 순
 * @param viewerRank 보는 팀의 **추정** 순위 범위 ("3~5위"). 보는 팀이 없으면 null
 */
data class LeaguePowerBoard(val rows: List<TeamPowerRow>, val viewerRank: IntRange?)

/**
 * 팀 전력을 **스카우트 시선으로** 본다 (불변 원칙 4).
 *
 * 화면이 [StrengthCalculator.of] 로 타 팀 전력을 직접 계산하면 진짜 능력치가 그대로 새어 나간다.
 * 그래서 선수 하나하나를 [ScoutingService] 의 정확도로 [ScoutingView] 에 통과시킨 **관측값**만 모아
 * 팀 전력 범위를 낸다. 우리 팀은 정확도가 "정확"이라 범위 폭이 0 — 즉 진짜 전력과 같다.
 * 집중 관찰을 붙인 타 팀 선수가 많을수록 그 팀 범위가 좁아진다.
 */
class TeamStrengthScouting(
    private val strength: StrengthCalculator,
    private val scouting: ScoutingService,
    balance: BalanceConfig,
) {
    private val spreadFactor = balance.double("scouting.teamStrength.spreadFactor")

    /** 현재 전력: 1군 등록 + 군 복무 아님 + 부상 아님. 지금 당장 경기에 나설 수 있는 전력이다 */
    /** @param record 선수의 공개 기록 (준주전급은 정확히 보인다, 2026-10-04). null 이면 예전 그대로 */
    fun current(
        players: List<Player>,
        department: ScoutingDepartment?,
        viewer: TeamId?,
        season: Int,
        record: ((Player) -> PublicRecord)? = null,
    ): TeamStrengthRange =
        rangeOf(
            players.filter { it.rosterLevel == RosterLevel.FIRST_TEAM && it.military.isAvailable && !it.condition.isInjured },
            department, viewer, season, record,
        )

    /** 베스트 전력: 1·2군 전체에서 군 복무 중이 아닌 선수로, 부상이 다 나았다고 치고 짠 최고 조합 */
    fun best(
        players: List<Player>,
        department: ScoutingDepartment?,
        viewer: TeamId?,
        season: Int,
        record: ((Player) -> PublicRecord)? = null,
    ): TeamStrengthRange =
        rangeOf(players.filter { it.military.isAvailable }, department, viewer, season, record)

    /**
     * 리그 전력 비교. 모든 팀의 **현재 전력**을 관측값으로 본다.
     *
     * 추정 순위: 보는 팀 전력이 정확한 값 u 일 때, 범위 아래 끝이 u 보다 높은 팀은 "확실히 위",
     * 위 끝이 u 보다 높은 팀은 "위일 수도". 그래서 순위는 (확실히 위 + 1) ~ (위일 수도 + 1) 이다.
     */
    fun leagueBoard(
        rosters: Map<TeamId, List<Player>>,
        department: ScoutingDepartment?,
        viewer: TeamId?,
        season: Int,
        record: ((Player) -> PublicRecord)? = null,
    ): LeaguePowerBoard {
        val rows = rosters.map { (teamId, players) ->
            TeamPowerRow(teamId, current(players, department, viewer, season, record), teamId == viewer)
        }.sortedByDescending { it.strength.overall.center }
        val mine = rows.firstOrNull { it.isViewer }?.strength?.overall
        val rank = mine?.let { own ->
            val others = rows.filterNot { it.isViewer }.map { it.strength.overall }
            val surelyAbove = others.count { it.low > own.center }
            val maybeAbove = others.count { it.high > own.center }
            (surelyAbove + 1)..(maybeAbove + 1)
        }
        return LeaguePowerBoard(rows, rank)
    }

    private fun rangeOf(
        pool: List<Player>,
        department: ScoutingDepartment?,
        viewer: TeamId?,
        season: Int,
        record: ((Player) -> PublicRecord)?,
    ): TeamStrengthRange {
        val estimates = pool.associateWith { estimateOf(it, department, viewer, season, record?.invoke(it)) }
        fun group(predicate: (Player) -> Boolean) = estimates.filterKeys(predicate).values.toList()
        return strength.estimate(
            batters = group { it is Batter },
            starters = group { it is Pitcher && it.role == PitcherRole.STARTER },
            relievers = group { it is Pitcher && it.role.isReliever },
            spreadFactor = spreadFactor,
        )
    }

    /** 관측한 능력치 범위의 중심으로 종합을 내고, 반폭은 정확도의 반폭을 쓴다 */
    private fun estimateOf(player: Player, department: ScoutingDepartment?, viewer: TeamId?, season: Int, record: PublicRecord?): OverallEstimate {
        val precision = scouting.precisionFor(department, player, viewer, record = record)
        val view = ScoutingView.of(player, precision, season, scouting.potentialScale)
        val center = strength.overallOf(view.ratings.mapValues { it.value.center }, isBatter = player is Batter)
        return OverallEstimate(center, precision.halfWidth.toDouble())
    }
}
