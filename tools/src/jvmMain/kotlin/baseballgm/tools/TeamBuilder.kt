package baseballgm.tools

import baseballgm.league.StrengthCalculator
import baseballgm.league.TeamStrength
import baseballgm.model.Contract
import baseballgm.model.MilitaryStatus
import baseballgm.model.PitcherRole
import baseballgm.model.Player
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import baseballgm.util.chance
import baseballgm.util.nextGaussian
import baseballgm.util.nextGaussianInt
import baseballgm.util.nextInRange
import baseballgm.util.weightedPick
import kotlin.random.Random

/** 한 팀 생성 결과. */
data class BuiltTeam(
    val players: List<Player>,
    val strength: TeamStrength,
    val attempts: Int,
    /** 목표와의 최대 차이. 이 값이 허용 오차 안이어야 채택된다 */
    val maxDifference: Double,
)

/**
 * 한 팀의 선수단을 만든다.
 *
 * 목표 전력(`data/teams.json`)에 맞추는 방법은 "될 때까지 다시 뽑기"가 아니라 **되먹임**이다.
 * 한 번 만들어 전력을 재고, 목표와의 차이만큼 잠재력 평균을 옮겨 다시 만든다. 보통 몇 번 안에
 * 허용 오차(±2) 안으로 들어온다. 순수 재시도보다 훨씬 빨리 끝나고, 팀 성격(나이·계약)은 유지된다.
 */
class TeamBuilder(
    private val params: GenerationParams,
    private val strength: StrengthCalculator,
    private val generator: PlayerGenerator,
    private val names: NameGenerator,
) {

    fun build(template: TeamTemplate, seed: Long): BuiltTeam {
        val flavor = flavorOf(template)
        var offsets = Offsets(0.0, 0.0, 0.0)
        var best: BuiltTeam? = null

        var bestNames: NameBatch? = null

        for (attempt in 1..params.teamRetryLimit) {
            // 처음 몇 번은 **같은 난수열**로 다시 만든다. 그러면 바뀌는 것이 잠재력 이동값뿐이라
            // 목표와의 차이가 그대로 되먹임되어 몇 번 만에 수렴한다. 그래도 안 맞으면 난수열을 바꿔 본다.
            val random = if (attempt <= SMOOTH_ATTEMPTS) Random(seed) else Random(seed + attempt * ATTEMPT_STRIDE)
            names.startBatch()
            val players = buildRoster(template, flavor, offsets, random)
            val attemptNames = names.endBatch()
            val measured = strength.of(players)

            val lineupDiff = template.targets.lineup - measured.lineup
            val rotationDiff = template.targets.rotation - measured.rotation
            val bullpenDiff = template.targets.bullpen - measured.bullpen
            val overallDiff = template.targets.overall - measured.overall
            val maxDiff = maxOf(abs(lineupDiff), abs(rotationDiff), abs(bullpenDiff), abs(overallDiff))

            val candidate = BuiltTeam(players, measured, attempt, maxDiff)
            if (best == null || maxDiff < best.maxDifference) {
                bestNames?.let { names.release(it) } // 버린 선수단의 이름은 풀에 돌려준다
                bestNames = attemptNames
                best = candidate
            } else {
                names.release(attemptNames)
            }
            if (maxDiff <= strength.targetTolerance) return normalizeSalaries(candidate, template)

            // 전력 차이를 능력치 차이로 바꿔서 이동한다 (전력 스케일이 능력치보다 넓기 때문)
            offsets = Offsets(
                lineup = offsets.lineup + strength.ratingDeltaOf(lineupDiff) * DAMPING,
                rotation = offsets.rotation + strength.ratingDeltaOf(rotationDiff) * DAMPING,
                bullpen = offsets.bullpen + strength.ratingDeltaOf(bullpenDiff) * DAMPING,
            )
        }
        return normalizeSalaries(requireNotNull(best), template)
    }

    // ---------- 선수단 구성 ----------

    private fun buildRoster(
        template: TeamTemplate,
        flavor: TeamFlavor,
        offsets: Offsets,
        random: Random,
    ): List<Player> {
        val teamId = TeamId(template.id)
        val players = mutableListOf<Player>()

        val pitcherCount = random.nextInRange(params.firstTeamPitchers)
        val batterCount = params.firstTeamSize - pitcherCount
        val foreignPitchers = params.foreignMaxSameType
        val foreignBatters = params.foreignPerTeam - foreignPitchers

        // 타선: 포지션마다 주전 1명 + 나머지는 백업.
        // 외국인 타자는 포수·유격수 같은 수비 부담 큰 자리가 아니라 코너·지명타자에 둔다.
        val lineupPositions = Position.entries.toList()
        val foreignPositions = FOREIGN_BATTER_POSITIONS.take(foreignBatters).toSet()
        lineupPositions.forEach { position ->
            players += generator.generate(
                PlayerSpec(
                    role = GenerationRole.LINEUP_STARTER,
                    rosterLevel = RosterLevel.FIRST_TEAM,
                    position = position,
                    potentialShift = offsets.lineup,
                    foreign = position in foreignPositions,
                ),
                teamId, flavor, random,
            )
        }
        repeat(max(0, batterCount - lineupPositions.size)) {
            players += generator.generate(
                PlayerSpec(
                    role = GenerationRole.LINEUP_BENCH,
                    rosterLevel = RosterLevel.FIRST_TEAM,
                    position = Position.fielding.random(random),
                    potentialShift = offsets.lineup,
                ),
                teamId, flavor, random,
            )
        }

        // 마운드: 선발 5 + 마무리 1 + 필승조 + 추격조
        repeat(ROTATION_SIZE) { index ->
            players += generator.generate(
                PlayerSpec(
                    role = GenerationRole.ROTATION_STARTER,
                    rosterLevel = RosterLevel.FIRST_TEAM,
                    pitcherRole = PitcherRole.STARTER,
                    potentialShift = offsets.rotation,
                    foreign = index < foreignPitchers,
                ),
                teamId, flavor, random,
            )
        }
        val relieverCount = pitcherCount - ROTATION_SIZE
        repeat(relieverCount) { index ->
            val isCloser = index == 0
            val core = index < BULLPEN_CORE_SIZE
            players += generator.generate(
                PlayerSpec(
                    role = if (core) GenerationRole.BULLPEN_CORE else GenerationRole.BULLPEN_DEPTH,
                    rosterLevel = RosterLevel.FIRST_TEAM,
                    pitcherRole = if (isCloser) PitcherRole.CLOSER else PitcherRole.RELIEVER,
                    potentialShift = offsets.bullpen,
                ),
                teamId, flavor, random,
            )
        }

        // 2군: 팜 등급(prospectGrade)이 잠재력 수준을 정한다
        val futuresShift = prospectShift(template.targets.prospectGrade)
        val futuresPitchers = (params.futuresSize * params.futuresPitcherShare).roundToInt()
        repeat(params.futuresSize) { index ->
            val isPitcher = index < futuresPitchers
            val aGrade = index < flavor.aGradeProspects
            players += generator.generate(
                PlayerSpec(
                    role = GenerationRole.FUTURES,
                    rosterLevel = RosterLevel.FUTURES,
                    position = if (isPitcher) null else Position.fielding.random(random),
                    pitcherRole = if (isPitcher) {
                        if (random.chance(FUTURES_STARTER_SHARE)) PitcherRole.STARTER else PitcherRole.RELIEVER
                    } else {
                        null
                    },
                    potentialShift = futuresShift,
                    potentialOverride = if (aGrade) random.nextGaussian(A_GRADE_POTENTIAL, 2.0) else null,
                ),
                teamId, flavor, random,
            )
        }

        return applyKeyPitcherContracts(players, flavor)
    }

    /** 인천 시걸스처럼 "핵심 투수 N명이 FA까지 M년" 인 팀 성격을 계약에 반영한다. */
    private fun applyKeyPitcherContracts(players: List<Player>, flavor: TeamFlavor): List<Player> {
        val years = flavor.keyPitchersFaInYears ?: return players
        if (flavor.keyPitchersCount <= 0) return players
        val keyIds = players
            .filter { it is baseballgm.model.Pitcher && it.role == PitcherRole.STARTER }
            .sortedByDescending { strength.overallOf(it) }
            .take(flavor.keyPitchersCount)
            .map { it.id }
            .toSet()
        return players.map { player ->
            if (player.id !in keyIds) {
                player
            } else {
                player.withContract(
                    player.contract.copy(
                        seasonsToFreeAgency = years,
                        yearsRemaining = max(1, years),
                    ),
                )
            }
        }
    }

    // ---------- 연봉 총액 맞추기 ----------

    /**
     * 팀 연봉 총액을 `teams.json` 의 목표에 맞춘다.
     * 선수별 연봉의 **상대 크기**는 능력치·연차가 정하고, 팀 전체 규모만 비례 조정한다.
     * 최저 연봉 밑으로 내려가지 않게 두 번 나눠 맞춘다.
     */
    private fun normalizeSalaries(team: BuiltTeam, template: TeamTemplate): BuiltTeam {
        val target = template.payroll.toDouble()
        val counted = team.players.filter { it.military.isAvailable }
        val current = counted.sumOf { it.contract.salary }
        if (current <= 0.0) return team

        var factor = target / current
        var players = team.players.map { it.scaleSalary(factor, params.minimumSalary) }

        val afterFirst = players.filter { it.military.isAvailable }.sumOf { it.contract.salary }
        val fixed = players.filter { it.military.isAvailable && it.contract.salary <= params.minimumSalary + 1e-9 }
            .sumOf { it.contract.salary }
        val adjustable = afterFirst - fixed
        if (adjustable > 0.0) {
            factor = ((target - fixed) / adjustable).coerceAtLeast(0.1)
            players = players.map { player ->
                if (player.contract.salary <= params.minimumSalary + 1e-9) {
                    player
                } else {
                    player.scaleSalary(factor, params.minimumSalary)
                }
            }
        }
        return team.copy(players = players)
    }

    private fun Player.scaleSalary(factor: Double, minimum: Double): Player {
        val scaled = max(minimum, contract.salary * factor)
        return withContract(
            contract.copy(
                salary = (scaled * 100).roundToInt() / 100.0,
                signingBonusRemaining = (contract.signingBonusRemaining * factor * 100).roundToInt() / 100.0,
            ),
        )
    }

    private fun Player.withContract(contract: Contract): Player = when (this) {
        is baseballgm.model.Batter -> copy(contract = contract)
        is baseballgm.model.Pitcher -> copy(contract = contract)
    }

    // ---------- 팀 성격 ----------

    /**
     * `teams.json` 의 `generation` 힌트를 생성 파라미터로 옮긴다.
     *
     * `*TargetDelta` 계열 키(예: 인천의 `pitcherTargetDelta`)는 **쓰지 않는다.** 그 성격은 이미
     * `targets` 값 자체(인천 선발 85)에 반영돼 있어서, 또 더하면 두 번 적용된다.
     */
    private fun flavorOf(template: TeamTemplate): TeamFlavor {
        val hints = template.generationHints
        val starterAgeMid = (params.firstTeamStarterAge.first + params.firstTeamStarterAge.last) / 2.0
        val cheapVeterans = hints.stringOrNull("cheapVeteransAge35Plus") != null
        return TeamFlavor(
            starterAgeShift = (hints.doubleOrNull("starterAvgAgeMin")?.minus(starterAgeMid) ?: 0.0) +
                if (cheapVeterans) CHEAP_VETERAN_AGE_SHIFT else 0.0,
            earlyGrowthBias = hints.doubleOrNull("earlyGrowthBias") ?: 0.0,
            powerBias = hints.doubleOrNull("hitterPowerBias") ?: 0.0,
            under25Share = hints.doubleOrNull("under25Share") ?: 0.0,
            cheapVeterans = cheapVeterans,
            lowSalaryShare = hints.doubleOrNull("lowSalaryShare") ?: 0.0,
            longContracts = hints.booleanOr("longContractsHeavy", false),
            badContractHitters = hints.intOrNull("overpaidVeteranFaHitters") ?: 0,
            unenlistedProspects = hints.stringOrNull("unenlistedProspects") == "many",
            futuresPotentialShift = farmShift(hints.stringOrNull("farmPotential")) +
                depthShift(hints.stringOrNull("prospectDepth")) +
                qualityShift(hints.stringOrNull("prospectQuality")),
            aGradeProspects = hints.intOrNull("aGradeProspects") ?: 0,
            foreignRatingBonus = template.foreignScoutingBonus?.ratingBonus?.toDouble() ?: 0.0,
            keyPitchersFaInYears = hints.intOrNull("keyPitchersFaInYears"),
            keyPitchersCount = hints.intOrNull("keyPitchersCount") ?: 0,
            potentialShiftAll = if (hints.stringOrNull("potentialOverall") == "low") LOW_POTENTIAL_SHIFT else 0.0,
        )
    }

    private fun prospectShift(grade: String): Double = when (grade) {
        "A" -> 8.0
        "B" -> 3.0
        "C" -> 0.0
        else -> -5.0
    }

    private fun farmShift(value: String?): Double = when (value) {
        "high" -> 5.0
        "low" -> -5.0
        else -> 0.0
    }

    private fun depthShift(value: String?): Double = if (value == "thin") -4.0 else 0.0

    private fun qualityShift(value: String?): Double = when (value) {
        "upper-mid" -> 3.0
        "low" -> -3.0
        else -> 0.0
    }

    private data class Offsets(val lineup: Double, val rotation: Double, val bullpen: Double)

    companion object {
        private val FOREIGN_BATTER_POSITIONS =
            listOf(Position.DESIGNATED_HITTER, Position.FIRST_BASE, Position.RIGHT_FIELD)
        private const val ROTATION_SIZE = 5
        private const val BULLPEN_CORE_SIZE = 5
        private const val FUTURES_STARTER_SHARE = 0.45
        private const val A_GRADE_POTENTIAL = 88.0
        private const val DAMPING = 0.9
        private const val ATTEMPT_STRIDE = 1_000_003L
        private const val SMOOTH_ATTEMPTS = 25
        private const val CHEAP_VETERAN_AGE_SHIFT = 3.0
        private const val LOW_POTENTIAL_SHIFT = -4.0
    }
}
