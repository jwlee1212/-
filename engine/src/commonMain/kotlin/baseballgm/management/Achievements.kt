package baseballgm.management

import baseballgm.league.League
import baseballgm.model.TeamId
import baseballgm.season.PostseasonResult

/**
 * 업적 하나 (docs/14).
 *
 * 조건은 **커리어 기록만 보고** 판정한다. 시즌마다 흩어진 상태를 뒤지지 않게 커리어에 필요한
 * 정보를 남겨 두는 구조다 ([CareerSeason]).
 */
data class Achievement(
    val id: String,
    val label: String,
    val description: String,
)

/** 이번 시즌 판정에 필요한 추가 정보. 커리어 기록만으로는 알 수 없는 것들이다. */
data class AchievementContext(
    val season: Int,
    val teamId: TeamId,
    /** 이번 시즌 연봉 총액 순위 (1 = 가장 많이 쓴 팀) */
    val payrollRank: Int,
    val teamCount: Int,
    /** 취임 첫 시즌의 팀 순위. "기적의 재건" 판정에 쓴다 */
    val rankWhenHired: Int,
    val seasonsWithTeam: Int,
    /** 5라운드 이하로 지명한 선수가 이번 시즌 리그 최고 수준이 되었는가 */
    val lateRoundStar: Boolean,
)

/**
 * 업적 (docs/14 중장기 목표).
 *
 * 업적은 **한 번 달성하면 남는다.** 그래서 판정은 "이번 시즌에 새로 달성한 것"만 골라내면 된다.
 * 조건은 전부 커리어 기록에서 계산할 수 있게 잡았다 — 세이브에 커리어만 들어 있으면
 * 언제든 다시 판정할 수 있다.
 */
object Achievements {

    val FIRST_TITLE = Achievement("firstTitle", "첫 우승", "한국시리즈 우승")
    val DYNASTY = Achievement("dynasty", "왕조", "3년 연속 우승")
    val MIRACLE_REBUILD = Achievement("miracleRebuild", "기적의 재건", "꼴찌 팀을 3년 안에 우승으로")
    val DRAFT_GENIUS = Achievement("draftGenius", "드래프트의 신", "5라운드 이하 지명 선수가 리그 최고 수준으로")
    val PENNY_PINCHER = Achievement("pennyPincher", "짠돌이 단장", "연봉 총액 최하위로 포스트시즌 진출")
    val NATIONWIDE = Achievement("nationwide", "전국구 단장", "서로 다른 3개 구단에서 우승")
    val LONG_SERVICE = Achievement("longService", "장수 단장", "한 구단에서 10시즌")
    val REBUILDER = Achievement("rebuilder", "육성의 달인", "포스트시즌 5회 진출")

    val all: List<Achievement> = listOf(
        FIRST_TITLE, DYNASTY, MIRACLE_REBUILD, DRAFT_GENIUS, PENNY_PINCHER, NATIONWIDE, LONG_SERVICE, REBUILDER,
    )

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }

    /**
     * 이번 시즌에 새로 달성한 업적.
     *
     * @param career 이번 시즌까지 기록이 들어간 커리어
     */
    fun newlyUnlocked(career: CareerRecord, context: AchievementContext): List<Achievement> {
        val already = career.unlockedAchievements.toSet()
        val played = career.seasons.filterNot { it.commentary }
        val last = played.lastOrNull() ?: return emptyList()
        val unlocked = mutableListOf<Achievement>()

        fun award(achievement: Achievement, condition: Boolean) {
            if (condition && achievement.id !in already) unlocked += achievement
        }

        award(FIRST_TITLE, last.champion)

        // 왕조: 최근 세 시즌이 모두 우승
        award(
            DYNASTY,
            played.takeLast(DYNASTY_YEARS).let { it.size == DYNASTY_YEARS && it.all { season -> season.champion } },
        )

        // 기적의 재건: 취임 때 꼴찌였던 팀을 3시즌 안에 우승시켰다
        award(
            MIRACLE_REBUILD,
            last.champion &&
                context.rankWhenHired >= context.teamCount &&
                context.seasonsWithTeam <= MIRACLE_YEARS,
        )

        award(DRAFT_GENIUS, context.lateRoundStar)

        // 짠돌이 단장: 연봉 총액 최하위로 포스트시즌
        award(PENNY_PINCHER, last.reachedRound != null && context.payrollRank >= context.teamCount)

        award(NATIONWIDE, career.championshipTeams.distinct().size >= NATIONWIDE_TEAMS)

        award(LONG_SERVICE, played.count { it.teamId == context.teamId } >= LONG_SERVICE_YEARS)

        award(REBUILDER, career.postseasonAppearances >= REBUILDER_APPEARANCES)

        return unlocked
    }

    /** 명예의 전당 평가 (docs/14 최종 목표). 유저가 은퇴를 선택할 때 보여 준다. */
    fun hallOfFameGrade(career: CareerRecord): String {
        val score = career.championships * CHAMPIONSHIP_POINTS +
            career.postseasonAppearances * POSTSEASON_POINTS +
            career.unlockedAchievements.size * ACHIEVEMENT_POINTS +
            career.reputation / REPUTATION_DIVISOR
        return when {
            score >= 60 -> "전설 (명예의 전당 만장일치)"
            score >= 40 -> "명예의 전당"
            score >= 25 -> "기억되는 단장"
            score >= 12 -> "한 시대를 보낸 단장"
            else -> "짧은 커리어"
        }
    }

    /** 연봉 총액 순위 (1 = 가장 많이 쓴 팀). */
    fun payrollRankOf(league: League, teamId: TeamId): Int =
        league.teams.sortedByDescending { league.payrollOf(it.id) }.indexOfFirst { it.id == teamId } + 1

    private const val DYNASTY_YEARS = 3
    private const val MIRACLE_YEARS = 3
    private const val NATIONWIDE_TEAMS = 3
    private const val LONG_SERVICE_YEARS = 10
    private const val REBUILDER_APPEARANCES = 5
    private const val CHAMPIONSHIP_POINTS = 12
    private const val POSTSEASON_POINTS = 3
    private const val ACHIEVEMENT_POINTS = 4
    private const val REPUTATION_DIVISOR = 5
}
