package baseballgm.events

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.util.chance
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random

/** 국제대회 종류 (docs/12). */
@Serializable
enum class TournamentKind(val configKey: String) {
    ASIAN_GAMES("asianGames"),
    OLYMPICS("olympics"),
}

/** 병역 특례 기준. */
@Serializable
enum class ExemptionRule { GOLD, MEDAL }

/** 대회 규정 한 벌. */
data class TournamentRules(
    val kind: TournamentKind,
    val label: String,
    val season: Int,
    val startWeek: Int,
    val weeksMissed: Int,
    val squadSize: Int,
    /** 0 이면 연령 제한 없음 */
    val ageLimit: Int,
    val wildcards: Int,
    val maxPerTeam: Int,
    val exemption: ExemptionRule,
    val rivals: List<String>,
) {
    val endWeek: Int get() = startWeek + weeksMissed - 1

    fun missesWeek(week: Int): Boolean = week in startWeek..endWeek
}

/** 대표 선발 한 명. */
@Serializable
data class SquadMember(
    val playerId: PlayerId,
    val teamId: TeamId,
    /** 연령 제한을 넘겨 와일드카드로 뽑혔는가 */
    val wildcard: Boolean,
)

/** 대회 순위. */
@Serializable
enum class Medal(val label: String) {
    GOLD("금메달"), SILVER("은메달"), BRONZE("동메달"), NONE("메달 없음")
}

/** 대회 결과 (docs/12). */
@Serializable
data class TournamentResult(
    val kind: TournamentKind,
    val label: String,
    val season: Int,
    val squad: List<SquadMember>,
    val medal: Medal,
    /** 예선·결승 스코어 표시용 */
    val scores: List<String>,
    /** 병역 특례를 받은 선수 */
    val exempted: List<PlayerId>,
) {
    fun summary(): String = buildString {
        append("$season $label ${medal.label}")
        if (exempted.isNotEmpty()) append(" — 병역 특례 ${exempted.size}명")
    }
}

/**
 * 국제대회와 병역 특례 (docs/12).
 *
 * **2026년 첫 시즌에 아시안게임이 열린다** — 게임을 시작하면 바로 겪는 첫 대형 이벤트다.
 * 리그 사무국 AI 가 대표를 뽑고, 대회 주간에는 소속팀 경기에 나오지 않는다. 금메달이면
 * 대표팀의 미필 선수 전원이 병역을 면제받는다 — 그래서 "우리 팀 유망주가 대표로 뽑혔는가"가
 * 팀 운영에 직접 영향을 준다(입대로 두 시즌을 비우지 않아도 된다).
 */
class InternationalTournament(
    private val balance: BalanceConfig,
    private val strength: StrengthCalculator,
) {
    private val section = balance.section("internationalTournament")
    private val strengthPivot = section.double("strengthPivot")
    private val strengthSlope = section.double("strengthSlope")
    private val goldRange = section.doubleRange("goldChanceRange")
    private val silverShare = section.double("silverShareOfRest")
    private val bronzeShare = section.double("bronzeShareOfRest")

    /** 이번 시즌에 열리는 대회. 없으면 null. */
    fun rulesFor(season: Int): TournamentRules? =
        TournamentKind.entries.firstNotNullOfOrNull { kind -> rulesOf(kind, season) }

    private fun rulesOf(kind: TournamentKind, season: Int): TournamentRules? {
        val sub = section.section(kind.configKey)
        val first = sub.int("firstSeason")
        val every = sub.int("everyYears")
        if (season < first || (season - first) % every != 0) return null
        return TournamentRules(
            kind = kind,
            label = sub.string("label"),
            season = season,
            startWeek = sub.int("startWeek"),
            weeksMissed = sub.int("weeksMissed"),
            squadSize = sub.int("squadSize"),
            ageLimit = sub.int("ageLimit"),
            wildcards = sub.int("wildcards"),
            maxPerTeam = sub.int("maxPerTeam"),
            exemption = if (sub.string("exemptionOn") == "gold") ExemptionRule.GOLD else ExemptionRule.MEDAL,
            rivals = sub.stringList("rivals"),
        )
    }

    /**
     * 대표 선발 (docs/12).
     *
     * 연령 제한 안에서 능력치 순으로 뽑고, 제한을 넘는 선수는 와일드카드로 몇 명만 넣는다.
     * **팀당 최대 인원**이 있어서 강팀 선수만으로 채워지지 않는다. 투수·야수 비율도 맞춘다 —
     * 안 맞추면 투수 20명짜리 대표팀이 나온다.
     */
    fun selectSquad(league: League, rules: TournamentRules): List<SquadMember> {
        val available = league.players.filter { player ->
            player.origin != Origin.FOREIGN &&
                player.military.isAvailable &&
                !player.condition.isInjured &&
                player.teamId != null
        }
        val eligible = available.filter { rules.ageLimit == 0 || it.ageIn(rules.season) <= rules.ageLimit }
        val overAge = available.filterNot { rules.ageLimit == 0 || it.ageIn(rules.season) <= rules.ageLimit }

        val pitcherQuota = (rules.squadSize * PITCHER_SHARE).roundToInt()
        val squad = mutableListOf<SquadMember>()
        val perTeam = mutableMapOf<TeamId, Int>()

        fun tryAdd(player: Player, wildcard: Boolean): Boolean {
            val teamId = player.teamId ?: return false
            if ((perTeam[teamId] ?: 0) >= rules.maxPerTeam) return false
            if (squad.any { it.playerId == player.id }) return false
            squad += SquadMember(player.id, teamId, wildcard)
            perTeam[teamId] = (perTeam[teamId] ?: 0) + 1
            return true
        }

        // 와일드카드를 먼저 잡는다 — 제한 나이를 넘긴 최고 선수들이 대표팀의 기둥이 된다
        overAge.sortedByDescending { strength.overallOf(it) }
            .take(rules.wildcards * WILDCARD_SEARCH)
            .forEach { if (squad.count { member -> member.wildcard } < rules.wildcards) tryAdd(it, wildcard = true) }

        val pitchers = eligible.filterIsInstance<Pitcher>().sortedByDescending { strength.overallOf(it) }
        val batters = eligible.filterIsInstance<Batter>().sortedByDescending { strength.overallOf(it) }

        pitchers.forEach { if (squad.count { m -> league.player(m.playerId) is Pitcher } < pitcherQuota) tryAdd(it, false) }
        batters.forEach { if (squad.size < rules.squadSize) tryAdd(it, false) }
        pitchers.forEach { if (squad.size < rules.squadSize) tryAdd(it, false) }

        return squad.take(rules.squadSize)
    }

    /** 대표팀 전력 (상위 선수 평균). 우승 확률의 재료다. */
    fun squadStrength(league: League, squad: List<SquadMember>): Double {
        if (squad.isEmpty()) return 0.0
        return squad.map { strength.overallOf(league.player(it.playerId)) }
            .sortedDescending()
            .take(CORE_SIZE)
            .average()
    }

    /**
     * 대회를 치른다.
     *
     * 대표팀 전력으로 금메달 확률을 뽑고, 못 따면 은·동·노메달로 나눈다. 스코어는 보여주기용
     * 이지만 **결과와 앞뒤가 맞게** 만든다 (금메달이면 결승을 이긴 스코어가 나온다).
     */
    fun play(league: League, rules: TournamentRules, squad: List<SquadMember>, random: Random): TournamentResult {
        val power = squadStrength(league, squad)
        val goldChance = (goldRange.start +
            (goldRange.endInclusive - goldRange.start) *
            logistic((power - strengthPivot) * strengthSlope * LOGISTIC_SCALE))
            .coerceIn(goldRange.start, goldRange.endInclusive)

        val medal = when {
            random.chance(goldChance) -> Medal.GOLD
            random.chance(silverShare) -> Medal.SILVER
            random.chance(bronzeShare) -> Medal.BRONZE
            else -> Medal.NONE
        }
        val exempted = exemptedBy(league, rules, squad, medal)
        return TournamentResult(
            kind = rules.kind,
            label = rules.label,
            season = rules.season,
            squad = squad,
            medal = medal,
            scores = scoresFor(rules, medal, random),
            exempted = exempted,
        )
    }

    /** 병역 특례 대상 (docs/12). 금메달(아시안게임) 또는 메달(올림픽) + 미필. */
    private fun exemptedBy(
        league: League,
        rules: TournamentRules,
        squad: List<SquadMember>,
        medal: Medal,
    ): List<PlayerId> {
        val qualifies = when (rules.exemption) {
            ExemptionRule.GOLD -> medal == Medal.GOLD
            ExemptionRule.MEDAL -> medal != Medal.NONE
        }
        if (!qualifies) return emptyList()
        return squad.map { it.playerId }
            .filter { league.player(it).military is MilitaryStatus.Unfulfilled }
    }

    private fun scoresFor(rules: TournamentRules, medal: Medal, random: Random): List<String> {
        val rivals = rules.rivals.ifEmpty { listOf("상대") }
        val group = rivals.take(2).map { rival ->
            val us = random.nextInt(3, 11)
            val them = random.nextInt(0, us)
            "예선 $rival ${us}-$them 승"
        }
        val finalLine = when (medal) {
            Medal.GOLD -> "결승 ${rivals.first()} ${random.nextInt(3, 8)}-${random.nextInt(0, 3)} 승"
            Medal.SILVER -> "결승 ${rivals.first()} ${random.nextInt(0, 3)}-${random.nextInt(3, 8)} 패"
            Medal.BRONZE -> "3·4위전 ${rivals.last()} ${random.nextInt(3, 9)}-${random.nextInt(0, 3)} 승"
            Medal.NONE -> "준결승 ${rivals.first()} ${random.nextInt(0, 3)}-${random.nextInt(4, 9)} 패"
        }
        return group + finalLine
    }

    private fun logistic(x: Double): Double = 1.0 / (1.0 + kotlin.math.exp(-x))

    private companion object {
        /** 대표팀 전력을 볼 때 쓰는 핵심 인원 */
        const val CORE_SIZE = 14
        const val PITCHER_SHARE = 0.45
        const val WILDCARD_SEARCH = 4
        const val LOGISTIC_SCALE = 18.0
    }
}
