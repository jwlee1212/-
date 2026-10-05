package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.league.AwardEntry
import baseballgm.league.AwardKind
import baseballgm.league.LeagueHistory
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.stats.LeagueConstants
import baseballgm.stats.Sabermetrics
import baseballgm.stats.War
import baseballgm.util.fixed

/**
 * 시즌 시상 (2026-10-01, 진단 3번).
 *
 * 정규시즌 기록만 본다 (포스트시즌 성적은 정규시즌 타이틀에 섞지 않는다 — Postseason 과 같은 원칙).
 * - **타이틀**: 타율·평균자책은 규정 타석·이닝, 나머지(홈런·타점·안타·도루·다승·탈삼진·세이브·홀드)는 누적 1위
 * - **MVP**: WAR 1위. 현실은 기자 투표지만, 투표를 흉내 내면 근거를 설명하기 어려워서 가장 투명한 기준을 썼다
 * - **신인왕**: 입단 5년 이내 + 이전 시즌 1군 60타석·30이닝 이하 국내 선수 중 WAR 1위
 * - **골든글러브**: 주 포지션별 WAR 1위 (외야는 세 명, 투수 한 명). 최소 출장은 규정의 일정 비율
 *
 * 난수를 쓰지 않는다 — 시즌이 끝난 순간 결과가 정해지고, 화면(시상식)과 스토브리그(역사 기록)가 같은 결과를 본다.
 * 동점이면 선수 ID 순으로 정한다 (매번 같게).
 */
class SeasonAwards(private val balance: BalanceConfig) {

    private val section = balance.section("awards")
    private val games = balance.int("schedule.gamesPerTeam")
    private val qualifiedPa = (games * section.double("qualifiedPaPerGame")).toInt()
    private val qualifiedOuts = (games * section.double("qualifiedInningsPerGame") * OUTS_PER_INNING).toInt()
    private val rookieWithin = section.int("rookieWithinSeasons")
    private val rookieMaxPa = section.int("rookieMaxPriorPa")
    private val rookieMaxOuts = section.int("rookieMaxPriorInnings") * OUTS_PER_INNING
    private val goldenGlovePa = (qualifiedPa * section.double("goldenGlovePaShare")).toInt()
    private val goldenGloveOuts = (qualifiedOuts * section.double("goldenGlovePitcherInningsShare")).toInt()
    private val outfielders = section.int("outfieldGoldenGloves")
    private val war = War(balance, Sabermetrics(balance))

    fun compute(state: SeasonState, history: LeagueHistory): List<AwardEntry> {
        val constants = LeagueConstants.from(state.stats, balance)
        val ids = (state.stats.allBatting().keys + state.stats.allPitching().keys).sortedBy { it.value }
        val players = ids.mapNotNull { id -> runCatching { state.player(id) }.getOrNull()?.takeIf { it.teamId != null } }
        val warOf = players.associate { player ->
            val park = state.league.team(player.teamId!!).parkFactor
            player.id to war.of(player, state.stats, constants, park).war
        }
        val bat = players.associateWith { state.stats.battingOf(it.id).total }
        val pitch = players.associateWith { state.stats.pitchingOf(it.id).total }
        val awards = mutableListOf<AwardEntry>()

        fun entry(kind: AwardKind, player: Player, value: String, position: String? = null) =
            AwardEntry(kind, player.id, player.registeredName, player.teamId!!, value, position)

        fun <T : Comparable<T>> best(pool: List<Player>, key: (Player) -> T): Player? =
            pool.maxWithOrNull(compareBy<Player> { key(it) }.thenByDescending { it.id.value })

        // 타이틀 — 타자
        val batters = players.filter { bat.getValue(it).plateAppearances > 0 }
        best(batters.filter { bat.getValue(it).plateAppearances >= qualifiedPa }) { bat.getValue(it).battingAverage }
            ?.let { awards += entry(AwardKind.BATTING, it, "타율 ${bat.getValue(it).battingAverage.fixed(3)}") }
        best(batters) { bat.getValue(it).homeRuns }?.takeIf { bat.getValue(it).homeRuns > 0 }
            ?.let { awards += entry(AwardKind.HOME_RUNS, it, "${bat.getValue(it).homeRuns}홈런") }
        best(batters) { bat.getValue(it).rbi }?.takeIf { bat.getValue(it).rbi > 0 }
            ?.let { awards += entry(AwardKind.RBI, it, "${bat.getValue(it).rbi}타점") }
        best(batters) { bat.getValue(it).hits }?.takeIf { bat.getValue(it).hits > 0 }
            ?.let { awards += entry(AwardKind.HITS, it, "${bat.getValue(it).hits}안타") }
        best(batters) { bat.getValue(it).stolenBases }?.takeIf { bat.getValue(it).stolenBases > 0 }
            ?.let { awards += entry(AwardKind.STEALS, it, "${bat.getValue(it).stolenBases}도루") }

        // 타이틀 — 투수
        val pitchers = players.filter { pitch.getValue(it).outs > 0 }
        best(pitchers) { pitch.getValue(it).wins }?.takeIf { pitch.getValue(it).wins > 0 }
            ?.let { awards += entry(AwardKind.WINS, it, "${pitch.getValue(it).wins}승") }
        best(pitchers.filter { pitch.getValue(it).outs >= qualifiedOuts }) { -pitch.getValue(it).era }
            ?.let { awards += entry(AwardKind.ERA, it, "평균자책 ${pitch.getValue(it).era.fixed(2)}") }
        best(pitchers) { pitch.getValue(it).strikeouts }?.takeIf { pitch.getValue(it).strikeouts > 0 }
            ?.let { awards += entry(AwardKind.STRIKEOUTS, it, "${pitch.getValue(it).strikeouts}탈삼진") }
        best(pitchers) { pitch.getValue(it).saves }?.takeIf { pitch.getValue(it).saves > 0 }
            ?.let { awards += entry(AwardKind.SAVES, it, "${pitch.getValue(it).saves}세이브") }
        best(pitchers) { pitch.getValue(it).holds }?.takeIf { pitch.getValue(it).holds > 0 }
            ?.let { awards += entry(AwardKind.HOLDS, it, "${pitch.getValue(it).holds}홀드") }

        // MVP
        best(players) { warOf.getValue(it.id) }?.let { awards += entry(AwardKind.MVP, it, "WAR ${warOf.getValue(it.id).fixed(1)}") }

        // 신인왕
        val rookies = players.filter { player ->
            if (player.isForeign || player.debutSeason < state.season - rookieWithin + 1) return@filter false
            val prior = history.careerOf(player.id).filter { it.season < state.season }
            prior.sumOf { it.bat?.pa ?: 0 } <= rookieMaxPa && prior.sumOf { it.pitch?.outs ?: 0 } <= rookieMaxOuts
        }
        best(rookies) { warOf.getValue(it.id) }?.takeIf { warOf.getValue(it.id) > 0 }
            ?.let { awards += entry(AwardKind.ROOKIE, it, "WAR ${warOf.getValue(it.id).fixed(1)}") }

        // 골든글러브
        // 최소 출장을 채운 선수 중에서 뽑는다. 그 포지션에 아무도 못 채웠으면 출장한 선수 전체에서 (자리는 늘 시상한다)
        val allBatters = batters.filterIsInstance<Batter>()
        fun pool(positions: Set<String>): List<Batter> {
            val atPosition = allBatters.filter { it.primaryPosition.label in positions }
            return atPosition.filter { bat.getValue(it).plateAppearances >= goldenGlovePa }.ifEmpty { atPosition }
        }
        listOf("C", "1B", "2B", "3B", "SS").forEach { position ->
            best(pool(setOf(position))) { warOf.getValue(it.id) }
                ?.let { awards += entry(AwardKind.GOLDEN_GLOVE, it, "WAR ${warOf.getValue(it.id).fixed(1)}", position) }
        }
        pool(OUTFIELD)
            .sortedWith(compareByDescending<Player> { warOf.getValue(it.id) }.thenBy { it.id.value })
            .take(outfielders)
            .forEach { awards += entry(AwardKind.GOLDEN_GLOVE, it, "WAR ${warOf.getValue(it.id).fixed(1)}", "OF") }
        // 지명타자: 주 포지션이 지명타자인 선수 중에서. 없으면(그해 지명타자 주 포지션 선수가 안 뛰었으면)
        // 아직 골든글러브를 못 받은 타자 중 WAR 1위 — 실제로는 그해 지명타자로 가장 많이 뛴 선수가 받는데,
        // 이 게임은 경기별 수비 위치를 따로 세지 않아서 대신 쓰는 기준이다 (임시 결정)
        val awarded = awards.filter { it.kind == AwardKind.GOLDEN_GLOVE }.map { it.playerId }.toSet()
        best(pool(setOf("DH")).ifEmpty { allBatters.filter { it.id !in awarded } }) { warOf.getValue(it.id) }
            ?.let { awards += entry(AwardKind.GOLDEN_GLOVE, it, "WAR ${warOf.getValue(it.id).fixed(1)}", "DH") }
        val allPitchers = pitchers.filterIsInstance<Pitcher>()
        best(allPitchers.filter { pitch.getValue(it).outs >= goldenGloveOuts }.ifEmpty { allPitchers }) { warOf.getValue(it.id) }
            ?.let { awards += entry(AwardKind.GOLDEN_GLOVE, it, "WAR ${warOf.getValue(it.id).fixed(1)}", "P") }

        return awards
    }

    private companion object {
        const val OUTS_PER_INNING = 3
        val OUTFIELD = setOf("LF", "CF", "RF")
    }
}
