package baseballgm.market

import baseballgm.development.AgingCurves
import baseballgm.development.CareerPhase
import baseballgm.io.BalanceConfig
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.util.interpolateAnchors
import kotlin.math.max
import kotlin.math.min

/** 구단이 지금 무엇을 노리는가 (docs/11). 연도 가중치를 정한다. */
enum class TeamMode(val configKey: String, val label: String) {
    /** 우승 도전: 올해 WAR 이 크고, 유망주·지명권은 싸게 본다 */
    CONTEND("contend", "우승 도전"),
    NEUTRAL("neutral", "중립"),
    /** 리빌딩: 3~5년 뒤를 보고, 베테랑을 싸게 본다 */
    REBUILD("rebuild", "리빌딩"),
}

/**
 * 평가에 쓰는 능력치 추정치.
 *
 * **진짜 값이 아니다.** 우리 팀 선수면 현재 능력치가 정확하고, 타 팀 선수면 스카우트 범위의
 * 중심값이다. 잠재력은 우리 팀이라도 흐릿하다 (docs/02).
 */
data class RatingEstimate(val overall: Double, val potential: Double)

/** 선수 가치를 뜯어본 값. */
data class PlayerValue(
    val playerId: PlayerId,
    val projectedWar: List<Double>,
    /** 연도별 잉여 가치(억원). 기대 WAR 의 시장가에서 연봉을 뺀 값 */
    val surplusByYear: List<Double>,
    /** 연도 가중치와 포지션 필요도까지 반영한 최종 가치 */
    val value: Double,
    val contractBurden: Double,
) {
    val isBadContract: Boolean get() = value < 0.0
}

/**
 * 선수 가치 평가 (docs/11).
 *
 * ```
 * 선수 가치 = Σ(향후 N년 기대 WAR × 연도 가중치 × 억원당 WAR 가격 − 그 해 연봉) × 포지션 필요도
 * ```
 *
 * 세 가지를 일부러 이렇게 했다.
 *
 * 1. **계약이 끝나는 해까지만 센다.** 1년 남은 선수는 아무리 좋아도 "렌탈"이라 가치가 낮다.
 *    반대로 싸고 긴 계약은 그 자체가 자산이 된다.
 * 2. **연봉을 뺀 잉여 가치다.** 그래서 기량보다 비싼 계약(악성 계약)은 **가치가 음수**가 되고,
 *    AI 는 그런 선수를 받으려면 웃돈을 요구한다.
 * 3. **폼은 보지 않는다** (docs/11). 최근 몇 경기 잘 친 선수를 비싸게 파는 꼼수를 막는다.
 */
class Valuation(
    balance: BalanceConfig,
    private val curves: AgingCurves,
) {
    private val section = balance.section("valuation")
    private val horizon = section.int("horizonYears")

    /** 몇 년 앞까지 보는가. 계약이 남아 있으면 그 기간까지만 본다 (떠날 선수는 값이 낮다). */
    fun projectionYears(player: Player): Int = min(horizon, max(1, player.contract.yearsRemaining))
    val salaryPerWar: Double = section.double("salaryPerWar")
    private val prospectPotentialWeight = section.double("prospectPotentialWeight")
    private val youngPlayerAge = section.int("youngPlayerAge")

    private val warAnchors: Map<String, Map<Int, Double>> = listOf("batter", "starter", "reliever")
        .associateWith { section.numericMap("warByOverall.$it") }

    private val yearWeights: Map<TeamMode, List<Double>> =
        TeamMode.entries.associateWith { section.doubleList("yearWeights.${it.configKey}") }

    /** 종합 능력치 → 올해 기대 WAR. 출장 기회까지 포함된 경험값이다 (`cli warcurve`). */
    fun expectedWar(player: Player, overall: Double): Double =
        interpolateAnchors(warAnchors.getValue(curveKeyOf(player)), overall)

    /**
     * 앞으로 몇 년간의 기대 WAR.
     *
     * 나이 곡선을 그대로 쓴다 — 성장기면 잠재력 쪽으로 다가가고, 전성기가 지났으면 매년 깎인다.
     * 어린 선수는 잠재력 추정치가 섞여서, **지금 약해도 잠재력이 큰 선수가 비싸게** 평가된다.
     */
    fun project(player: Player, estimate: RatingEstimate, season: Int, years: Int = horizon): List<Double> {
        val type = player.hidden.growthType
        var overall = estimate.overall
        val closure = curves.closureRange().let { (it.start + it.endInclusive) / 2.0 }

        return (0 until years).map { offset ->
            val age = player.ageIn(season + offset)
            when (curves.phaseOf(age, type)) {
                CareerPhase.GROWTH -> {
                    val gap = max(0.0, estimate.potential - overall)
                    overall += gap * closure * if (age <= youngPlayerAge) 1.0 else GROWTH_TAPER
                }

                CareerPhase.PEAK -> Unit
                CareerPhase.DECLINE -> overall -= averageDecline(age)
            }
            expectedWar(player, overall)
        }
    }

    /**
     * 최종 가치.
     *
     * @param need 포지션 필요도 (0.8~1.5). 생산에만 곱하고 연봉에는 곱하지 않는다 —
     *   "필요한 자리라고 해서 연봉이 싸지지는 않기" 때문이다
     */
    fun valueOf(
        player: Player,
        estimate: RatingEstimate,
        season: Int,
        mode: TeamMode,
        need: Double = 1.0,
    ): PlayerValue {
        val weights = yearWeights.getValue(mode)
        val years = min(max(1, player.contract.yearsRemaining), min(horizon, weights.size))
        val projected = project(player, estimate, season, years)
        val salary = player.contract.salary

        val surplus = projected.map { war -> war * salaryPerWar * need - salary }
        val weighted = surplus.mapIndexed { index, value -> value * weights[index] }.sum()

        return PlayerValue(
            playerId = player.id,
            projectedWar = projected,
            surplusByYear = surplus,
            value = weighted,
            contractBurden = salary * years + player.contract.signingBonusRemaining,
        )
    }

    /**
     * 아직 1군에서 뛰지 않은 유망주의 가치.
     *
     * 기대 WAR 이 0 에 가까워서 정상 계산으로는 **모든 유망주가 무가치**해진다. 그래서 잠재력
     * 기준으로 "다 자랐을 때의 기대 WAR"을 따로 계산해 할인한다. 리빌딩 팀의 연도 가중치가
     * 뒤쪽에 실려 있어서, 리빌딩 팀이 유망주를 더 비싸게 본다.
     */
    fun prospectValue(player: Player, estimate: RatingEstimate, season: Int, mode: TeamMode): PlayerValue {
        val weights = yearWeights.getValue(mode)
        val matureWar = expectedWar(player, estimate.potential) * prospectPotentialWeight
        val projected = project(player, estimate, season, weights.size)
            .mapIndexed { index, war -> max(war, matureWar * (index + 1) / weights.size) }
        val surplus = projected.map { it * salaryPerWar - player.contract.salary }
        return PlayerValue(
            playerId = player.id,
            projectedWar = projected,
            surplusByYear = surplus,
            value = surplus.mapIndexed { index, value -> value * weights[index] }.sum(),
            contractBurden = player.contract.salary * weights.size,
        )
    }

    // ---------- 트레이드 가치 (2026-10-04 개편) ----------

    private val trade = section.section("trade")
    private val starPremium = trade.double("starPremium")
    private val starWarFrom = trade.double("starWarFrom")
    private val afterControlWeight = trade.double("afterControlWeight")
    private val growthCertainty = trade.double("growthCertainty")
    /** 기본 가치(팀 상황 없음)를 셀 때의 연봉 가중치. 연도 가중치는 중립 구단 것을 쓴다 */
    val baseSalaryWeight: Double = trade.double("baseSalaryWeight")
    private val qualifyingHighSchool = balance.int("freeAgency.qualifyingSeasons.highSchool")
    private val qualifyingCollege = balance.int("freeAgency.qualifyingSeasons.college")

    /**
     * 이 선수를 몇 시즌 데리고 있을 수 있나 (이번 시즌 포함).
     *
     * 비FA 선수는 계약이 1년 단위라도 **FA 자격을 얻을 때까지 구단이 보유권을 가진다** (연봉 재계약).
     * 그래서 계약 연수가 아니라 FA까지 남은 시즌으로 센다. FA·다년계약은 계약 기간, 외국인은 1년.
     */
    fun controlYears(player: Player): Int {
        val contract = player.contract
        val remaining = max(1, contract.yearsRemaining)
        return when (contract.type) {
            baseballgm.model.ContractType.FOREIGN -> 1
            baseballgm.model.ContractType.FREE_AGENT, baseballgm.model.ContractType.MULTI_YEAR -> remaining
            else -> {
                val qualifying = if (player.origin == baseballgm.model.Origin.COLLEGE) qualifyingCollege else qualifyingHighSchool
                val toFa = max(contract.seasonsToFreeAgency, qualifying - contract.serviceSeasons - 1).coerceAtLeast(0)
                max(remaining, toFa + 1)
            }
        }
    }

    /**
     * 트레이드 가치 = **기본 가치(능력치·나이·잠재력) × 팀 상황 가중치** (docs/11).
     *
     * 기본 가치 (어느 팀이 봐도 같다):
     * - 해마다 기대 WAR 을 나이 곡선으로 굴린다. 어린 2군 유망주는 잠재력까지 자랐을 때의 WAR 을 할인해 섞는다.
     *   지금 실력을 넘는 몫(성장분)은 [growthCertainty] 만큼만 믿는다
     * - WAR 을 시장가(억원)로 바꾸고, **WAR 이 클수록 1 WAR 이 더 비싸다** (스타 프리미엄 —
     *   평범한 선수 둘로 스타 한 명을 살 수 없게)
     * - 보유 기간(FA까지) 뒤의 해는 [afterControlWeight] 만 센다 — 곧 FA 가 되는 선수는 "렌탈"이다
     *
     * 팀 상황 가중치 (보는 팀마다 다르다):
     * - 연도 가중치: 우승 도전은 올해를, 리빌딩은 3~5년 뒤를 크게 본다 (구단 모드)
     * - 포지션 필요도 0.8~1.5: 생산 가치에만 곱한다
     * - 연봉 부담 가중치: 연봉 총액이 캡에 가까운 팀일수록 연봉을 무겁게 뺀다
     *
     * 예전 식(잉여 가치 = WAR 시장가 − 연봉, 계약 기간만)은 연봉이 가치를 좌우해서
     * 비싼 주전이 리그 최저 가치가 되고, 1년 계약이 대부분인 비FA 선수가 모두 렌탈처럼 보였다.
     */
    fun tradeValue(player: Player, estimate: RatingEstimate, season: Int, situation: TeamSituation): TradeValue {
        val weights = yearWeights.getValue(situation.mode)
        val years = min(horizon, weights.size)
        val control = controlYears(player)
        var projected = project(player, estimate, season, years)
        if (player.rosterLevel == baseballgm.model.RosterLevel.FUTURES && player.ageIn(season) <= youngPlayerAge) {
            val matureWar = expectedWar(player, estimate.potential) * prospectPotentialWeight
            projected = projected.mapIndexed { index, war -> max(war, matureWar * (index + 1) / years) }
        }
        // 지금 실력보다 더 자랄 몫은 장담할 수 없어서 [growthCertainty] 만큼만 믿는다 (유망주가 스타보다 비싸지 않게)
        val now = expectedWar(player, estimate.overall)
        projected = projected.map { war -> if (war > now) now + (war - now) * growthCertainty else war }

        val neutral = yearWeights.getValue(TeamMode.NEUTRAL)
        var base = 0.0
        var total = 0.0
        projected.forEachIndexed { year, war ->
            val held = if (year < control) 1.0 else afterControlWeight
            val production = warPrice(war) * held
            // 연봉은 보유 기간 안에서만 낸다. 그 뒤는 재계약 조건이 새로 정해진다
            val salary = if (year < control) player.contract.salary else 0.0
            base += (production - salary * baseSalaryWeight) * neutral[year]
            total += (production * situation.need - salary * situation.salaryWeight) * weights[year]
        }
        return TradeValue(player.id, projected, control, base, total)
    }

    /** WAR → 억원. 기준([starWarFrom]) 을 넘는 만큼 1 WAR 값이 오른다. */
    private fun warPrice(war: Double): Double =
        war * salaryPerWar * (1.0 + starPremium * max(0.0, war - starWarFrom))

    private fun averageDecline(age: Int): Double =
        baseballgm.model.Attribute.entries
            .map { curves.annualDecline(it, age) }
            .average() * DECLINE_TO_OVERALL

    private fun curveKeyOf(player: Player): String = when (player) {
        is Batter -> "batter"
        is Pitcher -> if (player.role.isReliever) "reliever" else "starter"
    }

    private companion object {
        /** 전성기에 가까울수록 남은 성장 폭이 준다. */
        const val GROWTH_TAPER = 0.6

        /** 능력치별 하락 평균을 종합 능력치 하락으로 옮기는 계수 (가중 평균이라 조금 작다). */
        const val DECLINE_TO_OVERALL = 0.85
    }
}

/**
 * 트레이드 가치를 볼 때의 팀 상황.
 *
 * @param need 포지션 필요도 (0.8~1.5)
 * @param salaryWeight 연봉 1억을 가치에서 얼마나 빼나. 연봉 총액이 캡에 가까울수록 크다
 */
data class TeamSituation(val mode: TeamMode, val need: Double, val salaryWeight: Double)

/** 트레이드 가치를 뜯어본 값 (억원). */
data class TradeValue(
    val playerId: PlayerId,
    val projectedWar: List<Double>,
    /** FA까지 데리고 있을 수 있는 시즌 */
    val controlYears: Int,
    /** 팀 상황을 빼고 본 기본 가치 */
    val baseValue: Double,
    /** 팀 상황 가중치까지 곱한 최종 가치 */
    val value: Double,
)
