package baseballgm.development

import baseballgm.io.BalanceConfig
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.Coach
import baseballgm.model.CoachFocus
import baseballgm.model.CoachRole
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.StaffContract
import baseballgm.model.StaffId
import baseballgm.util.chance
import kotlin.math.max
import kotlin.random.Random

/**
 * 은퇴 (docs/09).
 *
 * 시즌이 끝나면 나이·능력치·계약 여부로 확률을 매긴다. 계약이 없는 30대 후반의 하락기 선수가
 * 주로 떠난다. 은퇴 선수 일부는 **코치 후보**가 되고, 성향은 선수 시절 유형에서 물려받는다
 * (거포였던 타자는 장타 중심 타격코치).
 */
class RetirementModel(private val balance: BalanceConfig) {

    private val section = balance.section("retirement")
    private val fromAge = section.int("fromAge")
    private val chancePerYear = section.double("chancePerYearOver")
    private val lowRatingThreshold = section.double("lowRatingThreshold")
    private val lowRatingChance = section.double("lowRatingChance")
    private val noContractChance = section.double("noContractChance")
    private val forcedAge = section.int("forcedAge")
    private val minimumAgeForLowRating = section.int("minimumAgeForLowRating")
    private val coachShare = section.double("coachCandidateShare")
    private val coachMinRating = section.double("coachCandidateMinRating")

    /** 은퇴 확률. 화면에 "은퇴 고민 중" 같은 표시를 하려면 이 값을 쓰면 된다. */
    fun chanceOf(player: Player, season: Int, overall: Double, hasContract: Boolean): Double {
        val age = player.ageIn(season)
        if (age >= forcedAge) return 1.0
        var chance = 0.0
        if (age >= fromAge) chance += (age - fromAge + 1) * chancePerYear
        if (age >= minimumAgeForLowRating && overall < lowRatingThreshold) chance += lowRatingChance
        if (!hasContract) chance += noContractChance
        return chance.coerceIn(0.0, 1.0)
    }

    fun retires(player: Player, season: Int, overall: Double, hasContract: Boolean, random: Random): Boolean =
        random.chance(chanceOf(player, season, overall, hasContract))

    /**
     * 은퇴 선수가 코치 후보가 되는지. 되는 선수는 소수다.
     * 코치 등급은 선수 시절 수준에서, 성향은 가장 잘하던 능력치에서 온다.
     */
    fun toCoach(player: Player, overall: Double, id: StaffId, random: Random): Coach? {
        if (overall < coachMinRating) return null
        if (!random.chance(coachShare)) return null
        val focus = inheritedFocus(player)
        return Coach(
            id = id,
            name = player.name,
            birthYear = player.birthYear,
            role = when (AttributeGroup.of(focus.attribute)) {
                AttributeGroup.BATTING -> CoachRole.BATTING
                AttributeGroup.PITCHING -> CoachRole.PITCHING
                AttributeGroup.FIELDING -> CoachRole.FIELDING
            },
            focus = focus,
            grade = gradeFor(overall),
            contract = StaffContract(salary = balance.double("staffGeneration.coachSalary.${gradeFor(overall)}"), yearsRemaining = 0),
            teamId = null,
        )
    }

    /** 선수 시절 가장 잘하던 능력치를 코치 성향으로 물려받는다. */
    private fun inheritedFocus(player: Player): CoachFocus {
        val best = player.ratingsMap().maxByOrNull { it.value }?.key ?: Attribute.CONTACT
        return CoachFocus.entries.firstOrNull { it.attribute == best }
            ?: when (player) {
                is Batter -> CoachFocus.CONTACT
                is Pitcher -> CoachFocus.CONTROL
            }
    }

    private fun gradeFor(overall: Double): Int = when {
        overall >= GRADE5 -> 5
        overall >= GRADE4 -> 4
        overall >= GRADE3 -> 3
        else -> 2
    }

    private companion object {
        const val GRADE5 = 82.0
        const val GRADE4 = 73.0
        const val GRADE3 = 65.0
    }
}

/** 남은 계약이 있는가. 은퇴 판정과 (M7의) FA 판정에 쓴다. */
fun Player.hasContract(): Boolean = max(0, contract.yearsRemaining) > 0
