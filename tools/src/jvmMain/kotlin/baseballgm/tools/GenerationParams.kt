package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.io.JsonSection
import baseballgm.model.Attribute
import baseballgm.model.BatterArchetype
import baseballgm.model.GrowthType
import baseballgm.model.Origin
import baseballgm.model.PitcherArchetype

/** `[평균, 표준편차]` 또는 `[최소, 최대]` 두 값짜리 설정을 담는다. */
data class Pair2(val first: Double, val second: Double) {
    val asRange: ClosedFloatingPointRange<Double> get() = first..second

    companion object {
        fun of(section: JsonSection, path: String): Pair2 {
            val values = section.doubleList(path)
            require(values.size == 2) { "$path 는 값 2개여야 한다" }
            return Pair2(values[0], values[1])
        }
    }
}

/** 선수 역할. 역할에 따라 잠재력 분포와 나이대가 달라진다. */
enum class GenerationRole(val configKey: String) {
    LINEUP_STARTER("lineupStarter"),
    LINEUP_BENCH("lineupBench"),
    ROTATION_STARTER("rotationStarter"),
    BULLPEN_CORE("bullpenCore"),
    BULLPEN_DEPTH("bullpenDepth"),
    FUTURES("futures"),
    ;

    val isPitcher: Boolean
        get() = this == ROTATION_STARTER || this == BULLPEN_CORE || this == BULLPEN_DEPTH
}

/**
 * `balance.json` 에서 리그 생성에 필요한 수치를 한 번에 읽어 둔다.
 * 생성기 코드 어디에도 숫자를 직접 쓰지 않기 위한 창구다 (불변 원칙 3).
 */
class GenerationParams(val balance: BalanceConfig) {

    private val gen = balance.section("leagueGeneration")
    private val growth = balance.section("growth")
    private val aging = balance.section("aging")
    private val salarySection = balance.section("salary")

    val firstTeamSize: Int = gen.int("rosterSize.firstTeam")
    val futuresSize: Int = gen.int("rosterSize.futures")
    val firstTeamPitchers: IntRange = gen.intRange("firstTeamPitchers")
    val futuresPitcherShare: Double = gen.double("futuresPitcherShare")
    val ageRange: IntRange = gen.intRange("ageRange")
    val firstTeamStarterAge: IntRange = gen.intRange("firstTeamStarterAge")
    val collegeShare: Double = gen.double("collegeShare")
    val archetypeSpread: Double = gen.double("archetypeSpread")
    val positionSpread: Double = gen.double("positionSpread")
    val teamRetryLimit: Int = gen.int("teamRetryLimit")

    fun debutAge(origin: Origin): Int = gen.int("debutAge.${origin.configKeyName()}")

    fun debutRatio(origin: Origin): ClosedFloatingPointRange<Double> =
        gen.doubleRange("debutRatioOfPotential.${origin.configKeyName()}")

    fun potentialOf(role: GenerationRole): Pair2 = Pair2.of(gen, "potentialByRole.${role.configKey}")

    val batterArchetypeShare: Map<BatterArchetype, Double> =
        BatterArchetype.entries.associateWith { gen.double("archetypeShare.batter.${it.configKey}") }

    val pitcherArchetypeShare: Map<PitcherArchetype, Double> =
        PitcherArchetype.entries.associateWith { gen.double("archetypeShare.pitcher.${it.configKey}") }

    val durability: Pair2 = Pair2.of(gen, "hiddenTraits.durability")
    val volatility: Pair2 = Pair2.of(gen, "hiddenTraits.volatility")
    val volatilityYouthBonus: Double = gen.double("hiddenTraits.volatilityYouthBonus")
    val platoonSplit: Pair2 = Pair2.of(gen, "hiddenTraits.platoonSplit")
    val platoonLeftyBonus: Double = gen.double("hiddenTraits.platoonLeftyBonus")
    val adaptability: Pair2 = Pair2.of(gen, "hiddenTraits.adaptability")

    val batterLeftShare: Double = gen.double("handedness.batterLeftShare")
    val batterSwitchShare: Double = gen.double("handedness.batterSwitchShare")
    val pitcherLeftShare: Double = gen.double("handedness.pitcherLeftShare")

    val topSpeedBase: Double = gen.double("topSpeedKmh.base")
    val topSpeedPerStuff: Double = gen.double("topSpeedKmh.perStuffPoint")
    val topSpeedFlamethrowerBonus: Double = gen.double("topSpeedKmh.flamethrowerBonus")
    val topSpeedSpread: Double = gen.double("topSpeedKmh.spread")

    val militaryCompletedFromAge: Int = gen.int("militaryService.completedFromAge")
    val militaryUnfulfilledUntilAge: Int = gen.int("militaryService.unfulfilledUntilAge")
    val servingShare: Double = gen.double("militaryService.servingShare")
    val sangmuShare: Double = gen.double("militaryService.sangmuShare")
    val enlistDeadlineAge: Int = balance.int("military.enlistDeadlineAge")
    val serviceMonths: Int = balance.int("military.serviceMonths")

    val growthTypeShare: Map<GrowthType, Double> =
        GrowthType.entries.associateWith { growth.double("growthTypeShare.${it.configKey}") }

    fun peakAges(type: GrowthType): IntRange = growth.intRange("peakAges.${type.configKey}")

    val annualGapClosure: ClosedFloatingPointRange<Double> = growth.doubleRange("annualGapClosure")

    fun growthSpeed(attribute: Attribute): Double = balance.double("attributeGrowthSpeed.${attribute.configKey}")

    val baseDeclinePerYear: Double = aging.double("baseDeclinePerYear")
    val extraDeclineFromAge: Int = aging.int("extraDeclinePerYearOver.age")
    val extraDeclineAmount: Double = aging.double("extraDeclinePerYearOver.amount")

    fun declineMultiplier(attribute: Attribute): Double = aging.double("attributeMultiplier.${attribute.configKey}")

    val salaryByOverall: Map<Int, Double> = salarySection.numericMap("byOverall")
    val minimumSalary: Double = balance.double("minimumSalary.value")
    val rookieSeasons: Int = salarySection.int("rookieSeasons")
    val arbitrationSeasons: Int = salarySection.int("arbitrationSeasons")
    val badContractShare: Double = salarySection.double("badContractShare")
    val badContractMultiplier: ClosedFloatingPointRange<Double> = salarySection.doubleRange("badContractMultiplier")
    val signingBonusShare: ClosedFloatingPointRange<Double> = salarySection.doubleRange("signingBonusShareOfSalary")
    val foreignSalary: ClosedFloatingPointRange<Double> = salarySection.doubleRange("foreignSalary")

    fun serviceMultiplier(key: String): Double = salarySection.double("serviceMultiplier.$key")

    fun contractYears(key: String): IntRange = salarySection.intRange("contractYears.$key")

    fun faQualifyingSeasons(origin: Origin): Int = when (origin) {
        Origin.HIGH_SCHOOL -> balance.int("freeAgency.qualifyingSeasons.highSchool")
        Origin.COLLEGE -> balance.int("freeAgency.qualifyingSeasons.college")
        Origin.FOREIGN -> 0
    }

    val foreignPerTeam: Int = balance.int("foreignPlayers.maxPerTeam")
    val foreignMaxSameType: Int = balance.int("foreignPlayers.maxSameType")

    private fun Origin.configKeyName(): String = when (this) {
        Origin.HIGH_SCHOOL -> "highSchool"
        Origin.COLLEGE -> "college"
        Origin.FOREIGN -> "foreign"
    }
}
