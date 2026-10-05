package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.PlayerId
import baseballgm.season.Offseason
import kotlin.math.roundToInt
import kotlin.random.Random

/** 순번 한 자리의 결과. */
data class PickOutcome(val overallPick: Int, val samples: Int, val averageValue: Double)

/** 지명권 가치표 한 줄. */
data class PickValueAnchor(val overallPick: Int, val samples: Int, val value: Double, val normalized: Double)

/**
 * 지명권 가치표 생성기 (docs/10 "M6 도구").
 *
 * 드래프트를 여러 번 시뮬레이션하고, 지명된 선수를 [horizon] 시즌 뒤에 다시 들여다봐서
 * "그 순번이 평균적으로 어떤 선수를 데려다 주는가"를 숫자로 만든다.
 *
 * WAR 은 아직 없으므로(M7) **대체 수준을 넘는 종합 능력치**를 가치로 쓴다. 은퇴·방출로 사라진
 * 선수는 0점이다 — 뽑아 놓고 못 키운 것도 그 순번의 결과이기 때문이다.
 */
class DraftPickValueTool(
    private val balance: BalanceConfig,
    private val startingLeague: League,
) {
    private val strength = StrengthCalculator(balance)
    private val replacementLevel = balance.double("ratingScale.leagueAverage")

    private data class Tracked(val pick: Int, val playerId: PlayerId, val dueSeason: Int)

    fun run(seasons: Int, seed: Long, horizon: Int = DEFAULT_HORIZON): List<PickOutcome> {
        val values = mutableMapOf<Int, MutableList<Double>>()
        val tracked = mutableListOf<Tracked>()
        var league = startingLeague
        val offseason = Offseason(balance, strength)

        repeat(seasons) { index ->
            val runner = SeasonRunner(balance, league)
            val result = runner.playSeason(seed + index, validate = false)
            result.state.draftResult?.selections?.forEach { selection ->
                tracked += Tracked(selection.overallPick, selection.playerId, league.season + horizon)
            }
            val (next, _) = offseason.run(
                state = result.state,
                random = Random(seed + index * OFFSEASON_STRIDE),
                rookieSupplier = RookieFactory(balance, strength, league),
                prospectSupplier = ProspectFactory(
                    balance = balance,
                    strength = strength,
                    seed = seed + index * PROSPECT_STRIDE,
                    startingIdNumber = nextPlayerIdNumber(league.players, league.draftPool.prospects),
                ),
            )
            league = next

            val due = tracked.filter { it.dueSeason == league.season }
            tracked -= due.toSet()
            val byId = league.players.associateBy { it.id }
            due.forEach { entry ->
                val player = byId[entry.playerId]
                val value = if (player == null) 0.0 else (strength.overallOf(player) - replacementLevel).coerceAtLeast(0.0)
                values.getOrPut(entry.pick) { mutableListOf() } += value
            }
        }
        return values.entries.sortedBy { it.key }
            .map { (pick, samples) -> PickOutcome(pick, samples.size, samples.average()) }
    }

    /**
     * 순번별 표본은 흔들리므로 구간으로 묶어 평균낸 뒤 1순위를 1.0 으로 정규화한다.
     * `config/balance.json` 의 `draftPickValue.byPick` 에 넣을 앵커가 된다.
     */
    fun anchors(outcomes: List<PickOutcome>, buckets: List<Int> = DEFAULT_BUCKETS): List<PickValueAnchor> {
        val byPick = outcomes.associateBy { it.overallPick }
        val raw = buckets.mapIndexed { index, start ->
            val end = buckets.getOrNull(index + 1)?.minus(1) ?: Int.MAX_VALUE
            val inBucket = (start..minOf(end, byPick.keys.maxOrNull() ?: start)).mapNotNull { byPick[it] }
            val samples = inBucket.sumOf { it.samples }
            val value = if (samples == 0) {
                0.0
            } else {
                inBucket.sumOf { it.averageValue * it.samples } / samples
            }
            Triple(start, samples, value)
        }
        val top = raw.firstOrNull()?.third?.takeIf { it > 0.0 } ?: 1.0
        return raw.map { (pick, samples, value) ->
            PickValueAnchor(pick, samples, value.round2(), (value / top).round2())
        }
    }

    /** `balance.json` 에 그대로 붙여 넣을 수 있는 한 줄. */
    fun asConfigLine(anchors: List<PickValueAnchor>): String =
        anchors.joinToString(", ", prefix = "\"byPick\": { ", postfix = " }") {
            "\"${it.overallPick}\": ${it.normalized}"
        }

    private fun Double.round2(): Double = (this * 100).roundToInt() / 100.0

    companion object {
        const val DEFAULT_HORIZON: Int = 5
        val DEFAULT_BUCKETS: List<Int> = listOf(1, 3, 6, 11, 21, 31, 41, 61, 81, 101)
        private const val OFFSEASON_STRIDE = 7919L
        private const val PROSPECT_STRIDE = 104729L
    }
}
