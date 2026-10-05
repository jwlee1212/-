package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.model.Coach
import baseballgm.model.CoachFocus
import baseballgm.model.CoachRole
import baseballgm.model.GeneralManager
import baseballgm.model.Manager
import baseballgm.model.ManagerSpecialty
import baseballgm.model.ManagerTendencies
import baseballgm.model.MedicalRole
import baseballgm.model.MedicalStaff
import baseballgm.model.StaffContract
import baseballgm.model.StaffId
import baseballgm.model.TeamId
import kotlin.math.roundToInt
import baseballgm.util.chance
import baseballgm.util.nextGaussian
import baseballgm.util.nextGaussianInt
import baseballgm.util.nextInRange
import baseballgm.util.weightedPick
import kotlin.random.Random

/** 감독·코치·메디컬·AI 단장 묶음. */
data class GeneratedStaff(
    val managers: List<Manager>,
    val coaches: List<Coach>,
    val medicalStaff: List<MedicalStaff>,
    val generalManagers: List<GeneralManager>,
)

/**
 * 스태프 생성 (docs/06, 09, 13).
 *
 * 구단마다 감독 1명 + 코치 4명(타격·투수·수비주루·2군 감독) + 메디컬 3명(팀 닥터·재활·컨디셔닝) +
 * AI 단장 1명. 여기에 무직 감독 후보를 더해 스태프 시장을 만든다.
 */
class StaffGenerator(
    balance: BalanceConfig,
    private val names: NameGenerator,
    private val season: Int,
) {
    private val staff = balance.section("staffGeneration")
    private val managerAge = staff.intRange("managerAge")
    private val tendency = Pair2.of(staff, "managerTendency")
    private val managerSalary = staff.doubleRange("managerSalary")
    private val managerContractYears = staff.intRange("managerContractYears")
    private val coachGrade = Pair2.of(staff, "coachGrade")
    private val medicalGrade = Pair2.of(staff, "medicalGrade")
    private val gmReputation = Pair2.of(staff, "gmReputation")
    private val unemployedManagerCount = staff.int("managerPoolUnemployed")

    fun generate(teamIds: List<TeamId>, random: Random): GeneratedStaff {
        val managers = mutableListOf<Manager>()
        val coaches = mutableListOf<Coach>()
        val medical = mutableListOf<MedicalStaff>()
        val generalManagers = mutableListOf<GeneralManager>()

        teamIds.forEachIndexed { index, teamId ->
            managers += manager(StaffId("M" + (index + 1).pad()), teamId, random)
            CoachRole.entries.forEachIndexed { roleIndex, role ->
                coaches += coach(StaffId("C" + (index * CoachRole.entries.size + roleIndex + 1).pad()), role, teamId, random)
            }
            MedicalRole.entries.forEachIndexed { roleIndex, role ->
                medical += medicalStaff(
                    StaffId("D" + (index * MedicalRole.entries.size + roleIndex + 1).pad()),
                    role,
                    teamId,
                    random,
                )
            }
            generalManagers += generalManager(StaffId("G" + (index + 1).pad()), teamId, random)
        }

        // 무직 감독 후보 (스태프 시장)
        repeat(unemployedManagerCount) { index ->
            managers += manager(StaffId("M" + (teamIds.size + index + 1).pad()), teamId = null, random = random)
        }

        return GeneratedStaff(managers, coaches, medical, generalManagers)
    }

    private fun manager(id: StaffId, teamId: TeamId?, random: Random): Manager {
        val age = random.nextInRange(managerAge)
        val salary = random.nextInRange(managerSalary)
        return Manager(
            id = id,
            name = names.staff(),
            birthYear = season - age,
            playingBackground = PLAYING_BACKGROUNDS.random(random),
            tendencies = ManagerTendencies(
                starterPatience = tendencyValue(random),
                bullpenAggression = tendencyValue(random),
                buntPreference = tendencyValue(random),
                stealAggression = tendencyValue(random),
                platoonUsage = tendencyValue(random),
                prospectUsage = tendencyValue(random),
                veteranTrust = tendencyValue(random),
            ),
            specialty = ManagerSpecialty.entries.random(random),
            reputation = random.nextGaussianInt(REPUTATION_MEAN, REPUTATION_SD, 20..95),
            contract = StaffContract(
                salary = salary.round2(),
                yearsRemaining = if (teamId == null) 0 else random.nextInRange(managerContractYears),
            ),
            teamId = teamId,
        )
    }

    private fun coach(id: StaffId, role: CoachRole, teamId: TeamId, random: Random): Coach {
        val grade = random.nextGaussianInt(coachGrade.first, coachGrade.second, 1..5)
        return Coach(
            id = id,
            name = names.staff(),
            birthYear = season - random.nextInRange(COACH_AGE),
            role = role,
            focus = focusFor(role, random),
            grade = grade,
            contract = StaffContract(
                salary = staff.double("coachSalary.$grade"),
                yearsRemaining = random.nextInt(1, 4),
            ),
            teamId = teamId,
        )
    }

    /** 2군 감독은 성향 없이 25세 이하 전체에 보너스를 준다 (docs/09). */
    private fun focusFor(role: CoachRole, random: Random): CoachFocus? = when (role) {
        CoachRole.BATTING -> listOf(CoachFocus.POWER, CoachFocus.CONTACT, CoachFocus.EYE).random(random)
        CoachRole.PITCHING -> listOf(CoachFocus.STUFF, CoachFocus.CONTROL, CoachFocus.GROUNDBALL).random(random)
        CoachRole.FIELDING -> listOf(CoachFocus.DEFENSE, CoachFocus.SPEED).random(random)
        CoachRole.FUTURES_MANAGER -> null
    }

    private fun medicalStaff(id: StaffId, role: MedicalRole, teamId: TeamId, random: Random): MedicalStaff {
        val grade = random.nextGaussianInt(medicalGrade.first, medicalGrade.second, 1..5)
        return MedicalStaff(
            id = id,
            name = names.staff(),
            birthYear = season - random.nextInRange(MEDICAL_AGE),
            role = role,
            grade = grade,
            // 메디컬 연봉표는 따로 두지 않고 코치 연봉표를 쓴다 (임시 결정, M9 에서 분리)
            contract = StaffContract(
                salary = staff.double("coachSalary.$grade"),
                yearsRemaining = random.nextInt(1, 4),
            ),
            teamId = teamId,
        )
    }

    private fun generalManager(id: StaffId, teamId: TeamId, random: Random): GeneralManager = GeneralManager(
        id = id,
        name = names.staff(),
        birthYear = season - random.nextInRange(GM_AGE),
        reputation = random.nextGaussianInt(gmReputation.first, gmReputation.second, 15..95),
        tradeAggression = random.nextGaussianInt(TENDENCY_MEAN, TENDENCY_SD, 5..95),
        teamId = teamId,
        isHuman = false,
    )

    private fun tendencyValue(random: Random): Int =
        random.nextGaussianInt(tendency.first, tendency.second, 5..95)

    private fun Int.pad(): String = toString().padStart(3, '0')

    private fun Double.round2(): Double = (this * 100).roundToInt() / 100.0

    companion object {
        private val COACH_AGE = 42..62
        private val MEDICAL_AGE = 35..60
        private val GM_AGE = 40..62
        private const val REPUTATION_MEAN = 55.0
        private const val REPUTATION_SD = 15.0
        private const val TENDENCY_MEAN = 50.0
        private const val TENDENCY_SD = 15.0
        private val PLAYING_BACKGROUNDS = listOf(
            "포수 출신", "내야수 출신", "외야수 출신", "선발 투수 출신", "불펜 투수 출신", "지도자 출신",
        )
    }
}
