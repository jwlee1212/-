package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.TeamRecord
import baseballgm.model.TeamId
import baseballgm.season.PostseasonResult
import baseballgm.season.PostseasonRound
import kotlin.math.roundToInt

/** 구단주가 내건 시즌 목표 (docs/13). */
enum class GoalKind(val label: String) {
    WIN_KOREAN_SERIES("한국시리즈 우승"),
    REACH_KOREAN_SERIES("한국시리즈 진출"),
    REACH_PLAYOFF("플레이오프 진출"),
    REACH_POSTSEASON("포스트시즌 진출"),
    WINNING_RECORD("승률 5할 이상"),
    AVOID_LAST_PLACE("탈꼴찌"),
    AVOID_DEFICIT("적자 축소"),
}

/** 시즌 목표. 구단 성격에 맞는 것이 개막 전에 제시된다. */
data class SeasonGoal(val kind: GoalKind, val description: String) {
    companion object {
        /** `teams.json` 의 구단주 목표 문구를 읽어 목표로 바꾼다. */
        fun fromOwnerGoal(text: String): SeasonGoal {
            // 구단마다 목표 문구가 복합적이라(예: "승률 5할 이상, 25세 이하 주전 2명") 앞머리로 판단한다.
            // 유망주 정착·지명자 데뷔 같은 부수 조건은 아직 판정하지 않는다 (임시)
            val kind = when {
                text.contains("적자") -> GoalKind.AVOID_DEFICIT
                text.contains("탈꼴찌") -> GoalKind.AVOID_LAST_PLACE
                text.contains("우승") -> GoalKind.WIN_KOREAN_SERIES
                text.contains("한국시리즈") -> GoalKind.REACH_KOREAN_SERIES
                text.contains("플레이오프") -> GoalKind.REACH_PLAYOFF
                text.contains("포스트시즌") -> GoalKind.REACH_POSTSEASON
                text.contains("5할") || text.contains("승률") -> GoalKind.WINNING_RECORD
                else -> GoalKind.REACH_POSTSEASON
            }
            return SeasonGoal(kind, text)
        }
    }
}

/** 목표 달성 정도. */
enum class GoalOutcome(val label: String) {
    EXCEEDED("초과 달성"), MET("달성"), MISSED("미달"), FAR_MISSED("크게 미달")
}

/** 시즌 말 구단주 평가 (docs/13). */
data class TrustEvaluation(
    val teamId: TeamId,
    val goal: SeasonGoal,
    val outcome: GoalOutcome,
    val trustBefore: Int,
    val trustAfter: Int,
    val deficitPenalty: Double,
    val fanChange: Int,
    val consecutiveMisses: Int,
    val fired: Boolean,
) {
    val delta: Int get() = trustAfter - trustBefore

    fun message(teamName: String): String = buildString {
        append("$teamName 구단주 평가 — 목표 '${goal.description}' ${outcome.label}")
        append(" · 신뢰도 $trustBefore → $trustAfter")
        if (fired) append(" · 해임")
    }
}

/**
 * 구단주 신뢰도 (docs/13).
 *
 * 세 가지를 본다: **목표 달성 · 적자 준수 · 팬심 변화.** 성적만 좋아도 적자를 크게 내면 깎이고,
 * 성적이 나빠도 적자를 줄이고 팬심을 올리면 덜 깎인다 — 그래서 "돈을 쏟아붓고 성적만 내는" 전략이
 * 정답이 되지 않는다.
 *
 * 신뢰도가 낮으면 큰 계약에 구단주 승인이 필요해지고, 시즌 말 20 미만이면 해임된다.
 * 2년 연속 목표 미달성은 추가로 크게 깎인다.
 */
class OwnerTrust(balance: BalanceConfig) {

    private val section = balance.section("owner")
    private val reward = section.section("goalReward")
    private val fireBelow = section.int("fireBelowTrust")
    private val warningBelow = section.int("warningBelow")
    private val approvalBelow = section.int("approvalRequiredBelow")
    private val approvalSalary = section.double("approvalSalary")

    val startingTrust: Int = section.int("start")

    fun patienceOf(difficulty: String): Double = section.double("patience.$difficulty")

    fun goalFor(league: League, teamId: TeamId): SeasonGoal =
        SeasonGoal.fromOwnerGoal(league.team(teamId).ownerGoal)

    /** 목표를 얼마나 이뤘는가. */
    fun outcomeOf(
        goal: SeasonGoal,
        teamId: TeamId,
        record: TeamRecord,
        rank: Int,
        postseason: PostseasonResult?,
        teamCount: Int = DEFAULT_TEAMS,
        finance: FinanceReport? = null,
    ): GoalOutcome {
        if (goal.kind == GoalKind.AVOID_DEFICIT && finance != null) {
            return when {
                finance.deficit <= 0 -> GoalOutcome.EXCEEDED
                finance.deficit <= finance.allowedDeficit * DEFICIT_GOAL_SHARE -> GoalOutcome.MET
                finance.withinAllowance -> GoalOutcome.MISSED
                else -> GoalOutcome.FAR_MISSED
            }
        }
        val reached = postseason?.reachedRound(teamId)
        val champion = postseason?.champion == teamId
        return when (goal.kind) {
            GoalKind.WIN_KOREAN_SERIES -> when {
                champion -> GoalOutcome.MET
                reached == PostseasonRound.KOREAN_SERIES -> GoalOutcome.MISSED
                reached != null -> GoalOutcome.MISSED
                else -> GoalOutcome.FAR_MISSED
            }

            GoalKind.REACH_KOREAN_SERIES -> when {
                champion -> GoalOutcome.EXCEEDED
                reached == PostseasonRound.KOREAN_SERIES -> GoalOutcome.MET
                reached != null -> GoalOutcome.MISSED
                else -> GoalOutcome.FAR_MISSED
            }

            GoalKind.REACH_PLAYOFF -> when {
                champion || reached == PostseasonRound.KOREAN_SERIES -> GoalOutcome.EXCEEDED
                reached == PostseasonRound.PLAYOFF -> GoalOutcome.MET
                reached != null -> GoalOutcome.MISSED
                else -> GoalOutcome.FAR_MISSED
            }

            GoalKind.REACH_POSTSEASON -> when {
                reached == null -> if (record.winPct >= NEAR_MISS_WIN_PCT) GoalOutcome.MISSED else GoalOutcome.FAR_MISSED
                reached.ordinal >= PostseasonRound.PLAYOFF.ordinal -> GoalOutcome.EXCEEDED
                else -> GoalOutcome.MET
            }

            GoalKind.WINNING_RECORD -> when {
                reached != null -> GoalOutcome.EXCEEDED
                record.winPct >= NEUTRAL -> GoalOutcome.MET
                record.winPct >= NEAR_MISS_WIN_PCT -> GoalOutcome.MISSED
                else -> GoalOutcome.FAR_MISSED
            }

            GoalKind.AVOID_LAST_PLACE -> when {
                reached != null -> GoalOutcome.EXCEEDED
                rank < teamCount -> if (rank <= teamCount / 2) GoalOutcome.EXCEEDED else GoalOutcome.MET
                else -> GoalOutcome.FAR_MISSED
            }

            // 적자 목표는 재정을 봐야 하므로 [evaluate] 에서 다시 판정한다
            GoalKind.AVOID_DEFICIT -> GoalOutcome.MET
        }
    }

    /**
     * 시즌 말 평가.
     *
     * @param consecutiveMisses 지난 시즌까지 연속 미달 횟수
     * @param patience 난이도별 구단주 인내심 (docs/14). 쉬움은 후하고 어려움은 박하다
     */
    fun evaluate(
        teamId: TeamId,
        goal: SeasonGoal,
        outcome: GoalOutcome,
        trustBefore: Int,
        finance: FinanceReport,
        fanChange: Int,
        consecutiveMisses: Int,
        patience: Double,
    ): TrustEvaluation {
        val goalDelta = reward.double(
            when (outcome) {
                GoalOutcome.EXCEEDED -> "exceeded"
                GoalOutcome.MET -> "met"
                GoalOutcome.MISSED -> "missed"
                GoalOutcome.FAR_MISSED -> "farMissed"
            },
        )
        // 적자가 허용 범위를 넘은 만큼 깎고, 흑자면 조금 올려 준다
        val excess = finance.deficit - finance.allowedDeficit
        val deficitPenalty = if (excess > 0) {
            -minOf(excess * section.double("deficitPenaltyPerUnit"), section.double("deficitPenaltyCap"))
        } else if (finance.deficit <= 0) {
            section.double("surplusReward")
        } else {
            0.0
        }
        val missed = outcome == GoalOutcome.MISSED || outcome == GoalOutcome.FAR_MISSED
        val nextMisses = if (missed) consecutiveMisses + 1 else 0
        val consecutivePenalty =
            if (nextMisses >= CONSECUTIVE_LIMIT) section.double("consecutiveMissPenalty") else 0.0

        val raw = goalDelta + deficitPenalty + consecutivePenalty + fanChange * section.double("fanChangeWeight")
        // 인내심이 높으면 깎이는 폭이 작고, 낮으면 크다
        val adjusted = if (raw < 0) raw / patience else raw * patience
        val trustAfter = (trustBefore + adjusted).roundToInt().coerceIn(MIN_TRUST, MAX_TRUST)

        return TrustEvaluation(
            teamId = teamId,
            goal = goal,
            outcome = outcome,
            trustBefore = trustBefore,
            trustAfter = trustAfter,
            deficitPenalty = deficitPenalty,
            fanChange = fanChange,
            consecutiveMisses = nextMisses,
            fired = trustAfter < fireBelow,
        )
    }

    /** 시즌 중 경고 단계 (docs/13). */
    fun warning(trust: Int): String? = when {
        trust < fireBelow -> "구단주가 사실상 경질을 준비하고 있다"
        trust < warningBelow -> "구단주가 성적에 크게 실망했다 (신뢰도 $trust)"
        trust < approvalBelow -> "구단주가 지출을 주시하고 있다 (신뢰도 $trust)"
        else -> null
    }

    /** 이 금액의 계약에 구단주 승인이 필요한가 (docs/13). */
    fun needsApproval(trust: Int, salary: Double): Boolean =
        trust < approvalBelow && salary >= approvalSalary

    fun label(trust: Int): String = when {
        trust >= 80 -> "전폭적 신뢰"
        trust >= 60 -> "신뢰"
        trust >= 40 -> "관망"
        trust >= fireBelow -> "불신"
        else -> "경질 임박"
    }

    private companion object {
        const val NEUTRAL = 0.5
        const val NEAR_MISS_WIN_PCT = 0.470
        const val CONSECUTIVE_LIMIT = 2
        const val DEFAULT_TEAMS = 10
        /** 적자 목표는 허용 범위의 이 비율 안으로 줄여야 달성이다 */
        const val DEFICIT_GOAL_SHARE = 0.7
        const val MIN_TRUST = 0
        const val MAX_TRUST = 100
    }
}
