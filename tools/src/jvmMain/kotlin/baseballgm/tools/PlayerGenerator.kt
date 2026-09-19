package baseballgm.tools

import baseballgm.league.StrengthCalculator
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.BatterArchetype
import baseballgm.model.BatterRatings
import baseballgm.model.Condition
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.GrowthType
import baseballgm.model.Hand
import baseballgm.model.HiddenTraits
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.PitcherArchetype
import baseballgm.model.PitcherRatings
import baseballgm.model.PitcherRole
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.model.ServiceKind
import baseballgm.model.TeamId
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import baseballgm.util.chance
import baseballgm.util.nextGaussian
import baseballgm.util.nextGaussianInt
import baseballgm.util.nextInRange
import baseballgm.util.weightedPick
import kotlin.random.Random

/** 구단 성격에서 온 생성 힌트 (`data/teams.json` 의 `generation`). 해당 없으면 기본값이다. */
data class TeamFlavor(
    val starterAgeShift: Double = 0.0,
    val earlyGrowthBias: Double = 0.0,
    val powerBias: Double = 0.0,
    val under25Share: Double = 0.0,
    val cheapVeterans: Boolean = false,
    val lowSalaryShare: Double = 0.0,
    val longContracts: Boolean = false,
    val badContractHitters: Int = 0,
    val unenlistedProspects: Boolean = false,
    val futuresPotentialShift: Double = 0.0,
    val aGradeProspects: Int = 0,
    val foreignRatingBonus: Double = 0.0,
    val keyPitchersFaInYears: Int? = null,
    val keyPitchersCount: Int = 0,
    /** 팀 전체 잠재력 이동. 음수면 "지금은 쓸 만하지만 더 오를 여지가 없는" 선수단이 된다 */
    val potentialShiftAll: Double = 0.0,
)

/** 선수 한 명을 만들 때 필요한 자리 정보. */
data class PlayerSpec(
    val role: GenerationRole,
    val rosterLevel: RosterLevel,
    val position: Position? = null,
    val pitcherRole: PitcherRole? = null,
    /** 팀 목표 전력에 맞추기 위한 잠재력 이동값 */
    val potentialShift: Double = 0.0,
    val foreign: Boolean = false,
    /** A급 유망주처럼 잠재력을 직접 지정할 때 */
    val potentialOverride: Double? = null,
    /** 나이대를 직접 지정할 때 (신인 유입 등) */
    val ageRange: IntRange? = null,
    /** 데뷔 시즌을 직접 지정할 때. 신인은 나이와 상관없이 올해가 데뷔 시즌이다 */
    val debutSeasonOverride: Int? = null,
) {
    /**
     * 투수인지 야수인지는 **보직이 있는지**로 정한다.
     * (2군 역할 `FUTURES` 는 투수·야수 양쪽에 쓰이므로 역할만으로는 알 수 없다)
     */
    val isPitcher: Boolean get() = pitcherRole != null
}

/** 잠재력 묶음과, 그 모양을 만든 유형. */
private data class PotentialSet(
    val values: Map<Attribute, Double>,
    val batterArchetype: BatterArchetype? = null,
    val pitcherArchetype: PitcherArchetype? = null,
)

/**
 * 선수 한 명을 만든다 (docs/03 생성 순서).
 *
 * 능력치를 먼저 뽑고 나이를 붙이는 게 아니라 **거꾸로** 만든다.
 * 1) 잠재력을 먼저 뽑고 → 2) 나이와 성장 타입으로 현재 능력치를 역산하고 → 3) 계약·군 복무를 채운다.
 * 이렇게 해야 "23세인데 이미 전성기 능력치" 같은 앞뒤 안 맞는 선수가 생기지 않는다.
 */
class PlayerGenerator(
    private val params: GenerationParams,
    private val strength: StrengthCalculator,
    private val names: NameGenerator,
    private val season: Int,
    /** 선수 id 시작 번호. 시즌이 넘어가며 신인을 만들 때 기존 id 와 겹치지 않게 뒤에서 이어 붙인다 */
    startingIdNumber: Int = 1,
) {
    private var nextIdNumber = startingIdNumber

    fun generate(spec: PlayerSpec, teamId: TeamId, flavor: TeamFlavor, random: Random): Player {
        val age = pickAge(spec, flavor, random)
        val birthYear = season - age
        val origin = pickOrigin(spec, age, random)
        val debutAge = params.debutAge(origin)
        val growthType = pickGrowthType(flavor, random)

        val potential = buildPotential(spec, flavor, random)
        val current = deriveCurrent(potential.values, age, debutAge, origin, growthType, random)

        // 복무 중인 선수는 엔트리에서 빠지므로(docs/12) 1군 자리에는 배치하지 않는다.
        // 1군 자리에 복무 중인 선수를 넣으면 등록 인원 28명이 깨진다.
        val military = pickMilitary(age, origin, flavor, random, allowServing = spec.rosterLevel == RosterLevel.FUTURES)
        val rosterLevel = spec.rosterLevel
        val serviceSeasons = serviceSeasonsOf(age, debutAge, military)
        val contract = buildContract(current, spec, origin, serviceSeasons, flavor, random)

        val id = PlayerId("P" + nextIdNumber.toString().padStart(4, '0'))
        nextIdNumber++
        val foreignName = if (origin == Origin.FOREIGN) names.foreign() else null
        val debutSeason = spec.debutSeasonOverride ?: (season - max(0, age - debutAge))

        return if (spec.isPitcher) {
            val throwsWith = if (random.chance(params.pitcherLeftShare)) Hand.LEFT else Hand.RIGHT
            val bats = if (random.chance(PITCHER_SAME_HAND_SHARE)) throwsWith else throwsWith.opposite()
            val ratings = PitcherRatings.from(current.mapValues { it.value.roundToInt() })
            val name = foreignName?.latinName ?: names.korean(birthYear)
            Pitcher(
                id = id,
                name = name,
                birthYear = birthYear,
                throwsWith = throwsWith,
                bats = bats,
                origin = origin,
                debutSeason = debutSeason,
                teamId = teamId,
                rosterLevel = rosterLevel,
                contract = contract,
                military = military,
                condition = Condition.HEALTHY,
                hidden = buildHidden(potential.values, growthType, age, bats, origin, random),
                registeredName = foreignName?.registeredName ?: name,
                role = spec.pitcherRole ?: PitcherRole.RELIEVER,
                ratings = ratings,
                topSpeedKmh = topSpeedOf(ratings.stuff, potential.pitcherArchetype, random),
            )
        } else {
            val position = spec.position ?: Position.LEFT_FIELD
            val bats = pickBats(random)
            val throwsWith = pickThrowingHand(position, bats, random)
            val ratings = BatterRatings.from(current.mapValues { it.value.roundToInt() })
            val name = foreignName?.latinName ?: names.korean(birthYear)
            Batter(
                id = id,
                name = name,
                birthYear = birthYear,
                throwsWith = throwsWith,
                bats = bats,
                origin = origin,
                debutSeason = debutSeason,
                teamId = teamId,
                rosterLevel = rosterLevel,
                contract = contract,
                military = military,
                condition = Condition.HEALTHY,
                hidden = buildHidden(potential.values, growthType, age, bats, origin, random),
                registeredName = foreignName?.registeredName ?: name,
                primaryPosition = position,
                defenseFitness = defenseFitness(position, ratings.defense, random),
                ratings = ratings,
            )
        }
    }

    // ---------- 나이·출신·성장 타입 ----------

    private fun pickAge(spec: PlayerSpec, flavor: TeamFlavor, random: Random): Int {
        spec.ageRange?.let { return random.nextInRange(it) }
        if (spec.foreign) return random.nextInRange(FOREIGN_AGE)
        return when (spec.role) {
            GenerationRole.FUTURES -> random.nextGaussianInt(FUTURES_AGE_MEAN, FUTURES_AGE_SD, 19..29)
            GenerationRole.LINEUP_BENCH, GenerationRole.BULLPEN_DEPTH ->
                random.nextGaussianInt(BENCH_AGE_MEAN + flavor.starterAgeShift * 0.5, BENCH_AGE_SD, params.ageRange)
            else ->
                if (random.chance(flavor.under25Share)) {
                    random.nextGaussianInt(23.0, 1.5, 20..25)
                } else {
                    val mid = (params.firstTeamStarterAge.first + params.firstTeamStarterAge.last) / 2.0
                    random.nextGaussianInt(mid + flavor.starterAgeShift, STARTER_AGE_SD, params.ageRange)
                }
        }
    }

    private fun pickOrigin(spec: PlayerSpec, age: Int, random: Random): Origin = when {
        spec.foreign -> Origin.FOREIGN
        age < params.debutAge(Origin.COLLEGE) -> Origin.HIGH_SCHOOL
        random.chance(params.collegeShare) -> Origin.COLLEGE
        else -> Origin.HIGH_SCHOOL
    }

    private fun pickGrowthType(flavor: TeamFlavor, random: Random): GrowthType =
        if (random.chance(flavor.earlyGrowthBias)) GrowthType.EARLY else random.weightedPick(params.growthTypeShare)

    // ---------- 잠재력 ----------

    private fun buildPotential(spec: PlayerSpec, flavor: TeamFlavor, random: Random): PotentialSet {
        val base = params.potentialOf(spec.role)
        val shift = spec.potentialShift + flavor.potentialShiftAll +
            if (spec.role == GenerationRole.FUTURES) flavor.futuresPotentialShift else 0.0
        val target = ((spec.potentialOverride ?: random.nextGaussian(base.first + shift, base.second))
            .coerceIn(POTENTIAL_MIN, POTENTIAL_MAX) + if (spec.foreign) flavor.foreignRatingBonus else 0.0)

        val attributes = if (spec.isPitcher) Attribute.pitcherAttributes else Attribute.batterAttributes
        val bias = mutableMapOf<Attribute, Double>()
        var batterArchetype: BatterArchetype? = null
        var pitcherArchetype: PitcherArchetype? = null

        if (spec.isPitcher) {
            pitcherArchetype = random.weightedPick(params.pitcherArchetypeShare)
            Archetypes.pitcherBias(pitcherArchetype).forEach { (a, v) -> bias.add(a, v * params.archetypeSpread / 10.0) }
            Archetypes.roleBias(spec.pitcherRole ?: PitcherRole.RELIEVER)
                .forEach { (a, v) -> bias.add(a, v * params.positionSpread / 10.0) }
        } else {
            batterArchetype = random.weightedPick(params.batterArchetypeShare)
            Archetypes.batterBias(batterArchetype).forEach { (a, v) -> bias.add(a, v * params.archetypeSpread / 10.0) }
            Archetypes.positionBias(spec.position ?: Position.LEFT_FIELD)
                .forEach { (a, v) -> bias.add(a, v * params.positionSpread / 10.0) }
            bias.add(Attribute.POWER, flavor.powerBias * POWER_BIAS_SCALE)
        }

        var values = attributes.associateWith { attribute ->
            target + (bias[attribute] ?: 0.0) + random.nextGaussian(0.0, ATTRIBUTE_NOISE)
        }
        // 편향을 더하면 종합이 목표에서 벗어나므로, 전체를 평행 이동해 다시 맞춘다.
        // (유형은 "모양"만 만들고 수준은 팀 목표가 정한다)
        repeat(2) {
            val diff = target - strength.overallOf(values, isBatter = !spec.isPitcher)
            values = values.mapValues { (_, value) -> (value + diff).coerceIn(ATTRIBUTE_MIN, ATTRIBUTE_MAX) }
        }
        return PotentialSet(values, batterArchetype, pitcherArchetype)
    }

    // ---------- 현재 능력치 역산 ----------

    /**
     * 잠재력·나이·성장 타입으로 지금 능력치를 만든다 (docs/09 곡선).
     *
     * 데뷔 시점 능력치에서 출발해 전성기 시작 나이까지 매년 (잠재력 − 현재)의 일정 비율만큼 좁히고,
     * 전성기가 지난 나이면 지난 햇수만큼 깎는다. 능력치마다 성장 속도·하락 속도가 달라서
     * (주루·수비는 빨리 떨어지고 선구안·제구는 오래 간다) 나이 든 선수는 저절로 모양이 바뀐다.
     */
    private fun deriveCurrent(
        potential: Map<Attribute, Double>,
        age: Int,
        debutAge: Int,
        origin: Origin,
        growthType: GrowthType,
        random: Random,
    ): Map<Attribute, Double> {
        val startRatio = random.nextInRange(params.debutRatio(origin))
        val closureBase = random.nextInRange(params.annualGapClosure)
        val peak = params.peakAges(growthType)
        val growthYears = max(0, minOf(age, peak.first) - debutAge)
        val declineYears = max(0, age - peak.last)

        return potential.mapValues { (attribute, ceiling) ->
            val start = ceiling * startRatio
            val closure = (closureBase * params.growthSpeed(attribute)).coerceIn(MIN_CLOSURE, MAX_CLOSURE)
            var value = ceiling - (ceiling - start) * (1.0 - closure).pow(growthYears)
            for (year in 1..declineYears) {
                val yearAge = peak.last + year
                val extra = max(0, yearAge - params.extraDeclineFromAge) * params.extraDeclineAmount
                value -= (params.baseDeclinePerYear + extra) * params.declineMultiplier(attribute)
            }
            value.coerceIn(ATTRIBUTE_MIN, ceiling)
        }
    }

    // ---------- 숨김 수치 ----------

    private fun buildHidden(
        potential: Map<Attribute, Double>,
        growthType: GrowthType,
        age: Int,
        bats: Hand,
        origin: Origin,
        random: Random,
    ): HiddenTraits = HiddenTraits(
        potential = potential.mapValues { it.value.roundToInt().coerceIn(1, 99) },
        growthType = growthType,
        durability = random.nextGaussianInt(params.durability.first, params.durability.second, 10..95),
        volatility = random.nextGaussianInt(
            params.volatility.first + if (age <= YOUTH_AGE) params.volatilityYouthBonus else 0.0,
            params.volatility.second,
            10..95,
        ),
        platoonSplit = random.nextGaussianInt(
            params.platoonSplit.first + if (bats == Hand.LEFT) params.platoonLeftyBonus else 0.0,
            params.platoonSplit.second,
            5..95,
        ),
        adaptability = if (origin == Origin.FOREIGN) {
            random.nextGaussianInt(params.adaptability.first, params.adaptability.second, 10..95)
        } else {
            null
        },
        scoutingNoiseSeed = random.nextInt(),
    )

    // ---------- 군 복무 ----------

    private fun pickMilitary(
        age: Int,
        origin: Origin,
        flavor: TeamFlavor,
        random: Random,
        allowServing: Boolean,
    ): MilitaryStatus {
        if (origin == Origin.FOREIGN) return MilitaryStatus.NotRequired
        if (age >= params.militaryCompletedFromAge) return MilitaryStatus.Completed

        val servingChance = params.servingShare * if (flavor.unenlistedProspects) UNENLISTED_BIAS else 1.0
        if (allowServing && age in SERVICE_AGE && random.chance(servingChance)) {
            val kind = if (random.chance(params.sangmuShare)) ServiceKind.SANGMU else ServiceKind.ACTIVE_DUTY
            return MilitaryStatus.Serving(
                kind = kind,
                returnSeason = season + random.nextInt(1, 3),
                returnWeek = random.nextInt(1, 25),
            )
        }
        if (age <= params.militaryUnfulfilledUntilAge) return MilitaryStatus.Unfulfilled(params.enlistDeadlineAge)

        // 24~27세는 군필·미필이 섞여 있다. 나이가 많을수록 군필 비율이 높다.
        val completedChance = (age - params.militaryUnfulfilledUntilAge).toDouble() /
            (params.militaryCompletedFromAge - params.militaryUnfulfilledUntilAge)
        val bias = if (flavor.unenlistedProspects) UNENLISTED_COMPLETED_BIAS else 1.0
        return if (random.chance(completedChance * bias)) {
            MilitaryStatus.Completed
        } else {
            MilitaryStatus.Unfulfilled(params.enlistDeadlineAge)
        }
    }

    /** 군 복무 기간은 FA 연차로 인정되지 않는다 (docs/12). */
    private fun serviceSeasonsOf(age: Int, debutAge: Int, military: MilitaryStatus): Int {
        val played = max(0, age - debutAge)
        val lost = when (military) {
            is MilitaryStatus.Completed -> MILITARY_LOST_SEASONS
            is MilitaryStatus.Serving -> 1
            else -> 0
        }
        return max(0, played - lost)
    }

    // ---------- 계약 ----------

    private fun buildContract(
        current: Map<Attribute, Double>,
        spec: PlayerSpec,
        origin: Origin,
        serviceSeasons: Int,
        flavor: TeamFlavor,
        random: Random,
    ): Contract {
        val overall = strength.overallOf(current, isBatter = !spec.isPitcher)
        val qualifying = params.faQualifyingSeasons(origin)
        val seasonsToFa = max(0, qualifying - serviceSeasons)

        if (origin == Origin.FOREIGN) {
            val salary = random.nextInRange(params.foreignSalary) * (FOREIGN_SALARY_BASE + overall / 100.0)
            return Contract(
                salary = salary.roundMoney(),
                yearsRemaining = 1,
                seasonsToFreeAgency = 0,
                serviceSeasons = serviceSeasons,
                type = ContractType.FOREIGN,
            )
        }

        val isFreeAgentSigned = serviceSeasons >= qualifying && random.chance(FA_SIGNED_SHARE)
        val bucket = when {
            isFreeAgentSigned -> "freeAgent"
            serviceSeasons < params.rookieSeasons -> "rookie"
            serviceSeasons < params.arbitrationSeasons -> "arbitration"
            else -> "veteran"
        }
        var salary = interpolate(params.salaryByOverall, overall) * params.serviceMultiplier(bucket)
        // 악성 계약: 연봉이 기량보다 높은 FA 계약 (docs/03)
        if (isFreeAgentSigned && random.chance(params.badContractShare + flavor.badContractHitters * BAD_CONTRACT_STEP)) {
            salary *= random.nextInRange(params.badContractMultiplier)
        }
        if (random.chance(flavor.lowSalaryShare)) salary *= LOW_SALARY_FACTOR
        if (flavor.cheapVeterans && serviceSeasons >= params.arbitrationSeasons) salary *= CHEAP_VETERAN_FACTOR

        val yearsKey = when {
            isFreeAgentSigned -> "freeAgent"
            serviceSeasons >= params.arbitrationSeasons -> "veteran"
            else -> "young"
        }
        val years = random.nextInRange(params.contractYears(yearsKey)) +
            if (flavor.longContracts && isFreeAgentSigned) 1 else 0
        val bonus = if (isFreeAgentSigned) salary * random.nextInRange(params.signingBonusShare) else 0.0

        return Contract(
            salary = max(params.minimumSalary, salary).roundMoney(),
            yearsRemaining = years,
            signingBonusRemaining = bonus.roundMoney(),
            seasonsToFreeAgency = seasonsToFa,
            serviceSeasons = serviceSeasons,
            type = when {
                isFreeAgentSigned -> ContractType.FREE_AGENT
                serviceSeasons < params.rookieSeasons -> ContractType.ROOKIE
                else -> ContractType.STANDARD
            },
        )
    }

    // ---------- 부속 ----------

    private fun topSpeedOf(stuff: Int, archetype: PitcherArchetype?, random: Random): Int {
        val bonus = if (archetype == PitcherArchetype.FLAMETHROWER) params.topSpeedFlamethrowerBonus else 0.0
        val speed = params.topSpeedBase + stuff * params.topSpeedPerStuff + bonus +
            random.nextGaussian(0.0, params.topSpeedSpread)
        return speed.roundToInt()
    }

    private fun pickBats(random: Random): Hand = when {
        random.chance(params.batterSwitchShare) -> Hand.SWITCH
        random.chance(params.batterLeftShare) -> Hand.LEFT
        else -> Hand.RIGHT
    }

    /** 포수·2루·3루·유격수는 우투만 가능하다. 1루·외야는 좌투가 섞인다. */
    private fun pickThrowingHand(position: Position, bats: Hand, random: Random): Hand = when (position) {
        Position.CATCHER, Position.SECOND_BASE, Position.THIRD_BASE, Position.SHORTSTOP -> Hand.RIGHT
        else -> if (bats == Hand.LEFT && random.chance(LEFTY_THROWS_SHARE)) Hand.LEFT else Hand.RIGHT
    }

    private fun defenseFitness(primary: Position, defense: Int, random: Random): Map<Position, Int> {
        val family = Archetypes.positionFamily(primary)
        return Position.entries.associateWith { position ->
            when {
                position == primary -> defense + random.nextInt(4, 10)
                position == Position.DESIGNATED_HITTER -> DH_FITNESS
                Archetypes.positionFamily(position) == family -> defense - random.nextInt(4, 12)
                else -> defense - random.nextInt(18, 34)
            }.coerceIn(1, 99)
        }
    }

    private fun Hand.opposite(): Hand = if (this == Hand.LEFT) Hand.RIGHT else Hand.LEFT

    private fun MutableMap<Attribute, Double>.add(attribute: Attribute, value: Double) {
        this[attribute] = (this[attribute] ?: 0.0) + value
    }

    private fun Double.roundMoney(): Double = (this * 100).roundToInt() / 100.0

    companion object {
        private val FOREIGN_AGE = 26..33
        private val SERVICE_AGE = 21..27
        private const val POTENTIAL_MIN = 30.0
        private const val POTENTIAL_MAX = 97.0
        private const val ATTRIBUTE_MIN = 15.0
        private const val ATTRIBUTE_MAX = 99.0
        private const val ATTRIBUTE_NOISE = 3.0
        private const val POWER_BIAS_SCALE = 10.0
        private const val MIN_CLOSURE = 0.05
        private const val MAX_CLOSURE = 0.7
        private const val FUTURES_AGE_MEAN = 22.0
        private const val FUTURES_AGE_SD = 2.4
        private const val BENCH_AGE_MEAN = 27.0
        private const val BENCH_AGE_SD = 4.0
        private const val STARTER_AGE_SD = 3.0
        private const val YOUTH_AGE = 24
        private const val PITCHER_SAME_HAND_SHARE = 0.8
        private const val LEFTY_THROWS_SHARE = 0.6
        private const val DH_FITNESS = 80
        private const val FA_SIGNED_SHARE = 0.55
        private const val LOW_SALARY_FACTOR = 0.7
        private const val CHEAP_VETERAN_FACTOR = 0.55
        private const val BAD_CONTRACT_STEP = 0.02
        private const val UNENLISTED_BIAS = 1.6
        private const val UNENLISTED_COMPLETED_BIAS = 0.5
        private const val FOREIGN_SALARY_BASE = 0.6
        private const val MILITARY_LOST_SEASONS = 2
    }
}
