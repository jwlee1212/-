package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.TeamId
import baseballgm.season.PostseasonResult
import baseballgm.season.SeasonState
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * 시즌을 넘어 이어지는 경영 상태 (docs/13).
 *
 * 팬심·구단주 신뢰도는 이미 [baseballgm.model.Team] 이 들고 있고, 여기에는 **여러 시즌을 봐야
 * 알 수 있는 것들**만 둔다. 단장 커리어가 여기 있는 이유도 같다 — 구단이 아니라 사람에게 붙는
 * 기록이기 때문이다 (M11 세이브에서 이 덩어리가 세이브의 중심이 된다).
 */
@Serializable
data class ManagementState(
    val career: CareerRecord? = null,
    /** 팀별 연속 목표 미달 횟수 */
    val consecutiveMisses: Map<TeamId, Int> = emptyMap(),
    /** 팀별 모기업 불황 누적 연수 */
    val slumpYears: Map<TeamId, Int> = emptyMap(),
    /** 올 시즌 구단주가 내건 목표 문구 */
    val seasonGoals: Map<TeamId, String> = emptyMap(),
    /** 유저가 맡은 구단. 해임되면 null */
    val userTeam: TeamId? = null,
    /** 해설위원으로 보내는 해인가 (docs/13) */
    val onSabbatical: Boolean = false,
    /** 유저 단장이 내린 선수 결정 (docs/13 과거 선택의 메아리·유대·커리어 연대기) */
    val decisions: List<GmDecision> = emptyList(),
    /** 유저 구단 선수의 만족도 (docs/13 "선수 성향과 만족도", 2026-10-04). 시즌을 넘어 이어진다 */
    val morale: Map<baseballgm.model.PlayerId, PlayerMorale> = emptyMap(),
)

/** 한 구단의 시즌 결산. */
data class TeamReview(
    val teamId: TeamId,
    val finance: FinanceReport,
    val fanBefore: Int,
    val fanAfter: Int,
    val trust: TrustEvaluation,
    val parent: ParentCompanyState,
    /**
     * 다음 시즌 시작 신뢰도.
     *
     * 해임된 구단은 **새 단장이 오므로 신뢰도가 초기화**된다 — 안 그러면 0 에 붙은 구단이
     * 해마다 "해임"을 반복한다.
     */
    val nextSeasonTrust: Int,
) {
    val fanChange: Int get() = fanAfter - fanBefore
}

/** 단장이 결정하지 않는 지출. 스토브리그가 계산해서 넘겨 준다 */
data class TeamSpend(
    val signingBonus: Double = 0.0,
    val scouting: Double = 0.0,
    val penalties: Double = 0.0,
    /** FA 옵션(성적 인센티브) 지급액 (2026-10-04) */
    val options: Double = 0.0,
)

/** 시즌 전체 경영 결산 (docs/13, 14). */
data class SeasonReview(
    val season: Int,
    val teams: List<TeamReview>,
    val management: ManagementState,
    val newAchievements: List<Achievement>,
    val userFired: Boolean,
    val offers: List<JobOffer>,
) {
    fun of(teamId: TeamId): TeamReview? = teams.firstOrNull { it.teamId == teamId }
}

/**
 * 시즌 말 경영 결산 (docs/13).
 *
 * 순서가 중요하다. **재정 → 팬심 → 구단주 평가 → 커리어·업적** 이다.
 * 구단주는 적자와 팬심 변화를 함께 보므로 재정과 팬심이 먼저 확정돼야 하고,
 * 커리어 평판은 구단주 평가(해임 여부)와 별개로 "기대 대비 성과"로 매겨진다.
 */
class ClubSeasonReview(
    private val balance: BalanceConfig,
    strength: StrengthCalculator,
) {
    private val finance = Finance(balance, strength)
    private val fans = FanSentiment(balance)
    private val ownerTrust = OwnerTrust(balance)
    private val parentEvents = ParentCompanyEvents(balance)
    private val career = Career(balance, strength)
    private val gamesPerTeam = balance.int("schedule.gamesPerTeam")

    val financeModel: Finance get() = finance
    val fanModel: FanSentiment get() = fans
    val trustModel: OwnerTrust get() = ownerTrust
    val careerModel: Career get() = career

    fun run(
        state: SeasonState,
        postseason: PostseasonResult?,
        spend: Map<TeamId, TeamSpend>,
        random: Random,
        difficulty: String = "normal",
    ): SeasonReview {
        val league = state.currentLeague()
        val management = league.management
        val patience = ownerTrust.patienceOf(difficulty)
        val reviews = mutableListOf<TeamReview>()
        val misses = management.consecutiveMisses.toMutableMap()
        val slumps = management.slumpYears.toMutableMap()

        league.teams.forEach { team ->
            val record = state.standings.record(team.id)
            val rank = state.standings.rankOf(team.id)
            val teamSpend = spend[team.id] ?: TeamSpend()
            val parent = parentEvents.next(team.id, team.parentCompany, slumps[team.id] ?: 0, random)
            slumps[team.id] = parent.consecutiveSlumps

            val report = finance.report(
                league = league,
                teamId = team.id,
                season = state.season,
                record = record,
                fanSupport = team.fanSupport,
                ownerTrust = team.ownerTrust,
                postseason = postseason,
                signingBonus = teamSpend.signingBonus,
                scouting = teamSpend.scouting,
                penalties = teamSpend.penalties,
                options = teamSpend.options,
                fundsBefore = state.funds[team.id] ?: team.operatingFunds,
                cycleMultiplier = parent.supportMultiplier,
            )

            val fanAfter = fans.afterSeason(
                current = team.fanSupport,
                record = record,
                rank = rank,
                teamCount = league.teams.size,
                postseason = postseason,
                volatility = team.fanVolatility,
            )

            val goal = management.seasonGoals[team.id]
                ?.let { SeasonGoal.fromOwnerGoal(it) }
                ?: ownerTrust.goalFor(league, team.id)
            val outcome = ownerTrust.outcomeOf(
                goal = goal,
                teamId = team.id,
                record = record,
                rank = rank,
                postseason = postseason,
                teamCount = league.teams.size,
                finance = report,
            )
            val evaluation = ownerTrust.evaluate(
                teamId = team.id,
                goal = goal,
                outcome = outcome,
                trustBefore = team.ownerTrust,
                finance = report,
                fanChange = fanAfter - team.fanSupport,
                consecutiveMisses = misses[team.id] ?: 0,
                patience = patience,
            )
            misses[team.id] = evaluation.consecutiveMisses

            reviews += TeamReview(
                teamId = team.id,
                finance = report,
                fanBefore = team.fanSupport,
                fanAfter = fanAfter,
                trust = evaluation,
                parent = parent,
                nextSeasonTrust = if (evaluation.fired) ownerTrust.startingTrust else evaluation.trustAfter,
            )
            if (evaluation.fired) misses[team.id] = 0
        }

        // ---------- 커리어·업적 ----------
        val userTeam = management.userTeam
        val userReview = userTeam?.let { id -> reviews.first { it.teamId == id } }
        var updatedCareer = management.career
        val newAchievements = mutableListOf<Achievement>()
        var fired = false
        var offers = emptyList<JobOffer>()

        if (userTeam == null && updatedCareer != null) {
            // 무직 — 한 해를 해설위원으로 보낸다 (docs/13)
            updatedCareer = updatedCareer.copy(
                reputation = career.reputationAfterCommentary(updatedCareer.reputation),
                seasons = updatedCareer.seasons + CareerSeason(
                    season = state.season,
                    teamId = null,
                    teamName = "해설위원",
                    wins = 0,
                    losses = 0,
                    ties = 0,
                    rank = 0,
                    commentary = true,
                ),
            )
        }

        if (userTeam != null && userReview != null && updatedCareer != null) {
            val record = state.standings.record(userTeam)
            val expected = career.expectedWins(league, userTeam, gamesPerTeam)
            val reputation = career.reputationAfterSeason(
                current = updatedCareer.reputation,
                record = record,
                expectedWins = expected,
                postseason = postseason,
                teamId = userTeam,
                teamPower = career.powerOf(league, userTeam),
            )
            val reached = postseason?.reachedRound(userTeam)
            val champion = postseason?.champion == userTeam
            val seasonRow = CareerSeason(
                season = state.season,
                teamId = userTeam,
                teamName = league.team(userTeam).name,
                wins = record.wins,
                losses = record.losses,
                ties = record.ties,
                rank = state.standings.rankOf(userTeam),
                reachedRound = reached,
                champion = champion,
                goalOutcome = userReview.trust.outcome,
                reputationAfter = reputation,
            )
            updatedCareer = updatedCareer.copy(
                reputation = reputation,
                seasons = updatedCareer.seasons + seasonRow,
                championshipTeams = if (champion) {
                    updatedCareer.championshipTeams + userTeam
                } else {
                    updatedCareer.championshipTeams
                },
            )

            val seasonsWithTeam = updatedCareer.seasons.count { it.teamId == userTeam }
            val rankWhenHired = updatedCareer.seasons.firstOrNull { it.teamId == userTeam }?.rank ?: seasonRow.rank
            val context = AchievementContext(
                season = state.season,
                teamId = userTeam,
                payrollRank = Achievements.payrollRankOf(league, userTeam),
                teamCount = league.teams.size,
                rankWhenHired = rankWhenHired,
                seasonsWithTeam = seasonsWithTeam,
                lateRoundStar = false,
            )
            newAchievements += Achievements.newlyUnlocked(updatedCareer, context)
            updatedCareer = updatedCareer.copy(
                unlockedAchievements = updatedCareer.unlockedAchievements + newAchievements.map { it.id },
            )

            fired = userReview.trust.fired
            if (fired) updatedCareer = updatedCareer.copy(reputation = career.reputationAfterFiring(reputation))
        }

        // AI 구단도 목표 실패로 해임된다 → 빈자리가 생긴다 (docs/13)
        val openings = reviews.filter { it.trust.fired }.map { it.teamId }.filter { it != userTeam }
        if ((fired || userTeam == null) && updatedCareer != null) {
            offers = career.offersFor(
                league = league,
                reputation = updatedCareer.reputation,
                openings = openings,
                trustOf = { teamId -> reviews.first { it.teamId == teamId }.nextSeasonTrust },
                random = random,
                guaranteed = management.onSabbatical,
            )
        }

        return SeasonReview(
            season = state.season,
            teams = reviews,
            management = management.copy(
                career = updatedCareer,
                consecutiveMisses = misses,
                slumpYears = slumps,
                userTeam = if (fired) null else userTeam,
                onSabbatical = userTeam == null && offers.isEmpty(),
            ),
            newAchievements = newAchievements,
            userFired = fired,
            offers = offers,
        )
    }

    /** 다음 시즌 목표를 내건다 (docs/13 개막 전 제시). */
    fun nextSeasonGoals(league: League): Map<TeamId, String> =
        league.teams.associate { it.id to it.ownerGoal }
}
