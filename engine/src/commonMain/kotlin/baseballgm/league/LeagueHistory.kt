package baseballgm.league

import baseballgm.model.Attribute
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.stats.BattingLine
import baseballgm.stats.PitchingLine
import kotlinx.serialization.Serializable

/**
 * 리그 역사 (2026-10-01, 진단 3번 "시즌이 끝나도 기억이 남지 않는다").
 *
 * 시즌 기록(`SeasonStats`)은 시즌이 끝나면 사라진다. 여기에 **선수별 시즌 기록·시즌 결과·시상**을 쌓아
 * 통산 기록, 역대 우승, 역대 수상자를 보여 준다. 리그 데이터에 들어 있어서 세이브에 같이 저장된다.
 *
 * 크기 때문에 시즌 기록은 핵심 칸만 담는다([BatSeason]/[PitchSeason]) — 30시즌을 해도 폰 웹 저장 한도 안에 들어가야 한다.
 * 리그를 떠난 선수(은퇴·방출)는 시즌별 기록 대신 통산 합계 한 줄([RetiredCareer])로 줄인다.
 */
@Serializable
data class LeagueHistory(
    /** 리그에 있는 선수의 시즌별 1군 기록 (정규시즌) */
    val careers: Map<PlayerId, List<CareerSeason>> = emptyMap(),
    /** 리그를 떠난 선수의 통산 */
    val retired: Map<PlayerId, RetiredCareer> = emptyMap(),
    /** 시즌 결과와 시상, 오래된 시즌이 앞 */
    val seasons: List<SeasonRecord> = emptyList(),
    /**
     * 리그에 있는 선수의 시즌별 능력치 (2026-10-03, 선수 상세 연도별 그래프). 오래된 시즌이 앞.
     * 진짜 값이라 내용은 `internal` — 화면은 `ScoutingService.ratingHistory` 를 거쳐 정확도만큼만 본다 (불변 원칙 4)
     */
    val ratings: Map<PlayerId, List<RatingSnapshot>> = emptyMap(),
) {
    fun careerOf(id: PlayerId): List<CareerSeason> = careers[id].orEmpty()

    /** 이 선수가 받은 상 (오래된 것부터) */
    fun awardsOf(id: PlayerId): List<Pair<Int, AwardEntry>> =
        seasons.flatMap { record -> record.awards.filter { it.playerId == id }.map { record.season to it } }

    fun seasonOf(season: Int): SeasonRecord? = seasons.firstOrNull { it.season == season }

    /**
     * [keep] 에 없는 선수(은퇴·방출로 리그를 떠난 선수)의 시즌별 기록을 통산 한 줄로 줄인다.
     * 30시즌을 해도 세이브가 커지지 않게 하려는 것이다.
     */
    fun withDeparted(keep: Set<PlayerId>): LeagueHistory {
        val (stay, gone) = careers.entries.partition { it.key in keep }
        // 떠났다 돌아왔다 다시 떠난 선수는 이전 요약에 합친다
        val summarized = gone.associate { (id, lines) -> id to (retired[id]?.let { it + summarize(lines) } ?: summarize(lines)) }
        return copy(
            careers = stay.associate { it.key to it.value },
            retired = retired + summarized,
            // 떠난 선수의 능력치 기록은 볼 일이 없어 버린다 (세이브 크기)
            ratings = ratings.filterKeys { it in keep },
        )
    }

    /**
     * 통산: 리그를 떠났던 시절의 요약(있으면) + 지금 리그에서의 시즌별 기록.
     * 방출됐다가 FA 로 돌아온 선수처럼 두 곳에 기록이 나뉜 경우에도 한 번씩만 센다.
     */
    fun totalOf(id: PlayerId): RetiredCareer? {
        val lines = careers[id].orEmpty()
        val earlier = retired[id]
        if (lines.isEmpty()) return earlier
        val now = summarize(lines)
        return earlier?.let { it + now } ?: now
    }

    private fun summarize(lines: List<CareerSeason>): RetiredCareer {
        val bats = lines.mapNotNull { it.bat }
        val pitches = lines.mapNotNull { it.pitch }
        return RetiredCareer(
            name = lines.last().name,
            lastTeam = lines.last().team,
            firstSeason = lines.first().season,
            lastSeason = lines.last().season,
            bat = bats.reduceOrNull { a, b -> a + b },
            pitch = pitches.reduceOrNull { a, b -> a + b },
            war = lines.sumOf { it.war },
        )
    }
}

/** 타자 시즌 기록 — 핵심 칸만 */
@Serializable
data class BatSeason(
    val pa: Int = 0,
    val ab: Int = 0,
    val h: Int = 0,
    val d: Int = 0,
    val t: Int = 0,
    val hr: Int = 0,
    val r: Int = 0,
    val rbi: Int = 0,
    val bb: Int = 0,
    val so: Int = 0,
    val sb: Int = 0,
    val hbp: Int = 0,
    val sf: Int = 0,
) {
    operator fun plus(o: BatSeason) = BatSeason(
        pa + o.pa, ab + o.ab, h + o.h, d + o.d, t + o.t, hr + o.hr, r + o.r, rbi + o.rbi, bb + o.bb, so + o.so, sb + o.sb,
        hbp + o.hbp, sf + o.sf,
    )

    val avg: Double get() = if (ab == 0) 0.0 else h.toDouble() / ab
    val obp: Double get() = (ab + bb + hbp + sf).let { if (it == 0) 0.0 else (h + bb + hbp).toDouble() / it }
    val slg: Double get() = if (ab == 0) 0.0 else (h + d + 2 * t + 3 * hr).toDouble() / ab
    val ops: Double get() = obp + slg

    companion object {
        fun of(line: BattingLine) = BatSeason(
            pa = line.plateAppearances, ab = line.atBats, h = line.hits, d = line.doubles, t = line.triples,
            hr = line.homeRuns, r = line.runs, rbi = line.rbi, bb = line.walks, so = line.strikeouts,
            sb = line.stolenBases, hbp = line.hitByPitch, sf = line.sacFlies,
        )
    }
}

/** 투수 시즌 기록 — 핵심 칸만 */
@Serializable
data class PitchSeason(
    val g: Int = 0,
    val gs: Int = 0,
    val outs: Int = 0,
    val h: Int = 0,
    val er: Int = 0,
    val bb: Int = 0,
    val so: Int = 0,
    val w: Int = 0,
    val l: Int = 0,
    val sv: Int = 0,
    val hld: Int = 0,
) {
    operator fun plus(o: PitchSeason) = PitchSeason(
        g + o.g, gs + o.gs, outs + o.outs, h + o.h, er + o.er, bb + o.bb, so + o.so, w + o.w, l + o.l, sv + o.sv, hld + o.hld,
    )

    val era: Double get() = if (outs == 0) 0.0 else er * 27.0 / outs
    val innings: Double get() = outs / 3.0

    companion object {
        fun of(line: PitchingLine) = PitchSeason(
            g = line.games, gs = line.gamesStarted, outs = line.outs, h = line.hits, er = line.earnedRuns,
            bb = line.walks, so = line.strikeouts, w = line.wins, l = line.losses, sv = line.saves, hld = line.holds,
        )
    }
}

/**
 * 한 시즌이 끝났을 때의 능력치 (성장·노화 전, 그 시즌을 뛴 모습).
 * 세이브 크기 때문에 능력치 이름 없이 숫자만 순서대로 담는다 — 타자는 [Attribute.batterAttributes],
 * 투수는 [Attribute.pitcherAttributes] 순서다.
 */
@Serializable
data class RatingSnapshot(
    val season: Int,
    internal val values: List<Int>,
) {
    internal fun toMap(pitcher: Boolean): Map<Attribute, Int> =
        (if (pitcher) Attribute.pitcherAttributes else Attribute.batterAttributes).zip(values).toMap()

    internal companion object {
        fun of(season: Int, player: baseballgm.model.Player): RatingSnapshot {
            val order = if (player is baseballgm.model.Pitcher) Attribute.pitcherAttributes else Attribute.batterAttributes
            return RatingSnapshot(season, order.map { player.rating(it) })
        }
    }
}

/** 한 선수의 한 시즌 */
@Serializable
data class CareerSeason(
    val season: Int,
    val team: TeamId,
    /** 그 시즌 등록명 — 선수가 리그를 떠나도 통산 기록에 이름이 남게 */
    val name: String = "",
    val bat: BatSeason? = null,
    val pitch: PitchSeason? = null,
    val war: Double = 0.0,
)

/** 리그를 떠난 선수의 통산 한 줄 */
@Serializable
data class RetiredCareer(
    val name: String,
    val lastTeam: TeamId?,
    val firstSeason: Int,
    val lastSeason: Int,
    val bat: BatSeason? = null,
    val pitch: PitchSeason? = null,
    val war: Double = 0.0,
) {
    /** 앞 시절 + 뒤 시절 */
    operator fun plus(later: RetiredCareer) = RetiredCareer(
        name = later.name,
        lastTeam = later.lastTeam,
        firstSeason = minOf(firstSeason, later.firstSeason),
        lastSeason = maxOf(lastSeason, later.lastSeason),
        bat = listOfNotNull(bat, later.bat).reduceOrNull { a, b -> a + b },
        pitch = listOfNotNull(pitch, later.pitch).reduceOrNull { a, b -> a + b },
        war = war + later.war,
    )
}

/** 상 종류 */
@Serializable
enum class AwardKind(val label: String, val title: Boolean) {
    MVP("MVP", false),
    ROOKIE("신인왕", false),
    GOLDEN_GLOVE("골든글러브", false),
    BATTING("타격왕", true),
    HOME_RUNS("홈런왕", true),
    RBI("타점왕", true),
    HITS("최다안타", true),
    STEALS("도루왕", true),
    WINS("다승왕", true),
    ERA("평균자책점 1위", true),
    STRIKEOUTS("탈삼진왕", true),
    SAVES("세이브왕", true),
    HOLDS("홀드왕", true),
}

/** 상 하나 */
@Serializable
data class AwardEntry(
    val kind: AwardKind,
    val playerId: PlayerId,
    /** 수상 당시 이름 — 은퇴해도 역대 수상자 목록에 남는다 */
    val name: String,
    val teamId: TeamId,
    /** 수상 근거 한 줄 ("WAR 7.2", "타율 0.342", "45홈런") */
    val value: String,
    /** 골든글러브 포지션 */
    val position: String? = null,
)

/** 한 시즌의 결과 */
@Serializable
data class SeasonRecord(
    val season: Int,
    val champion: TeamId?,
    val runnerUp: TeamId?,
    /** 정규시즌 순위 (1위부터): 구단, 승, 패, 무 */
    val standings: List<TeamFinish>,
    val awards: List<AwardEntry>,
)

@Serializable
data class TeamFinish(val teamId: TeamId, val wins: Int, val losses: Int, val ties: Int)
