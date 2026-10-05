package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.league.TeamRecord
import baseballgm.model.Player
import baseballgm.model.TeamId
import baseballgm.season.PostseasonResult
import baseballgm.season.PostseasonRound
import kotlin.math.roundToInt

/** 수입 내역 (docs/13). */
data class Revenue(
    val attendance: Double,
    val broadcast: Double,
    val sponsorship: Double,
    val postseason: Double,
    val parentSupport: Double,
) {
    val total: Double get() = attendance + broadcast + sponsorship + postseason + parentSupport
}

/** 지출 내역 (docs/13). */
data class Expenses(
    val payroll: Double,
    val signingBonus: Double,
    val staff: Double,
    val scouting: Double,
    val penalties: Double,
    /** FA 옵션(성적 인센티브) 지급액 (2026-10-04) */
    val options: Double = 0.0,
) {
    val total: Double get() = payroll + signingBonus + staff + scouting + penalties + options
}

/** 한 시즌 재정 결산. */
data class FinanceReport(
    val teamId: TeamId,
    val season: Int,
    val revenue: Revenue,
    val expenses: Expenses,
    val attendanceRate: Double,
    val allowedDeficit: Double,
    val fundsBefore: Double,
    val fundsAfter: Double,
) {
    /**
     * 모기업이 계획 밖으로 더 메워 준 금액.
     *
     * 운용 자금은 음수가 될 수 없다 — 현실에서는 모기업이 부족분을 메우기 때문이다. 대신 그
     * 금액이 여기 남아서, 구단주 신뢰도가 떨어지는 이유로 보인다.
     */
    val coveredByOwner: Double get() = maxOf(0.0, -(fundsBefore + revenue.total - expenses.total))

    /** 적자면 양수. 구단주가 보는 숫자다 */
    val deficit: Double get() = expenses.total - revenue.total

    val withinAllowance: Boolean get() = deficit <= allowedDeficit

    fun line(): String =
        "수입 ${money(revenue.total)} · 지출 ${money(expenses.total)} · " +
            (if (deficit > 0) "적자 ${money(deficit)}" else "흑자 ${money(-deficit)}") +
            " (허용 ${money(allowedDeficit)})"

    private fun money(value: Double): String = "${(value * 10).roundToInt() / 10.0}억"
}

/**
 * 구단 재정 (docs/13).
 *
 * **단장이 결정하는 돈만 추적한다.** 구장 운영비·직원 급여 같은 고정비는 게임에 등장하지 않는다 —
 * 유저가 바꿀 수 없는 숫자를 보여 줘도 결정에 도움이 안 되기 때문이다.
 *
 * 현실처럼 **자체 수입만으로는 적자**가 나고 모기업이 메운다. 그래서 연봉 총액을 키우면 적자가
 * 커지고, 적자가 구단주 허용 범위를 넘으면 신뢰도가 떨어진다 ([OwnerTrust]).
 */
class Finance(balance: BalanceConfig, private val strength: StrengthCalculator) {

    private val section = balance.section("finance")
    private val attendance = section.section("attendance")
    private val sponsorship = section.section("sponsorship")
    private val postseasonRevenue = section.section("postseasonRevenue")
    private val parentSupport = section.section("parentSupport")

    private val broadcast = section.double("broadcast")
    private val starOverall = sponsorship.double("starOverall")
    private val starBonus = sponsorship.double("starBonus")

    fun allowedDeficit(league: League, teamId: TeamId): Double =
        section.double("allowedDeficitByMarket.${league.team(teamId).marketSize}")

    /**
     * 관중률.
     *
     * 성적과 팬심이 절반씩 정하고 포스트시즌 진출이 보너스다. 이기면 관중이 늘고, 관중이 늘면
     * 다음 시즌 예산이 늘어난다 — 성적과 돈이 한 방향으로 묶인다.
     */
    fun attendanceRate(record: TeamRecord, fanSupport: Int, madePostseason: Boolean): Double {
        val winShare = if (record.games == 0) NEUTRAL else record.winPct
        val fanShare = fanSupport / MAX_FAN
        val rate = NEUTRAL +
            (winShare - NEUTRAL) * attendance.double("winPctWeight") * ATTENDANCE_SCALE +
            (fanShare - NEUTRAL) * attendance.double("fanSupportWeight") * ATTENDANCE_SCALE +
            if (madePostseason) attendance.double("postseasonBonus") else 0.0
        return rate.coerceIn(attendance.double("minShare"), attendance.double("maxShare"))
    }

    fun revenueOf(
        league: League,
        teamId: TeamId,
        record: TeamRecord,
        fanSupport: Int,
        ownerTrust: Int,
        postseason: PostseasonResult?,
    ): Revenue {
        val team = league.team(teamId)
        val reached = postseason?.reachedRound(teamId)
        val rate = attendanceRate(record, fanSupport, reached != null)

        return Revenue(
            attendance = round2(attendance.double("baseByMarket.${team.marketSize}") * rate),
            broadcast = broadcast,
            sponsorship = round2(sponsorshipOf(league.playersOf(teamId), fanSupport)),
            postseason = round2(postseasonRevenueOf(postseason, teamId)),
            parentSupport = round2(parentSupportOf(league, teamId, ownerTrust)),
        )
    }

    /** 스폰서·굿즈: 팬심과 스타 보유가 정한다 (docs/13). */
    private fun sponsorshipOf(roster: List<Player>, fanSupport: Int): Double {
        val stars = roster.count { strength.overallOf(it) >= starOverall }
        val fanShare = (NEUTRAL + (fanSupport / MAX_FAN - NEUTRAL) * sponsorship.double("fanSupportWeight") * 2)
            .coerceIn(sponsorship.double("minShare"), sponsorship.double("maxShare"))
        return sponsorship.double("base") * fanShare + stars * starBonus
    }

    /**
     * 포스트시즌 배분 (docs/13).
     *
     * **실제로 치른 시리즈만** 센다. 1위 팀은 와일드카드·준플레이오프를 건너뛰므로 그 몫이 없다 —
     * 누적으로 계산하면 정규시즌 1위가 5위보다 두 배를 받는 이상한 결과가 나온다.
     */
    private fun postseasonRevenueOf(postseason: PostseasonResult?, teamId: TeamId): Double {
        if (postseason == null) return 0.0
        var total = postseason.series
            .filter { it.higherSeed == teamId || it.lowerSeed == teamId }
            .sumOf { series ->
                postseasonRevenue.double(
                    when (series.round) {
                        PostseasonRound.WILDCARD -> "wildcard"
                        PostseasonRound.SEMI_PLAYOFF -> "semiPlayoff"
                        PostseasonRound.PLAYOFF -> "playoff"
                        PostseasonRound.KOREAN_SERIES -> "koreanSeries"
                    },
                )
            }
        if (postseason.champion == teamId) total += postseasonRevenue.double("champion")
        return total
    }

    /** 모기업 지원 = 연간 기본액 × 구단주 신뢰도 보정 × 모기업 경기 보정 (docs/13). */
    fun parentSupportOf(league: League, teamId: TeamId, ownerTrust: Int, cycleMultiplier: Double = 1.0): Double {
        val team = league.team(teamId)
        val base = team.parentCompany?.annualSupport ?: parentSupport.double("noParentBase")
        val health = team.parentCompany?.financialHealth ?: NEUTRAL_HEALTH
        val trustShare = 1.0 + (ownerTrust / MAX_FAN - NEUTRAL) * parentSupport.double("trustWeight") * 2
        val healthShare = 1.0 + (health / MAX_FAN - NEUTRAL) * parentSupport.double("healthWeight") * 2
        return base * trustShare * healthShare * cycleMultiplier
    }

    fun expensesOf(
        league: League,
        teamId: TeamId,
        signingBonus: Double,
        scouting: Double,
        penalties: Double,
        options: Double = 0.0,
    ): Expenses = Expenses(
        payroll = round2(league.payrollOf(teamId)),
        signingBonus = round2(signingBonus),
        staff = round2(staffSalaryOf(league, teamId)),
        scouting = round2(scouting),
        penalties = round2(penalties),
        options = round2(options),
    )

    /** 감독·코치·메디컬 연봉 (docs/13 운용 자금). */
    fun staffSalaryOf(league: League, teamId: TeamId): Double =
        (league.managerOf(teamId)?.contract?.salary ?: 0.0) +
            league.coachesOf(teamId).sumOf { it.contract.salary } +
            league.medicalStaffOf(teamId).sumOf { it.contract.salary }

    fun report(
        league: League,
        teamId: TeamId,
        season: Int,
        record: TeamRecord,
        fanSupport: Int,
        ownerTrust: Int,
        postseason: PostseasonResult?,
        signingBonus: Double,
        scouting: Double,
        penalties: Double,
        fundsBefore: Double,
        cycleMultiplier: Double = 1.0,
        options: Double = 0.0,
    ): FinanceReport {
        val revenue = revenueOf(league, teamId, record, fanSupport, ownerTrust, postseason).let {
            it.copy(parentSupport = round2(parentSupportOf(league, teamId, ownerTrust, cycleMultiplier)))
        }
        val expenses = expensesOf(league, teamId, signingBonus, scouting, penalties, options)
        return FinanceReport(
            teamId = teamId,
            season = season,
            revenue = revenue,
            expenses = expenses,
            attendanceRate = attendanceRate(record, fanSupport, postseason?.reachedRound(teamId) != null),
            allowedDeficit = allowedDeficit(league, teamId),
            fundsBefore = fundsBefore,
            fundsAfter = round2((fundsBefore + revenue.total - expenses.total).coerceAtLeast(0.0)),
        )
    }

    private fun round2(value: Double): Double = (value * 100).roundToInt() / 100.0

    private companion object {
        const val NEUTRAL = 0.5
        const val MAX_FAN = 100.0
        const val NEUTRAL_HEALTH = 50
        /** 성적·팬심이 관중률을 얼마나 흔드는지 */
        const val ATTENDANCE_SCALE = 2.0
    }
}
