package baseballgm.sim

import baseballgm.io.BalanceConfig
import baseballgm.model.Batter
import baseballgm.util.chance
import kotlin.random.Random

/**
 * 베이스 상태 변화 결과.
 *
 * **모든 주자는 득점했거나, 아웃됐거나, 베이스에 남아 있어야 한다.** 이 셋의 합이 맞지 않으면
 * 박스스코어 주자 보존 등식이 깨진다 (docs/05).
 */
data class AdvanceResult(
    val scored: List<Runner> = emptyList(),
    val runnersOut: List<Runner> = emptyList(),
    val rbi: Int = 0,
    /** 타자가 만든 아웃 수 (병살은 2) */
    val batterOuts: Int = 0,
)

/**
 * 주루 처리 (docs/05).
 *
 * 안타 때 추가 진루는 주자의 주루 능력치로 확률 판정하고, 무리한 진루에는 **주루사 확률**이 따른다.
 * 확률은 전부 `balance.json` 의 `baseRunning` / `baseRunningExtra` 에서 읽는다.
 */
class BaseRunning(
    balance: BalanceConfig,
    private val tables: RatingTables,
) {
    private val singleScoresFromSecond = balance.double("baseRunning.singleRunnerOnSecondScores")
    private val singleFirstToThird = balance.double("baseRunning.singleRunnerOnFirstToThird")
    private val doubleScoresFromFirst = balance.double("baseRunning.doubleRunnerOnFirstScores")
    private val outAttemptingExtraBase = balance.double("baseRunningExtra.outAttemptingExtraBase")
    private val groundOutScoresFromThird = balance.double("baseRunningExtra.groundOutScoresFromThird")
    private val groundOutAdvancesFromSecond = balance.double("baseRunningExtra.groundOutAdvancesFromSecond")

    /**
     * 타석 결과에 따라 주자를 움직인다. [bases] 를 직접 고친다.
     *
     * @param batterRunner 타자가 살아 나갈 때 베이스에 놓을 주자 정보
     * @param outsBefore 이 타석 전 아웃 수
     */
    fun resolve(
        outcome: PaOutcome,
        bases: BaseState,
        batter: Batter,
        batterRunner: Runner,
        outsBefore: Int,
        random: Random,
    ): AdvanceResult = when (outcome) {
        PaOutcome.HOME_RUN -> homeRun(bases, batterRunner)
        PaOutcome.TRIPLE -> extraBaseHit(bases, batterRunner, landingBase = 3)
        PaOutcome.DOUBLE -> doubleHit(bases, batter, batterRunner, outsBefore, random)
        PaOutcome.SINGLE -> single(bases, batter, batterRunner, outsBefore, random)
        PaOutcome.WALK, PaOutcome.INTENTIONAL_WALK, PaOutcome.HIT_BY_PITCH,
        PaOutcome.CATCHER_INTERFERENCE, PaOutcome.STRIKEOUT_REACHED,
        -> forcedWalk(bases, batterRunner, rbiCredited = outcome != PaOutcome.CATCHER_INTERFERENCE)
        PaOutcome.REACHED_ON_ERROR -> reachedOnError(bases, batterRunner)
        PaOutcome.FIELDERS_CHOICE -> fieldersChoice(bases, batterRunner, outsBefore)
        PaOutcome.DOUBLE_PLAY -> doublePlay(bases, outsBefore)
        PaOutcome.SAC_FLY -> sacFly(bases)
        PaOutcome.SAC_BUNT -> sacBunt(bases, outsBefore)
        PaOutcome.GROUND_OUT -> groundOut(bases, outsBefore, random)
        PaOutcome.FLY_OUT, PaOutcome.LINE_OUT, PaOutcome.STRIKEOUT ->
            AdvanceResult(batterOuts = 1)
    }

    // ---------- 안타 ----------

    private fun homeRun(bases: BaseState, batterRunner: Runner): AdvanceResult {
        val scored = bases.occupiedBases().mapNotNull { bases.remove(it) } + batterRunner
        return AdvanceResult(scored = scored, rbi = scored.size)
    }

    private fun extraBaseHit(bases: BaseState, batterRunner: Runner, landingBase: Int): AdvanceResult {
        val scored = bases.occupiedBases().mapNotNull { bases.remove(it) }
        bases[landingBase] = batterRunner
        return AdvanceResult(scored = scored, rbi = scored.size)
    }

    private fun doubleHit(
        bases: BaseState,
        batter: Batter,
        batterRunner: Runner,
        outsBefore: Int,
        random: Random,
    ): AdvanceResult {
        val scored = mutableListOf<Runner>()
        val out = mutableListOf<Runner>()

        bases.remove(3)?.let { scored += it }
        bases.remove(2)?.let { scored += it }
        bases.remove(1)?.let { runner ->
            if (random.chance(adjust(doubleScoresFromFirst, runner, batter))) {
                if (canMakeOut(outsBefore, out.size) && random.chance(outAttemptingExtraBase)) {
                    out += runner
                } else {
                    scored += runner
                }
            } else {
                bases[3] = runner
            }
        }
        bases[2] = batterRunner
        return AdvanceResult(scored = scored, runnersOut = out, rbi = scored.size, batterOuts = 0)
    }

    private fun single(
        bases: BaseState,
        batter: Batter,
        batterRunner: Runner,
        outsBefore: Int,
        random: Random,
    ): AdvanceResult {
        val scored = mutableListOf<Runner>()
        val out = mutableListOf<Runner>()

        bases.remove(3)?.let { scored += it }
        bases.remove(2)?.let { runner ->
            if (random.chance(adjust(singleScoresFromSecond, runner, batter))) {
                if (canMakeOut(outsBefore, out.size) && random.chance(outAttemptingExtraBase)) {
                    out += runner
                } else {
                    scored += runner
                }
            } else {
                bases[3] = runner
            }
        }
        bases.remove(1)?.let { runner ->
            val wantsThird = random.chance(adjust(singleFirstToThird, runner, batter))
            if (wantsThird && bases.isEmpty(3)) {
                if (canMakeOut(outsBefore, out.size) && random.chance(outAttemptingExtraBase)) {
                    out += runner
                } else {
                    bases[3] = runner
                }
            } else {
                bases[2] = runner
            }
        }
        bases[1] = batterRunner
        return AdvanceResult(scored = scored, runnersOut = out, rbi = scored.size)
    }

    /**
     * 한 타석에서 아웃이 3개를 넘지 않게 막는다.
     *
     * 2아웃에서 단타가 나와 주자 둘이 동시에 무리한 진루를 하면 아웃이 4개가 되어 이닝이 깨진다.
     * 실제 야구에서도 세 번째 아웃이 나오는 순간 플레이가 끝나므로, 그 뒤 주자는 있던 자리에 멈춘다.
     */
    private fun canMakeOut(outsBefore: Int, outsMade: Int): Boolean =
        outsBefore + outsMade < OUTS_PER_INNING

    // ---------- 출루 ----------

    private fun forcedWalk(bases: BaseState, batterRunner: Runner, rbiCredited: Boolean): AdvanceResult {
        // 밀어내기: 앞이 꽉 차 있을 때만 주자가 움직인다
        val scored = mutableListOf<Runner>()
        if (bases.isOccupied(1)) {
            if (bases.isOccupied(2)) {
                if (bases.isOccupied(3)) {
                    bases.remove(3)?.let { scored += it }
                }
                bases[3] = bases.remove(2)
            }
            bases[2] = bases.remove(1)
        }
        bases[1] = batterRunner
        return AdvanceResult(scored = scored, rbi = if (rbiCredited) scored.size else 0)
    }

    /** 실책 출루: 주자는 한 베이스씩 간다. 실책으로 들어온 점수는 비자책이므로 타점도 주지 않는다. */
    private fun reachedOnError(bases: BaseState, batterRunner: Runner): AdvanceResult {
        val scored = mutableListOf<Runner>()
        bases.remove(3)?.let { scored += it }
        bases[3] = bases.remove(2)
        bases[2] = bases.remove(1)
        bases[1] = batterRunner
        return AdvanceResult(scored = scored, rbi = 0)
    }

    private fun fieldersChoice(bases: BaseState, batterRunner: Runner, outsBefore: Int): AdvanceResult {
        val outRunner = bases.remove(1) ?: bases.remove(2) ?: bases.remove(3)
        val inningOver = outsBefore + 1 >= OUTS_PER_INNING
        val scored = mutableListOf<Runner>()
        if (!inningOver) {
            bases.remove(3)?.let { scored += it }
            bases[3] = bases.remove(2)
        }
        bases[1] = batterRunner
        return AdvanceResult(
            scored = scored,
            runnersOut = listOfNotNull(outRunner),
            rbi = scored.size,
        )
    }

    // ---------- 아웃 ----------

    private fun doublePlay(bases: BaseState, outsBefore: Int): AdvanceResult {
        val leadRunner = bases.remove(1)
        val scored = mutableListOf<Runner>()
        if (outsBefore + 2 < OUTS_PER_INNING) {
            bases.remove(3)?.let { scored += it }
            bases[3] = bases.remove(2)
        }
        return AdvanceResult(
            scored = scored,
            runnersOut = listOfNotNull(leadRunner),
            rbi = scored.size,
            batterOuts = 1,
        )
    }

    private fun sacFly(bases: BaseState): AdvanceResult {
        val scored = listOfNotNull(bases.remove(3))
        return AdvanceResult(scored = scored, rbi = scored.size, batterOuts = 1)
    }

    private fun sacBunt(bases: BaseState, outsBefore: Int): AdvanceResult {
        val scored = mutableListOf<Runner>()
        if (outsBefore + 1 < OUTS_PER_INNING) {
            bases.remove(3)?.let { scored += it }
            bases[3] = bases.remove(2)
            bases[2] = bases.remove(1)
        }
        return AdvanceResult(scored = scored, rbi = scored.size, batterOuts = 1)
    }

    private fun groundOut(bases: BaseState, outsBefore: Int, random: Random): AdvanceResult {
        val scored = mutableListOf<Runner>()
        if (outsBefore + 1 < OUTS_PER_INNING) {
            if (bases.isOccupied(3) && random.chance(groundOutScoresFromThird)) {
                bases.remove(3)?.let { scored += it }
            }
            if (bases.isOccupied(2) && bases.isEmpty(3) && random.chance(groundOutAdvancesFromSecond)) {
                bases[3] = bases.remove(2)
            }
            if (bases.isOccupied(1) && bases.isEmpty(2)) {
                bases[2] = bases.remove(1)
            }
        }
        return AdvanceResult(scored = scored, rbi = scored.size, batterOuts = 1)
    }

    // ---------- 타석 사이 ----------

    /** 폭투·포일: 주자가 한 베이스씩 간다. */
    fun onWildPitch(bases: BaseState): AdvanceResult {
        val scored = mutableListOf<Runner>()
        bases.remove(3)?.let { scored += it }
        if (bases.isOccupied(2)) bases[3] = bases.remove(2)
        if (bases.isOccupied(1)) bases[2] = bases.remove(1)
        return AdvanceResult(scored = scored)
    }

    /** 도루 시도. 성공하면 다음 베이스로, 실패하면 아웃. */
    fun onSteal(bases: BaseState, fromBase: Int, success: Boolean): AdvanceResult {
        val runner = bases.remove(fromBase) ?: return AdvanceResult()
        if (!success) return AdvanceResult(runnersOut = listOf(runner))
        val target = fromBase + 1
        return if (target > BaseState.BASE_COUNT) {
            AdvanceResult(scored = listOf(runner))
        } else {
            bases[target] = runner
            AdvanceResult()
        }
    }

    /** 주루 능력치로 진루 확률을 조정한다. 느린 주자는 덜 뛴다. */
    private fun adjust(baseChance: Double, runner: Runner, batter: Batter): Double {
        val speed = runnerSpeed(runner, batter)
        return (baseChance * (1.0 + tables.value("batterSpeedToExtraBase", speed))).coerceIn(0.02, 0.95)
    }

    /**
     * 주자의 주루 능력치. 주자 객체는 id 만 들고 있어서, 타석의 타자가 아닌 주자의 능력치는
     * [runnerSpeedLookup] 으로 채워 넣는다. 없으면 리그 평균(50)을 쓴다.
     */
    var runnerSpeedLookup: ((Runner) -> Int)? = null

    private fun runnerSpeed(runner: Runner, batter: Batter): Int =
        runnerSpeedLookup?.invoke(runner) ?: batter.ratings.speed

    companion object {
        const val OUTS_PER_INNING: Int = 3
    }
}
