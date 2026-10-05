package baseballgm.tools

import baseballgm.model.Attribute
import baseballgm.model.BatterArchetype
import baseballgm.model.PitcherArchetype
import baseballgm.model.PitcherRole
import baseballgm.model.Position

/**
 * 선수 유형·포지션에 따른 능력치 편향 (docs/03).
 *
 * 여기서 더한 값은 "모양"만 만든다. 더한 뒤 종합 능력치를 목표치에 다시 맞추기 때문에
 * (PlayerGenerator 의 정규화) 편향이 선수의 총합 수준을 올리거나 내리지는 않는다.
 */
object Archetypes {

    fun batterBias(archetype: BatterArchetype): Map<Attribute, Double> = when (archetype) {
        BatterArchetype.CONTACT_HITTER -> mapOf(
            Attribute.CONTACT to 11.0, Attribute.POWER to -11.0, Attribute.EYE to 2.0,
            Attribute.SPEED to 3.0, Attribute.DEFENSE to 0.0,
        )
        BatterArchetype.SLUGGER -> mapOf(
            Attribute.CONTACT to -6.0, Attribute.POWER to 13.0, Attribute.EYE to 2.0,
            Attribute.SPEED to -9.0, Attribute.DEFENSE to -3.0,
        )
        BatterArchetype.ON_BASE -> mapOf(
            Attribute.CONTACT to 3.0, Attribute.POWER to -6.0, Attribute.EYE to 13.0,
            Attribute.SPEED to -2.0, Attribute.DEFENSE to -1.0,
        )
        BatterArchetype.SPEEDSTER -> mapOf(
            Attribute.CONTACT to 5.0, Attribute.POWER to -9.0, Attribute.EYE to -6.0,
            Attribute.SPEED to 13.0, Attribute.DEFENSE to 3.0,
        )
        BatterArchetype.DEFENSIVE -> mapOf(
            Attribute.CONTACT to -6.0, Attribute.POWER to -8.0, Attribute.EYE to -2.0,
            Attribute.SPEED to 3.0, Attribute.DEFENSE to 13.0,
        )
    }

    fun pitcherBias(archetype: PitcherArchetype): Map<Attribute, Double> = when (archetype) {
        PitcherArchetype.FLAMETHROWER -> mapOf(
            Attribute.STUFF to 11.0, Attribute.CONTROL to -10.0,
            Attribute.GROUNDBALL to -3.0, Attribute.STAMINA to -2.0,
        )
        PitcherArchetype.CONTROL_ARTIST -> mapOf(
            Attribute.STUFF to -9.0, Attribute.CONTROL to 12.0,
            Attribute.GROUNDBALL to 2.0, Attribute.STAMINA to 1.0,
        )
        PitcherArchetype.GROUNDBALLER -> mapOf(
            Attribute.STUFF to -6.0, Attribute.CONTROL to 2.0,
            Attribute.GROUNDBALL to 14.0, Attribute.STAMINA to 0.0,
        )
        PitcherArchetype.INNINGS_EATER -> mapOf(
            Attribute.STUFF to -6.0, Attribute.CONTROL to 3.0,
            Attribute.GROUNDBALL to 2.0, Attribute.STAMINA to 13.0,
        )
    }

    /** 포지션 상관: 포수는 주루가 낮고, SS·CF 는 수비·주루가 높고, 1B·DH 는 파워가 높다. */
    fun positionBias(position: Position): Map<Attribute, Double> = when (position) {
        Position.CATCHER -> mapOf(Attribute.SPEED to -18.0, Attribute.DEFENSE to 6.0, Attribute.POWER to 1.0)
        Position.FIRST_BASE -> mapOf(Attribute.POWER to 8.0, Attribute.SPEED to -8.0, Attribute.DEFENSE to -5.0)
        Position.SECOND_BASE -> mapOf(Attribute.DEFENSE to 6.0, Attribute.SPEED to 5.0, Attribute.POWER to -6.0)
        Position.THIRD_BASE -> mapOf(Attribute.DEFENSE to 3.0, Attribute.POWER to 4.0, Attribute.SPEED to -3.0)
        Position.SHORTSTOP -> mapOf(Attribute.DEFENSE to 10.0, Attribute.SPEED to 7.0, Attribute.POWER to -7.0)
        Position.LEFT_FIELD -> mapOf(Attribute.POWER to 5.0, Attribute.DEFENSE to -4.0)
        Position.CENTER_FIELD -> mapOf(Attribute.DEFENSE to 8.0, Attribute.SPEED to 10.0, Attribute.POWER to -5.0)
        Position.RIGHT_FIELD -> mapOf(Attribute.POWER to 5.0, Attribute.DEFENSE to 1.0)
        Position.DESIGNATED_HITTER -> mapOf(Attribute.POWER to 10.0, Attribute.SPEED to -10.0, Attribute.DEFENSE to -13.0)
    }

    /** 보직 상관: 선발은 체력, 마무리는 구위. */
    fun roleBias(role: PitcherRole): Map<Attribute, Double> = when (role) {
        PitcherRole.STARTER -> mapOf(Attribute.STAMINA to 13.0, Attribute.CONTROL to 2.0)
        PitcherRole.RELIEVER -> mapOf(Attribute.STAMINA to -9.0, Attribute.STUFF to 2.0)
        PitcherRole.CLOSER -> mapOf(Attribute.STAMINA to -11.0, Attribute.STUFF to 7.0)
    }

    /** 같은 계열 포지션끼리는 수비 적성이 비슷하다. 백업·대수비 판단(M3)에 쓴다. */
    fun positionFamily(position: Position): Int = when (position) {
        Position.CATCHER -> 0
        Position.FIRST_BASE, Position.DESIGNATED_HITTER -> 1
        Position.SECOND_BASE, Position.SHORTSTOP -> 2
        Position.THIRD_BASE -> 3
        Position.LEFT_FIELD, Position.RIGHT_FIELD -> 4
        Position.CENTER_FIELD -> 5
    }
}

/** 앵커표 선형 보간. `{"40": 0.35, "50": 0.7}` 같은 표에서 사이값을 읽는다. */
fun interpolate(anchors: Map<Int, Double>, x: Double): Double {
    val keys = anchors.keys.sorted()
    if (x <= keys.first()) return anchors.getValue(keys.first())
    if (x >= keys.last()) return anchors.getValue(keys.last())
    for (index in 0 until keys.lastIndex) {
        val low = keys[index]
        val high = keys[index + 1]
        if (x in low.toDouble()..high.toDouble()) {
            val ratio = (x - low) / (high - low)
            return anchors.getValue(low) + (anchors.getValue(high) - anchors.getValue(low)) * ratio
        }
    }
    return anchors.getValue(keys.last())
}
