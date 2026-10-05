package baseballgm.season

import baseballgm.condition.AdaptationModel
import baseballgm.development.AgingCurves
import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.market.ForeignCandidate
import baseballgm.market.ForeignMarket
import baseballgm.market.MarketView
import baseballgm.market.PositionNeed
import baseballgm.market.Valuation
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import kotlin.math.max

/** 시즌 중 외국인 영입 결과. 빈 자리를 채웠으면 [outgoing] 이 없다 */
data class ForeignReplacement(
    val outgoing: PlayerId?,
    val incoming: PlayerId,
    val salary: Double,
    /** 내보내는 선수에게 지급하는 잔여 연봉 (docs/12) */
    val buyout: Double,
) {
    fun message(outName: String?, inName: String): String =
        if (outName == null) {
            "$inName 영입 — 빈 외국인 자리를 채웠어요 (${salary}억)"
        } else {
            "$outName 웨이버 공시 → $inName 영입 (${salary}억, 잔여 연봉 ${buyout}억 지급)"
        }
}

/**
 * 시즌 중 외국인 교체 (docs/12).
 *
 * 적응하지 못한 외국인을 갈아치우는 결정이다. **횟수와 마감일이 있어서** 아무 때나 몇 번이고
 * 바꿀 수는 없고, 내보내는 선수의 잔여 연봉도 지급해야 한다 — 그래서 "4~6주 기록을 보고
 * 언제 결정할까"가 시즌 초의 고민이 된다 (docs/12 적응력).
 *
 * 보유 인원이 3명 미만이면 **아무도 내보내지 않고 빈 자리에 바로 영입**할 수 있다
 * ([outgoing] = null). 이때 잔여 연봉 지급은 없지만 시즌 중 등록이므로 횟수·마감일은
 * 똑같이 적용한다 (임시 결정 — 방출 후 무료 재영입으로 횟수 제한을 피하는 길을 막는다).
 */
class ForeignService(balance: BalanceConfig, strength: StrengthCalculator) {

    private val valuation = Valuation(balance, AgingCurves(balance))
    private val marketView = MarketView(balance, strength)
    private val positionNeed = PositionNeed(balance, strength)
    private val regularSeasonWeeks = balance.int("season.regularSeasonWeeks")

    val market: ForeignMarket = ForeignMarket(balance, valuation, marketView, positionNeed)
    val adaptation: AdaptationModel = AdaptationModel(balance)

    fun replacementsLeft(state: SeasonState, teamId: TeamId): Int =
        max(0, market.rules.replacementsPerSeason - (state.foreignReplacements[teamId] ?: 0))

    fun isOpen(state: SeasonState): Boolean = state.week <= market.rules.replacementDeadlineWeek

    /** 내보내지 않고 바로 영입할 빈 자리가 있는가 */
    fun hasOpenSlot(state: SeasonState, teamId: TeamId): Boolean =
        market.rules.foreignersOf(state.playersOf(teamId)).size < market.rules.maxPerTeam

    /**
     * 지금 영입(교체)할 수 있는가. 못 하는 이유를 문장으로 돌려준다.
     * [outgoing] 이 null 이면 빈 자리 영입이다.
     */
    fun problemsForReplacement(
        state: SeasonState,
        teamId: TeamId,
        outgoing: PlayerId?,
        candidate: ForeignCandidate,
        salary: Double,
    ): List<String> {
        val problems = mutableListOf<String>()
        if (!isOpen(state)) {
            problems += "외국인 교체 마감(${market.rules.replacementDeadlineWeek}주차)이 지났다"
        }
        if (replacementsLeft(state, teamId) <= 0) {
            problems += "시즌 교체 횟수(${market.rules.replacementsPerSeason}회)를 다 썼다"
        }
        val out = outgoing?.let { id -> state.allPlayers().firstOrNull { it.id == id } }
        when {
            outgoing == null -> Unit
            out == null -> problems += "없는 선수다"
            out.teamId != teamId -> problems += "우리 팀 선수가 아니다"
            !out.isForeign -> problems += "외국인 선수만 교체 대상이다"
        }
        if (state.foreignPool().byId(candidate.id) == null) problems += "시장에 없는 선수다"

        val roster = state.playersOf(teamId).filter { it.id != outgoing }
        problems += market.rules.problemsForSigning(roster, candidate.player, salary, isNewContract = true)

        val funds = state.funds[teamId] ?: 0.0
        val buyout = buyoutOf(out, state.week)
        if (funds < buyout) problems += "잔여 연봉 ${buyout}억을 지급할 운용 자금이 모자란다"
        return problems.distinct()
    }

    /**
     * 남은 시즌 분의 연봉을 일시 지급한다 (docs/12 "기존 선수 잔여 연봉은 지급").
     * 주차가 늦을수록 물어 줄 돈이 적다.
     */
    fun buyoutOf(player: Player?, week: Int): Double {
        if (player == null) return 0.0
        val remaining = max(0, regularSeasonWeeks - week + 1).toDouble() / regularSeasonWeeks
        return (player.contract.salary * remaining * 100).toInt() / 100.0
    }

    /**
     * 교체(또는 빈 자리 영입)를 실행한다. 나가는 선수는 리그를 떠나고, 새 선수는 2군에서 시작한다
     * (적응 감점이 그대로 붙으므로 곧바로 잘하리라는 보장은 없다).
     */
    fun replace(
        state: SeasonState,
        teamId: TeamId,
        outgoing: PlayerId?,
        candidate: ForeignCandidate,
        salary: Double,
    ): ForeignReplacement {
        val out = outgoing?.let { state.player(it) }
        val buyout = buyoutOf(out, state.week)

        if (outgoing != null) state.removePlayer(outgoing)
        state.addPlayer(
            candidate.player
                .withTeam(teamId, RosterLevel.FUTURES)
                .withContract(
                    Contract(
                        salary = salary,
                        yearsRemaining = 1,
                        signingBonusRemaining = 0.0,
                        seasonsToFreeAgency = 0,
                        serviceSeasons = 0,
                        type = ContractType.FOREIGN,
                    ),
                )
                .withDebut(state.season),
            teamId,
        )
        state.consumeForeignPoolEntry(candidate.id)
        state.funds[teamId] = max(0.0, (state.funds[teamId] ?: 0.0) - buyout)
        state.foreignReplacements[teamId] = (state.foreignReplacements[teamId] ?: 0) + 1

        return ForeignReplacement(outgoing, candidate.id, salary, buyout)
    }

    /** 우리 팀이 지금 볼 수 있는 후보. 스카우트 정확도는 화면이 따로 입힌다 */
    fun candidates(state: SeasonState, isPitcher: Boolean? = null): List<ForeignCandidate> =
        state.foreignPool().candidates.filter { isPitcher == null || it.isPitcher == isPitcher }
}
