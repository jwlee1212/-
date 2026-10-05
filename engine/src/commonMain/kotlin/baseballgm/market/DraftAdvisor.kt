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

/** 지명 추천 기준. 유저가 고른다 (2026-10-03) */
@kotlinx.serialization.Serializable
enum class DraftPolicy(val label: String) {
    READY("즉시전력"),
    BALANCED("균형"),
    POTENTIAL("잠재력 중시"),
}

/** 추천 후보 한 명 */
data class AdvisedPick(
    val prospect: DraftProspect,
    /** 우리 순번까지 남아 있을 확률 (0~1). 모의 드래프트로 낸 값 */
    val availability: Double,
    /** 왜 추천하는지 (최대 2개) */
    val reasons: List<String>,
)

/** 우리 순번 하나에 대한 추천 */
data class RoundAdvice(val slot: DraftSlot, val picks: List<AdvisedPick>)

/**
 * 스카우트팀 지명 추천 (유저 요청 2026-10-03, docs/10).
 *
 * 두 가지를 따로 계산한다.
 *
 * 1. **우리 평가** — 우리 리포트의 추정치(범위 가운데)를 추천 기준([DraftPolicy])대로 섞고, 우리 선수단의
 *    포지션 필요도를 곱한다. 숨김 값을 직접 보지 않고 유저가 보는 리포트와 **같은 시선**(ScoutingView)만 쓴다.
 * 2. **남아 있을 확률** — 다른 구단이 무엇을 볼지 우리는 모른다. 그래서 "다른 구단도 대략 우리 리포트처럼 본다"고
 *    두고, 구단마다 **취향**(즉시전력·고졸·투수 선호)과 **급한 포지션**을 공개된 범위(balance.json) 안에서 무작위로
 *    정한 뒤 판단 흔들림을 얹어 모의 드래프트를 여러 번 돌린다. 다른 구단의 실제 편향·관찰 기록·선수단은 쓰지 않는다 —
 *    그걸 쓰면 확률을 통해 진짜 능력치가 새어 나간다 (불변 원칙 4). 이 가정으로 30회 실측과 비교해 흔들림 크기를 맞췄다.
 *
 * 모의 드래프트 난수는 시즌·순번·기준으로 만든 **별도 시드**를 쓴다. 시즌 난수를 건드리지 않으니 추천을 몇 번
 * 열어 봐도 실제 드래프트 결과는 바뀌지 않고, 같은 상황이면 같은 추천이 나온다 (불변 원칙 2).
 */
class DraftAdvisor(
    balance: BalanceConfig,
    private val positionNeed: PositionNeed,
    private val budget: ScoutingBudget,
) {
    private val section = balance.section("draftAdvisor")
    private val potentialScale = baseballgm.scouting.PotentialScale.from(balance)
    private val readinessByPolicy = DraftPolicy.entries.associateWith { section.double("readinessByPolicy.${it.name}") }
    // 다른 구단 취향은 "범위"만 안다 (balance.json draft.aiBias). 어느 구단이 어떤 값인지는 쓰지 않는다
    private val tasteHighSchool = balance.section("draft").doubleRange("aiBias.highSchool")
    private val tastePitcher = balance.section("draft").doubleRange("aiBias.pitcher")
    private val tasteReadiness = balance.section("draft").doubleRange("aiBias.readiness")
    /** 선발투수 가중치 — AI 구단과 같은 값 (draft.starterPremium) */
    private val starterPremium = balance.section("draft").double("starterPremium")
    // 구단마다 급한 포지션이 다르다. 어느 구단이 어디가 급한지는 모르니 필요도 배율 범위(넘침~결핍) 안에서 뽑는다
    private val needRange = balance.section("positionNeed").let { it.double("surplus")..it.doubleRange("critical").endInclusive }
    private val consensusNoise = section.double("consensusNoise")
    private val simulations = section.int("simulations")
    private val minAvailability = section.double("minAvailability")
    /** 모의 드래프트 확률 → 실측 확률 보정표 (키는 %) */
    private val calibration = section.numericMap("availabilityCalibration")
    private val candidatesPerSlot = section.int("candidatesPerSlot")
    private val reasonNeed = section.double("reasons.needAtLeast")
    private val reasonPotential = section.double("reasons.potentialAtLeast")
    private val reasonReady = section.double("reasons.currentAtLeast")
    private val reasonUncertain = section.int("reasons.uncertainHalfWidth")

    /**
     * 우리 평가. 현재 능력·잠재력 추정치를 기준대로 섞고 포지션 필요도와 겹침 감점을 곱한다.
     *
     * @param pending 이번 드래프트에서 이미 뽑았거나(우리 지명) 앞 순번에 추천한 선수. 같은 자리를 연달아 고르지 않게 한다
     */
    fun valueOf(
        prospect: DraftProspect,
        precision: ScoutingPrecision,
        policy: DraftPolicy,
        roster: List<Player>,
        pending: List<Player>,
        season: Int,
    ): Double = estimate(prospect, precision, readinessByPolicy.getValue(policy), season) *
        positionNeed.of(roster, prospect.player) * duplicatePenalty(prospect.player, pending) * premiumOf(prospect.player)

    /** 우리 평가 순 후보. 자동 집중 관찰이 빈 슬롯을 채울 때도 이 순서를 쓴다 */
    fun rank(
        prospects: List<DraftProspect>,
        policy: DraftPolicy,
        roster: List<Player>,
        pending: List<Player>,
        season: Int,
        precisionOf: (DraftProspect) -> ScoutingPrecision,
    ): List<DraftProspect> =
        prospects.sortedByDescending { valueOf(it, precisionOf(it), policy, roster, pending, season) }

    /**
     * 남은 우리 순번마다 추천 후보를 낸다.
     *
     * @param order 지금부터 남은 순번 (맨 앞이 지금 차례)
     * @param available 아직 안 뽑힌 선수
     * @param pending 이번 드래프트에서 우리가 이미 뽑은 선수
     */
    fun advise(
        order: List<DraftSlot>,
        available: List<DraftProspect>,
        userTeam: TeamId,
        roster: List<Player>,
        pending: List<Player>,
        season: Int,
        policy: DraftPolicy,
        ourPrecisionOf: (DraftProspect) -> ScoutingPrecision,
        seed: Long,
    ): List<RoundAdvice> {
        val userSlots = order.withIndex().filter { it.value.ownerTeam == userTeam }
        if (userSlots.isEmpty() || available.isEmpty()) return emptyList()

        val ourPrecision = available.associate { it.id to ourPrecisionOf(it) }
        // 겹침 감점 전 우리 평가. 모의 드래프트 안에서 리포트를 매번 다시 만들지 않으려고 한 번만 계산한다
        val readiness = readinessByPolicy.getValue(policy)
        val ourBase = available.associate {
            it.id to estimate(it, ourPrecision.getValue(it.id), readiness, season) * positionNeed.of(roster, it.player) *
                premiumOf(it.player)
        }
        // 리그 평가 = "다른 구단도 대략 우리처럼 본다". 우리 리포트에 없는 정보는 쓰지 않는다 —
        // 다른 구단의 실제 시선을 쓰면 확률을 통해 진짜 능력치가 새어 나간다 (불변 원칙 4)
        val looks = available.map { prospect ->
            val precision = ourPrecision.getValue(prospect.id)
            Look(
                current = ScoutingView.of(prospect.player, precision, season, potentialScale).overall.center,
                potential = ScoutingView.potentialRange(prospect.player, precision).center,
                highSchool = prospect.isHighSchool,
                pitcher = prospect.isPitcher,
                starter = (prospect.player as? Pitcher)?.role?.isReliever == false,
                role = roleOf(prospect.player),
            )
        }
        val availableCount = simulate(order, available, userTeam, pending, ourBase, looks, seed)

        // 한 선수는 가장 앞 순번에 한 번만 권한다. 앞 순번 1순위는 같은 자리 겹침 감점에도 센다
        val planned = mutableListOf<Player>()
        val usedIds = mutableSetOf<baseballgm.model.PlayerId>()
        return userSlots.mapIndexed { userIndex, (_, slot) ->
            val chosen = mutableListOf<Player>().apply { addAll(pending); addAll(planned) }
            val picks = available.asSequence()
                .filter { it.id !in usedIds }
                .map { it to calibrate(availableCount.getValue(it.id)[userIndex].toDouble() / simulations) }
                .filter { (_, chance) -> chance >= minAvailability }
                .sortedByDescending { (prospect, _) -> ourBase.getValue(prospect.id) * duplicatePenalty(prospect.player, chosen) }
                .take(candidatesPerSlot)
                .map { (prospect, chance) ->
                    AdvisedPick(prospect, chance, reasonsFor(prospect, ourPrecision.getValue(prospect.id), roster, season))
                }
                .toList()
            // 1순위는 "우리가 데려갈 선수"로 겹침 감점에 넣고, 이번 순번에 보여 준 후보는 뒤 순번에 다시 내지 않는다
            picks.firstOrNull()?.let { planned += it.prospect.player }
            usedIds += picks.map { it.prospect.id }
            RoundAdvice(slot, picks)
        }
    }

    /**
     * 모의 드래프트를 [simulations] 번 돌려 "우리 n 번째 순번에 이 선수가 남아 있던 횟수"를 센다.
     *
     * 한 번의 모의 드래프트 안에서 **구단마다 따로** 선수 평가에 흔들림을 얹는다. 모든 구단이 같은 흔들림을 보면
     * "한 구단만 좋아해도 일찍 뽑힌다"는 현실이 빠져서 남을 확률이 실제보다 높게 나온다 (보정 측정에서 확인).
     * 우리 순번에는 우리 평가 1위를 가져간다고 가정한다 — 우리가 누구를 뽑느냐에 따라 뒤 순번 상황이 바뀌기 때문이다.
     */
    private fun simulate(
        order: List<DraftSlot>,
        available: List<DraftProspect>,
        userTeam: TeamId,
        pending: List<Player>,
        ourBase: Map<baseballgm.model.PlayerId, Double>,
        looks: List<Look>,
        seed: Long,
    ): Map<baseballgm.model.PlayerId, IntArray> {
        val size = available.size
        val userSlotCount = order.count { it.ownerTeam == userTeam }
        val counts = Array(size) { IntArray(userSlotCount) }
        val ours = DoubleArray(size) { ourBase.getValue(available[it].id) }
        val teams = order.map { it.ownerTeam }.filter { it != userTeam }.distinct()
        val teamIndex = teams.withIndex().associate { it.value to it.index }
        val random = Random(seed)
        val view = Array(teams.size) { DoubleArray(size) }
        val need = DoubleArray(ROLES)
        val taken = BooleanArray(size)

        repeat(simulations) {
            // 구단마다 취향(즉시전력·고졸·투수 선호)을 공개 범위 안에서 뽑고, 선수마다 판단 흔들림을 얹는다.
            // 값은 먼저 한 번씩 다 뽑아 둔다 (비교 중에 뽑으면 최댓값이 흔들린다)
            for (t in teams.indices) {
                val readiness = random.nextIn(tasteReadiness)
                val highSchool = random.nextIn(tasteHighSchool)
                val pitcher = random.nextIn(tastePitcher)
                for (r in need.indices) need[r] = random.nextIn(needRange)
                for (i in 0 until size) {
                    val look = looks[i]
                    var value = (look.current * readiness + look.potential * (1.0 - readiness)) * need[look.role]
                    if (look.highSchool) value *= highSchool
                    if (look.pitcher) value *= pitcher
                    if (look.starter) value *= starterPremium
                    view[t][i] = value + random.nextGaussian(0.0, consensusNoise)
                }
            }
            taken.fill(false)
            val chosen = pending.toMutableList()
            var remaining = size
            var userIndex = 0
            for (slot in order) {
                if (remaining == 0) break
                val pick = if (slot.ownerTeam == userTeam) {
                    for (i in 0 until size) if (!taken[i]) counts[i][userIndex]++
                    userIndex++
                    bestIndex(taken) { i -> ours[i] * duplicatePenalty(available[i].player, chosen) }
                        .also { chosen += available[it].player }
                } else {
                    val row = view[teamIndex.getValue(slot.ownerTeam)]
                    bestIndex(taken) { i -> row[i] }
                }
                taken[pick] = true
                remaining--
            }
        }
        return available.withIndex().associate { (i, prospect) -> prospect.id to counts[i] }
    }

    /**
     * 모의 드래프트 확률을 실측에 맞춘다. 모의 드래프트는 다른 구단의 실제 시선을 모르니(일부러 안 쓴다)
     * 중상위 구간을 과신한다 — 실제 드래프트 30회로 잰 보정표로 한 번 더 고친다 (일기예보 확률 보정과 같은 방식).
     */
    private fun calibrate(raw: Double): Double =
        baseballgm.util.interpolateAnchors(calibration, raw * PERCENT_SCALE).coerceIn(0.0, 1.0)

    /** 모의 드래프트용으로 미리 꺼내 둔 우리 리포트 값. [role] 은 포지션 칸 번호 ([roleOf]) */
    private class Look(
        val current: Double,
        val potential: Double,
        val highSchool: Boolean,
        val pitcher: Boolean,
        val starter: Boolean,
        val role: Int,
    )

    /** 선발 후보면 선발 가중치 */
    private fun premiumOf(player: Player): Double =
        if ((player as? Pitcher)?.role?.isReliever == false) starterPremium else 1.0

    /** 포지션 칸: 야수는 포지션별, 투수는 선발·불펜 */
    private fun roleOf(player: Player): Int = when (player) {
        is Batter -> player.primaryPosition.ordinal
        is Pitcher -> baseballgm.model.Position.entries.size + if (player.role.isReliever) 1 else 0
    }

    private fun Random.nextIn(range: ClosedFloatingPointRange<Double>): Double =
        range.start + nextDouble() * (range.endInclusive - range.start)

    private inline fun bestIndex(taken: BooleanArray, score: (Int) -> Double): Int {
        var best = -1
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in taken.indices) {
            if (taken[i]) continue
            val value = score(i)
            if (best < 0 || value > bestScore) {
                best = i
                bestScore = value
            }
        }
        return best
    }

    /** 추정 가치 = 현재 능력 추정 × readiness + 잠재력 추정 × (1 − readiness) */
    private fun estimate(prospect: DraftProspect, precision: ScoutingPrecision, readiness: Double, season: Int): Double {
        val current = ScoutingView.of(prospect.player, precision, season, potentialScale).overall.center
        val potential = ScoutingView.potentialRange(prospect.player, precision).center
        return current * readiness + potential * (1.0 - readiness)
    }

    private fun duplicatePenalty(player: Player, pending: List<Player>): Double {
        val taken = when (player) {
            is Batter -> pending.filterIsInstance<Batter>().count { it.primaryPosition == player.primaryPosition }
            is Pitcher -> pending.filterIsInstance<Pitcher>().count { it.role.isReliever == player.role.isReliever }
        }
        var penalty = 1.0
        repeat(taken) { penalty *= positionNeed.surplus }
        return penalty
    }

    /** 추천 이유. 리포트에 보이는 값으로만 판단한다 */
    private fun reasonsFor(prospect: DraftProspect, precision: ScoutingPrecision, roster: List<Player>, season: Int): List<String> {
        val scouted = ScoutingView.of(prospect.player, precision, season, potentialScale)
        val potential = ScoutingView.potentialRange(prospect.player, precision).center
        return buildList {
            if (positionNeed.of(roster, prospect.player) >= reasonNeed) add("${scouted.positionLabel} 자리가 얇아요")
            if (budget.isKnownProspect(prospect.player)) add("주목 유망주")
            if (premiumOf(prospect.player) > 1.0) add("귀한 선발 자원")
            if (potential >= reasonPotential) add("잠재력이 높아 보여요")
            if (scouted.overall.center >= reasonReady) add("바로 쓸 만해요")
            if (precision.halfWidth >= reasonUncertain) add("정보가 부족해요 — 관찰 권장")
        }.take(MAX_REASONS)
    }

    private companion object {
        const val MAX_REASONS = 2
        const val PERCENT_SCALE = 100.0
        /** 포지션 칸 수: 야수 포지션 + 선발 + 불펜 */
        val ROLES = baseballgm.model.Position.entries.size + 2
    }
}
