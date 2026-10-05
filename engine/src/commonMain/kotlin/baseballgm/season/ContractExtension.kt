package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.market.Valuation
import baseballgm.model.ContractType
import baseballgm.model.Origin
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.util.interpolateAnchors
import baseballgm.util.nextInRange
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 비FA 다년계약 조건표 (docs/11 "FA 전 우리 선수와 장기 계약").
 *
 * @param years 새 계약 연수 (이번 시즌 뒤로 몇 시즌)
 * @param demand 이 연수라면 선수가 원하는 연봉(억). 이만큼 주면 바로 도장
 * @param marketSalary 지금 FA 시장에 나가면 부를 법한 희망 연봉 (비교용)
 * @param seasonsToFa 지금 계약대로면 몇 시즌 뒤 FA 인가. 0 이면 이번 시즌 뒤
 * @param preferredYears 선수가 가장 원하는 연수. 이보다 짧으면 연봉을 더 달라고 한다
 */
data class ExtensionTerms(
    val playerId: PlayerId,
    val years: Int,
    val demand: Double,
    val marketSalary: Double,
    val currentSalary: Double,
    val seasonsToFa: Int,
    val preferredYears: Int,
    val minYears: Int,
    val maxYears: Int,
    /** 남은 협상 기회(인내심). 0 이 되면 올 시즌엔 더 얘기하지 않는다 */
    val talksLeft: Int,
    /** 항목별 중요도 1~3 (FA 협상 테이블과 같은 기준, 2026-10-05) */
    val importance: Map<baseballgm.market.FaTerm, Int> = emptyMap(),
)

/**
 * 다년계약 제안 결과 (2026-10-05 협상 테이블: 합의 / 역제안 / 거절 / 결렬).
 * @param counter 역제안 연봉 — 이 연봉이면(나머지 조건 그대로) 도장
 */
data class ExtensionResult(
    val accepted: Boolean,
    val message: String,
    val talksLeft: Int,
    val outcome: baseballgm.market.FaTalkOutcome = if (accepted) baseballgm.market.FaTalkOutcome.SIGNED else baseballgm.market.FaTalkOutcome.REJECTED,
    val counter: Double? = null,
)

/**
 * 비FA 다년계약 (docs/11, 2026-10-03).
 *
 * FA 가 되기 전에 우리 선수와 장기 계약을 맺는다. **언제나 FA 시장가보다 싸다** — 선수는 시장에서 경쟁하는
 * 위험 대신 확실한 돈을 받고, 구단은 그 값으로 할인을 받는다. FA 가 멀수록 할인이 크다 (2026-10-04 유저 피드백
 * "다년 계약이 FA 보다 비싸면 할 이유가 없다" — 예전엔 FA 직전이면 시장가 × 1.1 이었다).
 *
 * ```
 * 시장가   = 기대 WAR × 억원/WAR × FA 희망 할증 (FA 시장 첫 희망 연봉과 같은 식)
 * 할인     = min(maxDiscount, baseDiscount + discountPerSeason × FA까지 시즌)
 * 요구 연봉 = max(지금 연봉 × raiseFloor, 시장가 × (1 − 할인) × 만족도 배율) × (1 + shortYearPremium × 선호보다 짧은 햇수)
 *   만족도 배율 = MoraleService.contractMultiplier (만족도·충성심이 높으면 1 보다 작다, docs/13)
 *   단 시장가 × (1 − minDiscount) 를 넘지 않는다 — 연봉이 이미 높은 선수도, 짧게 계약해도 FA 보다 비싸지 않게
 * ```
 *
 * 선수마다 **숨은 양보 폭**이 있다 (0 ~ maxFlex, 시즌·선수별 고정 난수). 요구 연봉보다 조금 낮게 불러도
 * 양보 폭 안이면 받아들인다. 그 아래면 거절이고, 거절이 [maxTalks] 번 쌓이면 올 시즌엔 협상이 닫힌다
 * (트레이드 협상 피로도와 같은 이유 — 수락 기준선을 한 푼씩 더듬는 꼼수를 막는다).
 *
 * 연봉은 **다음 시즌부터** 바뀐다 ([baseballgm.model.Contract.nextSalary]). 계약금은 지금 운용 자금에서 나간다.
 */
class ContractExtensionService(
    balance: BalanceConfig,
    private val strength: StrengthCalculator,
) {
    private val valuation = Valuation(balance, baseballgm.development.AgingCurves(balance))
    private val morale = baseballgm.management.MoraleService(balance, strength)
    private val cfg = balance.section("contractExtension")
    private val askingPremium = balance.double("faMarket.askingPremium")
    private val maxYearsByAge = balance.section("faMarket").numericMap("maxYearsByAge")
    private val minimumSalary = balance.double("minimumSalary.value")
    private val maxContractYears = balance.int("freeAgency.maxContractYears")
    private val qualifyingHighSchool = balance.int("freeAgency.qualifyingSeasons.highSchool")
    private val qualifyingCollege = balance.int("freeAgency.qualifyingSeasons.college")

    private val minYears = cfg.int("minYears")
    private val maxAge = cfg.int("maxAge")
    private val maxRemainingYears = cfg.int("maxRemainingYears")
    private val raiseFloor = cfg.double("raiseFloor")
    private val baseDiscount = cfg.double("baseDiscount")
    private val minDiscount = cfg.double("minDiscount")
    private val discountPerSeason = cfg.double("discountPerSeason")
    private val maxDiscount = cfg.double("maxDiscount")
    private val shortYearPremium = cfg.double("shortYearPremium")
    private val flexRange = cfg.doubleRange("flexRange")
    val maxTalks: Int = cfg.int("maxTalks")
    private val insultRatio = balance.double("faNegotiation.insultRatio")
    private val maxOptionRate = balance.double("faNegotiation.maxOptionRate")
    private val optionRules = baseballgm.market.OptionRules(balance)
    private val faWeights = balance.section("faMarket.weights")
    private val oldFromAge = balance.int("faMarket.oldFromAge")
    private val seasonGames = balance.int("schedule.gamesPerTeam")

    /** 다년계약을 제안할 수 없는 이유. 제안할 수 있으면 null */
    /** @param ignoreMorale 만족도로 인한 협상 거부는 보지 않는다 (선수가 먼저 계약 얘기를 꺼내는 메시지용) */
    fun ineligibleReason(state: SeasonState, team: TeamId, player: Player, ignoreMorale: Boolean = false): String? = when {
        player.teamId != team -> "우리 선수가 아니에요"
        player.isForeign -> "외국인 선수는 1년 단위로만 계약해요"
        !player.military.isAvailable -> "군 복무 중이에요"
        player.ageIn(state.season) > maxAge -> "${maxAge}세가 넘으면 다년계약을 하지 않아요"
        player.id in state.extendedThisSeason -> "올 시즌 이미 다년계약을 맺었어요"
        player.contract.type == ContractType.FREE_AGENT && player.contract.yearsRemaining > 1 -> "FA 계약 기간이 남아 있어요"
        player.contract.yearsRemaining > maxRemainingYears -> "계약이 ${player.contract.yearsRemaining}년 남아 있어요 (${maxRemainingYears}년 이하일 때 협상)"
        talksLeft(state, player.id) <= 0 -> "협상이 결렬됐어요. 다음 시즌에 다시 얘기해요"
        // 만족도가 바닥이면 협상 테이블에 앉지 않는다 (docs/13 선수 성향과 만족도)
        !ignoreMorale && morale.refusesTalks(state, player) -> "단장님과 사이가 틀어져서 지금은 계약 얘기를 안 하겠대요 (만족도를 먼저 올려야 해요)"
        else -> null
    }

    fun talksLeft(state: SeasonState, playerId: PlayerId): Int = max(0, maxTalks - (state.extensionRejections[playerId] ?: 0))

    /** FA 까지 남은 시즌. 지금 계약과 FA 자격 연수 중 늦은 쪽 */
    fun seasonsToFa(player: Player): Int {
        val qualifying = if (player.origin == Origin.COLLEGE) qualifyingCollege else qualifyingHighSchool
        return max(player.contract.seasonsToFreeAgency, qualifying - player.contract.serviceSeasons - 1).coerceAtLeast(0)
    }

    fun preferredYears(player: Player, season: Int): Int =
        interpolateAnchors(maxYearsByAge, player.ageIn(season).toDouble()).roundToInt().coerceIn(minYears, maxContractYears)

    /** [years] 년 계약이라면 선수가 원하는 조건. 연수는 [minYears]~최대 연수로 맞춘다 */
    fun terms(state: SeasonState, player: Player, years: Int = preferredYears(player, state.season)): ExtensionTerms {
        val length = years.coerceIn(minYears, maxContractYears)
        val market = marketSalary(player)
        val toFa = seasonsToFa(player)
        val discount = min(maxDiscount, baseDiscount + discountPerSeason * toFa)
        val preferred = preferredYears(player, state.season)
        val shortBy = max(0, preferred - length)
        // 만족도·충성심이 높으면 덜 부르고, 낮으면 더 부른다 (docs/13). 지금 연봉 아래로는 안 내려가고, FA 시장가 상한은 아래에서 그대로 건다
        val asked = max(player.contract.salary * raiseFloor, market * (1.0 - discount) * morale.contractMultiplier(state, player)) *
            (1.0 + shortYearPremium * shortBy)
        val demand = min(asked, market * (1.0 - minDiscount))
        return ExtensionTerms(
            playerId = player.id,
            years = length,
            demand = ceil1(max(minimumSalary, demand)),
            marketSalary = round1(market),
            currentSalary = player.contract.salary,
            seasonsToFa = toFa,
            preferredYears = preferred,
            minYears = minYears,
            maxYears = maxContractYears,
            talksLeft = talksLeft(state, player.id),
            importance = importanceOf(player, state.season),
        )
    }

    /** 항목별 중요도 — FA 협상 테이블과 같은 FA 가중치 기준 (20대는 연봉, 30대는 기간) */
    private fun importanceOf(player: Player, season: Int): Map<baseballgm.market.FaTerm, Int> {
        val weights = faWeights.section(if (player.ageIn(season) >= oldFromAge) "old" else "young")
        fun stars(weight: Double): Int = when {
            weight >= STRONG -> 3
            weight >= MEDIUM -> 2
            else -> 1
        }
        return mapOf(
            baseballgm.market.FaTerm.SALARY to stars(weights.double("money")),
            baseballgm.market.FaTerm.YEARS to stars(weights.double("years")),
            baseballgm.market.FaTerm.BONUS to (stars(weights.double("money")) - 1).coerceAtLeast(1),
        )
    }

    /** 옵션 조항을 선수가 한 해 몇 억으로 쳐 주나 (지난 시즌 기록·우리 팀 순위로 본 가능성, FA 와 같은 규칙) */
    fun optionValue(state: SeasonState, team: TeamId, player: Player, options: List<baseballgm.market.OptionClause>): Double =
        optionRules.valueToPlayer(options, player, state.league, state.standings, team, paceOf(state, team, player))

    /** 지난 시즌 기록이 없을 때 대신 보는 올 시즌 페이스 (옵션 가능성) */
    fun paceOf(state: SeasonState, team: TeamId, player: Player): baseballgm.market.OptionStats? =
        optionRules.paceOf(
            baseballgm.market.OptionStats.of(state.stats.battingOf(player.id).total, state.stats.pitchingOf(player.id).total),
            state.standings.record(team).games,
            seasonGames,
        )

    /** 협상 대화 (선수 → 줄). 화면이 보여 준다. 세이브에는 남기지 않는다 */
    fun talksOf(state: SeasonState, playerId: PlayerId): List<baseballgm.market.FaTalkLine> = state.extensionTalks[playerId].orEmpty()

    /**
     * 조건을 내민다. 받아들이면 계약이 바로 바뀐다.
     *
     * @param random 이 선수의 양보 폭을 정하는 난수. **시즌·선수마다 같은 시드**로 만들어 넘겨야 한다
     *   (같은 선수에게 몇 번을 물어도 기준선이 같아야 세이브 스컴이 안 된다)
     */
    fun propose(
        state: SeasonState,
        team: TeamId,
        playerId: PlayerId,
        salary: Double,
        years: Int,
        signingBonus: Double,
        random: Random,
        /** 옵션 조항 (2026-10-05). 다음 시즌부터 붙는다 */
        options: List<baseballgm.market.OptionClause> = emptyList(),
    ): ExtensionResult {
        val player = state.player(playerId)
        ineligibleReason(state, team, player)?.let {
            val outcome = if (talksLeft(state, playerId) <= 0) baseballgm.market.FaTalkOutcome.BROKEN_OFF else baseballgm.market.FaTalkOutcome.REJECTED
            return ExtensionResult(false, it, talksLeft(state, playerId), outcome)
        }
        val funds = state.funds[team] ?: 0.0
        if (signingBonus > funds) return ExtensionResult(false, "운용 자금이 모자라요 (${round1(funds)}억)", talksLeft(state, playerId))
        if (options.sumOf { it.amount } > salary * maxOptionRate + EPSILON) {
            return ExtensionResult(false, "옵션이 연봉의 ${(maxOptionRate * 100).roundToInt()}%를 넘으면 받지 않겠대요. 보장된 돈을 원해요.", talksLeft(state, playerId))
        }
        if (options.map { it.kind }.distinct().size != options.size || options.any { it.amount <= 0.0 }) {
            return ExtensionResult(false, "같은 옵션 조항은 하나만, 금액은 0보다 커야 해요.", talksLeft(state, playerId))
        }

        val terms = terms(state, player, years)
        val name = player.registeredName
        log(state, playerId, false, describe(salary, terms.years, signingBonus, options), null)
        // 계약금은 연으로 나눠, 옵션은 선수가 보는 가능성만큼 연봉에 얹어서 본다
        val optionValue = optionValue(state, team, player, options)
        val effective = salary + signingBonus / terms.years + optionValue
        val reservation = terms.demand * (1.0 - random.nextInRange(flexRange))

        if (effective + EPSILON < reservation) {
            // 터무니없는 제안은 인내심을 두 칸 깎는다 (FA 협상 테이블과 같다)
            val insult = effective < terms.demand * insultRatio
            state.extensionRejections[playerId] = (state.extensionRejections[playerId] ?: 0) + if (insult) 2 else 1
            val left = talksLeft(state, playerId)
            // 역제안: 계약금·옵션은 그대로 두고 연봉을 요구액에 맞춘다
            val counter = ceil1(max(minimumSalary, terms.demand - signingBonus / terms.years - optionValue))
            val (outcome, message) = when {
                left == 0 -> baseballgm.market.FaTalkOutcome.BROKEN_OFF to "$name 측이 거절했어요. 올 시즌엔 더 얘기하지 않겠대요."
                insult -> baseballgm.market.FaTalkOutcome.REJECTED to "$name 측이 언짢아해요. 요구와 차이가 너무 크대요. (남은 인내심 ${left})"
                else -> baseballgm.market.FaTalkOutcome.COUNTERED to "연봉을 ${round1(counter)}억으로 올려 주면 사인하겠대요. (남은 인내심 ${left})"
            }
            log(state, playerId, true, message, outcome)
            return ExtensionResult(false, message, left, outcome, counter.takeIf { outcome == baseballgm.market.FaTalkOutcome.COUNTERED })
        }

        val contract = player.contract
        state.update(
            player.withContract(
                contract.copy(
                    // 이번 시즌 + 새 계약 연수. 이번 시즌 연봉은 그대로, 다음 시즌부터 새 연봉
                    yearsRemaining = terms.years + 1,
                    nextSalary = round1(salary),
                    signingBonusRemaining = 0.0,
                    seasonsToFreeAgency = max(contract.seasonsToFreeAgency, terms.years),
                    type = ContractType.MULTI_YEAR,
                    nextOptions = options,
                ),
            ),
        )
        state.funds[team] = funds - signingBonus
        state.extendedThisSeason += playerId
        state.extensionRejections.remove(playerId)
        morale.remember(state, team, playerId, baseballgm.management.MemorySlot.CONTRACT, "extended", "다년계약으로 미래를 약속받았다")
        val mood = if (effective >= terms.demand) "흔쾌히" else "고민 끝에"
        val message = "$name 선수가 $mood 도장 찍었어요. 다음 시즌부터 ${terms.years}년간 연봉 ${round1(salary)}억" +
            (if (options.isNotEmpty()) " + 옵션 ${options.size}개" else "") + "이에요."
        log(state, playerId, true, message, baseballgm.market.FaTalkOutcome.SIGNED)
        return ExtensionResult(true, message, talksLeft(state, playerId))
    }

    private fun log(state: SeasonState, playerId: PlayerId, fromAgent: Boolean, text: String, outcome: baseballgm.market.FaTalkOutcome?) {
        state.extensionTalks.getOrPut(playerId) { mutableListOf() } += baseballgm.market.FaTalkLine(state.week, fromAgent, text, outcome)
    }

    private fun describe(salary: Double, years: Int, bonus: Double, options: List<baseballgm.market.OptionClause>): String = buildString {
        append("연 ${round1(salary)}억 × ${years}년")
        if (bonus > 0) append(", 계약금 ${round1(bonus)}억")
        if (options.isNotEmpty()) append(", 옵션 ${options.size}개 최대 연 ${round1(options.sumOf { it.amount })}억")
    }

    /** FA 시장에 나가면 처음 부를 희망 연봉과 같은 식 */
    private fun marketSalary(player: Player): Double =
        max(minimumSalary, valuation.expectedWar(player, strength.overallOf(player)) * valuation.salaryPerWar * askingPremium)

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

    private fun ceil1(value: Double): Double = ceil(value * 10 - 1e-9) / 10.0

    private companion object {
        const val EPSILON = 1e-6
        /** 중요도 점(표시 기준): FA 가중치가 이 이상이면 3 / 2 (FaTalks 와 같다) */
        const val STRONG = 0.4
        const val MEDIUM = 0.25
    }
}
