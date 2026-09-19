package baseballgm.condition

import baseballgm.io.BalanceConfig
import baseballgm.io.JsonSection
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.BatterRatings
import baseballgm.model.HiddenTraits
import baseballgm.model.Injury
import baseballgm.model.InjurySeverity
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRatings
import baseballgm.model.Player
import baseballgm.util.chance
import baseballgm.util.interpolateAnchors
import baseballgm.util.nextInRange
import baseballgm.util.weightedPick
import kotlin.math.max
import kotlin.random.Random

/** 부상 하나가 남긴 흔적. 영구 하락이 있으면 [changedPlayer] 가 원래 선수와 다르다. */
data class InjuryOutcome(val changedPlayer: Player, val permanentLoss: Map<Attribute, Int>)

/**
 * 부상 (docs/08).
 *
 * ```
 * 부상 확률 = 기본 × 피로 보정 × 내구도 보정 × 나이 보정 × 팀 닥터 보정
 * ```
 * 부위마다 복귀 후 떨어지는 능력치가 다르다 (햄스트링 → 주루, 팔꿈치 → 구위·제구).
 * 중상 이상은 **영구 하락**이 남고, 시즌 아웃급은 잠재력 상한까지 내려간다.
 */
class InjuryModel(private val balance: BalanceConfig) {

    private val section = balance.section("injury")
    private val baseChance = section.double("baseChancePerGame")
    private val pitcherExtra = section.double("pitcherExtraMultiplier")
    private val fatigueAt100 = section.double("fatigueMultiplierAt100")
    private val durabilityAnchors = section.numericMap("durabilityMultiplier")
    private val ageFrom = section.int("ageMultiplierFromAge")
    private val agePerYear = section.double("ageMultiplierPerYear")
    private val relapseMultiplier = section.double("relapseMultiplier")
    private val relapseWeeks = section.int("relapseWeeks")
    private val severityShare = mapOf(
        InjurySeverity.MINOR to section.double("severityShare.minor"),
        InjurySeverity.MODERATE to section.double("severityShare.moderate"),
        InjurySeverity.MAJOR to section.double("severityShare.major"),
        InjurySeverity.SEASON_ENDING to section.double("severityShare.seasonEnding"),
    )
    private val doctorEffectMin = balance.double("medicalStaffEffectRange.min")
    private val doctorEffectMax = balance.double("medicalStaffEffectRange.max")
    private val rehabWeeks = section.int("rehabWeeksInFutures")

    /** 한 경기 출장에 대한 부상 판정. 다치지 않으면 null. */
    fun roll(player: Player, season: Int, teamDoctorGrade: Int, random: Random): Injury? {
        if (player.condition.isInjured) return null
        var chance = baseChance
        if (player is Pitcher) chance *= pitcherExtra
        chance *= 1.0 + (fatigueAt100 - 1.0) * (player.condition.fatigue / 100.0)
        chance *= interpolateAnchors(durabilityAnchors, player.hidden.durability.toDouble())
        val age = player.ageIn(season)
        if (age > ageFrom) chance *= 1.0 + (age - ageFrom) * agePerYear
        if (player.condition.injury == null && player.condition.relapseRisk) chance *= relapseMultiplier
        chance *= doctorFactor(teamDoctorGrade)

        if (!random.chance(chance)) return null

        val severity = random.weightedPick(severityShare)
        val part = pickPart(player, random)
        val weeks = random.nextInRange(section.intRange("durationWeeks.${severity.configKey()}"))
        return Injury(part = part, severity = severity, weeksRemaining = weeks, relapseRiskWeeks = relapseWeeks)
    }

    /** 한 주가 지났다. 다 나으면 null (재활 기간은 [rehabWeeksInFutures] 로 따로 센다). */
    fun advanceWeek(injury: Injury): Injury? {
        val remaining = injury.weeksRemaining - 1
        return if (remaining <= 0) null else injury.copy(weeksRemaining = remaining)
    }

    val rehabWeeksInFutures: Int get() = rehabWeeks

    /**
     * 복귀할 때 영구 하락을 적용한다 (docs/08).
     * 중상은 50% 확률로 1~4점, 시즌 아웃급은 반드시 3~8점 떨어지고 잠재력 상한도 함께 내려간다.
     * 나이가 많을수록 크고, 팀 닥터가 좋으면 줄어든다.
     */
    fun applyPermanentLoss(
        player: Player,
        injury: Injury,
        season: Int,
        teamDoctorGrade: Int,
        random: Random,
    ): InjuryOutcome {
        val key = injury.severity.configKey()
        if (!section.contains("permanentLoss.$key")) return InjuryOutcome(player, emptyMap())
        val lossSection = section.section("permanentLoss.$key")
        if (!random.chance(lossSection.double("chance"))) return InjuryOutcome(player, emptyMap())

        val range = lossSection.intRange("range")
        val ageFactor = 1.0 + max(0, player.ageIn(season) - ageFrom) * agePerYear
        val reduction = doctorFactor(teamDoctorGrade)
        val attributes = affectedAttributes(player, injury.part)
        val losses = attributes.associateWith { attribute ->
            val raw = random.nextInRange(range.first.toDouble()..range.last.toDouble()) * ageFactor * reduction
            max(1, raw.toInt())
        }
        val lowersPotential = lossSection.booleanOr("lowersPotential", false)
        return InjuryOutcome(applyLosses(player, losses, lowersPotential), losses)
    }

    private fun applyLosses(player: Player, losses: Map<Attribute, Int>, lowersPotential: Boolean): Player {
        val ratings = player.ratingsMap().mapValues { (attribute, value) ->
            (value - (losses[attribute] ?: 0)).coerceAtLeast(MIN_RATING)
        }
        val hidden = if (!lowersPotential) {
            player.hidden
        } else {
            HiddenTraits(
                potential = player.hidden.potential.mapValues { (attribute, value) ->
                    (value - (losses[attribute] ?: 0)).coerceAtLeast(ratings[attribute] ?: MIN_RATING)
                },
                growthType = player.hidden.growthType,
                durability = player.hidden.durability,
                volatility = player.hidden.volatility,
                platoonSplit = player.hidden.platoonSplit,
                adaptability = player.hidden.adaptability,
                scoutingNoiseSeed = player.hidden.scoutingNoiseSeed,
            )
        }
        return when (player) {
            is Batter -> player.copy(ratings = BatterRatings.from(ratings), hidden = hidden)
            is Pitcher -> player.copy(ratings = PitcherRatings.from(ratings), hidden = hidden)
        }
    }

    private fun affectedAttributes(player: Player, part: String): List<Attribute> {
        val parts = partsSection(player)
        if (!parts.contains("$part.attributes")) return emptyList()
        return parts.stringList("$part.attributes").map { key ->
            Attribute.entries.first { it.configKey == key }
        }
    }

    private fun pickPart(player: Player, random: Random): String {
        val parts = partsSection(player)
        val weights = parts.keys.associateWith { parts.double("$it.share") }
        return random.weightedPick(weights)
    }

    private fun partsSection(player: Player): JsonSection =
        section.section(if (player is Pitcher) "pitcherParts" else "batterParts")

    /** 팀 닥터 등급이 높을수록 부상 확률과 영구 하락 폭이 줄어든다 (docs/13). */
    private fun doctorFactor(grade: Int): Double {
        if (grade <= 0) return 1.0
        val effect = doctorEffectMin + (doctorEffectMax - doctorEffectMin) * ((grade - 1) / 4.0)
        return 1.0 - effect
    }

    private fun InjurySeverity.configKey(): String = when (this) {
        InjurySeverity.MINOR -> "minor"
        InjurySeverity.MODERATE -> "moderate"
        InjurySeverity.MAJOR -> "major"
        InjurySeverity.SEASON_ENDING -> "seasonEnding"
    }

    private companion object {
        const val MIN_RATING = 5
    }
}
