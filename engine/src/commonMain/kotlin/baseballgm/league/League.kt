package baseballgm.league

import baseballgm.model.Coach
import baseballgm.model.GeneralManager
import baseballgm.model.Manager
import baseballgm.model.MedicalStaff
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.StaffId
import baseballgm.model.Team
import baseballgm.model.TeamId
import kotlinx.serialization.Serializable

/**
 * 리그 전체 상태. `data/league_2026.json` 이 이 구조 그대로다.
 *
 * @param seed 이 리그를 만든 시드. 같은 시드 → 같은 리그 (docs/03 재현성)
 * @param freeAgentPool 소속이 없는 선수 (M7 부터 사용). M1 에서는 비어 있다
 */
@Serializable
data class League(
    val season: Int,
    val seed: Long,
    val salaryCap: Double,
    val teams: List<Team>,
    val players: List<Player>,
    val managers: List<Manager>,
    val coaches: List<Coach>,
    val medicalStaff: List<MedicalStaff>,
    val generalManagers: List<GeneralManager>,
    val schedule: Schedule,
    val freeAgentPool: List<PlayerId> = emptyList(),
    /** 리그 환경(공인구·스트라이크존). 시즌마다 이벤트로 바뀐다 (docs/04, M8) */
    val environment: LeagueEnvironment = LeagueEnvironment.NEUTRAL,
) {
    private val playersById: Map<PlayerId, Player> by lazy { players.associateBy { it.id } }
    private val playersByTeam: Map<TeamId, List<Player>> by lazy {
        players.filter { it.teamId != null }.groupBy { it.teamId!! }
    }

    fun team(id: TeamId): Team = teams.firstOrNull { it.id == id } ?: error("그런 구단이 없다: $id")

    fun player(id: PlayerId): Player = playersById[id] ?: error("그런 선수가 없다: $id")

    fun playersOf(id: TeamId): List<Player> = playersByTeam[id].orEmpty()

    fun firstTeamOf(id: TeamId): List<Player> =
        playersOf(id).filter { it.rosterLevel == RosterLevel.FIRST_TEAM }

    fun futuresOf(id: TeamId): List<Player> =
        playersOf(id).filter { it.rosterLevel == RosterLevel.FUTURES }

    fun manager(id: StaffId): Manager = managers.firstOrNull { it.id == id } ?: error("그런 감독이 없다: $id")

    fun managerOf(teamId: TeamId): Manager? = managers.firstOrNull { it.teamId == teamId }

    /** 무직 감독 후보 (스태프 시장). */
    fun unemployedManagers(): List<Manager> = managers.filter { it.teamId == null }

    fun coachesOf(teamId: TeamId): List<Coach> = coaches.filter { it.teamId == teamId }

    fun medicalStaffOf(teamId: TeamId): List<MedicalStaff> = medicalStaff.filter { it.teamId == teamId }

    fun generalManagerOf(teamId: TeamId): GeneralManager? = generalManagers.firstOrNull { it.teamId == teamId }

    /** 팀의 이번 시즌 연봉 총액(억원). 복무 중인 선수는 제외한다 (docs/12). */
    fun payrollOf(teamId: TeamId): Double =
        playersOf(teamId).filter { it.military.isAvailable }.sumOf { it.contract.salary }
}
