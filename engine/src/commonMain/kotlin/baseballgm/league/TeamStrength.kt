package baseballgm.league

import baseballgm.io.BalanceConfig
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRole
import baseballgm.model.Player
import baseballgm.model.RosterLevel
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 팀 전력 세 갈래와 종합 (docs/01). 전부 1~100 스케일. */
data class TeamStrength(
    val lineup: Double,
    val rotation: Double,
    val bullpen: Double,
    val overall: Double,
) {
    fun rounded(): TeamStrength = TeamStrength(
        lineup = lineup.roundTo1(),
        rotation = rotation.roundTo1(),
        bullpen = bullpen.roundTo1(),
        overall = overall.roundTo1(),
    )

    private fun Double.roundTo1(): Double = (this * 10).roundToInt() / 10.0
}

/** 전력 값 하나의 범위. [low] == [high] 면 정확히 아는 값이다. */
data class StrengthRange(val low: Double, val high: Double) {
    val isExact: Boolean get() = low == high
    val center: Double get() = (low + high) / 2.0

    companion object {
        fun around(center: Double, halfWidth: Double) = StrengthRange(center - halfWidth, center + halfWidth)
    }
}

/** 팀 전력을 범위로 본 것 (타 팀은 스카우트 관측값이라 범위, 우리 팀은 정확한 값) */
data class TeamStrengthRange(
    val lineup: StrengthRange,
    val rotation: StrengthRange,
    val bullpen: StrengthRange,
    val overall: StrengthRange,
)

/** 선수 종합의 추정값. 중심 ± 반폭 (능력치 스케일) */
data class OverallEstimate(val center: Double, val halfWidth: Double)

/**
 * 능력치 → 선수 종합 → 팀 전력 계산기.
 *
 * 모든 가중치는 `balance.json` 에서 읽는다 (불변 원칙 3). 실제 승률과 가장 잘 맞는 비율은
 * M2 이후 경기 시뮬레이션 결과로 역산해 다시 맞춘다 (docs/01).
 */
class StrengthCalculator(balance: BalanceConfig) {

    private val batterWeights: Map<Attribute, Double> =
        Attribute.batterAttributes.associateWith { balance.double("playerOverallWeights.batter.${it.configKey}") }
            .normalized()

    private val pitcherWeights: Map<Attribute, Double> =
        Attribute.pitcherAttributes.associateWith { balance.double("playerOverallWeights.pitcher.${it.configKey}") }
            .normalized()

    private val lineupWeights: List<Double> = balance.doubleList("teamStrength.lineupWeights").normalized()
    private val rotationWeights: List<Double> = balance.doubleList("teamStrength.rotationWeights").normalized()
    private val bullpenWeights: List<Double> = balance.doubleList("teamStrength.bullpenWeights").normalized()

    private val compositeLineup = balance.double("teamStrength.compositeWeights.lineup")
    private val compositeRotation = balance.double("teamStrength.compositeWeights.rotation")
    private val compositeBullpen = balance.double("teamStrength.compositeWeights.bullpen")

    private val strengthPivot = balance.double("teamStrength.ratingToStrength.pivot")
    private val strengthSlope = balance.double("teamStrength.ratingToStrength.slope")

    /** 허용 오차. 생성한 팀 전력이 목표와 이 값 이상 차이 나면 다시 만든다 (docs/03). */
    val targetTolerance: Double = balance.double("teamStrength.targetTolerance")

    /**
     * 선수 능력치 평균 → 팀 전력 표시값.
     *
     * 둘은 같은 1~100 이지만 **스케일이 다르다.** 선수 능력치는 "주전 평균 60"(docs/02)이고,
     * 팀 전력은 "실제 팀 값이 40~90에 분포"(docs/01)라서 그대로 쓰면 전력 82인 팀을 만들려고
     * 주전 능력치를 82까지 끌어올리게 되고, 90+ 가 흔해져 능력치 스케일이 무너진다.
     * 그래서 평균에서 벗어난 만큼을 기울기만큼 늘려 표시 전력으로 바꾼다.
     */
    fun toStrengthScale(averageOverall: Double): Double =
        strengthPivot + (averageOverall - strengthPivot) * strengthSlope

    /** 표시 전력 → 선수 능력치 평균 (생성기가 목표를 되돌릴 때 쓴다). */
    fun toRatingScale(strength: Double): Double =
        strengthPivot + (strength - strengthPivot) / strengthSlope

    /** 전력 표시값 차이를 능력치 차이로 바꾼다. 생성기 되먹임이 과잉 교정되지 않게 한다. */
    fun ratingDeltaOf(strengthDelta: Double): Double = strengthDelta / strengthSlope

    /** 선수 종합 능력치 1~100. */
    fun overallOf(player: Player): Double {
        val weights = if (player is Batter) batterWeights else pitcherWeights
        return weights.entries.sumOf { (attribute, weight) -> player.rating(attribute) * weight }
    }

    /**
     * 능력치 묶음만으로 종합을 계산한다. 아직 [Player] 를 만들기 전인 생성기(M1)가 쓴다.
     */
    fun overallOf(ratings: Map<Attribute, Double>, isBatter: Boolean): Double {
        val weights = if (isBatter) batterWeights else pitcherWeights
        return weights.entries.sumOf { (attribute, weight) -> (ratings[attribute] ?: 0.0) * weight }
    }

    /**
     * 팀 전력. 1군 등록 선수 중 지금 뛸 수 있는 선수만 본다.
     * 타선은 종합 상위 9명, 선발은 선발 투수 상위 5명, 불펜은 구원 투수 상위 7명을
     * 순번 가중치로 평균낸다. 인원이 모자라면 남은 자리는 최하위 선수 값으로 채운다.
     */
    fun of(players: List<Player>): TeamStrength {
        val available = players.filter { it.rosterLevel == RosterLevel.FIRST_TEAM && it.military.isAvailable }

        val batters = available.filterIsInstance<Batter>().sortedByDescending { overallOf(it) }
        val pitchers = available.filterIsInstance<Pitcher>()
        val starters = pitchers.filter { it.role == PitcherRole.STARTER }.sortedByDescending { overallOf(it) }
        val relievers = pitchers.filter { it.role.isReliever }.sortedByDescending { overallOf(it) }

        val lineup = toStrengthScale(weightedAverage(batters, lineupWeights))
        val rotation = toStrengthScale(weightedAverage(starters, rotationWeights))
        val bullpen = toStrengthScale(weightedAverage(relievers, bullpenWeights))
        val overall = lineup * compositeLineup + rotation * compositeRotation + bullpen * compositeBullpen
        return TeamStrength(lineup, rotation, bullpen, overall)
    }

    /**
     * 선수 종합 **추정값**으로 팀 전력 범위를 낸다 (로스터 화면의 리그 전력 비교).
     *
     * 중심은 [of] 와 같은 방식(추정 중심 상위 N명 순번 가중 평균)으로 구한다.
     * 반폭은 선수 반폭을 그대로 더하지 않고 **제곱합의 제곱근**으로 묶는다 — 스카우트 오차는 선수마다
     * 따로 생겨서(고정 씨앗이 선수마다 다르다) 여러 명을 합치면 일부가 서로 상쇄되기 때문이다.
     * 그대로 더하면 반폭이 선수 하나와 같아져(능력치 ±5 → 전력 ±10) 리그 순위가 "2~8위"처럼 쓸모없어진다.
     * [spreadFactor] 는 그 상쇄를 얼마나 믿을지다 (1 = 표준편차 하나, 클수록 보수적).
     */
    fun estimate(
        batters: List<OverallEstimate>,
        starters: List<OverallEstimate>,
        relievers: List<OverallEstimate>,
        spreadFactor: Double,
    ): TeamStrengthRange {
        val lineup = groupEstimate(batters, lineupWeights, spreadFactor)
        val rotation = groupEstimate(starters, rotationWeights, spreadFactor)
        val bullpen = groupEstimate(relievers, bullpenWeights, spreadFactor)
        val center = lineup.center * compositeLineup + rotation.center * compositeRotation + bullpen.center * compositeBullpen
        // 세 갈래 오차도 서로 다른 선수에게서 오므로 같은 방식으로 묶는다
        val halfWidth = sqrt(
            square((lineup.high - lineup.center) * compositeLineup) +
                square((rotation.high - rotation.center) * compositeRotation) +
                square((bullpen.high - bullpen.center) * compositeBullpen),
        )
        return TeamStrengthRange(lineup, rotation, bullpen, StrengthRange.around(center, halfWidth))
    }

    private fun groupEstimate(estimates: List<OverallEstimate>, weights: List<Double>, spreadFactor: Double): StrengthRange {
        if (estimates.isEmpty()) return StrengthRange.around(toStrengthScale(0.0), 0.0)
        val sorted = estimates.sortedByDescending { it.center }
        var center = 0.0
        var variance = 0.0
        for (index in weights.indices) {
            // 인원이 모자라면 [weightedAverage] 처럼 최하위 선수로 채운다
            val estimate = sorted.getOrNull(index) ?: sorted.last()
            center += estimate.center * weights[index]
            variance += square(estimate.halfWidth * weights[index])
        }
        val halfWidth = sqrt(variance) * spreadFactor * strengthSlope
        return StrengthRange.around(toStrengthScale(center), halfWidth)
    }

    private fun square(value: Double): Double = value * value

    private fun weightedAverage(sorted: List<Player>, weights: List<Double>): Double {
        if (sorted.isEmpty()) return 0.0
        var sum = 0.0
        for (index in weights.indices) {
            val player = sorted.getOrNull(index) ?: sorted.last()
            sum += overallOf(player) * weights[index]
        }
        return sum
    }

    private fun Map<Attribute, Double>.normalized(): Map<Attribute, Double> {
        val total = values.sum()
        return mapValues { (_, weight) -> weight / total }
    }

    private fun List<Double>.normalized(): List<Double> {
        val total = sum()
        return map { it / total }
    }
}
