package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.TeamRecord
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.season.PostseasonResult
import kotlin.math.max
import kotlin.math.roundToInt

/** 한 시즌 연봉 칸이 어떤 근거의 숫자인가. */
enum class SalarySource {
    /** 계약서에 적힌 금액 (확정) */
    CONTRACT,

    /** 계약이 끝나도 FA 자격이 없어 재계약할 것으로 보는 금액 (지금 연봉 유지 가정). 외국인 재계약도 여기 */
    ESTIMATE,

    /** 지금 검토 중인 계약·트레이드 (아직 맺지 않음) */
    PLANNED,
}

data class SalaryCell(val amount: Double, val source: SalarySource)

/**
 * 선수 한 명의 앞으로 몇 년 연봉.
 *
 * @param cells 시즌별 연봉. 그 시즌에 팀에 없으면(FA·복무) null
 * @param freeAgentAfter 이 시즌이 끝나면 FA 자격으로 계약이 끝난다 (전망 기간 안일 때만)
 */
data class PlayerCommitment(
    val playerId: PlayerId?,
    val name: String,
    val cells: List<SalaryCell?>,
    val freeAgentAfter: Int?,
) {
    val total: Double get() = cells.sumOf { it?.amount ?: 0.0 }
}

/**
 * 전망 한 해.
 *
 * 연봉 총액은 세 갈래로 나눠 둔다 — 계약서로 확정된 돈, 재계약 예상, 검토 중인 거래. 유저가 "얼마나 확실한
 * 숫자인가"를 같이 보고 계획을 세우게 하려는 것이다.
 */
data class OutlookYear(
    val season: Int,
    val cap: Double,
    val contracted: Double,
    val estimated: Double,
    val planned: Double,
    /** 이 시즌 수입 (첫해는 지금 성적, 이후는 평년 가정) */
    val revenue: Double,
    val staff: Double,
    val scouting: Double,
    /** 연봉 총액이 상한을 넘으면 시즌 끝에 내는 제재금 */
    val capFine: Double,
    /** 시즌 시작(첫해는 지금) 운용 자금. 검토 중인 계약금·트레이드 현금은 이미 뺐다 */
    val fundsStart: Double,
    val allowedDeficit: Double,
) {
    val payroll: Double get() = contracted + estimated + planned

    /** 상한까지 남은 여유. 음수면 초과 */
    val capRoom: Double get() = cap - payroll

    val expenses: Double get() = payroll + staff + scouting + capFine

    /** 적자면 양수 (구단주가 보는 숫자, [FinanceReport.deficit] 과 같은 정의) */
    val deficit: Double get() = expenses - revenue

    val withinAllowance: Boolean get() = deficit <= allowedDeficit

    /** 운용 자금은 음수가 될 수 없다 — 모자라면 모기업이 메운다 ([FinanceReport.coveredByOwner]) */
    val fundsEnd: Double get() = max(0.0, fundsStart + revenue - expenses)

    val coveredByOwner: Double get() = max(0.0, -(fundsStart + revenue - expenses))
}

/** 몇 년 치 재정 계획. [years] 첫 칸이 이번 시즌이다. */
data class FinancialPlan(
    val teamId: TeamId,
    val years: List<OutlookYear>,
    val players: List<PlayerCommitment>,
) {
    val seasons: List<Int> get() = years.map { it.season }

    /** 이 시즌이 끝나고 FA 로 풀리는 선수 */
    fun freeAgentsAfter(season: Int): List<PlayerCommitment> = players.filter { it.freeAgentAfter == season }
}

/** 검토 중인 새 계약 한 건 (FA 조건, 비FA 다년계약 등). [years] 는 첫 시즌부터 센다. */
data class PlannedContract(
    val name: String,
    val salary: Double,
    val years: Int,
    val signingBonus: Double = 0.0,
    val playerId: PlayerId? = null,
)

/**
 * "이 거래를 하면" 가정.
 *
 * @param outgoing 내보낼 우리 선수 (트레이드)
 * @param incoming 데려올 선수 — 원래 계약을 그대로 넘겨받는다 (트레이드)
 * @param contracts 새로 맺을 계약 (FA). 계약금은 지금 운용 자금에서 나간다
 * @param cashOut 지금 나가는 현금. 받는 현금이면 음수
 */
data class OutlookChange(
    val outgoing: Set<PlayerId> = emptySet(),
    val incoming: List<Player> = emptyList(),
    val contracts: List<PlannedContract> = emptyList(),
    val cashOut: Double = 0.0,
) {
    val isEmpty: Boolean get() = outgoing.isEmpty() && incoming.isEmpty() && contracts.isEmpty() && cashOut == 0.0

    companion object {
        val NONE = OutlookChange()
    }
}

/**
 * 앞으로 몇 년의 연봉 총액·샐러리캡 여유·운용 자금 전망 (docs/11 소프트캡, docs/13 돈의 흐름).
 *
 * FA·트레이드는 몇 년짜리 결정인데 화면이 올해 숫자만 보여 주면 "내년에 누가 풀리고 얼마가 비는지"를
 * 유저가 머릿속으로 계산해야 한다. 그래서 엔진이 계약서를 시즌별로 펼쳐 둔다.
 *
 * **가정** (모두 화면에 같이 적는다):
 * - 계약이 끝났는데 FA 자격이 없는 국내 선수는 지금 연봉으로 재계약한다 (실제로는 성적에 따라 오르내린다)
 * - 외국인은 매년 지금 연봉으로 재계약한다 (자리는 어차피 채우므로)
 * - FA 자격을 채우는 선수는 계약이 끝나는 해에 빠진다. 재영입은 넣지 않는다
 * - 은퇴·신인 입단은 넣지 않는다
 * - 수입은 첫해만 지금 성적으로, 이후는 승률 5할·포스트시즌 없음(평년)으로 본다. 팬심·구단주 신뢰도·스태프·
 *   스카우트 비용은 지금 수준이 이어진다고 본다
 *
 * 연봉은 계약서의 숫자라 모든 구단에 공개된 정보다 — 숨김 수치(원칙 4)와 상관없다.
 */
class FinancialOutlook(balance: BalanceConfig, private val finance: Finance) {

    private val salaryCap = SalaryCap(balance)
    private val horizon = balance.intOrNull("finance.outlookYears") ?: DEFAULT_YEARS
    private val qualifyingHighSchool = balance.int("freeAgency.qualifyingSeasons.highSchool")
    private val qualifyingCollege = balance.int("freeAgency.qualifyingSeasons.college")

    val years: Int get() = horizon

    /**
     * @param league 지금 리그. 스토브리그 FA 시장 중이면 계약이 한 해 넘어간 다음 시즌 리그를 넘긴다
     * @param funds 지금 운용 자금
     * @param record 이번 시즌 지금까지 성적 (첫해 수입 추정용). 시즌 전이면 빈 기록
     */
    fun project(
        league: League,
        teamId: TeamId,
        funds: Double,
        record: TeamRecord,
        fanSupport: Int,
        ownerTrust: Int,
        postseason: PostseasonResult?,
        scoutingCost: Double,
        change: OutlookChange = OutlookChange.NONE,
    ): FinancialPlan {
        val first = league.season
        val seasons = (0 until horizon).map { first + it }

        val roster = league.playersOf(teamId).filter { it.id !in change.outgoing } + change.incoming
        val players = roster
            .map { player -> commitmentOf(player, seasons, planned = player in change.incoming) }
            .plus(change.contracts.map { plannedRow(it, seasons) })
            .filter { row -> row.cells.any { it != null } }
            .sortedWith(compareByDescending<PlayerCommitment> { it.cells.first()?.amount ?: 0.0 }.thenByDescending { it.total })

        val staff = finance.staffSalaryOf(league, teamId)
        val allowed = finance.allowedDeficit(league, teamId)
        val currentRevenue = finance.revenueOf(league, teamId, record, fanSupport, ownerTrust, postseason).total
        val normalRevenue = finance.revenueOf(league, teamId, TeamRecord(teamId), fanSupport, ownerTrust, null).total

        var fundsStart = funds - change.contracts.sumOf { it.signingBonus } - change.cashOut
        var overruns = league.capOverruns[teamId] ?: 0
        val years = seasons.mapIndexed { index, season ->
            fun sumOf(source: SalarySource) = players.sumOf { row -> row.cells[index]?.takeIf { it.source == source }?.amount ?: 0.0 }
            val contracted = sumOf(SalarySource.CONTRACT)
            val estimated = sumOf(SalarySource.ESTIMATE)
            val planned = sumOf(SalarySource.PLANNED)
            val payroll = contracted + estimated + planned
            val fine = if (payroll > salaryCap.cap) salaryCap.fineFor(payroll, overruns + 1) else 0.0
            overruns = if (payroll > salaryCap.cap) overruns + 1 else 0

            OutlookYear(
                season = season,
                cap = salaryCap.cap,
                contracted = round2(contracted),
                estimated = round2(estimated),
                planned = round2(planned),
                revenue = round2(if (index == 0) currentRevenue else normalRevenue),
                staff = round2(staff),
                scouting = round2(scoutingCost),
                capFine = round2(fine),
                fundsStart = round2(fundsStart),
                allowedDeficit = allowed,
            ).also { fundsStart = it.fundsEnd }
        }
        return FinancialPlan(teamId, years, players)
    }

    /**
     * 선수 한 명의 계약을 시즌별로 펼친다.
     *
     * 계약 마지막 해 다음 시즌부터는 FA 자격을 따진다. 자격이 있으면 거기서 끝, 없으면 지금 연봉으로 재계약했다고 본다.
     * FA 까지 남은 시즌([baseballgm.model.Contract.seasonsToFreeAgency])은 1군에서 뛴 해만 줄지만, 전망에서는
     * 매년 뛴다고 본다 (주전급 계획에 쓰는 숫자라서).
     */
    private fun commitmentOf(player: Player, seasons: List<Int>, planned: Boolean): PlayerCommitment {
        val contract = player.contract
        if (contract.yearsRemaining <= 0) return PlayerCommitment(player.id, player.registeredName, seasons.map { null }, null)

        val cells = mutableListOf<SalaryCell?>()
        var freeAgentAfter: Int? = null
        seasons.forEachIndexed { index, season ->
            if (freeAgentAfter != null) {
                cells += null
                return@forEachIndexed
            }
            val underContract = index < contract.yearsRemaining
            val salary = if (index == 0) contract.salary else contract.nextSalary ?: contract.salary
            val source = when {
                planned -> SalarySource.PLANNED
                underContract -> SalarySource.CONTRACT
                else -> SalarySource.ESTIMATE
            }
            val serving = if (index == 0) !player.military.isAvailable else isServing(player, season)
            cells += if (serving) null else SalaryCell(salary, source)

            // 계약이 이 시즌으로 끝나는가 (원래 계약이 끝났거나, 그 뒤 1년씩 재계약한 해)
            val contractEnds = index >= contract.yearsRemaining - 1
            if (contractEnds && reachesFreeAgency(player, index)) freeAgentAfter = season
        }
        return PlayerCommitment(player.id, player.registeredName, cells, freeAgentAfter)
    }

    private fun plannedRow(plan: PlannedContract, seasons: List<Int>): PlayerCommitment = PlayerCommitment(
        playerId = plan.playerId,
        name = plan.name,
        cells = seasons.mapIndexed { index, _ -> if (index < plan.years) SalaryCell(plan.salary, SalarySource.PLANNED) else null },
        freeAgentAfter = null,
    )

    /** 첫 시즌부터 [index] 번째 시즌이 끝났을 때 FA 자격을 채우는가 (Offseason.qualifiesForFreeAgency 와 같은 기준). */
    private fun reachesFreeAgency(player: Player, index: Int): Boolean {
        if (player.origin == Origin.FOREIGN) return false
        val contract = player.contract
        val needed = if (player.origin == Origin.COLLEGE) qualifyingCollege else qualifyingHighSchool
        return contract.seasonsToFreeAgency - (index + 1) <= 0 && contract.serviceSeasons + index + 1 >= needed
    }

    /** 그 시즌 개막 때 복무 중인가. 복무 중인 선수는 연봉 총액에서 빠진다 ([League.payrollOf]) */
    private fun isServing(player: Player, season: Int): Boolean {
        val military = player.military
        return military is MilitaryStatus.Serving && military.returnSeason > season
    }

    private fun round2(value: Double): Double = (value * 100).roundToInt() / 100.0

    private companion object {
        const val DEFAULT_YEARS = 4
    }
}
