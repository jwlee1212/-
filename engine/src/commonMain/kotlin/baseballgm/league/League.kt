package baseballgm.league

import baseballgm.market.DraftPool
import baseballgm.market.DraftRights
import baseballgm.management.ManagementState
import baseballgm.market.ForeignPool
import baseballgm.market.TradeRecord
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
 * @param draftPool 올해 신인 드래프트 지명 대상 (docs/10). **개막 시점에 공개**되어 시즌 내내 관찰할 수 있다
 * @param draftRights 지명권 보유 현황. 트레이드된 지명권은 소유자가 달라진다
 * @param tradeHistory 성사된 트레이드 기록. 되팔기 금지(docs/11)를 검사할 때 쓴다
 * @param faOrigins FA 선수의 원소속팀. 보상선수·보상금(docs/11)과 "원소속팀 애정" 판단에 쓴다
 * @param foreignPool 이번 시즌 외국인 시장 (docs/12). 스토브리그 계약과 시즌 중 교체가 여기서 꺼낸다
 * @param management 시즌을 넘어 이어지는 경영 상태와 단장 커리어 (docs/13). M11 세이브의 중심이 된다
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
    val draftPool: DraftPool = DraftPool.EMPTY,
    val draftRights: DraftRights = DraftRights(),
    val tradeHistory: List<TradeRecord> = emptyList(),
    val faOrigins: Map<PlayerId, TeamId> = emptyMap(),
    /** 연속 소프트캡 초과 횟수 (docs/11 제재 단계). */
    val capOverruns: Map<TeamId, Int> = emptyMap(),
    val foreignPool: ForeignPool = ForeignPool.EMPTY,
    val management: ManagementState = ManagementState(),
    /** 리그 환경(공인구·스트라이크존). 시즌마다 이벤트로 바뀐다 (docs/04, M8) */
    val environment: LeagueEnvironment = LeagueEnvironment.NEUTRAL,
    /** 리그 역사 — 통산 기록·역대 우승·시상 (2026-10-01). 스토브리그마다 한 시즌씩 쌓인다 */
    val history: LeagueHistory = LeagueHistory(),
    /** 트레이드 연봉 보조 (2026-10-05). [payrollOf] 에 반영된다 */
    val retainedSalaries: List<baseballgm.market.RetainedSalary> = emptyList(),
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

    /** 팀의 외국인 선수 (docs/12 보유 3명). */
    fun foreignersOf(teamId: TeamId): List<Player> = playersOf(teamId).filter { it.isForeign }

    /** 전년도 성적으로 정해진 지명 순번 (1 = 전체 1순위). */
    fun draftOrder(): Map<TeamId, Int> = teams.associate { it.id to it.draftPick }

    /** 팀의 이번 시즌 연봉 총액(억원). 복무 중인 선수는 제외한다 (docs/12). */
    /**
     * 연봉 총액. 연봉 보조(2026-10-05)를 반영한다 — 다른 팀이 대신 내 주는 몫은 빼고, 우리가 남의 선수 몫을 내 주는 건 더한다
     */
    fun payrollOf(teamId: TeamId): Double {
        val active = playersOf(teamId).filter { it.military.isAvailable }
        val ids = active.map { it.id }.toSet()
        val covered = retainedSalaries.filter { it.playerId in ids && it.payer != teamId }.sumOf { it.amount }
        val paying = retainedSalaries.filter { it.payer == teamId && it.playerId !in ids }.sumOf { it.amount }
        return active.sumOf { it.contract.salary } - covered + paying
    }
}
