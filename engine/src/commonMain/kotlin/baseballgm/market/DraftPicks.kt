package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.model.TeamId
import kotlinx.serialization.Serializable

/**
 * 지명권 한 장 (docs/10 지명권 트레이드).
 *
 * "몇 번째 순번"이 아니라 **"어느 팀의 몇 라운드 지명권"** 으로 들고 있다. 순번은 전년도 성적으로
 * 정해지므로 내년 지명권은 아직 순번을 알 수 없고, 트레이드된 뒤 원소속팀 성적이 달라지면
 * 순번도 따라 달라져야 하기 때문이다 (docs/10 "미래 지명권: 순번 미정").
 */
@Serializable
data class DraftPickRight(
    val season: Int,
    val round: Int,
    /** 순번을 제공하는 팀. 이 팀의 성적이 순번을 정한다 */
    val originalTeam: TeamId,
    /** 지금 이 지명권을 가진 팀 */
    val ownerTeam: TeamId,
) {
    val isTraded: Boolean get() = originalTeam != ownerTeam

    fun label(): String = "$season ${round}라운드" + if (isTraded) " (${originalTeam.value} 지명권)" else ""
}

/** 리그 전체의 지명권 보유 현황. */
@Serializable
data class DraftRights(val picks: List<DraftPickRight> = emptyList()) {

    fun ofOwner(teamId: TeamId): List<DraftPickRight> =
        picks.filter { it.ownerTeam == teamId }.sortedWith(compareBy({ it.season }, { it.round }))

    fun ofSeason(season: Int): List<DraftPickRight> = picks.filter { it.season == season }

    fun find(season: Int, round: Int, originalTeam: TeamId): DraftPickRight? =
        picks.firstOrNull { it.season == season && it.round == round && it.originalTeam == originalTeam }

    /** 지명권 한 장의 주인을 바꾼다. */
    fun transferred(pick: DraftPickRight, toTeam: TeamId): DraftRights =
        DraftRights(picks.map { if (it.sameSlot(pick)) it.copy(ownerTeam = toTeam) else it })

    /** 드래프트가 끝난 시즌의 지명권을 버리고, 새로 트레이드 가능한 연도를 채운다. */
    fun rolledForward(finishedSeason: Int, teams: List<TeamId>, rounds: Int, years: Int): DraftRights {
        val kept = picks.filter { it.season > finishedSeason }
        val existing = kept.map { it.season to (it.round to it.originalTeam) }.toSet()
        val added = mutableListOf<DraftPickRight>()
        for (offset in 1..years) {
            val season = finishedSeason + offset
            for (round in 1..rounds) {
                for (team in teams) {
                    if (season to (round to team) in existing) continue
                    added += DraftPickRight(season, round, team, team)
                }
            }
        }
        return DraftRights(kept + added)
    }

    private fun DraftPickRight.sameSlot(other: DraftPickRight): Boolean =
        season == other.season && round == other.round && originalTeam == other.originalTeam

    companion object {
        /** 리그를 처음 만들 때: 모든 팀이 자기 지명권을 [years] 년치 가진다. */
        fun initial(season: Int, teams: List<TeamId>, rounds: Int, years: Int): DraftRights =
            DraftRights(
                (0 until years).flatMap { offset ->
                    (1..rounds).flatMap { round ->
                        teams.map { DraftPickRight(season + offset, round, it, it) }
                    }
                },
            )
    }
}

/**
 * 지명권 트레이드 규칙 (docs/10 안전장치).
 *
 * 거래 자체는 M7 트레이드가 처리하고, 여기서는 **지명권 쪽 제약만** 본다.
 * 문제를 예외로 던지지 않고 목록으로 돌려주는 이유는, 화면에서 "왜 안 되는지"를 보여 주기 위해서다.
 */
class DraftPickTradeRules(balance: BalanceConfig) {

    private val section = balance.section("draft")
    val tradeableYears: Int = section.int("tradeablePickYears")
    private val noConsecutiveFirstRound: Boolean = section.boolean("noConsecutiveFirstRoundTrade")

    fun problems(
        rights: DraftRights,
        pick: DraftPickRight,
        from: TeamId,
        to: TeamId,
        currentSeason: Int,
        draftDone: Boolean,
    ): List<String> {
        val problems = mutableListOf<String>()
        if (pick.ownerTeam != from) problems += "${from.value} 가 가진 지명권이 아니다"
        if (from == to) problems += "같은 구단끼리는 주고받을 수 없다"
        if (pick.season < currentSeason || (pick.season == currentSeason && draftDone)) {
            problems += "이미 지난 드래프트의 지명권이다"
        }
        if (pick.season > currentSeason + tradeableYears - 1) {
            problems += "올해와 내년 지명권까지만 거래할 수 있다"
        }
        if (noConsecutiveFirstRound && pick.round == FIRST_ROUND && pick.originalTeam == from) {
            val neighbours = listOf(pick.season - 1, pick.season + 1)
            val alreadyGone = neighbours.any { season ->
                rights.find(season, FIRST_ROUND, from)?.let { it.ownerTeam != from } ?: false
            }
            if (alreadyGone) problems += "1라운드 지명권은 2년 연속 양도할 수 없다"
        }
        return problems
    }

    private companion object {
        const val FIRST_ROUND = 1
    }
}
