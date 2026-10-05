package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import kotlinx.serialization.Serializable
import kotlin.math.pow

/** 지명 순번 한 자리. */
@Serializable
data class DraftSlot(
    val overallPick: Int,
    val round: Int,
    /** 라운드 안에서의 순번 (1 = 그 라운드 맨 앞) */
    val slot: Int,
    /** 실제로 지명하는 팀 (지명권 소유자) */
    val ownerTeam: TeamId,
    /** 순번을 제공한 팀 */
    val originalTeam: TeamId,
) {
    val isTraded: Boolean get() = ownerTeam != originalTeam
}

/** 지명 한 건. */
@Serializable
data class DraftSelection(
    val overallPick: Int,
    val round: Int,
    val teamId: TeamId,
    val originalTeam: TeamId,
    val playerId: PlayerId,
    val playerName: String,
    val positionLabel: String,
    val schoolTypeLabel: String,
    /** 계약금(억원). 앞 순번일수록 크다 */
    val signingBonus: Double,
) {
    fun line(): String =
        "${round}R ${overallPick}순위 ${teamId.value} — $playerName ($positionLabel, $schoolTypeLabel) 계약금 ${signingBonus}억"
}

/** 드래프트가 끝난 뒤 남는 결과. 스토브리그가 이걸 보고 입단시킨다. */
@Serializable
data class DraftResult(
    val season: Int,
    val selections: List<DraftSelection>,
    /** 미지명 선수 (육성선수 계약 대상) */
    val undrafted: List<PlayerId>,
)

/**
 * 진행 중인 드래프트.
 *
 * 순번을 하나씩 소비하며 지명을 쌓는다. 유저 차례에서 멈출 수 있어야 해서 상태를 들고 있다
 * (docs/07 "드래프트 주차에는 자동 진행이 반드시 멈춘다").
 */
class DraftState(
    val season: Int,
    val order: List<DraftSlot>,
    val pool: DraftPool,
) {
    private val available = pool.prospects.toMutableList()
    private val picked = mutableListOf<DraftSelection>()

    var index: Int = 0
        private set

    val selections: List<DraftSelection> get() = picked.toList()

    val isComplete: Boolean get() = index >= order.size || available.isEmpty()

    fun current(): DraftSlot? = order.getOrNull(index)

    fun availableProspects(): List<DraftProspect> = available.toList()

    fun isAvailable(playerId: PlayerId): Boolean = available.any { it.id == playerId }

    fun selectionsOf(teamId: TeamId): List<DraftSelection> = picked.filter { it.teamId == teamId }

    internal fun commit(selection: DraftSelection) {
        available.removeAll { it.id == selection.playerId }
        picked += selection
        index++
    }

    /** 세이브에서 되살린다: 남은 후보(순서 그대로)·지명 목록·현재 순번 */
    internal fun restore(availableIds: List<PlayerId>, selections: List<DraftSelection>, at: Int) {
        val byId = pool.prospects.associateBy { it.id }
        available.clear(); available.addAll(availableIds.mapNotNull { byId[it] })
        picked.clear(); picked.addAll(selections)
        index = at
    }

    /** 남은 순번을 건너뛴다 (풀이 비었을 때). */
    internal fun skipRest() {
        index = order.size
    }

    fun result(): DraftResult = DraftResult(season, selections, available.map { it.id })
}

/**
 * 신인 드래프트 (docs/10).
 *
 * 순번은 **매 라운드 전년도 역순**이다 (임시 규칙, CLAUDE.md §8). 전년도 순위는 구단의
 * `draftPick` 에 들어 있고, 스토브리그에서 그해 최종 순위로 갱신된다.
 */
class Draft(balance: BalanceConfig) {

    private val section = balance.section("draft")
    val rounds: Int = section.int("rounds")
    private val bonusFirst = section.double("signingBonus.firstPick")
    private val bonusLast = section.double("signingBonus.lastPick")
    private val bonusCurve = section.double("signingBonus.curve")

    /**
     * 순번표를 만든다.
     *
     * @param pickOrder 구단 → 전년도 성적으로 정해진 순번(1 이 맨 앞)
     * @param rights 지명권 보유 현황. 트레이드된 지명권은 소유자가 대신 지명한다
     */
    fun prepare(
        season: Int,
        pickOrder: Map<TeamId, Int>,
        rights: DraftRights,
        pool: DraftPool,
    ): DraftState {
        val teams = pickOrder.entries.sortedBy { it.value }.map { it.key }
        val order = (1..rounds).flatMap { round ->
            teams.mapIndexed { slotIndex, team ->
                val slot = slotIndex + 1
                DraftSlot(
                    overallPick = (round - 1) * teams.size + slot,
                    round = round,
                    slot = slot,
                    ownerTeam = rights.find(season, round, team)?.ownerTeam ?: team,
                    originalTeam = team,
                )
            }
        }
        return DraftState(season, order, pool)
    }

    /** 계약금. 앞 순번일수록 가파르게 크다 (docs/10). */
    fun signingBonus(overallPick: Int, totalPicks: Int): Double {
        if (totalPicks <= 1) return bonusFirst
        val fromEnd = (totalPicks - overallPick).toDouble() / (totalPicks - 1)
        val value = bonusLast + (bonusFirst - bonusLast) * fromEnd.pow(bonusCurve)
        return (value * MONEY_ROUND).toInt() / MONEY_ROUND
    }

    /** 지명 한 건을 확정한다. 순번의 주인이 아니어도 막지 않는다 — 부르는 쪽이 차례를 판단한다. */
    fun select(state: DraftState, prospectId: PlayerId): DraftSelection {
        val slot = state.current() ?: error("드래프트가 이미 끝났다")
        val prospect = state.pool.byId(prospectId) ?: error("드래프트 풀에 없는 선수다: $prospectId")
        require(state.isAvailable(prospectId)) { "이미 지명된 선수다: $prospectId" }

        val selection = DraftSelection(
            overallPick = slot.overallPick,
            round = slot.round,
            teamId = slot.ownerTeam,
            originalTeam = slot.originalTeam,
            playerId = prospect.id,
            playerName = prospect.player.registeredName,
            positionLabel = positionLabelOf(prospect),
            schoolTypeLabel = prospect.schoolTypeLabel,
            signingBonus = signingBonus(slot.overallPick, state.order.size),
        )
        state.commit(selection)
        return selection
    }

    private fun positionLabelOf(prospect: DraftProspect): String = when (val player = prospect.player) {
        is baseballgm.model.Batter -> player.primaryPosition.label
        is baseballgm.model.Pitcher -> if (player.role.isReliever) "RP" else "SP"
    }

    private companion object {
        const val MONEY_ROUND = 100.0
    }
}
