package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.league.TeamRecord
import baseballgm.model.TeamId
import baseballgm.season.PostseasonResult
import baseballgm.season.PostseasonRound
import baseballgm.util.nextInRange
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random

/** 단장이 보낸 한 시즌 (docs/13 커리어 기록). */
@Serializable
data class CareerSeason(
    val season: Int,
    val teamId: TeamId?,
    val teamName: String,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val rank: Int,
    /** 포스트시즌에서 어디까지 갔는가. 진출 못 하면 null */
    val reachedRound: PostseasonRound? = null,
    val champion: Boolean = false,
    val goalOutcome: GoalOutcome? = null,
    val reputationAfter: Int = 0,
    /** 해설위원으로 보낸 해 (docs/13) */
    val commentary: Boolean = false,
) {
    fun line(): String = when {
        commentary -> "$season 해설위원"
        else -> buildString {
            append("$season $teamName ${rank}위 ${wins}승 ${losses}패")
            if (ties > 0) append(" ${ties}무")
            reachedRound?.let { append(" · ${it.label}") }
            if (champion) append(" · 우승")
            goalOutcome?.let { append(" · 목표 ${it.label}") }
        }
    }
}

/** 이직 제안 (docs/13). */
data class JobOffer(val teamId: TeamId, val teamName: String, val expectation: String, val ownerTrust: Int)

/** 커리어 전체. 세이브의 중심이다 (docs/13). */
@Serializable
data class CareerRecord(
    val gmName: String,
    val reputation: Int,
    val seasons: List<CareerSeason> = emptyList(),
    val unlockedAchievements: List<String> = emptyList(),
    /** 우승한 구단들. "전국구 단장" 업적에 쓴다 */
    val championshipTeams: List<TeamId> = emptyList(),
) {
    val totalSeasons: Int get() = seasons.count { !it.commentary }

    val championships: Int get() = seasons.count { it.champion }

    val postseasonAppearances: Int get() = seasons.count { it.reachedRound != null }

    val wins: Int get() = seasons.sumOf { it.wins }

    val losses: Int get() = seasons.sumOf { it.losses }

    val teamsServed: List<TeamId> get() = seasons.mapNotNull { it.teamId }.distinct()

    fun summary(): String =
        "${totalSeasons}시즌 · ${wins}승 ${losses}패 · 우승 ${championships}회 · " +
            "PS 진출 ${postseasonAppearances}회 · 평판 $reputation"
}

/**
 * 단장 커리어 (docs/13).
 *
 * **평판은 기대 대비 성과로 움직인다.** 약팀에서 5강에 가는 것이 강팀에서 우승하는 것보다 크게
 * 오른다 — 그래야 "좋은 팀을 골라 앉아 있기"가 최적 전략이 되지 않는다. 기대 승수는 시즌 시작
 * 시점의 팀 전력에서 뽑는다.
 *
 * 해임되어도 게임이 끝나지 않는다 (docs/13 확정 사항). 평판에 맞는 구단들이 제안을 보내고,
 * 제안이 없으면 한 해 해설위원으로 지내다 다시 기회를 받는다.
 */
class Career(balance: BalanceConfig, private val strength: StrengthCalculator) {

    private val section = balance.section("career")
    private val expectation = section.section("expectationFromStrength")
    private val gain = section.section("reputationGain")
    private val loss = section.section("reputationLoss")
    private val offerReputation = section.section("offerReputation")
    private val offerCount = section.intRange("offerCount")
    private val minimum = section.int("min")
    private val maximum = section.int("max")

    val startingReputation: Int = section.int("startReputation")

    /**
     * 이 팀 전력이면 몇 승이 기대되는가.
     *
     * 전력 68(리그 중간)이 5할이고, 전력이 1 오를 때 기대 승수가 slope 만큼 오른다.
     */
    fun expectedWins(league: League, teamId: TeamId, gamesPerTeam: Int): Double {
        val power = strength.of(league.playersOf(teamId)).overall
        val extra = (power - expectation.double("pivot")) * expectation.double("slope")
        return (gamesPerTeam / 2.0 + extra).coerceIn(gamesPerTeam * MIN_SHARE, gamesPerTeam * MAX_SHARE)
    }

    /**
     * 시즌이 끝난 뒤 평판 변화.
     *
     * 기대보다 많이 이긴 만큼 오르고, 포스트시즌 성과가 얹어진다.
     */
    /**
     * 시즌이 끝난 뒤 평판 변화.
     *
     * **기대보다 많이 이긴 만큼** 오른다. 기대 승수가 실제 전력-승률 관계에서 나오기 때문에,
     * 약팀을 맡아 포스트시즌에 가면 기대치를 20승 넘게 초과해 평판이 크게 오르고, 강팀으로
     * 우승해도 "그럴 만한 팀이었다"라서 조금 오른다 (docs/13).
     *
     * @param teamPower 시즌 시작 시점 팀 전력. 약팀의 포스트시즌에 웃돈을 준다
     */
    fun reputationAfterSeason(
        current: Int,
        record: TeamRecord,
        expectedWins: Double,
        postseason: PostseasonResult?,
        teamId: TeamId,
        teamPower: Double = expectation.double("pivot"),
    ): Int {
        val gap = record.wins - expectedWins
        // 기대에 못 미친 쪽은 덜 깎는다 (대칭이면 강팀 단장의 평판이 계단식으로 무너진다)
        val aboveExpectation = gap * gain.double("perWinAboveExpectation") *
            if (gap < 0) section.double("underperformanceShare") else 1.0
        val reached = postseason?.reachedRound(teamId)
        val postseasonBonus = when {
            postseason?.champion == teamId -> gain.double("championship")
            reached == PostseasonRound.KOREAN_SERIES -> gain.double("koreanSeries")
            reached != null -> gain.double("postseason")
            else -> 0.0
        }
        val underdog = (1.0 + (expectation.double("pivot") - teamPower) * section.double("underdogWeight"))
            .coerceIn(section.doubleRange("underdogRange"))
        return (current + aboveExpectation + postseasonBonus * underdog)
            .roundToInt()
            .coerceIn(minimum, maximum)
    }

    /** 팀 전력 (평판 계산에 쓰는 값). */
    fun powerOf(league: League, teamId: TeamId): Double = strength.of(league.playersOf(teamId)).overall

    fun reputationAfterFiring(current: Int): Int =
        (current + loss.double("fired")).roundToInt().coerceIn(minimum, maximum)

    /**
     * 해설위원으로 보낸 해.
     *
     * 평판이 조금 깎이지만 **바닥까지 떨어지지는 않는다** — 그러지 않으면 한 번 해임된 단장이
     * 영원히 복귀하지 못한다 (docs/13 은 "1년 해설위원 후 재제안"이라고 못박았다).
     */
    fun reputationAfterCommentary(current: Int): Int {
        val floor = section.int("sabbaticalReputationFloor")
        val next = (current + loss.double("commentaryYear")).roundToInt()
        return (if (current > floor) maxOf(next, floor) else current).coerceIn(minimum, maximum)
    }

    /**
     * 빈 자리에서 오는 제안 (docs/13).
     *
     * 평판이 높으면 강팀이, 낮으면 약팀이 부른다. 아무 제안도 없으면 한 해 해설위원이 된다.
     */
    /**
     * 빈 자리에서 오는 제안 (docs/13).
     *
     * @param guaranteed 해설위원으로 한 해를 보냈으면 빈 자리 중 가장 약한 팀이라도 제안을 준다.
     *   기획이 "제안이 없으면 1년 해설위원 후 재제안"이라고 정했으므로 영구 실업은 없다
     */
    fun offersFor(
        league: League,
        reputation: Int,
        openings: List<TeamId>,
        trustOf: (TeamId) -> Int,
        random: Random,
        guaranteed: Boolean = false,
    ): List<JobOffer> {
        if (openings.isEmpty()) return emptyList()
        val ranked = openings.sortedByDescending { strength.of(league.playersOf(it)).overall }
        val qualified = ranked.filter { teamId ->
            val power = strength.of(league.playersOf(teamId)).overall
            reputation >= requiredReputation(power)
        }
        val allowed = if (qualified.isEmpty() && guaranteed) listOf(ranked.last()) else qualified
        val count = random.nextInRange(offerCount).coerceAtMost(allowed.size)
        return allowed.take(count).map { teamId ->
            val team = league.team(teamId)
            JobOffer(
                teamId = teamId,
                teamName = team.name,
                expectation = team.ownerGoal,
                ownerTrust = trustOf(teamId),
            )
        }
    }

    /** 이 정도 전력의 구단을 맡으려면 평판이 얼마나 필요한가. */
    private fun requiredReputation(power: Double): Double = when {
        power >= STRONG_TEAM -> offerReputation.double("strongTeam")
        power >= MID_TEAM -> offerReputation.double("midTeam")
        else -> offerReputation.double("weakTeam")
    }

    fun label(reputation: Int): String = when {
        reputation >= 85 -> "명장"
        reputation >= 70 -> "능력 있는 단장"
        reputation >= 50 -> "평범한 단장"
        reputation >= 30 -> "의심받는 단장"
        else -> "실패한 단장"
    }

    private companion object {
        const val MIN_SHARE = 0.30
        const val MAX_SHARE = 0.70
        const val STRONG_TEAM = 74.0
        const val MID_TEAM = 66.0
    }
}
