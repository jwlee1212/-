package baseballgm.condition

import baseballgm.io.BalanceConfig
import baseballgm.model.Player
import baseballgm.util.nextGaussian
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 폼 (docs/08).
 *
 * 선수마다 숨겨진 폼 값(0~100)이 있고 매 경기 **랜덤 워크 + 평균 회귀**로 움직인다.
 * 변동 폭은 숨김 수치인 **기복**에 비례해서, 기복이 큰 선수는 오르내림이 심하다.
 * 표시는 5단계(급상승~슬럼프)로 모든 선수에게 공개된다.
 *
 * 2군에 내려가면 폼이 평균으로 빠르게 회복한다 — "슬럼프 탈출을 위한 말소"가 실제로 의미 있게 만든다.
 */
class FormModel(balance: BalanceConfig) {

    private val section = balance.section("form")
    private val walkSd = section.double("randomWalkSd")
    private val volatilityScale = section.double("volatilityScale")
    private val regression = section.double("regressionToMean")
    private val minForm = section.int("min")
    private val maxForm = section.int("max")
    private val demotionRecovery = section.double("demotionRecovery")
    private val restRecovery = section.double("restRecovery")
    private val levels = section.stringList("levels")
    private val thresholds = section.intList("levelThresholds")

    /** 하루가 지난 뒤의 폼. */
    fun next(player: Player, random: Random): Int {
        val volatility = player.hidden.volatility
        val sd = walkSd * (1.0 + (volatility - MIDDLE) * volatilityScale)
        val drift = (MIDDLE - player.condition.form) * regression
        val next = player.condition.form + drift + random.nextGaussian(0.0, sd)
        return next.roundToInt().coerceIn(minForm, maxForm)
    }

    /** 2군 말소: 평균 쪽으로 크게 당긴다. */
    fun afterDemotion(form: Int): Int = pullToMiddle(form, demotionRecovery)

    /** 휴식: 평균 쪽으로 조금 당긴다. */
    fun afterRest(form: Int): Int = pullToMiddle(form, restRecovery)

    /** 표시용 5단계 (급상승 / 상승 / 보통 / 하락 / 슬럼프). */
    fun levelOf(form: Int): String {
        thresholds.forEachIndexed { index, threshold ->
            if (form >= threshold) return levels[index]
        }
        return levels.last()
    }

    private fun pullToMiddle(form: Int, rate: Double): Int =
        (form + (MIDDLE - form) * rate).roundToInt().coerceIn(minForm, maxForm)

    private companion object {
        const val MIDDLE = 50
    }
}
