package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.model.ParentCompany
import baseballgm.model.TeamId
import baseballgm.util.chance
import baseballgm.util.nextInRange
import baseballgm.util.weightedPick
import kotlin.random.Random

/** 모기업 경기 (docs/13). */
enum class BusinessCycle(val configKey: String, val label: String) {
    BOOM("boom", "호황"),
    NORMAL("normal", "보통"),
    SLUMP("slump", "불황"),
}

/** 한 시즌 모기업 상황. */
data class ParentCompanyState(
    val teamId: TeamId,
    val cycle: BusinessCycle,
    val supportMultiplier: Double,
    /** 불황이 몇 년 이어졌는가 */
    val consecutiveSlumps: Int,
    /** 시즌 중 예산 삭감 통보가 오는가 (docs/13) */
    val midSeasonCut: Boolean,
    val company: ParentCompany?,
    val changed: Boolean = false,
) {
    fun announcement(teamName: String): String = buildString {
        val name = company?.name ?: "모기업 없음"
        append("$teamName 모기업($name) ${cycle.label}")
        if (midSeasonCut) append(" — 시즌 중 예산 삭감 통보")
        if (changed) append(" — 모기업이 바뀌었다")
    }
}

/**
 * 모기업 이벤트 (docs/13).
 *
 * 매년 호황·보통·불황을 뽑아 지원금 배율을 정한다. **불황이 이어지면 시즌 중에 예산을 깎는다** —
 * 스토브리그에 짜 둔 계획이 시즌 중에 흔들리는 경험을 만들기 위한 장치다.
 *
 * 드물게 모기업이 바뀐다. 구단 별칭은 유지하고 투자 성향(재정 상태)만 새로 뽑는다 — 팬에게는
 * 같은 구단이지만 단장에게는 예산이 달라지는 사건이다.
 */
class ParentCompanyEvents(balance: BalanceConfig) {

    private val section = balance.section("parentCompany")
    private val cycleShare = BusinessCycle.entries.associateWith { section.double("cycleShare.${it.configKey}") }
    private val slumpCutAfter = section.int("slumpCutWarningAfter")
    private val midSeasonCutShare = section.double("midSeasonCutShare")
    private val ownerChangeChance = section.double("ownerChangeChance")
    private val newOwnerHealth = section.intRange("newOwnerHealth")

    val midSeasonCutRate: Double get() = midSeasonCutShare

    fun next(
        teamId: TeamId,
        company: ParentCompany?,
        previousSlumps: Int,
        random: Random,
    ): ParentCompanyState {
        val cycle = random.weightedPick(cycleShare)
        val slumps = if (cycle == BusinessCycle.SLUMP) previousSlumps + 1 else 0
        val changed = company != null && random.chance(ownerChangeChance)

        val updated = when {
            company == null -> null
            changed -> company.copy(
                name = company.name,
                financialHealth = random.nextInt(newOwnerHealth.first, newOwnerHealth.last + 1),
            )

            else -> company.copy(
                financialHealth = (company.financialHealth + section.int("healthDelta.${cycle.configKey}"))
                    .coerceIn(MIN_HEALTH, MAX_HEALTH),
            )
        }

        return ParentCompanyState(
            teamId = teamId,
            cycle = cycle,
            supportMultiplier = section.double("supportMultiplier.${cycle.configKey}"),
            consecutiveSlumps = slumps,
            midSeasonCut = slumps >= slumpCutAfter,
            company = updated,
            changed = changed,
        )
    }

    /** 시즌 중 삭감액. 운용 자금에서 이만큼 깎인다 */
    fun midSeasonCutAmount(funds: Double): Double = funds * midSeasonCutShare

    private companion object {
        const val MIN_HEALTH = 20
        const val MAX_HEALTH = 95
    }
}
