package baseballgm.tactics

import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.Hand
import baseballgm.model.Manager
import baseballgm.model.ManagerTendencies
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRole
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import kotlin.math.roundToInt

/**
 * 감독 AI (docs/06).
 *
 * 감독의 **성향 7개**(1~100)를 규칙표 수치로 옮긴다. 성향 → 규칙표 변환표는 전부
 * `balance.json` 의 `managerAI` 에 있어서, 이 파일에는 "어떤 성향이 어떤 항목을 정하는가" 만 있다.
 *
 * 단장 전용 모드에서 유저가 내리는 방침은 성향을 목표값까지 바로 옮기지 않고
 * **감독 성향 쪽으로 일부만** 움직인다 (`managerDirectiveAdoption`). 감독이 캐릭터인 이유다.
 */
class ManagerAI(
    private val balance: BalanceConfig,
    private val strength: StrengthCalculator,
) {
    private val ai = balance.section("managerAI")
    private val adoption = balance.double("managerDirectiveAdoption.value")

    /** 감독이 없으면(감독 겸직 모드) 정석형 성향으로 만든다. */
    fun buildSheet(manager: Manager?, roster: List<Player>, season: Int): DirectiveSheet =
        buildSheet(manager?.tendencies ?: DirectivePreset.STANDARD.tendencies(), roster, season)

    fun buildSheet(tendencies: ManagerTendencies, roster: List<Player>, season: Int): DirectiveSheet {
        val available = roster.filter {
            it.rosterLevel == RosterLevel.FIRST_TEAM && it.military.isAvailable && !it.condition.isInjured
        }
        val restThreshold = lerp("restFatigueThreshold", 100 - tendencies.bullpenAggression).roundToInt()
        // 휴식 규칙(docs/06 ①): 피로도가 기준을 넘은 야수는 라인업에서 뺀다.
        // 단 9명을 못 채우면 덜 지친 순서로 채운다 — 경기는 어떻게든 해야 한다.
        val allBatters = available.filterIsInstance<Batter>()
        val rested = allBatters.filter { it.condition.fatigue < restThreshold }
        val batters = if (rested.size >= LineupPlan.SIZE) {
            rested
        } else {
            fillUp(allBatters.sortedBy { it.condition.fatigue }, roster, season)
        }
        val pitchers = available.filterIsInstance<Pitcher>().ifEmpty { emergencyPitchers(roster) }
        require(batters.size >= LineupPlan.SIZE) { "야수가 ${batters.size}명뿐이라 라인업을 짤 수 없다" }
        require(pitchers.isNotEmpty()) { "던질 투수가 한 명도 없다" }

        val rotation = pitchers.filter { it.role == PitcherRole.STARTER }
            .sortedByDescending { strength.overallOf(it) }
            .ifEmpty { pitchers.sortedByDescending { strength.overallOf(it) }.take(1) }
        val relievers = pitchers.filter { it.id !in rotation.take(ROTATION_SIZE).map { s -> s.id }.toSet() }

        return DirectiveSheet(
            lineupVsLeft = buildLineup(batters, Hand.LEFT, tendencies, season),
            lineupVsRight = buildLineup(batters, Hand.RIGHT, tendencies, season),
            rest = RestRule(
                fatigueThreshold = restThreshold,
                catcherRestEveryDays = ai.int("catcherRestEveryDays"),
            ),
            rotation = RotationPlan(
                starters = rotation.take(ROTATION_SIZE).map { it.id },
                minimumRestDays = ai.int("minimumRestDays"),
                substituteStarter = rotation.getOrNull(ROTATION_SIZE)?.id
                    ?: relievers.maxByOrNull { it.ratings.stamina }?.id,
            ),
            starterHook = StarterHookRule(
                pitchLimit = lerp("starterPitchLimit", tendencies.starterPatience).roundToInt(),
                runsAllowedLimit = lerp("starterRunsLimit", tendencies.starterPatience).roundToInt(),
                pullOnThirdTimeThroughOrder =
                tendencies.starterPatience < ai.int("thirdTimeThroughPatienceBelow"),
                fatigueLimit = lerp("starterFatigueLimit", tendencies.starterPatience).roundToInt(),
            ),
            bullpen = buildBullpen(relievers, tendencies),
            substitution = SubstitutionRules(
                pinchHitFromInning = lerp("pinchHitFromInning", tendencies.prospectUsage).roundToInt(),
                pinchHitMaxDeficit = lerp("pinchHitMaxDeficit", tendencies.prospectUsage).roundToInt(),
                pinchHitRatingGap = lerp("pinchHitRatingGap", 100 - tendencies.veteranTrust).roundToInt(),
                pinchHitOnlyInScoringPosition = tendencies.buntPreference > SCORING_POSITION_ONLY_ABOVE,
                pinchHitPlatoonOnly = tendencies.platoonUsage > PLATOON_ONLY_ABOVE,
                pinchRunFromInning = lerp("pinchRunFromInning", tendencies.stealAggression).roundToInt(),
                pinchRunMaxMargin = PINCH_RUN_MAX_MARGIN,
                pinchRunSpeedGap = PINCH_RUN_SPEED_GAP,
                defensiveSubFromInning = lerp("defensiveSubFromInning", tendencies.platoonUsage).roundToInt(),
                defensiveSubMinLead = lerp("defensiveSubMinLead", 100 - tendencies.platoonUsage).roundToInt(),
                defensiveSubDefenseGap = lerp("defensiveSubDefenseGap", tendencies.platoonUsage).roundToInt(),
            ),
            tactics = TacticSliders(
                bunt = toSlider(tendencies.buntPreference),
                steal = toSlider(tendencies.stealAggression),
                intentionalWalk = toSlider((tendencies.buntPreference + tendencies.starterPatience) / 2),
            ),
        )
    }

    /**
     * 부상이 겹쳐 1군 야수가 9명이 안 될 때의 비상 라인업.
     *
     * 실제 구단이라면 그날로 2군에서 올린다. 여기서도 같은 순서로 자원을 넓힌다:
     * 멀쩡한 1군 → 2군에서 뛸 수 있는 선수 → 그래도 모자라면 부상자까지. **경기는 멈추지 않는다.**
     */
    private fun fillUp(firstTeam: List<Batter>, roster: List<Player>, season: Int): List<Batter> {
        if (firstTeam.size >= LineupPlan.SIZE) return firstTeam
        val used = firstTeam.map { it.id }.toMutableSet()
        val result = firstTeam.toMutableList()

        roster.filterIsInstance<Batter>()
            .filter { it.id !in used && it.military.isAvailable && !it.condition.isInjured }
            .sortedByDescending { strength.overallOf(it) }
            .forEach { if (result.size < LineupPlan.SIZE) { result += it; used += it.id } }

        roster.filterIsInstance<Batter>()
            .filter { it.id !in used && it.military.isAvailable }
            .sortedBy { it.condition.injury?.weeksRemaining ?: 0 }
            .forEach { if (result.size < LineupPlan.SIZE) { result += it; used += it.id } }

        return result
    }

    private fun emergencyPitchers(roster: List<Player>): List<Pitcher> =
        roster.filterIsInstance<Pitcher>()
            .filter { it.military.isAvailable }
            .sortedBy { it.condition.injury?.weeksRemaining ?: 0 }
            .take(EMERGENCY_PITCHERS)

    /**
     * 단장 방침을 감독 성향에 섞는다 (docs/06 방침 반영률).
     * 목표값으로 바로 가지 않고 반영률만큼만 움직인다 — 감독은 지시를 그대로 따르는 기계가 아니다.
     */
    fun blendTendencies(
        current: ManagerTendencies,
        requested: ManagerTendencies,
        adoptionRate: Double = adoption,
    ): ManagerTendencies = ManagerTendencies(
        starterPatience = blend(current.starterPatience, requested.starterPatience, adoptionRate),
        bullpenAggression = blend(current.bullpenAggression, requested.bullpenAggression, adoptionRate),
        buntPreference = blend(current.buntPreference, requested.buntPreference, adoptionRate),
        stealAggression = blend(current.stealAggression, requested.stealAggression, adoptionRate),
        platoonUsage = blend(current.platoonUsage, requested.platoonUsage, adoptionRate),
        prospectUsage = blend(current.prospectUsage, requested.prospectUsage, adoptionRate),
        veteranTrust = blend(current.veteranTrust, requested.veteranTrust, adoptionRate),
    )

    /**
     * 주간 방침 적용 (docs/06).
     * 총력전은 투구수·연투 제한을 풀고 필승조 범위를 넓히며, 선수 보호는 반대로 조인다.
     */
    fun applyPolicy(sheet: DirectiveSheet, policy: WeeklyPolicy): DirectiveSheet {
        val section = balance.section("weeklyPolicy.${policy.configKey}")
        val pitchLimitFactor = section.double("pitchLimit")
        val consecutiveDelta = section.int("consecutiveDays")
        val restDelta = section.int("restThreshold")
        val extraHighLeverage = section.int("highLeverageExtra")

        val roles = sheet.bullpen.roles.toMutableMap()
        if (extraHighLeverage > 0) {
            // 총력전: 추격조 앞쪽 투수를 필승조로 끌어올린다
            val promoted = roles[BullpenRole.MOP_UP].orEmpty().take(extraHighLeverage)
            if (promoted.isNotEmpty()) {
                roles[BullpenRole.HIGH_LEVERAGE] = roles[BullpenRole.HIGH_LEVERAGE].orEmpty() + promoted
                roles[BullpenRole.MOP_UP] = roles[BullpenRole.MOP_UP].orEmpty().drop(extraHighLeverage)
            }
        }

        return sheet.copy(
            rest = sheet.rest.copy(
                fatigueThreshold = (sheet.rest.fatigueThreshold + restDelta).coerceIn(30, 100),
            ),
            starterHook = sheet.starterHook.copy(
                pitchLimit = (sheet.starterHook.pitchLimit * pitchLimitFactor).roundToInt(),
                fatigueLimit = (sheet.starterHook.fatigueLimit + restDelta).coerceIn(30, 100),
            ),
            bullpen = sheet.bullpen.copy(
                roles = roles,
                maxConsecutiveDays = (sheet.bullpen.maxConsecutiveDays + consecutiveDelta).coerceAtLeast(1),
            ),
        )
    }

    // ---------- 라인업 ----------

    /**
     * 상대 선발의 손에 맞춘 라인업.
     *
     * 플래툰 활용 성향이 높을수록 반대 손 타자에게 큰 가산점을 줘서 두 라인업의 차이가 커진다.
     * 유망주 기용·베테랑 신뢰 성향도 나이로 가산점을 준다.
     */
    private fun buildLineup(
        batters: List<Batter>,
        opposingHand: Hand,
        tendencies: ManagerTendencies,
        season: Int,
    ): LineupPlan {
        val platoonBonus = lerp("platoonLineupBonus", tendencies.platoonUsage)
        val youthBonus = lerp("youthLineupBonus", tendencies.prospectUsage)
        val veteranBonus = lerp("veteranLineupBonus", tendencies.veteranTrust)

        fun bias(batter: Batter): Double {
            val effectiveHand = if (batter.bats == Hand.SWITCH) opposingHand.opposite() else batter.bats
            val platoon = if (effectiveHand != opposingHand) platoonBonus else 0.0
            val age = batter.ageIn(season)
            val youth = if (age <= YOUNG_AGE) youthBonus else 0.0
            val veteran = if (age >= VETERAN_AGE) veteranBonus else 0.0
            return platoon + youth + veteran
        }

        val taken = mutableSetOf<PlayerId>()
        val assigned = linkedMapOf<Position, Batter>()
        Position.entries.sortedByDescending { it.defenseDifficulty }.forEach { position ->
            val pick = batters.filter { it.id !in taken }.maxByOrNull { score(it, position) + bias(it) }
            if (pick != null) {
                taken += pick.id
                assigned[position] = pick
            }
        }

        val order = battingOrder(assigned.entries.map { it.value })
        return LineupPlan(order.map { batter -> LineupSlot(batter.id, assigned.entries.first { it.value.id == batter.id }.key) })
    }

    private fun score(batter: Batter, position: Position): Double {
        if (position == Position.DESIGNATED_HITTER) return strength.overallOf(batter)
        val fitness = batter.defenseFitness[position]?.toDouble() ?: 1.0
        return fitness * FITNESS_WEIGHT + strength.overallOf(batter) * OVERALL_WEIGHT
    }

    /** 1번 출루·주루, 2번 컨택, 3~5번 장타, 나머지는 종합 순. */
    private fun battingOrder(starters: List<Batter>): List<Batter> {
        val remaining = starters.toMutableList()
        val order = mutableListOf<Batter>()
        fun take(selector: (Batter) -> Double) {
            val pick = remaining.maxByOrNull(selector) ?: return
            remaining -= pick
            order += pick
        }
        take { it.ratings.eye * 1.0 + it.ratings.speed * 0.8 + it.ratings.contact * 0.6 }
        take { it.ratings.contact * 1.0 + it.ratings.eye * 0.4 }
        take { strength.overallOf(it) + it.ratings.power * 0.2 }
        take { it.ratings.power * 1.0 + it.ratings.contact * 0.3 }
        take { it.ratings.power * 1.0 + it.ratings.contact * 0.2 }
        while (remaining.isNotEmpty()) take { strength.overallOf(it) }
        return order
    }

    // ---------- 불펜 ----------

    private fun buildBullpen(relievers: List<Pitcher>, tendencies: ManagerTendencies): BullpenPlan {
        val byOverall = relievers.sortedByDescending { strength.overallOf(it) }
        val roles = mutableMapOf<BullpenRole, List<PlayerId>>()
        val assigned = mutableSetOf<PlayerId>()

        fun claim(count: Int, selector: (List<Pitcher>) -> List<Pitcher>): List<PlayerId> {
            val picks = selector(byOverall.filter { it.id !in assigned }).take(count)
            assigned += picks.map { it.id }
            return picks.map { it.id }
        }

        // 마무리는 보직이 마무리인 투수, 없으면 가장 좋은 투수
        roles[BullpenRole.CLOSER] = claim(1) { pool ->
            pool.filter { it.role == PitcherRole.CLOSER }.ifEmpty { pool }
        }
        roles[BullpenRole.SETUP] = claim(1) { it }
        val highLeverageCount = lerp("highLeverageCount", tendencies.bullpenAggression).roundToInt()
        roles[BullpenRole.HIGH_LEVERAGE] = claim(highLeverageCount) { it }
        roles[BullpenRole.LEFTY_SPECIALIST] = claim(1) { pool -> pool.filter { it.throwsWith == Hand.LEFT } }
        roles[BullpenRole.LONG_RELIEF] = claim(1) { pool -> pool.sortedByDescending { it.ratings.stamina } }
        roles[BullpenRole.MOP_UP] = byOverall.filter { it.id !in assigned }.map { it.id }

        return BullpenPlan(
            roles = roles,
            maxConsecutiveDays = lerp("bullpenMaxConsecutiveDays", tendencies.bullpenAggression).roundToInt(),
            maxPitchesLast7Days = lerp("bullpenMaxPitches7Days", tendencies.bullpenAggression).roundToInt(),
        )
    }

    // ---------- 부속 ----------

    /** 성향 1~100 을 설정의 `[성향 1일 때, 성향 100일 때]` 사이 값으로 옮긴다. */
    private fun lerp(key: String, tendency: Int): Double {
        val values = ai.doubleList(key)
        val ratio = ((tendency - 1).coerceIn(0, 99)) / 99.0
        return values[0] + (values[1] - values[0]) * ratio
    }

    private fun toSlider(tendency: Int): Int =
        ((tendency - 1) / 20 + 1).coerceIn(TacticSliders.RANGE.first, TacticSliders.RANGE.last)

    private fun blend(current: Int, requested: Int, rate: Double): Int =
        (current + (requested - current) * rate).roundToInt().coerceIn(1, 100)

    private fun Hand.opposite(): Hand = if (this == Hand.LEFT) Hand.RIGHT else Hand.LEFT

    private companion object {
        const val ROTATION_SIZE = 5
        const val FITNESS_WEIGHT = 0.6
        const val OVERALL_WEIGHT = 0.4
        const val YOUNG_AGE = 24
        const val VETERAN_AGE = 33
        const val SCORING_POSITION_ONLY_ABOVE = 60
        const val PLATOON_ONLY_ABOVE = 65
        const val PINCH_RUN_MAX_MARGIN = 2
        const val PINCH_RUN_SPEED_GAP = 15
        const val EMERGENCY_PITCHERS = 6
    }
}
