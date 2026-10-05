package baseballgm.app

import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.season.WatchedGame
import baseballgm.season.WeekReport
import baseballgm.sim.GameEvent
import baseballgm.sim.GameStarted
import baseballgm.sim.Half
import baseballgm.sim.PlateAppearanceCompleted
import baseballgm.sim.WildPitchThrown
import baseballgm.stats.BattingLine
import baseballgm.stats.PitchingLine
import baseballgm.text.CommentaryRenderer
import baseballgm.util.fixed

/** 경기 결과 (우리 팀 기준) */
enum class GameOutcome(val label: String) { WIN("승"), LOSS("패"), TIE("무") }

/**
 * 결과 공개 화면의 경기 한 장 (2026-10-02, 재미 개선 1번 "결과 공개 연출").
 * 이번 주 일정의 모든 경기가 한 장씩 있다. 아직 안 치른 경기(주중에 멈췄을 때)는 [result] 가 null.
 *
 * @param ourStarter / theirStarter 그날 선발 투수 (치른 경기만)
 */
data class RevealGame(
    val index: Int,
    val dayLabel: String?,
    val opponent: TeamId,
    val home: Boolean,
    val ourStarter: String?,
    val theirStarter: String?,
    val result: RevealResult?,
)

/**
 * 치른 경기의 결과.
 * @param ourInnings / theirInnings 이닝별 득점 (라인 스코어가 한 칸씩 채워지는 연출에 쓴다)
 * @param tags 경기 성격 ("끝내기 승", "역전승", "연장 11회" …) 최대 2개 — 칩 규칙
 * @param tense 접전이라 연출을 늦춘다 (1점 차·끝내기·연장·역전)
 * @param keyPlay 승부를 가른 장면 한 줄 (결승 득점 타석). 무승부·장면을 못 찾으면 null
 * @param decisions 승·패·세이브 투수 한 줄 ("승 김OO · 패 박OO · 세 이OO")
 */
data class RevealResult(
    val ourScore: Int,
    val theirScore: Int,
    val outcome: GameOutcome,
    val ourInnings: List<Int>,
    val theirInnings: List<Int>,
    val tags: List<String>,
    val tense: Boolean,
    val keyPlay: String?,
    val decisions: String?,
)

/**
 * 이번 주 답한 돌발 이벤트 한 줄. 공개 목록에서 [afterGame] 번째 경기 카드 바로 뒤에 끼운다 (-1 = 주 시작, 첫 카드 앞).
 */
data class RevealIncident(val afterGame: Int, val text: String)

/** 이번 주 영웅: 우리 팀에서 한 주 기록이 가장 좋은 선수 */
data class WeekHero(val playerId: PlayerId, val line: String)

/**
 * 한 주 결과 공개 (재미 개선 1번).
 *
 * 경기를 다시 돌리지 않는다. 주간 진행이 남긴 **박스스코어와 이벤트 스트림**을 읽어 공개 순서·태그·장면만 정한다
 * (불변 원칙 5). 난수도 쓰지 않는다 — 같은 주는 몇 번 열어도 같은 연출이다.
 */
data class WeekReveal(
    val week: Int,
    val games: List<RevealGame>,
    val incidents: List<RevealIncident>,
    val rankBefore: Int?,
    val rankAfter: Int,
    /** 주가 끝났는가. 끝나야 전적·순위·영웅을 정리한다 */
    val complete: Boolean,
    val hero: WeekHero?,
) {
    /** 지금까지 치른 경기 수 (앞에서부터) */
    val played: Int get() = games.count { it.result != null }
    val wins: Int get() = games.count { it.result?.outcome == GameOutcome.WIN }
    val losses: Int get() = games.count { it.result?.outcome == GameOutcome.LOSS }
    val ties: Int get() = games.count { it.result?.outcome == GameOutcome.TIE }

    companion object {
        /**
         * [week] 주차의 공개 데이터. 주가 끝났으면 주간 리포트에서, 주중에 멈춰 있으면 지금까지 치른 경기에서 만든다.
         * @param rankBefore 주를 시작할 때 순위. 모르면 null (순위 변화 대신 지금 순위만 보인다)
         */
        fun of(session: GameSession, week: Int, rankBefore: Int?): WeekReveal {
            val me = session.userTeamId
            val report = session.lastReport?.takeIf { it.week == week }
            val watched = (report?.watched ?: session.weekGamesSoFar())
                .filter { it.box.home.teamId == me || it.box.away.teamId == me }
            val renderer = CommentaryRenderer { session.nameOf(it) }
            // 관전 기록은 치른 순서대로다. 일정표(요일 순)의 앞에서부터 짝을 맞춘다
            val schedule = session.weekSchedule(week).sortedBy { it.day }
            val games = schedule.mapIndexed { index, slot ->
                val game = watched.getOrNull(index)
                val start = game?.events?.filterIsInstance<GameStarted>()?.firstOrNull()
                val home = slot.home == me
                RevealGame(
                    index = index,
                    dayLabel = DAY_NAMES.getOrNull(slot.day),
                    opponent = if (home) slot.away else slot.home,
                    home = home,
                    ourStarter = start?.let { session.nameOf(if (home) it.homeStarter else it.awayStarter) },
                    theirStarter = start?.let { session.nameOf(if (home) it.awayStarter else it.homeStarter) },
                    result = game?.let { result(it, me, renderer, session) },
                )
            }
            val dayToIndex = schedule.mapIndexed { index, slot -> slot.day to index }.toMap()
            val incidents = session.incidentsOf(week).map { record ->
                RevealIncident(
                    afterGame = record.day?.let { dayToIndex[it] } ?: -1,
                    text = "${record.headline} → ${record.choice}${if (record.delegated) " (비서 처리)" else ""}",
                )
            }
            return WeekReveal(
                week = week,
                games = games,
                incidents = incidents,
                rankBefore = rankBefore,
                rankAfter = session.rank(),
                complete = report != null,
                hero = if (report != null) hero(session, watched.map { it.box }) else null,
            )
        }

        private fun result(game: WatchedGame, me: TeamId, renderer: CommentaryRenderer, session: GameSession): RevealResult {
            val box = game.box
            val home = box.home.teamId == me
            val ours = if (home) box.home else box.away
            val theirs = if (home) box.away else box.home
            val outcome = when {
                box.tie -> GameOutcome.TIE
                box.winner == me -> GameOutcome.WIN
                else -> GameOutcome.LOSS
            }
            val diff = ours.runs - theirs.runs
            // 이긴 팀이 경기 중 가장 크게 뒤졌던 점수 (반 이닝마다 누적으로 잰다). 3점 이상이면 역전
            val comebackDeficit = if (outcome == GameOutcome.TIE) 0 else maxDeficit(box.away.inningRuns, box.home.inningRuns, winnerHome = box.winner == box.home.teamId)
            val extra = box.innings > REGULATION_INNINGS
            val tags = buildList {
                if (box.walkOff) add(if (outcome == GameOutcome.WIN) "끝내기 승" else "끝내기 패")
                if (comebackDeficit >= COMEBACK_RUNS) add(if (outcome == GameOutcome.WIN) "역전승" else "역전패")
                if (extra) add("연장 ${box.innings}회")
                if (outcome == GameOutcome.WIN && theirs.runs == 0) add("완봉승")
                if (outcome == GameOutcome.LOSS && ours.runs == 0) add("영봉패")
                if (kotlin.math.abs(diff) >= BLOWOUT_RUNS) add(if (diff > 0) "대승" else "대패")
                if (kotlin.math.abs(diff) == 1 && !box.walkOff) add("1점 차")
            }.take(MAX_TAGS)
            val decisions = listOfNotNull(
                box.winningPitcher?.let { "승 ${session.nameOf(it)}" },
                box.losingPitcher?.let { "패 ${session.nameOf(it)}" },
                box.savePitcher?.let { "세 ${session.nameOf(it)}" },
            ).joinToString(" · ").ifBlank { null }
            return RevealResult(
                ourScore = ours.runs,
                theirScore = theirs.runs,
                outcome = outcome,
                ourInnings = ours.inningRuns,
                theirInnings = theirs.inningRuns,
                tags = tags,
                tense = kotlin.math.abs(diff) <= 1 || box.walkOff || extra || comebackDeficit >= COMEBACK_RUNS,
                keyPlay = if (outcome == GameOutcome.TIE) null else keyPlay(game.events, box.winner!!, renderer),
                decisions = decisions,
            )
        }

        /** 반 이닝마다 점수를 쌓아 가며 이긴 팀이 가장 크게 뒤졌던 점수 차 */
        private fun maxDeficit(away: List<Int>, home: List<Int>, winnerHome: Boolean): Int {
            var awayRuns = 0
            var homeRuns = 0
            var worst = 0
            for (inning in 0 until maxOf(away.size, home.size)) {
                awayRuns += away.getOrElse(inning) { 0 }
                worst = maxOf(worst, if (winnerHome) awayRuns - homeRuns else homeRuns - awayRuns)
                homeRuns += home.getOrElse(inning) { 0 }
                worst = maxOf(worst, if (winnerHome) awayRuns - homeRuns else homeRuns - awayRuns)
            }
            return worst
        }

        /**
         * 결승 장면: 이긴 팀이 **마지막으로 앞서 나간** 득점 장면. 그 뒤로 한 번도 따라잡히지 않았으니 승부를 가른 순간이다.
         * 중계 문장을 그대로 쓴다 (문자 중계와 같은 표현).
         */
        private fun keyPlay(events: List<GameEvent>, winner: TeamId, renderer: CommentaryRenderer): String? {
            val start = events.filterIsInstance<GameStarted>().firstOrNull() ?: return null
            var winnerRuns = 0
            var loserRuns = 0
            var decisive: GameEvent? = null
            var decisiveInning: Pair<Int, Half>? = null
            events.forEach { event ->
                val (batting, runs, inning) = when (event) {
                    is PlateAppearanceCompleted -> Triple(event.battingTeam, event.runs.size, event.inning to event.half)
                    is WildPitchThrown -> Triple(
                        if (event.fieldingTeam == start.homeTeam) start.awayTeam else start.homeTeam,
                        event.runs.size,
                        event.inning to event.half,
                    )
                    else -> return@forEach
                }
                if (runs == 0) return@forEach
                val wasAhead = winnerRuns > loserRuns
                if (batting == winner) winnerRuns += runs else loserRuns += runs
                if (!wasAhead && winnerRuns > loserRuns) {
                    decisive = event
                    decisiveInning = inning
                }
            }
            val play = decisive ?: return null
            val line = renderer.render(listOf(play)).firstOrNull() ?: return null
            val (inning, half) = decisiveInning!!
            return "${inning}회${if (half == Half.TOP) "초" else "말"} · $line"
        }

        /**
         * 이번 주 영웅. 타자는 루타 + 타점 + 볼넷 절반, 투수는 이닝 + 탈삼진 0.3 + 승 2 + 세이브 1.5 − 자책점 2 로 점수를 매겨
         * 가장 높은 우리 선수를 고른다. 화면 연출용 순위라 balance.json 이 아니라 여기 둔다. 동점은 선수 id 순.
         */
        private fun hero(session: GameSession, boxes: List<baseballgm.stats.BoxScore>): WeekHero? {
            val me = session.userTeamId
            val batting = mutableMapOf<PlayerId, BattingLine>()
            val pitching = mutableMapOf<PlayerId, PitchingLine>()
            boxes.forEach { box ->
                val ours = when (me) {
                    box.home.teamId -> box.home
                    box.away.teamId -> box.away
                    else -> return@forEach
                }
                ours.batting.forEach { (id, line) -> batting[id] = (batting[id] ?: BattingLine.EMPTY) + line.total }
                ours.pitching.forEach { (id, line) -> pitching[id] = (pitching[id] ?: PitchingLine.EMPTY) + line.total }
            }
            val batters = batting.map { (id, l) ->
                val totalBases = l.hits + l.doubles + 2 * l.triples + 3 * l.homeRuns
                Triple(id, totalBases + l.rbi + l.walks * 0.5, batterLine(l))
            }
            val pitchers = pitching.map { (id, l) ->
                Triple(id, l.outs / 3.0 + l.strikeouts * 0.3 + l.wins * 2.0 + l.saves * 1.5 - l.earnedRuns * 2.0, pitcherLine(l))
            }
            val best = (batters + pitchers).sortedWith(compareByDescending<Triple<PlayerId, Double, String>> { it.second }.thenBy { it.first.value })
                .firstOrNull() ?: return null
            if (best.second <= 0.0) return null
            return WeekHero(best.first, best.third)
        }

        private fun batterLine(l: BattingLine): String = listOfNotNull(
            "${l.atBats}타수 ${l.hits}안타",
            l.homeRuns.takeIf { it > 0 }?.let { "${it}홈런" },
            l.rbi.takeIf { it > 0 }?.let { "${it}타점" },
            l.atBats.takeIf { it > 0 }?.let { "타율 ${(l.hits.toDouble() / it).fixed(3)}" },
        ).joinToString(" · ")

        private fun pitcherLine(l: PitchingLine): String = listOfNotNull(
            l.wins.takeIf { it > 0 }?.let { "${it}승" },
            l.saves.takeIf { it > 0 }?.let { "${it}세이브" },
            "${l.outs / 3}${if (l.outs % 3 > 0) " ${l.outs % 3}/3" else ""}이닝 ${l.earnedRuns}자책",
            "${l.strikeouts}탈삼진",
        ).joinToString(" · ")

        private val DAY_NAMES = baseballgm.events.Incident.DAY_NAMES

        // 경기 성격을 가르는 화면 표시 기준 — 게임 밸런스 수치가 아니다
        private const val REGULATION_INNINGS = 9
        private const val COMEBACK_RUNS = 3
        private const val BLOWOUT_RUNS = 7
        private const val MAX_TAGS = 2
    }
}
