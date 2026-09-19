package baseballgm.development

import baseballgm.io.BalanceConfig
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.BatterRatings
import baseballgm.model.CoachFocus
import baseballgm.model.Hand
import baseballgm.model.ManagerSpecialty
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRatings
import baseballgm.model.Player
import baseballgm.util.nextGaussian
import baseballgm.util.nextInRange
import kotlin.math.roundToInt
import kotlin.random.Random

/** 출장 경험 (docs/09). 25세 이하 선수의 성장 속도를 바꾼다. */
enum class PlayingExperience(val configKey: String) {
    FIRST_TEAM_STARTER("firstTeamStarter"),
    FUTURES_STARTER("futuresStarter"),
    FIRST_TEAM_BENCH("firstTeamBench"),
    INJURED("injured"),
    SANGMU("sangmu"),
    ACTIVE_DUTY("activeDuty"),
}

/** 능력치 그룹. 코치 한 명이 담당하는 범위다. */
enum class AttributeGroup(val attributes: Set<Attribute>) {
    BATTING(setOf(Attribute.CONTACT, Attribute.POWER, Attribute.EYE)),
    PITCHING(setOf(Attribute.STUFF, Attribute.CONTROL, Attribute.GROUNDBALL, Attribute.STAMINA)),
    FIELDING(setOf(Attribute.DEFENSE, Attribute.SPEED)),
    ;

    companion object {
        fun of(attribute: Attribute): AttributeGroup = entries.first { attribute in it.attributes }
    }
}

/** 능력치 그룹 하나를 맡은 코치. */
data class CoachAssignment(val focus: CoachFocus, val grade: Int)

/**
 * 성장에 영향을 주는 팀 사정 (docs/09, 13).
 *
 * 야수는 타격코치와 수비·주루코치 **둘 다**의 영향을 받으므로 코치를 그룹별로 들고 있는다.
 *
 * @param futuresManagerGrade 2군 감독 등급 (25세 이하 전체 보너스, 성향은 없음)
 * @param managerSpecialty 감독 전문 분야 (해당 그룹 +10%)
 */
data class GrowthContext(
    val experience: PlayingExperience,
    val coaches: Map<AttributeGroup, CoachAssignment> = emptyMap(),
    val futuresManagerGrade: Int = 0,
    val managerSpecialty: ManagerSpecialty? = null,
)

/** 성장 결과. [changes] 는 능력치별 증감이다. */
data class GrowthResult(val player: Player, val changes: Map<Attribute, Int>)

/**
 * 성장과 노화 (docs/09).
 *
 * **성장기**에는 매년 (잠재력 − 현재)의 20~35%를 좁힌다. 그중 40%는 시즌 중 월 1회씩(25세 이하만),
 * 60%는 시즌 후에 반영한다. **전성기**에는 작은 랜덤 변동만 있고, **하락기**에는 나이가 많을수록
 * 크게 떨어진다.
 *
 * 코치는 성장량을 **재분배**한다. 집중 능력치를 ×1.3 해 주는 대신 같은 그룹의 나머지를 ×0.9 한다 —
 * 좋은 코치를 모으면 무조건 다 오르는 구조가 되지 않게 하려는 장치다.
 */
class GrowthModel(
    private val balance: BalanceConfig,
    private val curves: AgingCurves,
) {
    private val growth = balance.section("growth")
    private val inSeasonShare = growth.double("inSeasonShare")
    private val inSeasonMaxAge = growth.int("inSeasonMaxAge")
    private val monthlyCount = growth.int("monthlyGrowthCount")
    private val switchPenalty = growth.double("switchHitterPenalty")
    private val focusedMultiplier = growth.double("coachFocus.focused")
    private val othersMultiplier = growth.double("coachFocus.others")
    private val managerBonus = growth.double("managerSpecialtyBonus")
    private val futuresManagerBonus = growth.double("futuresManagerBonus")
    private val activeDutyDecline = growth.double("activeDutyDecline")

    /** 시즌 중 월 1회 성장 (25세 이하만). */
    fun monthly(player: Player, season: Int, context: GrowthContext, random: Random): GrowthResult {
        if (player.ageIn(season) > inSeasonMaxAge) return GrowthResult(player, emptyMap())
        val share = inSeasonShare / monthlyCount
        return applyGrowth(player, season, context, share, random, includeDecline = false)
    }

    /**
     * 시즌 후 성장·노화. 25세 이하는 시즌 중에 이미 40%를 받았으므로 나머지 60%만,
     * 그보다 나이가 많으면 한 해치 전부를 여기서 받는다.
     */
    fun afterSeason(player: Player, season: Int, context: GrowthContext, random: Random): GrowthResult {
        val share = if (player.ageIn(season) <= inSeasonMaxAge) 1.0 - inSeasonShare else 1.0
        return applyGrowth(player, season, context, share, random, includeDecline = true)
    }

    private fun applyGrowth(
        player: Player,
        season: Int,
        context: GrowthContext,
        share: Double,
        random: Random,
        includeDecline: Boolean,
    ): GrowthResult {
        val age = player.ageIn(season)
        val phase = curves.phaseOf(age, player.hidden.growthType)
        val baseClosure = random.nextInRange(curves.closureRange())
        val changes = mutableMapOf<Attribute, Int>()

        val updated = player.ratingsMap().mapValues { (attribute, current) ->
            val potential = player.hidden.potential[attribute] ?: current
            val delta = when {
                context.experience == PlayingExperience.ACTIVE_DUTY ->
                    // 현역·사회복무는 성장이 멈추고 감각이 떨어진다 (docs/12)
                    if (includeDecline) -activeDutyDecline else 0.0

                phase == CareerPhase.GROWTH && current < potential -> {
                    val closure = curves.annualClosure(attribute, baseClosure)
                    (potential - current) * closure * share * multiplierFor(player, attribute, context, age)
                }

                phase == CareerPhase.PEAK ->
                    if (includeDecline) random.nextGaussian(0.0, curves.peakVariation) else 0.0

                phase == CareerPhase.DECLINE ->
                    if (includeDecline) -curves.annualDecline(attribute, age) else 0.0

                else -> 0.0
            }
            val next = (current + delta).roundToInt().coerceIn(MIN_RATING, maxOf(potential, MIN_RATING))
            if (next != current) changes[attribute] = next - current
            next
        }
        return GrowthResult(player.withRatings(updated), changes)
    }

    /**
     * 성장량 배율: 출장 경험 × 코치 재분배 × 감독 전문 분야 × 2군 감독 × 양타 보정.
     * 출장 경험 보정은 25세 이하에만 적용한다 (docs/09).
     */
    private fun multiplierFor(player: Player, attribute: Attribute, context: GrowthContext, age: Int): Double {
        var multiplier = 1.0
        if (age <= inSeasonMaxAge) {
            multiplier *= growth.double("experienceMultiplier.${context.experience.configKey}")
            if (context.futuresManagerGrade > 0) {
                multiplier *= 1.0 + futuresManagerBonus * gradeShare(context.futuresManagerGrade)
            }
        }
        multiplier *= coachMultiplier(attribute, context)
        if (context.managerSpecialty != null && AttributeGroup.of(attribute) == context.managerSpecialty.group()) {
            multiplier *= 1.0 + managerBonus
        }
        if (player is Batter && player.bats == Hand.SWITCH) multiplier *= switchPenalty
        return multiplier
    }

    /**
     * 코치는 총량을 늘리지 않고 **재분배**한다: 집중 능력치 ×1.3, 같은 그룹의 나머지 ×0.9.
     * 배율의 크기는 코치 등급에 비례한다 (1등급 코치는 거의 차이가 없다).
     */
    private fun coachMultiplier(attribute: Attribute, context: GrowthContext): Double {
        val assignment = context.coaches[AttributeGroup.of(attribute)] ?: return 1.0
        if (assignment.grade <= 0) return 1.0
        val share = gradeShare(assignment.grade)
        return if (assignment.focus.attribute == attribute) {
            1.0 + (focusedMultiplier - 1.0) * share
        } else {
            1.0 - (1.0 - othersMultiplier) * share
        }
    }

    private fun gradeShare(grade: Int): Double = grade / MAX_GRADE

    private fun ManagerSpecialty.group(): AttributeGroup = when (this) {
        ManagerSpecialty.BATTING -> AttributeGroup.BATTING
        ManagerSpecialty.PITCHING -> AttributeGroup.PITCHING
        ManagerSpecialty.FIELDING -> AttributeGroup.FIELDING
    }

    private companion object {
        const val MIN_RATING = 5
        const val MAX_GRADE = 5.0
    }
}

/** 능력치만 바꾼 사본. */
fun Player.withRatings(values: Map<Attribute, Int>): Player = when (this) {
    is Batter -> copy(ratings = BatterRatings.from(values))
    is Pitcher -> copy(ratings = PitcherRatings.from(values))
}
