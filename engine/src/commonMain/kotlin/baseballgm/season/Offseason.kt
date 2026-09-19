package baseballgm.season

import baseballgm.development.AgingCurves
import baseballgm.development.AwakeningModel
import baseballgm.development.GrowthModel
import baseballgm.development.PlayingExperience
import baseballgm.development.RetirementModel
import baseballgm.development.hasContract
import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.Coach
import baseballgm.model.Condition
import baseballgm.model.Contract
import baseballgm.model.MilitaryStatus
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.ServiceKind
import baseballgm.model.StaffId
import baseballgm.model.TeamId
import baseballgm.util.chance
import baseballgm.util.nextInRange
import kotlin.math.max
import kotlin.random.Random

/**
 * 신인 공급기.
 *
 * 엔진은 선수를 만들 수 없다 — 이름 생성기와 아키타입이 도구(`tools`)에 있기 때문이다.
 * 그래서 신인을 만드는 일은 밖에서 받아 쓴다. M6 에서 실제 드래프트가 이 자리를 대신한다.
 */
fun interface RookieSupplier {
    fun create(count: Int, season: Int, teamId: TeamId, random: Random): List<Player>
}

/** 스토브리그에서 일어난 일 (docs/09). */
data class OffseasonReport(
    val season: Int,
    val retired: List<PlayerId>,
    val awakened: List<PlayerId>,
    val collapsed: List<PlayerId>,
    val rookies: List<PlayerId>,
    val newCoachCandidates: List<StaffId>,
    val enlisted: List<PlayerId>,
    val discharged: List<PlayerId>,
) {
    fun summary(): String =
        "은퇴 ${retired.size} · 신인 ${rookies.size} · 각성 ${awakened.size} · 급노쇠 ${collapsed.size} · " +
            "입대 ${enlisted.size} · 제대 ${discharged.size} · 코치 후보 ${newCoachCandidates.size}"
}

/**
 * 시즌 후 처리 (docs/09).
 *
 * 순서가 중요하다.
 * ① 성장·노화 → ② 각성·급노쇠 → ③ 은퇴 → ④ 군 복무 갱신 → ⑤ 계약 갱신 →
 * ⑥ 신인 유입 → ⑦ 컨디션 초기화 → 다음 시즌 리그 완성
 *
 * 성장을 먼저 하는 이유는, 그해 성적으로 얻은 성장까지 반영한 뒤에 은퇴를 판정해야
 * "마지막에 반등한 베테랑이 그대로 은퇴하는" 이상한 결과가 안 나오기 때문이다.
 */
class Offseason(
    private val balance: BalanceConfig,
    private val strength: StrengthCalculator,
) {
    private val curves = AgingCurves(balance)
    private val growthModel = GrowthModel(balance, curves)
    private val awakeningModel = AwakeningModel(balance, curves)
    private val retirementModel = RetirementModel(balance)

    private val contexts = GrowthContextResolver(balance)
    private val rookieSection = balance.section("rookieIntake")
    private val targetRosterSize = rookieSection.int("targetRosterSize")
    private val maxRookiesPerTeam = rookieSection.int("maxPerTeamPerSeason")
    private val offseasonWeeks = balance.int("season.offseasonWeeks")
    private val enlistDeadlineAge = balance.int("military.enlistDeadlineAge")
    private val sangmuShare = balance.double("leagueGeneration.militaryService.sangmuShare")
    private val contractYears = balance.section("salary.contractYears")

    fun run(state: SeasonState, random: Random, rookieSupplier: RookieSupplier): Pair<League, OffseasonReport> {
        val league = state.league
        val season = league.season
        val nextSeason = season + 1

        val survivors = mutableListOf<Player>()
        val retired = mutableListOf<PlayerId>()
        val awakened = mutableListOf<PlayerId>()
        val collapsed = mutableListOf<PlayerId>()
        val enlisted = mutableListOf<PlayerId>()
        val discharged = mutableListOf<PlayerId>()
        val newCoaches = mutableListOf<Coach>()
        var nextCoachNumber = league.coaches.size + 1

        state.allPlayers().forEach { player ->
            // ① 성장·노화
            var updated = growthModel.afterSeason(player, season, contexts.contextOf(state, player), random).player

            // ② 각성·급노쇠
            val awakening = awakeningModel.check(updated, season, random)
            updated = awakening.player
            if (awakening.awakened) awakened += updated.id
            if (awakening.collapsed) collapsed += updated.id

            // ③ 은퇴 (다음 시즌 나이 기준으로 판정한다)
            val overall = strength.overallOf(updated)
            if (retirementModel.retires(updated, nextSeason, overall, updated.hasContract(), random)) {
                retired += updated.id
                retirementModel.toCoach(updated, overall, StaffId("C" + (nextCoachNumber).toString().padStart(3, '0')), random)
                    ?.let {
                        newCoaches += it
                        nextCoachNumber++
                    }
                return@forEach
            }

            // ④ 군 복무
            val military = updateMilitary(updated, nextSeason, random)
            if (military != updated.military) {
                if (military is MilitaryStatus.Serving) enlisted += updated.id
                if (military is MilitaryStatus.Completed) discharged += updated.id
            }

            // ⑤ 계약 ⑥ 컨디션
            updated = updated
                .withMilitary(military)
                .withContract(updateContract(updated, state, random))
                .withCondition(restedCondition(updated))
            survivors += updated
        }

        // ⑦ 신인 유입: 은퇴로 빈 자리를 채워 선수단 규모를 유지한다 (M6 드래프트로 대체 예정)
        val rookies = mutableListOf<Player>()
        league.teams.forEach { team ->
            val current = survivors.count { it.teamId == team.id }
            val need = (targetRosterSize - current).coerceIn(0, maxRookiesPerTeam)
            if (need > 0) rookies += rookieSupplier.create(need, nextSeason, team.id, random)
        }

        val nextLeague = league.copy(
            season = nextSeason,
            players = survivors + rookies,
            coaches = league.coaches + newCoaches,
            schedule = league.schedule.copy(season = nextSeason),
        )
        val report = OffseasonReport(
            season = season,
            retired = retired,
            awakened = awakened,
            collapsed = collapsed,
            rookies = rookies.map { it.id },
            newCoachCandidates = newCoaches.map { it.id },
            enlisted = enlisted,
            discharged = discharged,
        )
        return nextLeague to report
    }

    // ---------- 군 복무 (docs/12) ----------

    private fun updateMilitary(player: Player, nextSeason: Int, random: Random): MilitaryStatus {
        return when (val military = player.military) {
            is MilitaryStatus.Serving ->
                if (military.returnSeason <= nextSeason) MilitaryStatus.Completed else military

            is MilitaryStatus.Unfulfilled ->
                // 입대 기한에 닿으면 입대한다. 상무 지원·합격 판정은 M8 에서 제대로 만든다
                if (player.ageIn(nextSeason) >= enlistDeadlineAge) {
                    MilitaryStatus.Serving(
                        kind = if (random.chance(sangmuShare)) ServiceKind.SANGMU else ServiceKind.ACTIVE_DUTY,
                        returnSeason = nextSeason + SERVICE_SEASONS,
                        returnWeek = MID_SEASON_WEEK,
                    )
                } else {
                    military
                }

            else -> military
        }
    }

    // ---------- 계약 ----------

    /**
     * 계약을 한 해 넘긴다. 계약이 끝난 선수는 일단 재계약한다 (임시).
     * FA 선언·협상은 M7 에서 이 자리를 대신한다.
     */
    private fun updateContract(player: Player, state: SeasonState, random: Random): Contract {
        val playedInFirstTeam = state.stats.battingOf(player.id).total.plateAppearances > 0 ||
            state.stats.pitchingOf(player.id).total.outs > 0
        val contract = player.contract
        val years = contract.yearsRemaining - 1
        val renewed = if (years <= 0) random.nextInRange(contractYears.intRange("veteran")) else years
        return contract.copy(
            yearsRemaining = renewed,
            signingBonusRemaining = max(0.0, contract.signingBonusRemaining * BONUS_PAYOUT),
            // 군 복무 기간은 FA 연차로 인정하지 않는다 (docs/12)
            serviceSeasons = contract.serviceSeasons + if (playedInFirstTeam && player.military.isAvailable) 1 else 0,
            seasonsToFreeAgency = max(0, contract.seasonsToFreeAgency - if (playedInFirstTeam) 1 else 0),
        )
    }

    /** 비시즌 동안 피로가 풀리고 폼이 평균으로 돌아온다. 긴 부상은 다음 시즌으로 이어진다. */
    private fun restedCondition(player: Player): Condition {
        val injury = player.condition.injury
        val remaining = (injury?.weeksRemaining ?: 0) - offseasonWeeks
        return Condition(
            fatigue = 0,
            form = NEUTRAL_FORM,
            injury = if (injury != null && remaining > 0) injury.copy(weeksRemaining = remaining) else null,
            relapseRiskWeeks = 0,
        )
    }

    private companion object {
        const val SERVICE_SEASONS = 2
        const val MID_SEASON_WEEK = 12
        const val NEUTRAL_FORM = 50
        const val BONUS_PAYOUT = 0.0
    }
}

/** 군 복무 상태만 바꾼 사본. */
fun Player.withMilitary(military: MilitaryStatus): Player = when (this) {
    is baseballgm.model.Batter -> copy(military = military)
    is Pitcher -> copy(military = military)
}

/** 계약만 바꾼 사본. */
fun Player.withContract(contract: Contract): Player = when (this) {
    is baseballgm.model.Batter -> copy(contract = contract)
    is Pitcher -> copy(contract = contract)
}

/** 복무 중이거나 부상 중이면 1군에 둘 수 없다. 새 시즌을 시작할 때 정리한다. */
fun Player.startingRosterLevel(): RosterLevel =
    if (!military.isAvailable || condition.isInjured) RosterLevel.FUTURES else rosterLevel
