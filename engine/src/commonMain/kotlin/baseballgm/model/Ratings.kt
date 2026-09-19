package baseballgm.model

import kotlinx.serialization.Serializable

/** 능력치 최소·최대. `balance.json` 의 `ratingScale` 과 같은 값이며, 여기서는 클램프용으로만 쓴다. */
const val RATING_MIN: Int = 1
const val RATING_MAX: Int = 100

fun Int.clampRating(): Int = coerceIn(RATING_MIN, RATING_MAX)

/** 타자 능력치 5종 (docs/02). */
@Serializable
data class BatterRatings(
    val contact: Int,
    val power: Int,
    val eye: Int,
    val speed: Int,
    val defense: Int,
) {
    operator fun get(attribute: Attribute): Int = when (attribute) {
        Attribute.CONTACT -> contact
        Attribute.POWER -> power
        Attribute.EYE -> eye
        Attribute.SPEED -> speed
        Attribute.DEFENSE -> defense
        else -> error("타자에게 없는 능력치: $attribute")
    }

    fun toMap(): Map<Attribute, Int> = Attribute.batterAttributes.associateWith { this[it] }

    companion object {
        fun from(values: Map<Attribute, Int>): BatterRatings = BatterRatings(
            contact = values.getValue(Attribute.CONTACT).clampRating(),
            power = values.getValue(Attribute.POWER).clampRating(),
            eye = values.getValue(Attribute.EYE).clampRating(),
            speed = values.getValue(Attribute.SPEED).clampRating(),
            defense = values.getValue(Attribute.DEFENSE).clampRating(),
        )
    }
}

/** 투수 능력치 4종 (docs/02). 최고 구속은 표시값이라 [Pitcher] 가 따로 들고 있다. */
@Serializable
data class PitcherRatings(
    val stuff: Int,
    val control: Int,
    val groundball: Int,
    val stamina: Int,
) {
    operator fun get(attribute: Attribute): Int = when (attribute) {
        Attribute.STUFF -> stuff
        Attribute.CONTROL -> control
        Attribute.GROUNDBALL -> groundball
        Attribute.STAMINA -> stamina
        else -> error("투수에게 없는 능력치: $attribute")
    }

    fun toMap(): Map<Attribute, Int> = Attribute.pitcherAttributes.associateWith { this[it] }

    companion object {
        fun from(values: Map<Attribute, Int>): PitcherRatings = PitcherRatings(
            stuff = values.getValue(Attribute.STUFF).clampRating(),
            control = values.getValue(Attribute.CONTROL).clampRating(),
            groundball = values.getValue(Attribute.GROUNDBALL).clampRating(),
            stamina = values.getValue(Attribute.STAMINA).clampRating(),
        )
    }
}
