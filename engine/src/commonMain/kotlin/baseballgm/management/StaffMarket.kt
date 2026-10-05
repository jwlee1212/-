package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.model.Coach
import baseballgm.model.CoachRole
import baseballgm.model.Manager
import baseballgm.model.MedicalRole
import baseballgm.model.MedicalStaff
import baseballgm.model.StaffContract
import baseballgm.model.StaffId
import baseballgm.model.TeamId
import baseballgm.util.chance
import baseballgm.util.nextInRange
import kotlin.math.roundToInt
import kotlin.random.Random

/** 스태프 시장에 나온 사람 하나 (docs/13). */
sealed class StaffOffer {
    abstract val id: StaffId
    abstract val name: String
    abstract val grade: Int
    abstract val askingSalary: Double
    abstract val roleLabel: String

    data class CoachOffer(
        val coach: Coach,
        override val askingSalary: Double,
    ) : StaffOffer() {
        override val id: StaffId get() = coach.id
        override val name: String get() = coach.name
        override val grade: Int get() = coach.grade
        override val roleLabel: String
            get() = when (coach.role) {
                CoachRole.BATTING -> "타격코치"
                CoachRole.PITCHING -> "투수코치"
                CoachRole.FIELDING -> "수비주루코치"
                CoachRole.FUTURES_MANAGER -> "2군 감독"
            } + (coach.focus?.let { " (${it.attribute.label})" } ?: "")
    }

    data class MedicalOffer(
        val staff: MedicalStaff,
        override val askingSalary: Double,
    ) : StaffOffer() {
        override val id: StaffId get() = staff.id
        override val name: String get() = staff.name
        override val grade: Int get() = staff.grade
        override val roleLabel: String
            get() = when (staff.role) {
                MedicalRole.TEAM_DOCTOR -> "팀 닥터"
                MedicalRole.REHAB_TRAINER -> "재활 트레이너"
                MedicalRole.CONDITIONING_COACH -> "컨디셔닝 코치"
            }
    }

    data class ManagerOffer(
        val manager: Manager,
        override val askingSalary: Double,
    ) : StaffOffer() {
        override val id: StaffId get() = manager.id
        override val name: String get() = manager.name
        override val grade: Int get() = manager.reputation / GRADE_DIVISOR
        override val roleLabel: String get() = "감독"

        private companion object {
            const val GRADE_DIVISOR = 20
        }
    }
}

/** 스태프 고용 한 건. */
data class StaffHire(val teamId: TeamId, val staffId: StaffId, val salary: Double, val roleLabel: String, val name: String)

/** 스태프 시장 진행 상태. */
class StaffMarketState(offers: List<StaffOffer>) {
    private val available = offers.toMutableList()
    val hires: MutableList<StaffHire> = mutableListOf()

    fun offers(): List<StaffOffer> = available.sortedByDescending { it.grade }

    fun byId(id: StaffId): StaffOffer? = available.firstOrNull { it.id == id }

    internal fun take(id: StaffId): StaffOffer? = available.firstOrNull { it.id == id }?.also { available.remove(it) }
}

/**
 * 스태프 시장 (docs/13).
 *
 * 스토브리그마다 무직 코치·메디컬·감독이 시장에 나온다. **좋은 스태프는 AI 와 경쟁**하기 때문에
 * 등급이 높을수록 값이 붙고 먼저 채가는 일이 생긴다.
 *
 * 등급별 효과는 10~20% 로 작게 유지한다 (docs/13) — "무조건 최고 스태프를 산다"가 정답이 되면
 * 결정이 아니라 작업이 되기 때문이다. 대신 연봉이 운용 자금에서 나가므로 기회비용이 생긴다.
 */
class StaffMarket(balance: BalanceConfig) {

    private val section = balance.section("staffMarket")
    private val contractYears = section.intRange("contractYears")
    private val aiHireChance = section.double("aiHireChance")
    private val competitionPremium = section.doubleRange("competitionPremium")
    private val firingCostShare = section.double("firingCostShare")

    fun salaryFor(grade: Int): Double = section.double("salaryByGrade.${grade.coerceIn(1, 5)}")

    /** 남은 계약 연수만큼 위약금을 물어야 한다 (docs/13 경질 잔여 연봉). */
    fun firingCost(contract: StaffContract): Double =
        (contract.salary * contract.yearsRemaining * firingCostShare * MONEY_ROUND).roundToInt() / MONEY_ROUND

    /**
     * 시장을 연다.
     *
     * 리그에 이미 있는 **무직 스태프**가 후보다. 은퇴 선수가 코치로 전향해 쌓인 사람들이
     * 여기서 팀을 찾는다 (docs/09 → docs/13 연결).
     */
    fun open(league: League, random: Random): StaffMarketState {
        val coachLimit = section.int("poolSize.coach")
        val medicalLimit = section.int("poolSize.medical")
        val managerLimit = section.int("poolSize.manager")

        val coaches = league.coaches.filter { it.teamId == null }
            .sortedByDescending { it.grade }
            .take(coachLimit)
            .map { StaffOffer.CoachOffer(it, askingSalary(salaryFor(it.grade), random)) }
        val medical = league.medicalStaff.filter { it.teamId == null }
            .sortedByDescending { it.grade }
            .take(medicalLimit)
            .map { StaffOffer.MedicalOffer(it, askingSalary(salaryFor(it.grade), random)) }
        val managers = league.unemployedManagers()
            .sortedByDescending { it.reputation }
            .take(managerLimit)
            .map { StaffOffer.ManagerOffer(it, askingSalary(it.contract.salary, random)) }

        return StaffMarketState(coaches + medical + managers)
    }

    /** 경쟁이 붙으면 값이 오른다. */
    private fun askingSalary(base: Double, random: Random): Double =
        (base * random.nextInRange(competitionPremium) * MONEY_ROUND).roundToInt() / MONEY_ROUND

    /** 계약을 맺는다. 자금이 모자라면 null. */
    fun hire(state: StaffMarketState, league: League, teamId: TeamId, staffId: StaffId, funds: Double): StaffHire? {
        val offer = state.byId(staffId) ?: return null
        if (offer.askingSalary > funds) return null
        state.take(staffId) ?: return null
        val hire = StaffHire(teamId, staffId, offer.askingSalary, offer.roleLabel, offer.name)
        state.hires += hire
        return hire
    }

    /**
     * AI 구단의 고용.
     *
     * 자리가 빈 구단이 등급 높은 순으로 데려간다. 유저가 고민하는 동안 좋은 코치가 사라지는
     * 이유이기도 하다.
     */
    fun runAiHiring(
        state: StaffMarketState,
        league: League,
        funds: Map<TeamId, Double>,
        userTeam: TeamId?,
        random: Random,
    ): List<StaffHire> {
        val hires = mutableListOf<StaffHire>()
        league.teams.filter { it.id != userTeam }.forEach { team ->
            if (!random.chance(aiHireChance)) return@forEach
            val budget = funds[team.id] ?: team.operatingFunds
            val needsCoach = league.coachesOf(team.id).size < COACH_SLOTS
            val needsMedical = league.medicalStaffOf(team.id).size < MEDICAL_SLOTS
            val needsManager = league.managerOf(team.id) == null

            val wanted = state.offers().firstOrNull { offer ->
                offer.askingSalary <= budget && when (offer) {
                    is StaffOffer.CoachOffer -> needsCoach
                    is StaffOffer.MedicalOffer -> needsMedical
                    is StaffOffer.ManagerOffer -> needsManager
                }
            } ?: return@forEach
            hire(state, league, team.id, wanted.id, budget)?.let { hires += it }
        }
        return hires
    }

    /** 계약 연수는 시장에서 뽑는다. */
    fun contractFor(salary: Double, random: Random): StaffContract =
        StaffContract(salary = salary, yearsRemaining = random.nextInRange(contractYears))

    private companion object {
        const val MONEY_ROUND = 100.0
        /** 코치는 타격·투수·수비주루·2군 감독 네 자리 */
        const val COACH_SLOTS = 4
        const val MEDICAL_SLOTS = 3
    }
}
