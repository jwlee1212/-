package baseballgm.development

import baseballgm.io.BalanceConfig
import baseballgm.model.Attribute
import baseballgm.model.GrowthType
import kotlin.math.max

/** 나이에 따른 선수의 시기. */
enum class CareerPhase { GROWTH, PEAK, DECLINE }

/**
 * 나이 곡선 (docs/09).
 *
 * 성장 타입마다 전성기 구간이 다르고(조기 24~28 / 일반 27~31 / 대기만성 29~33),
 * 능력치마다 오르는 속도와 떨어지는 속도가 다르다. 주루·수비는 20대 후반부터 먼저 무너지고
 * 선구안·제구는 30대 중반까지 버틴다. 이 차이 때문에 유격수가 1루로 옮기고
 * 파이어볼러가 기교파로 바뀌는 커리어가 **저절로** 생긴다.
 *
 * M1 리그 생성기가 현재 능력치를 역산할 때도 같은 곡선을 쓴다 — 생성과 진행이 어긋나지 않게.
 */
class AgingCurves(private val balance: BalanceConfig) {

    private val growth = balance.section("growth")
    private val aging = balance.section("aging")
    private val baseDecline = aging.double("baseDeclinePerYear")
    private val extraFromAge = aging.int("extraDeclinePerYearOver.age")
    private val extraAmount = aging.double("extraDeclinePerYearOver.amount")

    fun peakAges(type: GrowthType): IntRange = growth.intRange("peakAges.${type.configKey}")

    fun phaseOf(age: Int, type: GrowthType): CareerPhase {
        val peak = peakAges(type)
        return when {
            age < peak.first -> CareerPhase.GROWTH
            age <= peak.last -> CareerPhase.PEAK
            else -> CareerPhase.DECLINE
        }
    }

    /** 성장기에 (잠재력 − 현재)를 한 해에 얼마나 좁히는지. 능력치마다 속도가 다르다. */
    fun annualClosure(attribute: Attribute, base: Double): Double =
        (base * balance.double("attributeGrowthSpeed.${attribute.configKey}")).coerceIn(MIN_CLOSURE, MAX_CLOSURE)

    fun closureRange(): ClosedFloatingPointRange<Double> = growth.doubleRange("annualGapClosure")

    /** 전성기가 지난 뒤 한 해 하락 폭. 나이가 많을수록 커진다. */
    fun annualDecline(attribute: Attribute, age: Int): Double {
        val extra = max(0, age - extraFromAge) * extraAmount
        return (baseDecline + extra) * balance.double("aging.attributeMultiplier.${attribute.configKey}")
    }

    /** 전성기에는 작은 랜덤 변동만 있다. */
    val peakVariation: Double get() = growth.double("peakVariation")

    private companion object {
        const val MIN_CLOSURE = 0.05
        const val MAX_CLOSURE = 0.7
    }
}
