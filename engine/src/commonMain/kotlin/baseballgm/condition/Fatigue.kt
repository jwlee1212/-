package baseballgm.condition

import baseballgm.io.BalanceConfig
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.Position
import baseballgm.sim.RatingTables
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 피로도 (docs/08).
 *
 * 투수는 투구수로, 야수는 출장으로 쌓이고 매일 조금씩 회복한다.
 * 기준: 100구 던진 선발은 4일 쉬면 거의 회복하고, 20구 던진 불펜은 하루면 회복한다.
 * 포수는 두 배로 쌓여서 자연스럽게 더 자주 쉬게 된다.
 *
 * 피로가 쌓이면 능력치가 떨어지고(`PlateAppearanceSim`) 부상 확률이 올라간다(`InjuryModel`).
 */
class FatigueModel(balance: BalanceConfig, private val tables: RatingTables) {

    private val section = balance.section("fatigue")
    private val perPitch = section.double("pitcher.perPitch")
    private val backToBackExtra = section.double("pitcher.backToBackExtra")
    private val thirdConsecutiveExtra = section.double("pitcher.thirdConsecutiveExtra")
    private val pitcherDailyRecovery = section.double("pitcher.dailyRecovery")
    private val pitcherRestRecovery = section.double("pitcher.restDayRecovery")
    private val batterPerGame = section.double("batter.perGame")
    private val catcherMultiplier = section.double("batter.catcherMultiplier")
    private val batterDailyRecovery = section.double("batter.dailyRecovery")
    private val batterRestRecovery = section.double("batter.restDayRecovery")
    private val recoveryAgeFrom = section.int("recoveryAgeFrom")
    private val recoveryAgePenalty = section.double("recoveryAgePenaltyPerYear")
    private val allStarBreakRecovery = section.double("allStarBreakRecovery")
    private val conditioningBonus = section.double("conditioningCoachBonus")
    private val staminaBonus = section.numericMap("recoveryStaminaBonus")

    /** 등판 후 피로. [consecutiveDays] 는 어제까지 며칠 연속 던졌는지. */
    fun afterPitching(current: Int, pitches: Int, consecutiveDays: Int): Int {
        var added = pitches * perPitch
        if (consecutiveDays >= 1) added += backToBackExtra
        if (consecutiveDays >= 2) added += thirdConsecutiveExtra
        return (current + added).roundToInt().coerceIn(0, MAX)
    }

    /** 출장 후 피로. 포수는 두 배. */
    fun afterPlaying(current: Int, position: Position): Int {
        val multiplier = if (position == Position.CATCHER) catcherMultiplier else 1.0
        return (current + batterPerGame * multiplier).roundToInt().coerceIn(0, MAX)
    }

    /**
     * 하루 회복. 체력이 높고 젊을수록 빠르고, 컨디셔닝 코치가 있으면 더 빠르다 (docs/13).
     *
     * @param played 오늘 경기에 나섰는가 (쉬는 날이면 더 많이 회복한다)
     * @param conditioningGrade 컨디셔닝 코치 등급 1~5 (없으면 0)
     */
    fun recover(player: Player, season: Int, played: Boolean, conditioningGrade: Int): Int {
        // 투수와 야수는 피로가 쌓이는 속도가 크게 달라서 회복 속도도 따로 둔다
        val base = when (player) {
            is Pitcher -> if (played) pitcherDailyRecovery else pitcherRestRecovery
            is Batter -> if (played) batterDailyRecovery else batterRestRecovery
        }
        val stamina = when (player) {
            is Pitcher -> player.ratings.stamina
            is Batter -> LEAGUE_AVERAGE
        }
        var rate = base * interpolateStamina(stamina.toDouble())
        val age = player.ageIn(season)
        if (age > recoveryAgeFrom) rate *= max(MIN_AGE_FACTOR, 1.0 - (age - recoveryAgeFrom) * recoveryAgePenalty)
        if (conditioningGrade > 0) rate *= 1.0 + conditioningBonus * (conditioningGrade / MAX_STAFF_GRADE.toDouble())
        return max(0, (player.condition.fatigue - rate).roundToInt())
    }

    /** 올스타 브레이크: 한 주 쉬면서 크게 회복한다 (docs/07). */
    fun afterAllStarBreak(current: Int): Int = max(0, (current - allStarBreakRecovery).roundToInt())

    private fun interpolateStamina(stamina: Double): Double {
        val keys = staminaBonus.keys.sorted()
        if (stamina <= keys.first()) return staminaBonus.getValue(keys.first())
        if (stamina >= keys.last()) return staminaBonus.getValue(keys.last())
        for (index in 0 until keys.lastIndex) {
            val low = keys[index]
            val high = keys[index + 1]
            if (stamina <= high) {
                val ratio = (stamina - low) / (high - low)
                return staminaBonus.getValue(low) +
                    (staminaBonus.getValue(high) - staminaBonus.getValue(low)) * ratio
            }
        }
        return staminaBonus.getValue(keys.last())
    }

    private companion object {
        const val MAX = 100
        const val LEAGUE_AVERAGE = 50
        const val MIN_AGE_FACTOR = 0.6
        const val MAX_STAFF_GRADE = 5
    }
}
