package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.Standings
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.util.nextInRange
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** 협상 항목 (FA 협상 테이블, 2026-10-04) */
enum class FaTerm(val label: String) {
    SALARY("연봉"),
    YEARS("기간"),
    BONUS("계약금"),
}

/**
 * 에이전트가 내미는 요구 조건. 이대로 내면 선수가 만족하고, 지금 경쟁 구단 조건보다도 좋다.
 * @param importance 항목별 중요도 1~3 (선수 나이에 따른 FA 가중치에서 나온다 — 30대는 기간을 중시)
 * @param moneyOnlyShort 돈을 최대로 써도 경쟁 구단을 못 넘는다 (요구 조건이 사실상 상한)
 */
data class FaDemand(
    val salary: Double,
    val years: Int,
    val signingBonus: Double,
    val importance: Map<FaTerm, Int>,
    val moneyOnlyShort: Boolean = false,
) {
    fun asOffer(teamId: TeamId, playerId: PlayerId): ContractOffer = ContractOffer(teamId, playerId, salary, years, signingBonus)

    fun value(term: FaTerm): Double = when (term) {
        FaTerm.SALARY -> salary
        FaTerm.YEARS -> years.toDouble()
        FaTerm.BONUS -> signingBonus
    }
}

/** 제안에 대한 에이전트의 답 */
enum class FaTalkOutcome(val label: String) {
    /** 도장 — 그 자리에서 계약 */
    SIGNED("합의"),

    /** 만족하지만 다른 구단 조건이 더 좋다. 제안은 남아서 라운드 경쟁에 들어간다 */
    CONSIDERING("고민 중"),

    /** 조금 모자라다 — 무엇을 고치면 되는지 역제안 */
    COUNTERED("역제안"),

    /** 너무 모자라거나 최저 연봉 미달 */
    REJECTED("거절"),

    /** 인내심이 바닥 — 이번 라운드엔 더 얘기하지 않는다 */
    BROKEN_OFF("결렬"),
}

/** 협상 대화 한 줄. [fromAgent] 가 false 면 우리(단장)가 한 말 */
data class FaTalkLine(val round: Int, val fromAgent: Boolean, val text: String, val outcome: FaTalkOutcome? = null)

data class FaTalkResult(
    val outcome: FaTalkOutcome,
    val message: String,
    /** 에이전트가 되돌려 준 조건 (역제안·고민 중일 때) */
    val counter: ContractOffer? = null,
    val signing: FaSigning? = null,
    val patienceLeft: Int,
)

/**
 * FA 협상 테이블 (docs/11, 2026-10-04 유저 요청 "세부 조건을 조율할 수 있는 별개의 창 — FM식 협상").
 *
 * FM 처럼 **에이전트가 항목별 요구 조건을 내밀고**, 단장이 항목을 조정해 제안하면 **그 자리에서 답한다**.
 * 유저 결정 "혼합형": 선수가 만족하고(서명 기준) **지금 경쟁 구단 조건보다도 좋으면 바로 도장**,
 * 만족은 하지만 다른 구단이 더 좋으면 "고민 중" — 제안이 남아 라운드 진행 때 기존 경쟁 입찰로 정해진다.
 *
 * - **인내심**: 라운드마다 `patience` 칸. 모자란 제안마다 한 칸, 터무니없는 제안(목표의 `insultRatio` 미만)은 두 칸.
 *   바닥나면 그 라운드엔 협상이 끝난다 (낸 제안은 남는다). 수락 기준선을 한 푼씩 더듬는 꼼수를 막는다 — 비FA 다년계약·트레이드와 같은 이유
 * - **숨은 양보 폭**: 선수마다(시즌·선수 고정 난수) 서명 기준을 0~`flexRange` 만큼 낮게 본다. 요구 조건보다 조금 싸게 불러도 될 수 있다
 * - **옵션 조항**: 홈런·이닝·팀 포스트시즌 같은 기준을 달성하면 주는 돈. 선수는 지난 시즌 기록으로 본 달성 가능성만큼만 쳐 준다
 *   ([OptionRules]). 조항 합이 연봉의 `maxOptionRate` 까지, 같은 조항은 하나만
 *
 * 난수를 소비하지 않는다(양보 폭은 따로 만든 고정 난수) — 협상 테이블을 몇 번 열어도 게임 결과가 흔들리지 않는다.
 */
class FaTalks(balance: BalanceConfig, private val market: FreeAgencyMarket) {

    private val cfg = balance.section("faNegotiation")
    val maxPatience: Int = cfg.int("patience")
    private val insultRatio = cfg.double("insultRatio")
    private val flexRange = cfg.doubleRange("flexRange")
    private val bonusShare = cfg.double("demandBonusShare")
    val maxOptionRate: Double = cfg.double("maxOptionRate")

    fun patienceLeft(state: FreeAgencyState, playerId: PlayerId): Int {
        val (round, spent) = state.patienceSpent[playerId] ?: return maxPatience
        return if (round == state.round) max(0, maxPatience - spent) else maxPatience
    }

    /**
     * 에이전트의 요구 조건. 기간 = 희망 기간(나이 상한 안), 돈 = 서명 기준과 경쟁 구단 최고 점수 중 높은 쪽에 닿는 만큼을
     * 연봉과 계약금으로 나눈다(계약금 비중 `demandBonusShare`). 옵션은 요구하지 않는다 — 선수는 보장된 돈을 원한다.
     */
    /**
     * @param bonusBudget 이 구단이 계약금으로 쓸 수 있는 돈. 요구 계약금이 넘으면 넘는 만큼을 연봉으로 돌려 요구한다
     *   (에이전트도 구단 사정은 안다 — 못 낼 계약금을 요구해 협상이 막히지 않게)
     */
    fun demand(
        state: FreeAgencyState,
        playerId: PlayerId,
        teamId: TeamId,
        league: League,
        standings: Standings,
        bonusBudget: Double? = null,
    ): FaDemand? {
        val agent = state.agent(playerId) ?: return null
        val player = league.player(playerId)
        val years = min(agent.askingYears, market.maxYearsFor(player, state.season)).coerceAtLeast(1)
        val target = targetScore(state, playerId, teamId, league, standings) ?: return null
        val base = ContractOffer(teamId, playerId, agent.askingSalary, years)
        val needed = market.salaryFor(target, player, base, league, standings, agent)
        // 돈을 최대로 써도 못 닿으면 상한까지만 부른다 (돈 점수 상한 = 희망 연봉 × MONEY_CAP)
        val effective = needed ?: (agent.askingSalary * market.moneyCap)
        val wantedBonus = max(0.0, effective * bonusShare) * years
        // 0.1억 단위로 내린다 — 반올림하면 쓸 수 있는 돈을 살짝 넘을 수 있다
        val bonus = kotlin.math.floor(min(wantedBonus, max(0.0, bonusBudget ?: wantedBonus)) * 10 + 1e-9) / 10.0
        // 연 환산 돈(연봉 + 계약금/년)이 필요한 만큼. 최저 연봉도 이 합에 걸린다 (salaryFor 가 이미 맞춰 준다).
        // 계약금을 줄인 만큼은 연봉으로. 0.1억 올림이라 조금 남는 쪽으로
        val salary = kotlin.math.ceil((max(agent.salaryFloor, effective) - bonus / years) * 10 - 1e-9) / 10.0
        val weights = market.weightsFor(player, league)
        fun stars(weight: Double): Int = when {
            weight >= STRONG -> 3
            weight >= MEDIUM -> 2
            else -> 1
        }
        return FaDemand(
            salary = salary,
            years = years,
            signingBonus = bonus,
            importance = mapOf(
                FaTerm.SALARY to stars(weights.double("money")),
                FaTerm.YEARS to stars(weights.double("years")),
                FaTerm.BONUS to (stars(weights.double("money")) - 1).coerceAtLeast(1),
            ),
            moneyOnlyShort = needed == null,
        )
    }

    /**
     * 조건을 내민다 — 에이전트가 그 자리에서 답한다.
     * 낸 조건은 (합의든 아니든) 우리 제안으로 시장에 남는다. 그래서 라운드를 진행하면 지금처럼 경쟁 입찰로도 정해진다.
     */
    fun propose(state: FreeAgencyState, offer: ContractOffer, league: League, standings: Standings): FaTalkResult {
        val playerId = offer.playerId
        val agent = state.agent(playerId) ?: return FaTalkResult(FaTalkOutcome.REJECTED, "이미 다른 곳과 계약했어요.", patienceLeft = 0)
        val left = patienceLeft(state, playerId)
        if (left <= 0) {
            return FaTalkResult(FaTalkOutcome.BROKEN_OFF, "오늘은 더 얘기하지 않겠대요. 다음 라운드에 다시 오시래요.", patienceLeft = 0)
        }
        if (offer.optionPerYear > offer.salary * maxOptionRate + EPSILON) {
            return FaTalkResult(
                FaTalkOutcome.REJECTED,
                "옵션이 연봉의 ${(maxOptionRate * 100).roundToInt()}%를 넘으면 받지 않겠대요. 보장된 돈을 원해요.",
                patienceLeft = left,
            )
        }
        if (offer.options.map { it.kind }.distinct().size != offer.options.size || offer.options.any { it.amount <= 0.0 }) {
            return FaTalkResult(FaTalkOutcome.REJECTED, "같은 옵션 조항은 하나만, 금액은 0보다 커야 해요.", patienceLeft = left)
        }
        val player = league.player(playerId)
        log(state, playerId, false, describe(offer), null)
        state.putOffer(offer)

        val score = market.score(player, offer, league, standings, agent)
        val rival = bestRival(state, playerId, offer.teamId, league, standings)
        val sign = market.signThresholdValue * (1.0 - flexOf(state, playerId))
        val target = max(market.signThresholdValue, (rival ?: 0.0) + TIE_MARGIN)
        val beatsRivals = rival == null || score > rival

        val result = when {
            !market.meetsFloor(offer, agent) -> {
                spend(state, playerId, 1)
                FaTalkResult(
                    FaTalkOutcome.REJECTED,
                    "최저 연봉 ${format(agent.salaryFloor)}억도 안 된대요. 연봉에 계약금을 연으로 나눈 돈이 그 이상이어야 해요.",
                    patienceLeft = patienceLeft(state, playerId),
                )
            }
            score >= sign && beatsRivals -> {
                val signing = market.signNow(state, offer, league)
                FaTalkResult(FaTalkOutcome.SIGNED, "좋습니다, 사인하겠습니다! ${describe(offer)}에 합의했어요.", signing = signing, patienceLeft = left)
            }
            score >= sign -> {
                spend(state, playerId, 1)
                val lead = market.salaryFor(target, player, offer, league, standings, agent)
                FaTalkResult(
                    FaTalkOutcome.CONSIDERING,
                    "조건은 마음에 드는데 다른 구단 조건이 더 좋아서 고민해 보겠대요. " +
                        (lead?.let { "연봉 ${format(it)}억이면 바로 사인하겠대요." } ?: "연봉만으론 뒤집기 어렵고 기간이나 계약금이 더 필요해요."),
                    counter = lead?.let { offer.copy(salary = it) },
                    patienceLeft = patienceLeft(state, playerId),
                )
            }
            score < target * insultRatio -> {
                spend(state, playerId, 2)
                FaTalkResult(
                    FaTalkOutcome.REJECTED,
                    "이건 협상할 조건이 아니라며 언짢아해요. 요구 조건과 차이가 너무 커요.",
                    patienceLeft = patienceLeft(state, playerId),
                )
            }
            else -> {
                spend(state, playerId, 1)
                val counter = counterOf(state, offer, target, league, standings)
                FaTalkResult(FaTalkOutcome.COUNTERED, counter.second, counter = counter.first, patienceLeft = patienceLeft(state, playerId))
            }
        }
        log(state, playerId, true, result.message, result.outcome)
        return result
    }

    /** 역제안: 기간이 모자라 그것만 늘리면 되면 기간을, 아니면 연봉을 고친다. 연봉만으론 안 되면 요구 조건 전체 */
    private fun counterOf(
        state: FreeAgencyState,
        offer: ContractOffer,
        target: Double,
        league: League,
        standings: Standings,
    ): Pair<ContractOffer?, String> {
        val agent = state.agent(offer.playerId) ?: return null to ""
        val player = league.player(offer.playerId)
        val wantedYears = min(agent.askingYears, market.maxYearsFor(player, state.season))
        if (offer.years < wantedYears) {
            val longer = offer.copy(years = wantedYears)
            if (market.score(player, longer, league, standings, agent) >= target) {
                return longer to "금액은 괜찮은데 기간을 ${wantedYears}년으로 늘려 주면 사인하겠대요."
            }
        }
        market.salaryFor(target, player, offer, league, standings, agent)?.let { salary ->
            return offer.copy(salary = salary) to "연봉을 ${format(salary)}억으로 올려 주면 사인하겠대요."
        }
        val demand = demand(state, offer.playerId, offer.teamId, league, standings)
        return demand?.asOffer(offer.teamId, offer.playerId) to "연봉만으론 모자라요. 처음 요구한 조건으로 다시 봐 달래요."
    }

    /** 지금 우리가 넘어야 할 점수: 서명 기준과 경쟁 구단 최고 점수 중 높은 쪽 */
    private fun targetScore(state: FreeAgencyState, playerId: PlayerId, teamId: TeamId, league: League, standings: Standings): Double? {
        state.agent(playerId) ?: return null
        val rival = bestRival(state, playerId, teamId, league, standings)
        return max(market.signThresholdValue, (rival ?: 0.0) + TIE_MARGIN)
    }

    private fun bestRival(state: FreeAgencyState, playerId: PlayerId, teamId: TeamId, league: League, standings: Standings): Double? {
        val agent = state.agent(playerId) ?: return null
        return market.ranked(state, agent, league, standings).firstOrNull { it.first.teamId != teamId }?.second
    }

    /** 숨은 양보 폭 — 시즌·선수마다 고정 (몇 번을 물어도 같다) */
    private fun flexOf(state: FreeAgencyState, playerId: PlayerId): Double =
        Random(state.season * 7_919L + playerId.value.hashCode()).nextInRange(flexRange)

    private fun spend(state: FreeAgencyState, playerId: PlayerId, amount: Int) {
        val (round, spent) = state.patienceSpent[playerId] ?: (state.round to 0)
        state.patienceSpent[playerId] = state.round to (if (round == state.round) spent else 0) + amount
    }

    private fun log(state: FreeAgencyState, playerId: PlayerId, fromAgent: Boolean, text: String, outcome: FaTalkOutcome?) {
        state.talkLog.getOrPut(playerId) { mutableListOf() } += FaTalkLine(state.round, fromAgent, text, outcome)
    }

    fun describe(offer: ContractOffer): String = buildString {
        append("연 ${format(offer.salary)}억 × ${offer.years}년")
        if (offer.signingBonus > 0) append(", 계약금 ${format(offer.signingBonus)}억")
        if (offer.options.isNotEmpty()) append(", 옵션 ${offer.options.size}개 최대 연 ${format(offer.optionPerYear)}억")
    }

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

    private fun format(value: Double): String = round1(value).toString()

    private companion object {
        /** 경쟁 구단과 동점이면 이긴 게 아니다 — 아주 조금 넘어야 한다 */
        const val TIE_MARGIN = 0.001
        const val EPSILON = 1e-6
        /** 중요도 별(표시 기준): FA 가중치(`faMarket.weights`)가 이 이상이면 ★★★ / ★★ */
        const val STRONG = 0.4
        const val MEDIUM = 0.25
    }
}
