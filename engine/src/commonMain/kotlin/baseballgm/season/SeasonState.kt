package baseballgm.season

import baseballgm.league.League
import baseballgm.league.Standings
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.model.ManagerTendencies
import baseballgm.stats.SeasonStats
import baseballgm.tactics.DirectivePreset
import baseballgm.tactics.WeeklyPolicy

/**
 * 한 시즌이 진행되는 동안 바뀌는 상태 전부.
 *
 * `League` 는 시즌 시작 시점의 고정 데이터고, 경기를 치르며 달라지는 것(컨디션, 엔트리, 기록, 순위)은
 * 여기 모여 있다. 선수는 불변 객체라 바뀔 때마다 [update] 로 새 사본을 넣는다 —
 * "어딘가에서 몰래 선수를 고쳐 놓는" 버그를 막기 위해서다.
 */
class SeasonState(
    val league: League,
    val calendar: SeasonCalendar,
) {
    private val playerMap: MutableMap<PlayerId, Player> = league.players.associateBy { it.id }.toMutableMap()
    private val teamRosters: Map<TeamId, List<PlayerId>> =
        league.teams.associate { team -> team.id to league.playersOf(team.id).map { it.id } }
    private val rotationIndex: MutableMap<TeamId, Int> = mutableMapOf()

    var week: Int = 1
        internal set

    var standings: Standings = Standings.empty(league.teams.map { it.id })
        internal set

    val stats: SeasonStats = SeasonStats()
    val roster: RosterState = RosterState()
    val usage: PitcherUsage = PitcherUsage()
    val inbox: Inbox = Inbox()
    val policies: MutableMap<TeamId, WeeklyPolicy> = mutableMapOf()

    /**
     * 팀별 감독 성향. 감독은 캐릭터라서 시즌 중에도 값이 움직인다 —
     * 단장이 방침을 내리면 반영률만큼 이쪽으로 끌려온다 (docs/06).
     */
    val managerTendencies: MutableMap<TeamId, ManagerTendencies> = league.teams.associate { team ->
        team.id to (league.managerOf(team.id)?.tendencies ?: DirectivePreset.STANDARD.tendencies())
    }.toMutableMap()

    /** 단장이 내린 방침(목표 성향). 매주 조금씩 감독 성향에 섞인다. */
    val gmDirections: MutableMap<TeamId, ManagerTendencies> = mutableMapOf()

    val season: Int get() = league.season

    val isRegularSeasonOver: Boolean get() = week > calendar.regularSeasonWeeks

    fun player(id: PlayerId): Player = playerMap[id] ?: error("그런 선수가 없다: $id")

    fun playersOf(teamId: TeamId): List<Player> = teamRosters.getValue(teamId).map { playerMap.getValue(it) }

    fun firstTeamOf(teamId: TeamId): List<Player> =
        playersOf(teamId).filter { it.rosterLevel == RosterLevel.FIRST_TEAM }

    fun futuresOf(teamId: TeamId): List<Player> =
        playersOf(teamId).filter { it.rosterLevel == RosterLevel.FUTURES }

    fun allPlayers(): List<Player> = playerMap.values.toList()

    fun update(player: Player) {
        playerMap[player.id] = player
    }

    fun policyOf(teamId: TeamId): WeeklyPolicy = policies[teamId] ?: WeeklyPolicy.NORMAL

    fun tendenciesOf(teamId: TeamId): ManagerTendencies =
        managerTendencies[teamId] ?: DirectivePreset.STANDARD.tendencies()

    /** 선발 로테이션은 팀마다 순서대로 돌린다. 경기마다 한 칸씩 전진한다. */
    fun nextRotationIndex(teamId: TeamId): Int {
        val index = rotationIndex.getOrElse(teamId) { 0 }
        rotationIndex[teamId] = index + 1
        return index
    }

    /** 시즌 시작부터 통산 며칠째인가 (연투·7일 투구수 계산용). */
    fun absoluteDay(week: Int, day: Int): Int = (week - 1) * DAYS_PER_WEEK + day

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
