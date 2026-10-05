package baseballgm.events

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.Standings
import baseballgm.model.InjurySeverity
import baseballgm.model.PlayerId
import baseballgm.model.Team
import baseballgm.model.TeamId
import baseballgm.season.InjuryEvent
import baseballgm.season.SeasonCalendar
import baseballgm.season.TradeNews
import baseballgm.stats.BattingLine
import baseballgm.stats.BoxScore
import baseballgm.stats.PitchingLine
import baseballgm.util.eulReul
import baseballgm.util.eunNeun
import baseballgm.util.fixed
import baseballgm.util.iGa
import baseballgm.util.waGwa
import kotlin.random.Random

/**
 * 기사 종류. 뉴스 칩 라벨이 된다.
 * 순서는 같은 주 안에서의 **중요도 순**이다 (앞일수록 위에 놓인다).
 */
@kotlinx.serialization.Serializable
enum class NewsKind(val label: String) {
    FRONT_OFFICE("프런트"),
    LEADER("순위"),
    TRADE("트레이드"),
    INJURY("부상"),
    STREAK("연승·연패"),
    RIVAL("라이벌전"),
    MANAGER("감독"),
    RACE("순위 경쟁"),
    MILESTONE("기록"),
    GAME("경기"),
    PITCHING("호투"),
    SLUGGER("홈런"),
    SLUMP("부진"),
    COLUMN("칼럼"),
}

/**
 * 리그 기사 한 건.
 * @param outlet 매체 이름 (가상)
 * @param lead 기사 첫 문장. 헤드라인만으로는 안 보이는 숫자를 담는다
 */
@kotlinx.serialization.Serializable
data class NewsItem(
    val season: Int,
    val week: Int,
    val kind: NewsKind,
    val headline: String,
    val teams: List<TeamId>,
    val outlet: String = "",
    val lead: String = "",
)

@kotlinx.serialization.Serializable
enum class FanMood(val label: String) { POSITIVE("응원"), NEUTRAL("담담"), NEGATIVE("불만") }

/** 우리 팀 팬 SNS 글 한 건 */
@kotlinx.serialization.Serializable
data class FanPost(
    val season: Int,
    val week: Int,
    val author: String,
    val text: String,
    val mood: FanMood,
    /** 공감 수. 단장 결정에 대한 반응과 감정이 센 글일수록 많다 */
    val likes: Int = 0,
)

/** 한 주 동안 라이벌과 치른 경기 (팀 기준) */
data class RivalSeries(val rival: TeamId, val wins: Int, val losses: Int, val ties: Int) {
    val won: Boolean get() = wins > losses
    val lost: Boolean get() = losses > wins
}

/** 팬 글에 쓸 이번 주 이야깃거리. 없으면 null — 그 주제의 글은 나오지 않는다 */
data class FanTopics(
    /** 이번 주 가장 잘 친 타자 */
    val hero: String? = null,
    /** 이번 주 가장 못 친 주전 타자 */
    val goat: String? = null,
    /** 이번 주 가장 많이 실점한 불펜 */
    val meltdown: String? = null,
    /** 이번 주 다친 우리 주전 */
    val injured: List<String> = emptyList(),
    /** 이번 주 단장 결정에 대한 반응 (돌발 이벤트 답변) */
    val reactions: List<FanReaction> = emptyList(),
    val rank: Int? = null,
    /** 5위와의 게임 차 (5위 안이면 0 이하) */
    val gamesFromPostseason: Double? = null,
)

/**
 * 뉴스 데스크 (docs/16 뉴스·팬 SNS).
 *
 * 한 주의 경기 결과·순위·부상·트레이드에서 **기사**(리그 전체)와 **팬 글**(유저 구단)을 뽑는다.
 * 경기 결과를 바꾸지 않는다 — 이벤트 스트림을 문장으로 바꾸는 [baseballgm.text.CommentaryRenderer] 와 같은 자리다.
 *
 * 같은 일도 매번 같은 문장이면 몇 주 만에 질린다. 그래서 기사마다 **틀을 여러 개** 두고 주차 시드 난수로 고른다.
 * 매체 이름도 가상의 다섯 곳에서 돌린다. 헤드라인 아래 첫 문장(lead)에는 숫자를 넣어 "진짜 기사"처럼 읽히게 한다.
 *
 * 문장 고르기에 쓰는 난수는 **주차 시드로 따로 만든다.** 시즌 난수를 쓰면 뉴스 한 줄 때문에 다음 경기
 * 결과가 달라지기 때문이다 (불변 원칙 2 — 같은 결정이면 같은 결과).
 */
class NewsDesk(balance: BalanceConfig) {
    private val news = balance.section("narrative").section("news")
    private val maxPerWeek = news.int("maxPerWeek")
    private val streakHeadline = news.int("streakHeadline")
    private val blowoutMargin = news.int("blowoutMargin")
    private val weeklyHomeRuns = news.int("weeklyHomeRunHeadline")
    private val milestoneHomeRuns = news.intList("milestoneHomeRuns")
    private val milestoneWins = news.intList("milestoneWins")
    private val milestoneSaves = news.intList("milestoneSaves")
    private val milestoneHits = news.intList("milestoneHits")
    private val strikeoutGame = news.int("strikeoutGame")
    private val injuryWeeks = news.int("injuryHeadlineWeeks")
    private val starOverall = news.double("injuryHeadlineOverall")
    private val raceFromWeek = news.int("raceFromWeek")
    private val raceGap = news.double("raceGap")
    private val heatStreak = news.int("managerHeatStreak")
    private val slumpPa = news.int("slumpHeadlinePa")
    private val slumpOps = news.double("slumpHeadlineOps")
    private val maxMilestones = news.int("maxMilestonesPerWeek")

    private val posts = balance.section("narrative").section("fanPosts")
    private val postsPerWeek = posts.int("perWeek")
    private val fanWeight = posts.double("fanWeight")
    private val weekWeight = posts.double("weekWeight")
    private val positiveAbove = posts.double("positiveAbove")
    private val negativeBelow = posts.double("negativeBelow")
    private val likesBase = posts.int("likesBase")
    private val likesSpread = posts.int("likesSpread")

    /** 주차마다 다른, 그러나 항상 같은 난수 */
    fun randomFor(league: League, week: Int): Random = Random(league.seed * 7919 + league.season * 131 + week)

    /**
     * 이번 주 기사. 유저 구단이 나오는 기사를 앞에 둔다.
     * @param previousLeader 지난주 1위. 바뀌었으면 "선두 교체" 기사가 된다
     * @param seasonBatting 시즌 누적 타격 (기록 달성 기사용). null 이면 기록 기사를 쓰지 않는다
     */
    fun headlines(
        league: League,
        week: Int,
        games: List<BoxScore>,
        standings: Standings,
        previousLeader: TeamId?,
        userTeam: TeamId?,
        playerName: (PlayerId) -> String,
        teamOfPlayer: (PlayerId) -> TeamId?,
        injuries: List<InjuryEvent> = emptyList(),
        trades: List<TradeNews> = emptyList(),
        seasonBatting: ((PlayerId) -> BattingLine)? = null,
        seasonPitching: ((PlayerId) -> PitchingLine)? = null,
        overallOf: (PlayerId) -> Double? = { null },
        managerName: (TeamId) -> String? = { null },
        calendar: SeasonCalendar? = null,
        random: Random = randomFor(league, week),
    ): List<NewsItem> {
        fun name(id: TeamId) = league.team(id).name
        val items = mutableListOf<NewsItem>()
        val ranked = standings.ranked()
        fun item(kind: NewsKind, headline: String, teams: List<TeamId>, lead: String = "") {
            items += NewsItem(league.season, week, kind, headline, teams, OUTLETS[random.nextInt(OUTLETS.size)], lead)
        }

        // 순위
        ranked.firstOrNull()?.let { leader ->
            if (previousLeader != null && previousLeader != leader.teamId && leader.games > 0) {
                val l = name(leader.teamId)
                val p = name(previousLeader)
                item(
                    NewsKind.LEADER,
                    random.pick(
                        "선두 교체! $l, $p 끌어내리고 1위",
                        "${l.iGa()} 1위 탈환… ${p.eunNeun()} 2위로",
                        "판도 요동, $l 선두 등극",
                    ),
                    listOf(leader.teamId, previousLeader),
                    "${l.iGa()} ${leader.wins}승 ${leader.losses}패로 순위표 맨 위에 올랐다. " +
                        "${p.waGwa()}의 차이는 ${standings.gamesBehind(previousLeader).fixed(1)}경기.",
                )
            }
        }

        // 트레이드
        trades.forEach { trade ->
            val (a, b) = trade.teams
            item(
                NewsKind.TRADE,
                random.pick(
                    "[오피셜] ${name(a)}-${name(b)} 트레이드 단행",
                    "${name(a)}·${name(b)} 전격 트레이드",
                    "트레이드 시장 움직였다… ${name(a)}↔${name(b)}",
                ),
                listOf(a, b),
                trade.text,
            )
        }

        // 부상
        injuries.filter { it.injury.weeksRemaining >= injuryWeeks || it.injury.severity == InjurySeverity.SEASON_ENDING }
            .filter { (overallOf(it.playerId) ?: 0.0) >= starOverall }
            .distinctBy { it.playerId }
            .forEach { event ->
                val team = event.teamId ?: return@forEach
                val who = playerName(event.playerId)
                val part = event.injury.part
                val weeks = event.injury.weeksRemaining
                val headline = if (event.injury.severity == InjurySeverity.SEASON_ENDING) {
                    random.pick("${name(team)} 날벼락, $who 시즌 아웃", "$who($part) 시즌 아웃… ${name(team)} 비상")
                } else {
                    random.pick(
                        "${name(team)} 비상! $who $part 부상으로 ${weeks}주 이탈",
                        "$who(${name(team)}) $part 부상… 최소 ${weeks}주 결장",
                        "'주축' $who 빠진 ${name(team)}, 버틸 수 있을까",
                    )
                }
                item(NewsKind.INJURY, headline, listOf(team), "${name(team)} 구단은 \"$who 선수가 $part 부상으로 ${weeks}주 진단을 받았다\"고 밝혔다.")
            }

        // 연승·연패, 감독 거취
        ranked.forEach { record ->
            val t = name(record.teamId)
            when {
                record.streak >= streakHeadline -> item(
                    NewsKind.STREAK,
                    random.pick(
                        "$t, ${record.streak}연승 질주",
                        "멈추지 않는 $t… 파죽의 ${record.streak}연승",
                        "$t ${record.streak}연승, 이 기세 어디까지",
                    ),
                    listOf(record.teamId),
                    "${t.iGa()} ${record.streak}경기 연속 승리를 챙기며 ${standings.rankOf(record.teamId)}위에 자리했다.",
                )
                record.streak <= -streakHeadline -> {
                    val n = -record.streak
                    item(
                        NewsKind.STREAK,
                        random.pick(
                            "$t, ${n}연패 늪에 빠졌다",
                            "$t 끝 모를 추락… ${n}연패",
                            "${n}연패 $t, 해법이 안 보인다",
                        ),
                        listOf(record.teamId),
                        "${t.eunNeun()} 최근 ${n}경기를 내리 졌다. 시즌 성적 ${record.wins}승 ${record.losses}패, ${standings.rankOf(record.teamId)}위.",
                    )
                    val manager = managerName(record.teamId)
                    if (n >= heatStreak && manager != null) {
                        item(
                            NewsKind.MANAGER,
                            random.pick(
                                "$t ${n}연패… $manager 감독 거취 주목",
                                "흔들리는 $t 벤치, $manager 감독 '책임론' 고개",
                            ),
                            listOf(record.teamId),
                            "구단 안팎에서 $manager 감독의 거취를 둘러싼 이야기가 나오고 있다.",
                        )
                    }
                }
            }
        }

        // 라이벌전
        rivalSeries(league, games).forEach { (team, series) ->
            if (team.value > series.rival.value) return@forEach // 한 쌍은 한 번만
            val (winner, loser, w, l) = when {
                series.won -> Quad(team, series.rival, series.wins, series.losses)
                series.lost -> Quad(series.rival, team, series.losses, series.wins)
                else -> null
            } ?: run {
                item(
                    NewsKind.RIVAL,
                    random.pick("${name(team)}·${name(series.rival)} 라이벌전, 승부 못 가렸다", "팽팽했던 자존심 대결, ${name(team)}-${name(series.rival)} 무승부 시리즈"),
                    listOf(team, series.rival),
                )
                return@forEach
            }
            item(
                NewsKind.RIVAL,
                random.pick(
                    "라이벌전 ${name(winner)} ${w}승 ${l}패, ${name(loser)}에 판정승",
                    "${name(winner)}, 라이벌 ${name(loser)} 상대 ${w}승 ${l}패 우세",
                    "자존심 대결 ${name(winner)} 웃었다… ${name(loser)} 상대 ${w}승 ${l}패",
                ),
                listOf(team, series.rival),
                "이번 주 ${games.count { setOf(it.home.teamId, it.away.teamId) == setOf(team, series.rival) }}경기에서 ${name(winner).iGa()} 웃었다.",
            )
        }

        // 가을야구 경쟁·독주
        if (calendar == null || week >= raceFromWeek) {
            val fifth = ranked.getOrNull(POSTSEASON_SPOTS - 1)
            val sixth = ranked.getOrNull(POSTSEASON_SPOTS)
            if (fifth != null && sixth != null && week >= raceFromWeek) {
                val gap = gap(fifth.wins, fifth.losses, sixth.wins, sixth.losses)
                if (gap <= raceGap && random.nextBoolean()) {
                    item(
                        NewsKind.RACE,
                        random.pick(
                            "가을야구 막차 싸움 점입가경… ${name(fifth.teamId)}-${name(sixth.teamId)} ${gap.fixed(1)}경기 차",
                            "5위 전쟁, ${name(sixth.teamId)} ${name(fifth.teamId)} 턱밑 추격",
                        ),
                        listOf(fifth.teamId, sixth.teamId),
                        "5위 ${name(fifth.teamId)}(${fifth.wins}승 ${fifth.losses}패)와 6위 ${name(sixth.teamId)}(${sixth.wins}승 ${sixth.losses}패)의 격차는 ${gap.fixed(1)}경기.",
                    )
                }
            }
            val first = ranked.getOrNull(0)
            val second = ranked.getOrNull(1)
            if (first != null && second != null && week >= raceFromWeek) {
                val lead = gap(first.wins, first.losses, second.wins, second.losses)
                if (lead >= RUNAWAY_GAP && random.nextInt(RUNAWAY_EVERY) == 0) {
                    item(
                        NewsKind.RACE,
                        random.pick("${name(first.teamId)} 독주 체제… 2위와 ${lead.fixed(1)}경기 차", "적수가 없다, ${name(first.teamId)} 1위 굳히기"),
                        listOf(first.teamId),
                    )
                }
            }
        }

        // 눈에 띄는 경기: 끝내기, 대승. 같은 매치업에서 여러 번이면 한 기사로 묶는다
        val walkOffs = mutableMapOf<Pair<TeamId, TeamId>, Int>()
        games.forEach { box ->
            val winner = box.winner ?: return@forEach
            val loser = if (winner == box.home.teamId) box.away.teamId else box.home.teamId
            val high = maxOf(box.homeScore, box.awayScore)
            val low = minOf(box.homeScore, box.awayScore)
            when {
                box.walkOff -> walkOffs[winner to loser] = (walkOffs[winner to loser] ?: 0) + 1
                high - low >= blowoutMargin -> item(
                    NewsKind.GAME,
                    random.pick(
                        "${name(winner)}, ${name(loser)}에 $high-$low 대승",
                        "${name(winner)} 타선 폭발, ${name(loser)} $high-$low 대파",
                        "${name(loser)} 마운드 붕괴… ${name(winner)}에 $low-$high 완패",
                    ),
                    listOf(winner, loser),
                    "${name(winner).eunNeun()} 장단 ${(if (winner == box.home.teamId) box.home else box.away).hits}안타를 몰아쳤다.",
                )
            }
        }
        walkOffs.forEach { (pair, count) ->
            val (winner, loser) = pair
            val headline = if (count > 1) {
                random.pick("${name(winner)}, ${name(loser)} 상대로 끝내기만 ${count}번", "또 끝냈다! ${name(winner)}, ${name(loser)} 상대로 끝내기만 ${count}번")
            } else {
                random.pick(
                    "${name(winner)}, 끝내기로 ${name(loser)} 제압",
                    "극장 승부! ${name(winner)} 끝내기 승",
                    "${name(winner)} 마지막 공격에서 ${name(loser).eulReul()} 울렸다",
                )
            }
            item(NewsKind.GAME, headline, listOf(winner, loser), "마지막 공격에서 승부가 갈렸다. 패한 ${name(loser).eunNeun()} 불펜 운용에 숙제를 남겼다.")
        }

        // 호투: 완봉, 탈삼진쇼
        games.forEach { box ->
            listOf(box.home to box.away, box.away to box.home).forEach { (side, other) ->
                val single = side.pitching.entries.singleOrNull()
                if (single != null && single.value.total.outs >= COMPLETE_GAME_OUTS && other.runs == 0) {
                    val team = side.teamId
                    item(
                        NewsKind.PITCHING,
                        random.pick("${playerName(single.key)}, ${name(other.teamId)} 상대 완봉승", "'혼자 다 막았다' ${playerName(single.key)} 완봉 역투"),
                        listOf(team),
                        "${playerName(single.key).iGa()} 9이닝을 ${single.value.total.hits}피안타 무실점으로 책임졌다. 탈삼진 ${single.value.total.strikeouts}개.",
                    )
                    return@forEach
                }
                side.pitching.forEach { (id, line) ->
                    val k = line.total.strikeouts
                    if (k >= strikeoutGame) {
                        item(
                            NewsKind.PITCHING,
                            random.pick("${playerName(id)}, ${k}탈삼진 쾌투", "${playerName(id)} 탈삼진쇼… 한 경기 ${k}K"),
                            listOf(side.teamId),
                        )
                    }
                }
            }
        }

        // 이번 주 홈런·기록 달성
        val weekBatting = mutableMapOf<PlayerId, BattingLine>()
        val weekWins = mutableMapOf<PlayerId, Int>()
        val weekSaves = mutableMapOf<PlayerId, Int>()
        games.forEach { box ->
            listOf(box.home, box.away).forEach { side ->
                side.batting.forEach { (id, line) -> weekBatting[id] = (weekBatting[id] ?: BattingLine.EMPTY) + line.total }
            }
            box.winningPitcher?.let { weekWins[it] = (weekWins[it] ?: 0) + 1 }
            box.savePitcher?.let { weekSaves[it] = (weekSaves[it] ?: 0) + 1 }
        }
        val milestoneDone = mutableSetOf<PlayerId>()
        if (seasonBatting != null) {
            weekBatting.forEach { (id, week) ->
                val team = teamOfPlayer(id) ?: return@forEach
                val season = seasonBatting(id)
                if (items.count { it.kind == NewsKind.MILESTONE } >= maxMilestones) return@forEach
                crossed(season.homeRuns - week.homeRuns, season.homeRuns, milestoneHomeRuns)?.let { t ->
                    milestoneDone += id
                    item(
                        NewsKind.MILESTONE,
                        random.pick("${playerName(id)}(${name(team)}), 시즌 ${t}호 홈런 고지", "${t}홈런 돌파 ${playerName(id)}, 거포 본색", "${playerName(id)} 시즌 ${t}홈런… 홈런왕 레이스 불붙었다"),
                        listOf(team),
                        "시즌 ${season.homeRuns}홈런 ${season.rbi}타점, 타율 ${season.battingAverage.fixed(3)}.",
                    )
                }
                if (id in milestoneDone) return@forEach
                crossed(season.hits - week.hits, season.hits, milestoneHits)?.let { t ->
                    item(NewsKind.MILESTONE, "${playerName(id)}(${name(team)}), 시즌 ${t}안타 돌파", listOf(team), "시즌 타율 ${season.battingAverage.fixed(3)}.")
                }
            }
        }
        if (seasonPitching != null) {
            weekWins.forEach { (id, count) ->
                if (items.count { it.kind == NewsKind.MILESTONE } >= maxMilestones) return@forEach
                val team = teamOfPlayer(id) ?: return@forEach
                val season = seasonPitching(id)
                crossed(season.wins - count, season.wins, milestoneWins)?.let { t ->
                    item(
                        NewsKind.MILESTONE,
                        random.pick("${playerName(id)}, 시즌 ${t}승 고지", "에이스의 품격, ${playerName(id)} ${t}승 달성"),
                        listOf(team),
                        "시즌 ${season.wins}승 ${season.losses}패, 평균자책점 ${season.era.fixed(2)}.",
                    )
                }
            }
            weekSaves.forEach { (id, count) ->
                val team = teamOfPlayer(id) ?: return@forEach
                val season = seasonPitching(id)
                crossed(season.saves - count, season.saves, milestoneSaves)?.let { t ->
                    item(NewsKind.MILESTONE, "뒷문은 내가 지킨다, ${playerName(id)} 시즌 ${t}세이브", listOf(team))
                }
            }
        }
        weekBatting.filterValues { it.homeRuns >= weeklyHomeRuns }.entries.sortedByDescending { it.value.homeRuns }.forEach { (id, line) ->
            if (id in milestoneDone) return@forEach
            val team = teamOfPlayer(id) ?: return@forEach
            val count = line.homeRuns
            item(
                NewsKind.SLUGGER,
                random.pick(
                    "${playerName(id)}(${name(team)}), 한 주에 홈런 ${count}개 폭발",
                    "${playerName(id)} 방망이 불붙었다… 주간 ${count}홈런",
                    "'홈런 공장' 가동 ${playerName(id)}, 일주일 ${count}방",
                ),
                listOf(team),
                seasonBatting?.let { "시즌 ${it(id).homeRuns}홈런." } ?: "",
            )
        }

        // 부진: 이름값 있는 타자의 침묵
        weekBatting.filter { (id, line) ->
            line.plateAppearances >= slumpPa && line.ops < slumpOps && (overallOf(id) ?: 0.0) >= starOverall
        }.maxByOrNull { overallOf(it.key) ?: 0.0 }?.let { (id, line) ->
            val team = teamOfPlayer(id) ?: return@let
            item(
                NewsKind.SLUMP,
                random.pick("${playerName(id)} 침묵 길어진다… 주간 타율 ${line.battingAverage.fixed(3)}", "${name(team)} 고민, 터지지 않는 ${playerName(id)}"),
                listOf(team),
                "이번 주 ${line.atBats}타수 ${line.hits}안타.",
            )
        }

        // 칼럼: 시즌의 마디
        if (calendar != null) {
            val first = ranked.firstOrNull()
            val last = ranked.lastOrNull()
            when {
                calendar.isAllStarBreakAfter(week) && first != null && last != null -> item(
                    NewsKind.COLUMN,
                    "[전반기 결산] ${name(first.teamId)} 1위로 반환점… 최하위 ${name(last.teamId)}와 ${standings.gamesBehind(last.teamId).fixed(1)}경기 차",
                    listOf(first.teamId, last.teamId),
                    "후반기 판도는 부상 관리와 트레이드 마감까지의 보강이 가를 전망이다.",
                )
                calendar.isTradeDeadline(week) -> item(
                    NewsKind.COLUMN,
                    random.pick("트레이드 마감 임박, 각 구단 셈법 분주", "[칼럼] 마감 직전, 사는 팀과 파는 팀"),
                    emptyList(),
                )
                calendar.isFinalWeek(week) -> item(NewsKind.COLUMN, "정규시즌 마지막 주, 순위표 마지막 퍼즐", emptyList())
            }
        }

        return items
            .distinctBy { it.headline }
            .sortedWith(compareByDescending<NewsItem> { userTeam != null && userTeam in it.teams }.thenBy { it.kind.ordinal })
            .take(maxPerWeek)
    }

    /** 팀별 이번 주 라이벌 경기 결과. 라이벌과 붙지 않은 팀은 빠진다 */
    fun rivalSeries(league: League, games: List<BoxScore>): Map<TeamId, RivalSeries> {
        val result = mutableMapOf<TeamId, RivalSeries>()
        league.teams.forEach { team ->
            val rival = team.rival ?: return@forEach
            var wins = 0
            var losses = 0
            var ties = 0
            games.forEach { box ->
                val pair = setOf(box.home.teamId, box.away.teamId)
                if (pair != setOf(team.id, rival)) return@forEach
                when (box.winner) {
                    null -> ties++
                    team.id -> wins++
                    else -> losses++
                }
            }
            if (wins + losses + ties > 0) result[team.id] = RivalSeries(rival, wins, losses, ties)
        }
        return result
    }

    /**
     * 우리 팀 팬 글. 기분은 팬심과 이번 주 승률을 섞은 점수로 정한다 —
     * 팬심이 높은 팀은 한 주 부진해도 글이 덜 험하다.
     *
     * 단장 결정에 대한 반응([FanTopics.reactions])은 가장 먼저, 공감을 많이 받은 글로 올라온다.
     * "내 결정을 팬들이 보고 있다"가 느껴져야 하기 때문이다.
     */
    fun fanPosts(
        season: Int,
        week: Int,
        team: Team,
        fanSupport: Int,
        weekWins: Int,
        weekLosses: Int,
        streak: Int,
        rival: RivalSeries?,
        rivalName: String?,
        hero: String?,
        random: Random,
        topics: FanTopics = FanTopics(hero = hero),
    ): List<FanPost> {
        val weekRate = if (weekWins + weekLosses == 0) 0.5 else weekWins.toDouble() / (weekWins + weekLosses)
        val score = fanWeight * fanSupport / 100.0 + weekWeight * weekRate
        val context = PostContext(team.nickname, team.city, streak, rival, rivalName, topics.copy(hero = topics.hero ?: hero), weekWins, weekLosses)

        val result = mutableListOf<FanPost>()
        topics.reactions.take(MAX_REACTION_POSTS).forEach { reaction ->
            result += FanPost(season, week, author(context, random), reaction.text, reaction.mood, likes(random, REACTION_LIKES))
        }
        val target = result.size + postsPerWeek
        var guard = 0
        while (result.size < target && guard < postsPerWeek * GUARD) {
            guard++
            // 점수 주변으로 조금씩 흔들어 한 주에도 여러 목소리가 섞이게 한다
            val jitter = score + (random.nextDouble() - 0.5) * 0.3
            val mood = when {
                jitter >= positiveAbove -> FanMood.POSITIVE
                jitter <= negativeBelow -> FanMood.NEGATIVE
                else -> FanMood.NEUTRAL
            }
            val candidates = templates(mood, context)
            val text = candidates[random.nextInt(candidates.size)]
            if (result.any { it.text == text }) continue
            val heat = if (mood == FanMood.NEUTRAL) 1.0 else MOOD_LIKES
            // 한 주에 같은 사람이 두 번 쓰지 않게 (핸들이 겹치면 다시 뽑는다)
            var handle = author(context, random)
            repeat(AUTHOR_RETRIES) { if (result.any { it.author == handle }) handle = author(context, random) }
            result += FanPost(season, week, handle, text, mood, likes(random, heat))
        }
        return result
    }

    private fun likes(random: Random, weight: Double): Int {
        val r = random.nextDouble()
        return ((likesBase + likesSpread * r * r) * weight).toInt()
    }

    private data class PostContext(
        val nickname: String,
        val city: String,
        val streak: Int,
        val rival: RivalSeries?,
        val rivalName: String?,
        val topics: FanTopics,
        val weekWins: Int,
        val weekLosses: Int,
    )

    private fun templates(mood: FanMood, c: PostContext): List<String> = buildList {
        val t = c.topics
        when (mood) {
            FanMood.POSITIVE -> {
                add("오늘도 직관 간 보람 있다 ${c.nickname} 최고")
                add("이 페이스면 가을야구 간다 진짜로")
                add("요즘 경기 보는 맛 난다 ㅋㅋ")
                add("이번 주 ${c.weekWins}승이면 말 다 했지")
                add("퇴근길에 하이라이트 보는 게 낙이다 요즘")
                add("올해는 뭔가 다르다… 설레발 금지인데 설렌다")
                add("응원가 따라 부르다 목 쉼 ㅋㅋㅋ 내일도 간다")
                if (c.streak >= 2) add("${c.streak}연승 실화냐 ㅋㅋㅋ")
                if (c.streak >= 4) add("${c.streak}연승 중엔 양말도 안 빤다 (진지)")
                t.hero?.let {
                    add("$it 요즘 폼 미쳤다")
                    add("$it 유니폼 지금 사면 늦은 거냐")
                    add("이번 주 MVP는 무조건 $it")
                }
                t.rank?.let { if (it <= 3) add("${it}위 ㅋㅋ 순위표 캡처해둠") }
                if (c.rival?.won == true && c.rivalName != null) {
                    add("${c.rivalName} 잡은 주는 치킨이 더 맛있다")
                    add("${c.rivalName} 팬 친구한테 전화 왔는데 안 받음 ㅋ")
                }
            }
            FanMood.NEUTRAL -> {
                add("반타작이면 뭐… 다음 주 보자")
                add("아직 시즌 길다. 일희일비 금지")
                add("단장님 이번 주 조용하시네")
                add("이기는 날은 시원하게 이기고 지는 날은 허무하게 지고… 롤러코스터")
                add("선발은 괜찮은데 불펜이 늘 불안함")
                add("직관 승률 5할 유지 중. 나쁘지 않다")
                t.hero?.let { add("$it 하나는 건졌다") }
                t.gamesFromPostseason?.let { gap ->
                    if (gap > 0) add("5위랑 ${gap.fixed(1)}경기 차. 아직 모른다") else add("5위 안에는 있는데 불안불안")
                }
                if (c.rival != null && !c.rival.won && !c.rival.lost) add("라이벌전 무승부 느낌… 찝찝하다")
                t.injured.firstOrNull()?.let { add("$it 빠진 자리 누가 메우냐가 관건") }
            }
            FanMood.NEGATIVE -> {
                add("불펜 누가 좀 살려줘")
                add("이럴 거면 차라리 리빌딩하자")
                add("${c.city} 야구 이렇게 하는 거 아니다")
                add("이번 주 ${c.weekLosses}패… 주말에 뭐 하고 살지")
                add("작전 왜 이렇게 하는지 아는 사람?")
                add("득점권에서 병살 나올 때 리모컨 던질 뻔")
                add("단장님 보강 좀 해주세요 제발")
                if (c.streak <= -2) add("${-c.streak}연패… 할 말이 없다")
                if (c.streak <= -4) add("${-c.streak}연패 중인데 구단은 뭐 하냐")
                t.goat?.let {
                    add("$it 요즘 왜 이래… 2군 가서 조정 좀 하자")
                    add("$it 타석만 오면 채널 돌림")
                }
                t.meltdown?.let { add("$it 올라오면 불안해서 못 보겠다") }
                t.injured.firstOrNull()?.let { add("$it 부상이라니 시즌 끝났다 진짜") }
                t.gamesFromPostseason?.let { if (it > 3) add("5위랑 ${it.fixed(1)}경기 차… 가을야구는 꿈인가") }
                if (c.rival?.lost == true && c.rivalName != null) {
                    add("${c.rivalName}한테 지는 건 못 참지")
                    add("${c.rivalName} 팬들 조롱 짤 벌써 돌던데 ㅠ")
                }
            }
        }
    }

    private fun author(c: PostContext, random: Random): String {
        val handles = listOf(
            "${c.nickname}_직관러", "${c.city}토박이", "야구보는고양이", "${c.nickname}팬_${random.nextInt(10, 99)}",
            "외야석3루", "9회말2아웃", "치맥러버", "${c.nickname}영구결번", "응원가장인", "불펜걱정러",
            "주말직관러", "${c.city}야구사랑", "스코어북쓰는사람", "홈런볼수집가", "평일출근러", "${c.nickname}_30년차",
        )
        return "@" + handles[random.nextInt(handles.size)]
    }

    private fun crossed(before: Int, after: Int, thresholds: List<Int>): Int? =
        thresholds.lastOrNull { before < it && after >= it }

    private fun gap(w1: Int, l1: Int, w2: Int, l2: Int): Double = ((w1 - l1) - (w2 - l2)) / 2.0

    private data class Quad(val winner: TeamId, val loser: TeamId, val w: Int, val l: Int)

    companion object {
        /** 가상의 매체. 실제 언론사 이름을 쓰지 않는다 (CLAUDE.md §1) */
        val OUTLETS: List<String> = listOf("그라운드일보", "다이아몬드스포츠", "베이스라인뉴스", "더그아웃24", "홈플레이트")

        private const val POSTSEASON_SPOTS = 5
        private const val COMPLETE_GAME_OUTS = 27
        private const val RUNAWAY_GAP = 8.0
        private const val RUNAWAY_EVERY = 4
        private const val MAX_REACTION_POSTS = 3
        private const val REACTION_LIKES = 2.5
        private const val MOOD_LIKES = 1.4
        private const val GUARD = 6
        private const val AUTHOR_RETRIES = 4
    }
}

private fun <T> Random.pick(vararg options: T): T = options[nextInt(options.size)]
