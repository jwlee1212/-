package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.Standings
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.TeamId
import baseballgm.util.interpolateAnchors
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * 옵션 조항 종류 (2026-10-04 유저 요청 "옵션을 다양화 — WAR 기준은 빼").
 * KBO FA 계약의 옵션처럼 **기록 하나 + 기준선**이다. 기준선은 `faNegotiation.options.<key>.threshold`.
 *
 * @param pitcher true = 투수만, false = 타자만, null = 둘 다
 * @param lowerIsBetter 기준 이하면 달성 (평균자책)
 */
@Serializable
enum class OptionKind(val key: String, val pitcher: Boolean?, val lowerIsBetter: Boolean = false) {
    PLATE_APPEARANCES("plateAppearances", false),
    BATTING_AVERAGE("battingAverage", false),
    HOME_RUNS("homeRuns", false),
    RBI("rbi", false),
    STOLEN_BASES("stolenBases", false),
    INNINGS("innings", true),
    WINS("wins", true),
    ERA("era", true, lowerIsBetter = true),
    SAVES("saves", true),
    HOLDS("holds", true),
    APPEARANCES("appearances", true),
    TEAM_POSTSEASON("teamPostseason", null),
}

/** 옵션 조항 하나: [kind] 기준을 달성하면 그 시즌 [amount] 억을 준다 */
@Serializable
data class OptionClause(val kind: OptionKind, val amount: Double)

/** 한 시즌 1군 기록 중 옵션이 보는 값 (지난 시즌 기록 · 이번 시즌 기록 공통) */
data class OptionStats(
    val plateAppearances: Int = 0,
    val atBats: Int = 0,
    val hits: Int = 0,
    val homeRuns: Int = 0,
    val rbi: Int = 0,
    val stolenBases: Int = 0,
    val outs: Int = 0,
    val earnedRuns: Int = 0,
    val wins: Int = 0,
    val saves: Int = 0,
    val holds: Int = 0,
    val games: Int = 0,
) {
    /** 누적 기록을 [factor] 배 (시즌 페이스 환산). 비율 기록(타율·평균자책)은 그대로다 */
    fun scaled(factor: Double): OptionStats {
        fun s(value: Int) = (value * factor).roundToInt()
        return OptionStats(
            s(plateAppearances), s(atBats), s(hits), s(homeRuns), s(rbi), s(stolenBases),
            s(outs), s(earnedRuns), s(wins), s(saves), s(holds), s(games),
        )
    }

    val battingAverage: Double get() = if (atBats == 0) 0.0 else hits.toDouble() / atBats
    val era: Double get() = if (outs == 0) 0.0 else earnedRuns * 27.0 / outs

    companion object {
        fun of(batting: baseballgm.stats.BattingLine, pitching: baseballgm.stats.PitchingLine) = OptionStats(
            plateAppearances = batting.plateAppearances, atBats = batting.atBats, hits = batting.hits,
            homeRuns = batting.homeRuns, rbi = batting.rbi, stolenBases = batting.stolenBases,
            outs = pitching.outs, earnedRuns = pitching.earnedRuns, wins = pitching.wins,
            saves = pitching.saves, holds = pitching.holds, games = pitching.games,
        )

        fun of(season: baseballgm.league.CareerSeason) = OptionStats(
            plateAppearances = season.bat?.pa ?: 0, atBats = season.bat?.ab ?: 0, hits = season.bat?.h ?: 0,
            homeRuns = season.bat?.hr ?: 0, rbi = season.bat?.rbi ?: 0, stolenBases = season.bat?.sb ?: 0,
            outs = season.pitch?.outs ?: 0, earnedRuns = season.pitch?.er ?: 0, wins = season.pitch?.w ?: 0,
            saves = season.pitch?.sv ?: 0, holds = season.pitch?.hld ?: 0, games = season.pitch?.g ?: 0,
        )
    }
}

/**
 * 옵션 규칙 (docs/11 협상 테이블 "옵션").
 *
 * - **달성 판정**: 시즌이 끝나면 그 시즌 1군 기록이 기준선을 넘었는가. 타율·평균자책은 표본 하한(`minPaForRate`, `minOutsForRate`)이 있다.
 *   팀 포스트시즌은 우리 팀이 포스트시즌에 나갔는가
 * - **선수가 매기는 값**: 금액 × 달성 가능성 × `riskRate`. 가능성은 **지난 시즌 1군 기록 ÷ 기준선**(평균자책은 거꾸로)을
 *   `chanceByRatio` 곡선에 넣어 구한다 — 작년에 홈런 25개 친 선수에게 "20홈런 옵션"은 거의 보장된 돈이고, 8개 친 선수에겐 헐값이다.
 *   지난 시즌 1군 기록이 없으면 `chanceNoRecord`. 팀 포스트시즌은 그 팀의 지난 시즌 순위로
 * - 기록은 모두 공개 정보다 — 숨김 수치를 쓰지 않는다 (화면에 "가능성 높음"으로 보여 줘도 된다)
 */
class OptionRules(balance: BalanceConfig) {
    private val cfg = balance.section("faNegotiation.options")
    private val riskRate = cfg.double("riskRate")
    /** 지난 시즌 기록 ÷ 기준선(%) → 달성 가능성 */
    private val chanceCurve = cfg.numericMap("chanceByRatioPercent")
    private val chanceNoRecord = cfg.double("chanceNoRecord")
    private val minPaForRate = cfg.int("minPaForRate")
    private val minOutsForRate = cfg.int("minOutsForRate")
    private val minGamesForPace = cfg.int("minGamesForPace")
    private val postseasonSpots = balance.int("postseason.spots")
    private val chanceIfMadePostseason = cfg.double("teamPostseason.chanceIfMadeLast")
    private val chanceIfMissedPostseason = cfg.double("teamPostseason.chanceIfMissedLast")

    fun threshold(kind: OptionKind): Double = cfg.double("${kind.key}.threshold")

    /** 이 선수에게 걸 수 있는 조항 */
    fun applicable(player: Player): List<OptionKind> =
        OptionKind.entries.filter { it.pitcher == null || it.pitcher == (player is Pitcher) }

    /** "홈런 20개 이상" 같은 이름 */
    fun label(kind: OptionKind): String {
        // 팀 포스트시즌은 기준 수치가 없다
        val t = if (kind == OptionKind.TEAM_POSTSEASON) 0.0 else threshold(kind)
        return when (kind) {
            OptionKind.PLATE_APPEARANCES -> "${t.roundToInt()}타석 이상"
            OptionKind.BATTING_AVERAGE -> "타율 ${rate(t)} 이상"
            OptionKind.HOME_RUNS -> "홈런 ${t.roundToInt()}개 이상"
            OptionKind.RBI -> "타점 ${t.roundToInt()}개 이상"
            OptionKind.STOLEN_BASES -> "도루 ${t.roundToInt()}개 이상"
            OptionKind.INNINGS -> "${t.roundToInt()}이닝 이상"
            OptionKind.WINS -> "${t.roundToInt()}승 이상"
            OptionKind.ERA -> "평균자책 ${twoDecimals(t)} 이하"
            OptionKind.SAVES -> "${t.roundToInt()}세이브 이상"
            OptionKind.HOLDS -> "${t.roundToInt()}홀드 이상"
            OptionKind.APPEARANCES -> "${t.roundToInt()}경기 등판"
            OptionKind.TEAM_POSTSEASON -> "팀 포스트시즌 진출"
        }
    }

    /** 기록 값을 조항 단위 글자로 ("24개", ".312", "3.21", "152이닝") */
    fun valueText(kind: OptionKind, value: Double): String = when (kind) {
        OptionKind.BATTING_AVERAGE -> rate(value)
        OptionKind.ERA -> twoDecimals(value)
        OptionKind.PLATE_APPEARANCES -> "${value.roundToInt()}타석"
        OptionKind.INNINGS -> "${value.toInt()}이닝"
        OptionKind.WINS -> "${value.roundToInt()}승"
        OptionKind.SAVES -> "${value.roundToInt()}세이브"
        OptionKind.HOLDS -> "${value.roundToInt()}홀드"
        OptionKind.APPEARANCES -> "${value.roundToInt()}경기"
        OptionKind.TEAM_POSTSEASON -> ""
        else -> "${value.roundToInt()}개"
    }

    /** 기록에서 이 조항이 보는 값. 표본이 모자라 판정할 수 없는 비율 기록은 null */
    fun valueOf(kind: OptionKind, stats: OptionStats): Double? = when (kind) {
        OptionKind.PLATE_APPEARANCES -> stats.plateAppearances.toDouble()
        OptionKind.BATTING_AVERAGE -> stats.battingAverage.takeIf { stats.plateAppearances >= minPaForRate }
        OptionKind.HOME_RUNS -> stats.homeRuns.toDouble()
        OptionKind.RBI -> stats.rbi.toDouble()
        OptionKind.STOLEN_BASES -> stats.stolenBases.toDouble()
        OptionKind.INNINGS -> stats.outs / 3.0
        OptionKind.WINS -> stats.wins.toDouble()
        OptionKind.ERA -> stats.era.takeIf { stats.outs >= minOutsForRate }
        OptionKind.SAVES -> stats.saves.toDouble()
        OptionKind.HOLDS -> stats.holds.toDouble()
        OptionKind.APPEARANCES -> stats.games.toDouble()
        OptionKind.TEAM_POSTSEASON -> null
    }

    /** 시즌 끝 판정: 기록 조항은 기준선, 팀 포스트시즌은 [madePostseason] */
    fun achieved(kind: OptionKind, stats: OptionStats, madePostseason: Boolean): Boolean {
        if (kind == OptionKind.TEAM_POSTSEASON) return madePostseason
        val value = valueOf(kind, stats) ?: return false
        return if (kind.lowerIsBetter) value <= threshold(kind) else value >= threshold(kind)
    }

    /** 지난 시즌 1군 기록 (없으면 null) */
    fun lastSeason(player: Player, league: League): OptionStats? =
        league.history.careerOf(player.id).lastOrNull { it.season == league.season - 1 }?.let { OptionStats.of(it) }

    /**
     * 선수가 보는 달성 가능성 0~1.
     * @param pace 지난 시즌 기록이 없을 때 대신 볼 올 시즌 페이스 (시즌 중 다년계약, [paceOf]). 없으면 `chanceNoRecord`
     */
    fun chance(kind: OptionKind, player: Player, league: League, standings: Standings, teamId: TeamId, pace: OptionStats? = null): Double {
        if (kind == OptionKind.TEAM_POSTSEASON) {
            val rank = standings.rankOf(teamId)
            return if (rank in 1..postseasonSpots) chanceIfMadePostseason else chanceIfMissedPostseason
        }
        val last = lastSeason(player, league) ?: pace ?: return chanceNoRecord
        val value = valueOf(kind, last) ?: return chanceNoRecord
        val t = threshold(kind)
        val ratio = if (kind.lowerIsBetter) (if (value <= 0.0) RATIO_CAP else t / value) else value / t
        return interpolateAnchors(chanceCurve, ratio.coerceIn(0.0, RATIO_CAP) * PERCENT).coerceIn(0.0, 1.0)
    }

    /** 선수가 이 조항들을 한 해 몇 억으로 쳐 주나 */
    fun valueToPlayer(
        clauses: List<OptionClause>,
        player: Player,
        league: League,
        standings: Standings,
        teamId: TeamId,
        pace: OptionStats? = null,
    ): Double = clauses.sumOf { it.amount * chance(it.kind, player, league, standings, teamId, pace) * riskRate }

    /**
     * 올 시즌 1군 기록을 한 시즌으로 환산한 페이스. 팀이 `minGamesForPace` 경기 이상 치렀을 때만 (너무 이르면 null).
     * 지난 시즌 기록이 없는 첫 시즌의 다년계약 옵션이 전부 "어렵다"가 되지 않게 한다
     */
    fun paceOf(current: OptionStats, teamGames: Int, seasonGames: Int): OptionStats? =
        if (teamGames < minGamesForPace || teamGames <= 0) null else current.scaled(seasonGames.toDouble() / teamGames)

    private fun rate(value: Double): String = ".${((value * 1000).roundToInt()).toString().padStart(3, '0')}"

    private fun twoDecimals(value: Double): String {
        val hundredths = (value * 100).roundToInt()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }

    private companion object {
        /** 비율이 이 이상이면 확실한 셈 (곡선 끝) */
        const val RATIO_CAP = 3.0
        const val PERCENT = 100.0
    }
}
