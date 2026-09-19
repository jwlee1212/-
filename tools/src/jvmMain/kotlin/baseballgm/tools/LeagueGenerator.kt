package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.ScheduleRules
import baseballgm.league.StrengthCalculator
import baseballgm.league.TeamStrength
import baseballgm.model.ParentCompany
import baseballgm.model.Player
import baseballgm.model.Team
import baseballgm.model.TeamId
import kotlin.math.abs
import baseballgm.util.chance
import baseballgm.util.nextGaussian
import baseballgm.util.nextGaussianInt
import baseballgm.util.nextInRange
import baseballgm.util.weightedPick
import kotlin.random.Random

/** 팀 하나의 생성 결과 요약. 콘솔 출력과 테스트가 본다. */
data class TeamReport(
    val teamId: String,
    val name: String,
    val targetLineup: Int,
    val targetRotation: Int,
    val targetBullpen: Int,
    val targetOverall: Int,
    val strength: TeamStrength,
    val attempts: Int,
    val maxDifference: Double,
    val payrollTarget: Double,
    val payroll: Double,
    val playerCount: Int,
)

/** 리그 생성 결과. */
data class GeneratedLeague(val league: League, val reports: List<TeamReport>) {
    /** 목표 허용 오차를 넘은 팀. 비어 있어야 정상이다. */
    fun teamsOffTarget(tolerance: Double): List<TeamReport> = reports.filter { it.maxDifference > tolerance }
}

/**
 * 고정 리그 생성기 (docs/03).
 *
 * 시드 하나를 받아 리그 전체(선수·스태프·일정)를 만든다. **같은 시드면 항상 같은 리그**가 나온다.
 * 이를 위해 난수는 딱 한 군데, 시드로 만든 [Random] 에서만 나오고, 팀별·부문별로 하위 시드를
 * 미리 뽑아 쓴다 (불변 원칙 2).
 */
class LeagueGenerator(
    private val balance: BalanceConfig,
    private val templates: TeamTemplates,
    private val seed: Long,
) {
    private val season = templates.season

    fun generate(): GeneratedLeague {
        val root = Random(seed)
        val teamSeeds = templates.teams.map { root.nextLong() }
        val staffSeed = root.nextLong()
        val scheduleSeed = root.nextLong()

        val params = GenerationParams(balance)
        val strength = StrengthCalculator(balance)
        val names = NameGenerator(Random(root.nextLong()))
        val playerGenerator = PlayerGenerator(params, strength, names, season)
        val builder = TeamBuilder(params, strength, playerGenerator, names)

        val players = mutableListOf<Player>()
        val reports = mutableListOf<TeamReport>()

        templates.teams.forEachIndexed { index, template ->
            val built = builder.build(template, teamSeeds[index])
            players += built.players
            reports += TeamReport(
                teamId = template.id,
                name = template.name,
                targetLineup = template.targets.lineup,
                targetRotation = template.targets.rotation,
                targetBullpen = template.targets.bullpen,
                targetOverall = template.targets.overall,
                strength = built.strength.rounded(),
                attempts = built.attempts,
                maxDifference = built.maxDifference,
                payrollTarget = template.payroll.toDouble(),
                payroll = built.players.filter { it.military.isAvailable }.sumOf { it.contract.salary },
                playerCount = built.players.size,
            )
        }

        val teamIds = templates.teams.map { TeamId(it.id) }
        val staff = StaffGenerator(balance, names, season).generate(teamIds, Random(staffSeed))
        val schedule = ScheduleGenerator(ScheduleRules.from(balance)).generate(season, teamIds, Random(scheduleSeed))

        val initial = balance.section("initialTeamState")
        val healthParams = Pair2.of(initial, "parentCompanyHealth")
        val supportFactor = initial.double("parentCompanySupportFromFunds")
        val companyRandom = Random(seed)

        val teams = templates.teams.map { template ->
            val teamId = TeamId(template.id)
            Team(
                id = teamId,
                name = template.name,
                city = template.city,
                nickname = template.nickname,
                parentCompany = template.parentCompany?.let { name ->
                    ParentCompany(
                        name = name,
                        annualSupport = template.operatingFunds * supportFactor,
                        financialHealth = companyRandom.nextGaussianInt(healthParams.first, healthParams.second, 20..95),
                    )
                },
                tier = template.tier,
                keyword = template.keyword,
                marketSize = template.marketSize,
                parkFactor = template.parkFactor,
                fanVolatility = template.fanVolatility,
                ownerGoal = template.ownerGoal,
                operatingFunds = template.operatingFunds.toDouble(),
                draftPick = template.draftPick,
                fanSupport = initial.int("fanSupport"),
                ownerTrust = initial.int("ownerTrust"),
                managerId = staff.managers.firstOrNull { it.teamId == teamId }?.id,
                coachIds = staff.coaches.filter { it.teamId == teamId }.map { it.id },
                medicalStaffIds = staff.medicalStaff.filter { it.teamId == teamId }.map { it.id },
                generalManagerId = staff.generalManagers.firstOrNull { it.teamId == teamId }?.id,
            )
        }

        val league = League(
            season = season,
            seed = seed,
            salaryCap = templates.salaryCap.toDouble(),
            teams = teams,
            players = players,
            managers = staff.managers,
            coaches = staff.coaches,
            medicalStaff = staff.medicalStaff,
            generalManagers = staff.generalManagers,
            schedule = schedule,
        )
        return GeneratedLeague(league, reports)
    }

    companion object {
        /** `data/league_2026.json` 을 만들 때 쓴 시드. 바꾸면 리그가 통째로 달라진다. */
        const val DEFAULT_SEED: Long = 20260301L
    }
}

/** 생성 결과를 사람이 읽을 수 있게 표로 만든다. 콘솔 출력용이라 엔진에는 두지 않는다. */
fun GeneratedLeague.report(tolerance: Double): String = buildString {
    appendLine("ID  구단명        전력(목표)          타선        선발        불펜      인원  연봉(목표)  시도")
    reports.forEach { r ->
        appendLine(
            buildString {
                append(r.teamId.padEnd(4))
                append(r.name.padEnd(14))
                append(fmt(r.strength.overall, r.targetOverall))
                append(fmt(r.strength.lineup, r.targetLineup))
                append(fmt(r.strength.rotation, r.targetRotation))
                append(fmt(r.strength.bullpen, r.targetBullpen))
                append(r.playerCount.toString().padStart(5))
                append("  ${"%.0f".format(r.payroll)}(${"%.0f".format(r.payrollTarget)})".padStart(11))
                append(r.attempts.toString().padStart(5))
            },
        )
    }
    val off = teamsOffTarget(tolerance)
    appendLine(if (off.isEmpty()) "모든 팀이 목표 ±$tolerance 안에 들어왔다" else "목표를 벗어난 팀: ${off.map { it.teamId }}")
}

private fun fmt(value: Double, target: Int): String {
    val diff = value - target
    val mark = if (abs(diff) <= 2.0) " " else "!"
    return "%5.1f(%2d)%s".format(value, target, mark).padEnd(12)
}
