package baseballgm.development

import baseballgm.io.BalanceConfig
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.GrowthType
import baseballgm.model.HiddenTraits
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.util.chance
import baseballgm.util.nextInRange
import kotlin.math.roundToInt
import kotlin.random.Random

/** 각성·급노쇠 판정 결과. */
data class AwakeningResult(
    val player: Player,
    val awakened: Boolean = false,
    val collapsed: Boolean = false,
)

/**
 * 각성과 급노쇠 (docs/09).
 *
 * 둘 다 **드물다.** 리그 전체에서 시즌당 몇 명 나오는 정도여야 "저 선수가 터졌다"가 사건이 된다.
 * - 각성: 25세 이하, 대기만성형에게 더 자주. 잠재력이 5~10 오른다 (현재치가 아니라 **상한**이 오른다)
 * - 급노쇠: 33세 이상. 그해 하락 폭이 2.4배가 된다
 */
class AwakeningModel(
    balance: BalanceConfig,
    private val curves: AgingCurves,
) {
    private val section = balance.section("awakening")
    private val chance = section.double("chance")
    private val maxAge = section.int("maxAge")
    private val lateBloomerMultiplier = section.double("lateBloomerMultiplier")
    private val potentialGain = section.intRange("potentialGain")
    private val collapseFromAge = section.int("collapse.fromAge")
    private val collapseChance = section.double("collapse.chance")
    private val collapseMultiplier = section.double("collapse.extraDeclineMultiplier")

    fun check(player: Player, season: Int, random: Random): AwakeningResult {
        val age = player.ageIn(season)

        if (age <= maxAge) {
            val multiplier = if (player.hidden.growthType == GrowthType.LATE) lateBloomerMultiplier else 1.0
            if (random.chance(chance * multiplier)) {
                val gain = random.nextInRange(potentialGain)
                return AwakeningResult(player.withPotentialGain(gain), awakened = true)
            }
        }

        if (age >= collapseFromAge && random.chance(collapseChance)) {
            // 이미 적용된 한 해치 하락에 더해, 추가분을 한 번 더 깎는다
            val extra = collapseMultiplier - 1.0
            val ratings = player.ratingsMap().mapValues { (attribute, value) ->
                (value - curves.annualDecline(attribute, age) * extra).roundToInt().coerceAtLeast(MIN_RATING)
            }
            return AwakeningResult(player.withRatings(ratings), collapsed = true)
        }

        return AwakeningResult(player)
    }

    private fun Player.withPotentialGain(gain: Int): Player {
        val hidden = HiddenTraits(
            potential = this.hidden.potential.mapValues { (_, value) -> (value + gain).coerceAtMost(MAX_RATING) },
            growthType = this.hidden.growthType,
            durability = this.hidden.durability,
            volatility = this.hidden.volatility,
            platoonSplit = this.hidden.platoonSplit,
            adaptability = this.hidden.adaptability,
            scoutingNoiseSeed = this.hidden.scoutingNoiseSeed,
        )
        return when (this) {
            is Batter -> copy(hidden = hidden)
            is Pitcher -> copy(hidden = hidden)
        }
    }

    private companion object {
        const val MIN_RATING = 5
        const val MAX_RATING = 99
    }
}

/** 각성한 선수의 잠재력이 실제로 올랐는지 확인할 때 쓴다 (테스트·스카우트 리포트용). */
internal fun Player.potentialOf(attribute: Attribute): Int = hidden.potential[attribute] ?: 0
