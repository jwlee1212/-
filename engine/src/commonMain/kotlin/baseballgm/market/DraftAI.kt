package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.TeamId
import baseballgm.scouting.ScoutingBudget
import baseballgm.scouting.ScoutingPrecision
import baseballgm.scouting.ScoutingView
import baseballgm.util.nextGaussian
import kotlin.random.Random

/**
 * 구단마다 고정된 지명 편향 (docs/10).
 *
 * 실제 구단이 "우리는 고졸 유망주를 키운다", "투수부터 뽑는다" 같은 색깔을 가지는 것을 흉내 낸다.
 * **AI가 편향을 가져야 저평가된 선수가 뒤 순번까지 남는다** — 모두가 같은 잣대로 뽑으면
 * 드래프트가 그냥 능력치 순 정렬이 되어 버린다.
 *
 * @param highSchool 고졸 선호 배율 (1.0 이 중립)
 * @param pitcher 투수 선호 배율
 * @param readiness 즉시전력 선호. 평가에서 **현재 능력치에 주는 가중치**다. 낮을수록 잠재력을 본다
 */
data class DraftBias(val highSchool: Double, val pitcher: Double, val readiness: Double)

/**
 * AI 구단의 지명 로직 (docs/10).
 *
 * `스카우트 추정 가치 × 포지션 필요도 × 구단 편향` 으로 줄을 세우고 맨 위를 뽑는다.
 * 추정에는 **구단별 오차**가 섞인다 (docs/02 구현 규칙 3) — AI도 완벽한 정보를 쓰지 않는다.
 */
class DraftAI(
    balance: BalanceConfig,
    private val positionNeed: PositionNeed,
    private val budget: ScoutingBudget,
) {
    private val section = balance.section("draft")
    private val potentialScale = baseballgm.scouting.PotentialScale.from(balance)
    private val highSchoolRange = section.doubleRange("aiBias.highSchool")
    private val pitcherRange = section.doubleRange("aiBias.pitcher")
    private val readinessRange = section.doubleRange("aiBias.readiness")
    /** 선발투수 가중치. KBO 는 선발 자원이 귀해서 모든 구단이 선발 후보를 더 쳐준다 */
    private val starterPremium = section.double("starterPremium")

    /** 구단 성향은 시즌마다 흔들리지 않는다. 구단 id 로 고정한다. */
    fun biasOf(teamId: TeamId): DraftBias {
        val seed = teamId.value.hashCode()
        return DraftBias(
            highSchool = spread(seed, salt = 3, range = highSchoolRange),
            pitcher = spread(seed, salt = 5, range = pitcherRange),
            readiness = spread(seed, salt = 7, range = readinessRange),
        )
    }

    /**
     * 지명 가치.
     *
     * 현재 능력치와 잠재력을 구단 성향(`readiness`)대로 섞고, 구단 고유 오차를 얹은 뒤
     * 포지션 필요도와 선호 배율을 곱한다. 값은 "능력치 스케일 × 배율"이라 순서를 정하는 데만 쓴다.
     */
    fun evaluate(
        prospect: DraftProspect,
        teamId: TeamId,
        roster: List<Player>,
        precision: ScoutingPrecision,
        season: Int,
        /** 이번 드래프트에서 이미 뽑은 선수들. 같은 자리를 연달아 뽑지 않게 한다 (docs/11) */
        pending: List<Player> = emptyList(),
    ): Double {
        val bias = biasOf(teamId)
        val scouted = ScoutingView.of(prospect.player, precision, season, potentialScale)
        val current = scouted.overall.center
        val potential = ScoutingView.potentialRange(prospect.player, precision).center
        val estimate = current * bias.readiness + potential * (1.0 - bias.readiness) + teamNoise(teamId, prospect, precision)

        var value = estimate * positionNeed.of(roster, prospect.player) * duplicatePenalty(prospect, pending)
        if (prospect.isHighSchool) value *= bias.highSchool
        if (prospect.isPitcher) value *= bias.pitcher
        if ((prospect.player as? Pitcher)?.role?.isReliever == false) value *= starterPremium
        return value
    }

    /**
     * 같은 자리를 이미 뽑았으면 가치를 깎는다.
     *
     * 아마추어는 현재 능력치가 낮아서 [PositionNeed] 로는 "주전이 생겼다"로 세어지지 않는다.
     * 그래서 이번 드래프트에서 뽑은 인원만 따로 세어 한 명당 넘침 배율(0.8)을 곱한다 —
     * 이게 없으면 AI 가 11라운드 내내 3루수만 뽑는다.
     */
    private fun duplicatePenalty(prospect: DraftProspect, pending: List<Player>): Double {
        if (pending.isEmpty()) return 1.0
        val taken = when (val player = prospect.player) {
            is Batter -> pending.filterIsInstance<Batter>().count { it.primaryPosition == player.primaryPosition }
            is Pitcher -> pending.filterIsInstance<Pitcher>().count { it.role.isReliever == player.role.isReliever }
        }
        var penalty = 1.0
        repeat(taken) { penalty *= positionNeed.surplus }
        return penalty
    }

    /** 가치 순 후보 목록. 화면에서 "우리 팀 지명 후보"를 보여줄 때도 쓴다. */
    fun rank(
        prospects: List<DraftProspect>,
        teamId: TeamId,
        roster: List<Player>,
        season: Int,
        pending: List<Player> = emptyList(),
        precisionOf: (DraftProspect) -> ScoutingPrecision,
    ): List<Pair<DraftProspect, Double>> =
        prospects.map { it to evaluate(it, teamId, roster, precisionOf(it), season, pending) }
            .sortedByDescending { it.second }

    /**
     * AI 차례의 지명.
     *
     * 1순위를 그대로 뽑지 않고 상위 후보 중에서 아주 작은 흔들림을 준다. 같은 시드면 같은 결과지만,
     * 순위가 거의 같은 두 선수 사이에서는 해마다 다른 선택이 나온다.
     */
    fun choose(
        prospects: List<DraftProspect>,
        teamId: TeamId,
        roster: List<Player>,
        season: Int,
        random: Random,
        pending: List<Player> = emptyList(),
        precisionOf: (DraftProspect) -> ScoutingPrecision,
    ): DraftProspect {
        require(prospects.isNotEmpty()) { "드래프트 풀이 비었다" }
        return prospects
            .map { it to evaluate(it, teamId, roster, precisionOf(it), season, pending) + random.nextGaussian(0.0, JITTER) }
            .maxBy { it.second }
            .first
    }

    /**
     * 구단 고유 오차. 같은 선수라도 구단마다 다르게 본다.
     * 정확도가 나쁠수록(반폭이 클수록) 구단 간 편차도 커진다.
     */
    private fun teamNoise(teamId: TeamId, prospect: DraftProspect, precision: ScoutingPrecision): Double {
        val salt = teamId.value.hashCode() and SALT_MASK
        return ScoutingView.noise(prospect.player.hidden.scoutingNoiseSeed, salt) *
            precision.halfWidth * budget.aiEvaluationNoise
    }

    private fun spread(seed: Int, salt: Int, range: ClosedFloatingPointRange<Double>): Double {
        val unit = (ScoutingView.noise(seed, salt) + 1.0) / 2.0
        return range.start + (range.endInclusive - range.start) * unit
    }

    private companion object {
        const val JITTER = 0.6
        const val SALT_MASK = 0xFFFF
    }
}
