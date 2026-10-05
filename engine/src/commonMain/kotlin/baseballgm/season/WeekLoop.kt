package baseballgm.season

import baseballgm.util.fixed
import baseballgm.condition.AdaptationModel
import baseballgm.condition.FatigueModel
import baseballgm.development.AgingCurves
import baseballgm.development.GrowthModel
import baseballgm.condition.FormModel
import baseballgm.condition.InjuryModel
import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.events.InternationalTournament
import baseballgm.events.MilitaryService
import baseballgm.league.StrengthCalculator
import baseballgm.management.FanEvent
import baseballgm.management.FanSentiment
import baseballgm.management.OwnerTrust
import baseballgm.management.ParentCompanyEvents
import baseballgm.market.DraftProspect
import baseballgm.model.Batter
import baseballgm.model.MedicalRole
import baseballgm.model.MilitaryStatus
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.sim.GameRules
import baseballgm.sim.GameSimulator
import baseballgm.sim.GameTeam
import baseballgm.scouting.ScoutNews
import baseballgm.sim.RatingTables
import baseballgm.stats.BoxScore
import baseballgm.stats.BoxScoreValidator
import baseballgm.stats.StatsRecorder
import baseballgm.tactics.DirectiveSheet
import baseballgm.tactics.ManagerAI
import kotlin.random.Random

/** 관전할 수 있게 남겨 둔 경기 (docs/07 문자 중계). */
data class WatchedGame(val box: BoxScore, val events: List<baseballgm.sim.GameEvent>)

/** 한 주가 끝난 뒤의 결산 (docs/07 주 결산). */
data class WeekReport(
    val week: Int,
    val games: List<BoxScore>,
    val messages: List<InboxMessage>,
    val validationProblems: List<String>,
    val allStarBreak: Boolean,
    /** 주요 장면 문자 중계 (docs/07). 관전 여부와 상관없이 이벤트 스트림에서 뽑는다 */
    val highlights: List<String> = emptyList(),
    /** 관전용으로 남겨 둔 경기. [highlightTeam] 을 지정했을 때만 채워진다 */
    val watched: List<WatchedGame> = emptyList(),
    /** 이번 주 리그 기사 (docs/16 뉴스 탭) */
    val news: List<baseballgm.events.NewsItem> = emptyList(),
    /** 이번 주 [highlightTeam] 팬 SNS 글 */
    val fanPosts: List<baseballgm.events.FanPost> = emptyList(),
    /** 과거 결정의 메아리 — 비서가 브리핑에서 꺼낼 문장 (docs/13) */
    val echoes: List<String> = emptyList(),
    /** 이번 주 답한 돌발 이벤트 */
    val incidents: List<baseballgm.events.IncidentRecord> = emptyList(),
)

/** 경기 중 부상 한 건. 뉴스와 돌발 이벤트가 읽는다 */
data class InjuryEvent(
    val playerId: PlayerId,
    val teamId: TeamId?,
    val injury: baseballgm.model.Injury,
    val day: Int,
)

/**
 * 진행 중인 한 주 (docs/07·16 주중 개입).
 *
 * 돌발 이벤트로 주중에 멈췄다가 이어 가려면 "지금까지 치른 경기"와 "다음 날"을 들고 있어야 한다.
 */
class WeekProgress internal constructor(
    val week: Int,
    internal val validate: Boolean,
    val highlightTeam: TeamId?,
) {
    /** 다음에 치를 요일 (0=화 … 5=일). 6 이면 경기는 다 끝났다 */
    var nextDay: Int = 0
        internal set

    internal val games = mutableListOf<BoxScore>()
    internal val problems = mutableListOf<String>()
    internal val highlights = mutableListOf<String>()
    internal val watched = mutableListOf<WatchedGame>()
    internal var appearedBefore: Set<PlayerId> = emptySet()
    internal val injuries = mutableListOf<InjuryEvent>()
    internal val trades = mutableListOf<TradeNews>()
    internal var incidentCount = 0

    /** 이번 주 시작에 자동으로 올리지 않고 부상 복귀 이벤트로 물을 주전 */
    internal var heldReturns: Set<PlayerId> = emptySet()

    val gamesSoFar: List<BoxScore> get() = games.toList()

    /** 지금까지 치른 [highlightTeam] 경기의 박스스코어 + 이벤트 (주중 결과 공개용, 읽기 전용) */
    val watchedSoFar: List<WatchedGame> get() = watched.toList()

    val isDone: Boolean get() = nextDay >= DAYS

    private companion object {
        const val DAYS = 6
    }
}

/**
 * 주간 루프 (docs/07).
 *
 * 한 주 = 화~일 6경기. 경기를 치르고, 컨디션을 갱신하고, 기록을 쌓고, 결산을 만든다.
 *
 * 라인업은 **매일 다시 짠다.** 화요일에 다친 선수가 수요일에 나오면 안 되고, 피로가 쌓인 포수는
 * 규칙표의 휴식 기준에 걸려 저절로 빠져야 하기 때문이다 (docs/06 ①).
 */
class WeekLoop(
    private val balance: BalanceConfig,
    private val league: League,
) {
    private val tables = RatingTables(balance)
    private val strength = StrengthCalculator(balance)
    private val managerAI = ManagerAI(balance, strength)
    private val simulator = GameSimulator(balance, tables, GameRules.regularSeason(balance), league.environment)
    private val fatigueModel = FatigueModel(balance, tables)
    private val injuryModel = InjuryModel(balance)
    private val formModel = FormModel(balance)
    private val rosterManager = RosterManager(balance)
    private val futuresLeague = FuturesLeague(balance, tables)
    private val growthModel = GrowthModel(balance, AgingCurves(balance))
    private val growthContexts = GrowthContextResolver(balance)
    /** 시즌 중 성장은 월 1회 = 24주를 6번으로 나눈다 (docs/09). */
    private val monthlyGrowthInterval =
        balance.int("season.regularSeasonWeeks") / balance.int("growth.monthlyGrowthCount")
    private val draftRunner = DraftRunner(balance, strength)
    private val tradeService = TradeService(balance, strength)
    private val tradeRumors = TradeRumors(balance, strength, tradeService)
    private val adaptationModel = AdaptationModel(balance)
    private val militaryService = MilitaryService(balance)
    private val tournaments = InternationalTournament(balance, strength)
    private val fans = FanSentiment(balance)
    private val newsDesk = baseballgm.events.NewsDesk(balance)
    private val echoes = baseballgm.management.Echoes(balance)
    private val ownerTrust = OwnerTrust(balance)
    private val parentEvents = ParentCompanyEvents(balance)
    private val slumpCutWeek = balance.int("season.allStarBreakAfterWeek")
    private val autoStop = balance.section("autoAdvance")
    private val commentary = baseballgm.text.CommentaryRenderer { id -> league.player(id).registeredName }
    private val incidentDesk = IncidentDesk(balance, strength)
    private val morale = baseballgm.management.MoraleService(balance, strength)
    private val tradeOfferWeeks = balance.int("incidents.tradeOffer.validWeeks")
    private val fanPostRules = balance.section("narrative.fanPosts")
    private val heroMinPa = fanPostRules.int("heroMinPa")
    private val goatMinPa = fanPostRules.int("goatMinPa")
    private val goatMaxOps = fanPostRules.double("goatMaxOps")
    private val meltdownRuns = fanPostRules.int("bullpenMeltdownRuns")

    /**
     * 한 주를 끝까지 진행한다 (대량 시뮬레이션·AI 전용 — 돌발 이벤트 없이 멈추지 않는다).
     * 검증에 실패한 경기가 있으면 [WeekReport.validationProblems] 에 담긴다.
     *
     * @param highlightTeam 하이라이트를 뽑을 구단 (유저 팀). null 이면 뽑지 않는다 —
     *   720경기 전부의 중계 문장을 들고 있을 이유가 없다
     */
    fun playWeek(
        state: SeasonState,
        random: Random,
        validate: Boolean = true,
        highlightTeam: TeamId? = null,
    ): WeekReport {
        val progress = beginWeek(state, random, validate, highlightTeam)
        while (!progress.isDone) playDay(state, progress, random)
        return endWeek(state, progress, random)
    }

    /**
     * 유저 구단의 한 주 진행 (docs/07·16 주중 개입).
     *
     * [playWeek] 와 같은 순서로 같은 난수를 쓰되, **주 시작과 매일 경기 뒤에 돌발 이벤트를 살핀다.**
     * 생기면 [SeasonState.pendingIncidents] 에 넣고 그 자리에서 멈춘다(null). 유저가 답한 뒤 다시 부르면
     * 멈춘 다음 날부터 이어 간다. 주가 끝나면 결산을 돌려준다.
     *
     * 돌발 이벤트 판정은 따로 만든 난수를 쓰므로, "멈췄다 이어 간 주"와 "한 번에 간 주"는
     * 답한 효과를 빼면 완전히 같은 경기를 치른다.
     */
    fun advance(state: SeasonState, random: Random, userTeam: TeamId, validate: Boolean = false): WeekReport? {
        if (state.pendingIncidents.isNotEmpty() || state.isRegularSeasonOver) return null
        var progress = state.weekInProgress
        if (progress == null) {
            progress = beginWeek(state, random, validate, userTeam, askReturns = true)
            state.weekInProgress = progress
            state.pendingIncidents += incidentDesk.atWeekStart(state, userTeam, progress)
            if (state.pendingIncidents.isNotEmpty()) return null
        }
        while (!progress.isDone) {
            val day = progress.nextDay
            val today = playDay(state, progress, random)
            state.pendingIncidents += incidentDesk.afterDay(state, userTeam, progress, day, today)
            if (state.pendingIncidents.isNotEmpty()) return null
        }
        state.weekInProgress = null
        return endWeek(state, progress, random)
    }

    /**
     * 경기 전 준비: 방침 반영 → 적응 → 제대 → 국제대회 → 엔트리 → 스카우트 → 드래프트 → 트레이드
     *
     * @param askReturns 재활을 마친 유저 구단 주전을 자동으로 올리지 않고 2군에 둔다 — 곧이어 부상 복귀 돌발 이벤트로 묻는다
     *   ([advance] 만 true. 돌발 이벤트가 없는 [playWeek] 는 예전처럼 자동 복귀)
     */
    fun beginWeek(
        state: SeasonState,
        random: Random,
        validate: Boolean,
        highlightTeam: TeamId?,
        askReturns: Boolean = false,
    ): WeekProgress {
        val week = state.week
        val progress = WeekProgress(week, validate, highlightTeam)
        applyGmDirections(state)
        applyAdaptation(state, week)
        processDischarges(state, week)
        runTournament(state, week, random)
        // 엔트리 정리 전에 골라야 한다 — 정리가 먼저 돌면 이미 1군에 올라가 있다
        if (askReturns && highlightTeam != null) {
            progress.heldReturns = incidentDesk.returningCore(state, highlightTeam, week).map { it.id }.toSet()
        }
        manageRosters(state, week, highlightTeam, progress.heldReturns)
        runScouting(state, week, highlightTeam)
        runDraftIfDue(state, week, random)
        runTrades(state, week, random, highlightTeam, progress)
        // 트레이드 소문 (2026-10-05): 유저 구단에만, 따로 만든 난수로 — 경기 결과를 흔들지 않는다
        highlightTeam?.let { tradeRumors.weekly(state, it, week) }
        // 트레이드로 선수가 오가면 그 자리에서 엔트리를 다시 맞춘다 (포수를 보냈으면 2군 포수를 바로 올린다)
        progress.trades.flatMap { listOf(it.teams.first, it.teams.second) }.distinct()
            .forEach { manageTeamRoster(state, it, week, guided = it != highlightTeam, hold = progress.heldReturns) }
        // 메아리의 "첫 1군 경기" 판정용: 이번 주 전에 이미 뛴 적 있는 우리 선수
        progress.appearedBefore = highlightTeam?.let { team ->
            state.playersOf(team).filter { hasAppeared(state, it.id) }.map { it.id }.toSet()
        }.orEmpty()
        return progress
    }

    /** 하루치 경기. 라인업은 매일 다시 짠다 (주중에 바뀐 엔트리·부상·휴식이 다음 날 바로 반영된다) */
    fun playDay(state: SeasonState, progress: WeekProgress, random: Random): List<BoxScore> {
        val week = progress.week
        val day = progress.nextDay
        val highlightTeam = progress.highlightTeam
        val sheets = buildSheets(state)
        val playedToday = mutableSetOf<PlayerId>()
        val today = mutableListOf<BoxScore>()
        league.schedule.gamesInWeek(week).filter { it.day == day }.forEach { scheduled ->
            val watching = highlightTeam != null &&
                (scheduled.home == highlightTeam || scheduled.away == highlightTeam)
            val (box, events) = playGame(state, sheets, scheduled.home, scheduled.away, week, day, random, playedToday, progress)
            progress.games += box
            today += box
            if (progress.validate) progress.problems += BoxScoreValidator.validate(box)
            if (watching) {
                progress.highlights += commentary.highlights(events, HIGHLIGHTS_PER_GAME)
                progress.watched += WatchedGame(box, events)
            }
        }
        applyDailyRecovery(state, playedToday, random)
        progress.nextDay = day + 1
        return today
    }

    /** 주 결산: 부상 경과·팬심·성장·2군·알림 → 서사 → 주차 넘김 */
    fun endWeek(state: SeasonState, progress: WeekProgress, random: Random): WeekReport {
        val week = progress.week
        val games = progress.games.toList()
        finishWeek(state, week, games, random)
        // 선수 만족도 (docs/13): 유저 구단만. 난수를 쓰지 않는다
        progress.highlightTeam?.let { morale.weekly(state, it, week) }
        val narrative = writeNarrative(state, week, games, progress.highlightTeam, progress)
        val messages = state.inbox.ofWeek(week)
        val allStarBreak = state.calendar.isAllStarBreakAfter(week)
        // 이번 주만 적용한 지시를 거둔다
        state.restingThisWeek.clear()
        state.policyToRestore.forEach { (team, policy) -> state.policies[team] = policy }
        state.policyToRestore.clear()
        state.week = week + 1
        return WeekReport(
            week = week,
            games = games,
            messages = messages,
            validationProblems = progress.problems.toList(),
            allStarBreak = allStarBreak,
            highlights = progress.highlights.takeLast(WEEK_HIGHLIGHTS),
            watched = progress.watched.toList(),
            news = narrative.news,
            fanPosts = narrative.fanPosts,
            echoes = narrative.echoes,
            incidents = state.incidentLog.filter { it.season == state.season && it.week == week },
        )
    }

    // ---------- 서사 (docs/13 라이벌·메아리, docs/16 뉴스) ----------

    private class Narrative(
        val news: List<baseballgm.events.NewsItem>,
        val fanPosts: List<baseballgm.events.FanPost>,
        val echoes: List<String>,
    )

    private fun hasAppeared(state: SeasonState, id: PlayerId): Boolean =
        state.stats.battingOf(id).total != baseballgm.stats.BattingLine.EMPTY ||
            state.stats.pitchingOf(id).total != baseballgm.stats.PitchingLine.EMPTY

    /**
     * 경기 결과를 이야기로 바꾼다. 상대 전적과 라이벌전 팬심을 빼면 **게임 상태를 바꾸지 않는다.**
     * 문장 고르기 난수는 주차 시드로 따로 만들어 시즌 난수를 건드리지 않는다.
     */
    private fun writeNarrative(
        state: SeasonState,
        week: Int,
        games: List<BoxScore>,
        userTeam: TeamId?,
        progress: WeekProgress,
    ): Narrative {
        val appearedBefore = progress.appearedBefore
        // 상대 전적
        games.forEach { box ->
            val winner = box.winner ?: return@forEach
            val loser = if (winner == box.home.teamId) box.away.teamId else box.home.teamId
            state.headToHead[winner to loser] = (state.headToHead[winner to loser] ?: 0) + 1
        }

        // 라이벌전 팬심: 한 주 시리즈에서 앞서면 오르고 밀리면 내린다
        val rivalSeries = newsDesk.rivalSeries(league, games)
        rivalSeries.forEach { (teamId, series) ->
            val event = when {
                series.won -> FanEvent.RIVAL_SERIES_WIN
                series.lost -> FanEvent.RIVAL_SERIES_LOSS
                else -> return@forEach
            }
            state.fanSupport[teamId] = fans.onEvent(state.fanSupportOf(teamId), event, league.team(teamId).fanVolatility)
        }

        val nameOf = { id: PlayerId -> runCatching { state.player(id).registeredName }.getOrDefault("?") }
        // 기사 문장 고르기 난수 — 주차 시드라 시즌 난수와 따로 논다
        val newsRandom = newsDesk.randomFor(league, week)
        val news = newsDesk.headlines(
            league = league,
            week = week,
            games = games,
            standings = state.standings,
            previousLeader = state.lastLeader,
            userTeam = userTeam,
            playerName = nameOf,
            teamOfPlayer = { id -> runCatching { state.player(id).teamId }.getOrNull() },
            injuries = progress.injuries,
            trades = progress.trades,
            seasonBatting = { id -> state.stats.battingOf(id).total },
            seasonPitching = { id -> state.stats.pitchingOf(id).total },
            overallOf = { id -> runCatching { strength.overallOf(state.player(id)) }.getOrNull() },
            managerName = { id -> state.league.managerOf(id)?.name },
            calendar = state.calendar,
            random = newsRandom,
        )
        state.lastLeader = state.standings.ranked().firstOrNull()?.teamId
        state.news += news

        if (userTeam == null) return Narrative(news, emptyList(), emptyList())

        // 우리 팀 팬 글 — 이번 주 영웅·역적·방화범·부상자, 단장 결정에 대한 반응
        val mine = games.filter { it.home.teamId == userTeam || it.away.teamId == userTeam }
        val wins = mine.count { it.winner == userTeam }
        val losses = mine.count { it.winner != null && it.winner != userTeam }
        val sides = mine.map { box -> if (box.home.teamId == userTeam) box.home else box.away }
        val weekBatting = sides.flatMap { it.batting.entries }
            .groupBy({ it.key }, { it.value.total })
            .mapValues { (_, lines) -> lines.fold(baseballgm.stats.BattingLine.EMPTY) { acc, line -> acc + line } }
        val heroId = weekBatting.filterValues { it.plateAppearances >= heroMinPa }
            .maxByOrNull { it.value.ops + it.value.homeRuns * HERO_HOME_RUN_BONUS }?.key
        val core = incidentDesk.coreOf(state, userTeam)
        val goatId = weekBatting.filter { (id, line) -> id in core && line.plateAppearances >= goatMinPa && line.ops <= goatMaxOps }
            .minByOrNull { it.value.ops }?.key
        val meltdownId = sides.flatMap { it.pitching.entries }
            .filter { (id, _) -> (state.player(id) as? baseballgm.model.Pitcher)?.role?.isReliever == true }
            .groupBy({ it.key }, { it.value.total.runs })
            .mapValues { it.value.sum() }
            .filterValues { it >= meltdownRuns }
            .maxByOrNull { it.value }?.key
        val injured = progress.injuries.filter { it.teamId == userTeam && it.playerId in core }.map { nameOf(it.playerId) }
        // 결정 하나에 반응 글은 두 개까지 — 같은 결정을 여러 번 해도 매번 다른 목소리가 나오게 섞어서 고른다
        val reactions = state.incidentLog.filter { it.season == state.season && it.week == week }
            .flatMap { it.reactions.shuffled(newsRandom).take(REACTIONS_PER_DECISION) }
        val ranked = state.standings.ranked()
        val rank = state.standings.rankOf(userTeam)
        val fifth = ranked.getOrNull(POSTSEASON_SPOTS - 1)
        val record = state.standings.record(userTeam)
        val fromPostseason = fifth?.let { ((it.wins - it.losses) - (record.wins - record.losses)) / 2.0 }

        val team = league.team(userTeam)
        val rival = rivalSeries[userTeam]
        val posts = newsDesk.fanPosts(
            season = league.season,
            week = week,
            team = team,
            fanSupport = state.fanSupportOf(userTeam),
            weekWins = wins,
            weekLosses = losses,
            streak = record.streak,
            rival = rival,
            rivalName = rival?.let { league.team(it.rival).name },
            hero = heroId?.let(nameOf),
            random = newsRandom,
            topics = baseballgm.events.FanTopics(
                hero = heroId?.let(nameOf),
                goat = goatId?.takeIf { it != heroId }?.let(nameOf),
                meltdown = meltdownId?.let(nameOf),
                injured = injured,
                reactions = reactions,
                rank = rank,
                gamesFromPostseason = fromPostseason,
            ),
        )
        state.fanPosts += posts

        // 과거 결정의 메아리
        val firstAppearances = mine.flatMap { box ->
            val side = if (box.home.teamId == userTeam) box.home else box.away
            side.batting.keys + side.pitching.keys
        }.filter { it !in appearedBefore }.toSet()
        val echoLines = echoes.find(
            season = league.season,
            userTeam = userTeam,
            decisions = state.userDecisions(userTeam),
            games = games,
            firstAppearances = firstAppearances,
            playerOf = { id -> runCatching { state.player(id) }.getOrNull() },
        )
        return Narrative(news, posts, echoLines)
    }

    // ---------- 경영 (docs/13) ----------

    /**
     * 주간 팬심 (docs/13).
     *
     * 승패로는 천천히 움직인다 — 한 주를 다 이겨도 몇 점이다. 큰 변동은 트레이드·FA 같은 사건이
     * 만든다([FanSentiment.onEvent]). 구단별 변화 폭(`fanVolatility`)이 곱해져서 부산 타이드는
     * 더 뜨겁고 더 차갑다.
     */
    private fun applyFanSentiment(state: SeasonState, week: Int, games: List<BoxScore>) {
        league.teams.forEach { team ->
            var wins = 0
            var losses = 0
            games.forEach { box ->
                val isHome = box.home.teamId == team.id
                val isAway = box.away.teamId == team.id
                if (!isHome && !isAway) return@forEach
                if (box.tie) return@forEach
                val won = if (isHome) box.homeScore > box.awayScore else box.awayScore > box.homeScore
                if (won) wins++ else losses++
            }
            if (wins == 0 && losses == 0) return@forEach

            val before = state.fanSupportOf(team.id)
            state.fanSupport[team.id] = fans.afterWeek(
                current = before,
                weekWins = wins,
                weekLosses = losses,
                streak = state.standings.record(team.id).streak,
                volatility = team.fanVolatility,
            )
        }

        // 구단주 경고 단계 (docs/13). 매주 떠들면 시끄러우니 분기마다 한 번 알린다
        if (week % OWNER_WARNING_INTERVAL != 0) return
        league.teams.forEach { team ->
            ownerTrust.warning(team.ownerTrust)?.let { warning ->
                state.inbox.add(week, InboxCategory.WARNING, team.id, warning)
            }
        }
    }

    /**
     * 모기업 불황이 이어지면 시즌 중에 예산을 깎는다 (docs/13).
     * 스토브리그에 짜 둔 계획이 시즌 중에 흔들리는 사건이다.
     */
    private fun applyBudgetCut(state: SeasonState, week: Int) {
        if (week != slumpCutWeek) return
        val slumps = state.league.management.slumpYears
        league.teams.forEach { team ->
            val years = slumps[team.id] ?: 0
            if (years < BUDGET_CUT_SLUMP_YEARS) return@forEach
            val funds = state.funds[team.id] ?: return@forEach
            val cut = parentEvents.midSeasonCutAmount(funds)
            if (cut <= 0.0) return@forEach
            state.funds[team.id] = (funds - cut).coerceAtLeast(0.0)
            state.inbox.add(
                week = week,
                category = InboxCategory.WARNING,
                teamId = team.id,
                text = "모기업 불황 ${years}년째 — 시즌 중 예산 ${(cut * 10).toInt() / 10.0}억 삭감 통보",
            )
        }
    }

    // ---------- 외국인·군 복무·국제대회 (docs/12) ----------

    /**
     * 외국인 선수의 적응 감점을 갱신한다 (docs/12).
     *
     * 매주 다시 계산하는 이유는 감점이 **주차에 따라 줄어들기** 때문이다. 시즌 초에 크게 깎이고
     * 갈수록 작아져서, 기록만 보면 "처음엔 못하다가 살아나는" 모양이 된다.
     */
    private fun applyAdaptation(state: SeasonState, week: Int) {
        state.allPlayers().forEach { player ->
            if (!player.isForeign) return@forEach
            val penalty = adaptationModel.penaltyFor(player, state.season, week)
            if (penalty != player.condition.adaptationPenalty) {
                state.update(player.withCondition(player.condition.copy(adaptationPenalty = penalty)))
            }
        }
    }

    /** 복무를 마친 선수는 **시즌 중에도 즉시 복귀**한다 (docs/12). 2군에서 몸을 만든다. */
    private fun processDischarges(state: SeasonState, week: Int) {
        state.allPlayers().forEach { player ->
            if (player.military !is MilitaryStatus.Serving) return@forEach
            if (!militaryService.dischargesNow(player, state.season, week)) return@forEach
            state.update(player.withMilitary(MilitaryStatus.Completed))
            state.inbox.add(
                week = week,
                category = InboxCategory.RETURN,
                teamId = player.teamId,
                text = "${player.registeredName} 제대 — 2군에서 복귀 준비",
            )
        }
    }

    /**
     * 국제대회 (docs/12).
     *
     * 대회 주간이 시작되면 대표를 뽑아 차출하고, 그 자리에서 대회를 치른다. 결과(금메달)는
     * **병역 특례**로 바로 이어져서, 대표로 뽑힌 미필 선수는 입대하지 않아도 된다.
     */
    private fun runTournament(state: SeasonState, week: Int, random: Random) {
        val rules = tournaments.rulesFor(state.season) ?: return
        if (week != rules.startWeek || state.tournamentResult != null) return

        val current = state.currentLeague()
        val squad = tournaments.selectSquad(current, rules)
        if (squad.isEmpty()) return

        squad.forEach { member -> state.internationalDuty[member.playerId] = rules.endWeek }
        val result = tournaments.play(current, rules, squad, random)
        state.tournamentResult = result

        result.exempted.forEach { playerId ->
            state.update(state.player(playerId).withMilitary(MilitaryStatus.Exempt))
        }

        state.inbox.add(week, InboxCategory.NATIONAL_TEAM, null, "${rules.label} 대표 ${squad.size}명 차출 (${rules.weeksMissed}주 결장)")
        result.scores.forEach { state.inbox.add(week, InboxCategory.NATIONAL_TEAM, null, it) }
        state.inbox.add(week, InboxCategory.NATIONAL_TEAM, null, result.summary())
        squad.groupBy { it.teamId }.forEach { (teamId, members) ->
            val exempted = members.count { it.playerId in result.exempted }
            state.inbox.add(
                week = week,
                category = InboxCategory.NATIONAL_TEAM,
                teamId = teamId,
                text = "대표 차출 ${members.size}명" + if (exempted > 0) " · 병역 특례 ${exempted}명" else "",
            )
        }
    }

    // ---------- 스카우트·드래프트 ----------

    /**
     * 주간 스카우트 진행 (docs/10).
     *
     * 집중 관찰 주차를 1 올리고, 몇 주에 한 번 알림함에 소식을 남긴다. 소식은 읽을거리일 뿐이고
     * 리포트를 실제로 정확하게 만드는 것은 쌓인 **관찰 주차**다.
     */
    private fun runScouting(state: SeasonState, week: Int, userTeam: TeamId?) {
        if (state.league.draftPool.prospects.isEmpty()) return
        if (week == 1) assignAiFocus(state, userTeam)
        if (userTeam != null) {
            val auto = draftRunner.autoFocus(state, userTeam)
            if (!auto.isEmpty) state.inbox.add(week, InboxCategory.SCOUTING, userTeam, ScoutNews.forAutoFocus(auto))
        }

        league.teams.forEach { team ->
            val department = state.scoutingOf(team.id)
            department.observeWeek()
            if (week % draftRunner.scouting.budget.newsEveryWeeks != 0) return@forEach

            val watched = department.activeFocus()
                .mapNotNull { state.league.draftPool.byId(it) }
                .maxByOrNull { department.weeksOn(it.id) }
                ?: return@forEach
            state.inbox.add(
                week = week,
                category = InboxCategory.SCOUTING,
                teamId = team.id,
                text = ScoutNews.forProspect(draftRunner.report(state, team.id, watched), week),
            )
        }
        if (userTeam != null) writeDigestIfDue(state, week, userTeam)
    }

    /**
     * 스카우트 정기 리포트 (2026-10-04). 4주마다 + 드래프트 직전 주(최종)에 유저 구단에만 쓴다.
     * 드래프트는 드래프트 주차 경기 **전에** 열리므로, 최종 리포트는 그 앞 주에 와야 지명 전에 읽을 수 있다.
     * 관찰 주차가 오른 뒤에 써서 이번 주까지 본 것이 담긴다. 드래프트가 끝났으면 쓰지 않는다.
     */
    private fun writeDigestIfDue(state: SeasonState, week: Int, userTeam: TeamId) {
        if (state.draftResult != null) return
        val finalWeek = state.calendar.draftWeek - 1
        val final = week == finalWeek
        if (!final && (week > finalWeek || week % draftRunner.digestEveryWeeks != 0)) return
        val digest = draftRunner.writeDigest(state, userTeam, final, state.scoutDigests.lastOrNull())
        state.scoutDigests += digest
        state.inbox.add(
            week = week,
            category = InboxCategory.SCOUTING,
            teamId = userTeam,
            text = (if (final) "[드래프트 최종 리포트] " else "[${week}주차 스카우팅 리포트] ") + digest.headline,
        )
    }

    /**
     * AI 구단도 슬롯을 다 쓴다. 자기 평가 기준으로 상위 후보를 붙잡는다.
     * **유저 구단은 건드리지 않는다** — 슬롯 배분이 유저의 결정거리이기 때문이다 (docs/10).
     */
    private fun assignAiFocus(state: SeasonState, userTeam: TeamId?) {
        league.teams.filter { it.id != userTeam }.forEach { team ->
            val department = state.scoutingOf(team.id)
            val slots = draftRunner.scouting.focusSlots(department)
            if (department.activeFocus().size >= slots) return@forEach
            draftRunner.board(state, team.id, limit = slots)
                .forEach { (prospect: DraftProspect, _) -> department.addFocus(prospect.id, slots) }
        }
    }

    /**
     * 드래프트 주차 (docs/07).
     *
     * 유저가 직접 지명하는 경우에는 화면이 먼저 [DraftRunner] 로 지명을 끝내 놓으므로
     * 여기서는 남은 순번만 AI 가 채운다. 대량 시뮬레이션에서는 전부 AI 가 뽑는다.
     */
    private fun runDraftIfDue(state: SeasonState, week: Int, random: Random) {
        if (!state.calendar.isDraftWeek(week)) return
        if (state.draftResult != null) return
        if (state.league.draftPool.prospects.isEmpty()) return
        draftRunner.advance(state, stopForTeam = null, random = random)
    }

    /**
     * 시즌 중 트레이드 (docs/11).
     *
     * AI 끼리의 거래가 매주 낮은 확률로 성사되고, 가끔 유저에게 제안이 온다. 유저 팀이 낀 거래는
     * 유저가 답해야 하므로 여기서 실행하지 않고 알림함에만 올린다.
     */
    private fun runTrades(state: SeasonState, week: Int, random: Random, userTeam: TeamId?, progress: WeekProgress) {
        tradeService.runAiTrades(state, random, state.difficulty, userTeam).forEach { news ->
            progress.trades += news
            listOf(news.teams.first, news.teams.second).forEach { teamId ->
                state.inbox.add(week, InboxCategory.TRADE, teamId, news.text)
            }
        }
        // 답을 미룬 제안은 기한이 지나면 철회된다 (돌발 이벤트 "트레이드 제안")
        val offered = state.pendingTradeOfferWeek
        val offer = state.pendingTradeOffer
        if (userTeam != null && offer != null && offered != null && week >= offered + tradeOfferWeeks) {
            state.pendingTradeOffer = null
            state.pendingTradeOfferWeek = null
            state.inbox.add(week, InboxCategory.TRADE, userTeam, "${league.team(offer.proposer).name}이(가) 트레이드 제안을 거둬들였다")
        }
        if (userTeam != null && state.pendingTradeOffer == null) {
            tradeService.aiProposalToUser(state, userTeam, random, state.difficulty)?.let { proposal ->
                state.pendingTradeOffer = proposal
                state.pendingTradeOfferWeek = week
                val nameOf = { id: baseballgm.model.PlayerId -> state.player(id).registeredName }
                state.inbox.add(
                    week = week,
                    category = InboxCategory.TRADE,
                    teamId = userTeam,
                    text = "${league.team(proposal.proposer).name} 트레이드 제안: " +
                        "${proposal.fromProposer.describe(nameOf)} ↔ ${proposal.fromPartner.describe(nameOf)}",
                )
            }
        }
    }

    // ---------- 경기 ----------

    private fun playGame(
        state: SeasonState,
        sheets: Map<TeamId, DirectiveSheet>,
        homeId: TeamId,
        awayId: TeamId,
        week: Int,
        day: Int,
        random: Random,
        playedToday: MutableSet<PlayerId>,
        progress: WeekProgress,
    ): Pair<BoxScore, List<baseballgm.sim.GameEvent>> {
        val absoluteDay = state.absoluteDay(week, day)
        val home = gameTeam(state, sheets.getValue(homeId), homeId, absoluteDay)
        val away = gameTeam(state, sheets.getValue(awayId), awayId, absoluteDay)
        val events = simulator.simulate(home, away, league.team(homeId).parkFactor, random)
        val box = StatsRecorder.record(events)

        state.stats.add(box)
        state.standings = state.standings.withResult(box)
        applyGameEffects(state, box, absoluteDay, week, random, playedToday) { player, injury ->
            progress.injuries += InjuryEvent(player.id, player.teamId, injury, day)
        }
        return box to events
    }

    private fun gameTeam(
        state: SeasonState,
        sheet: DirectiveSheet,
        teamId: TeamId,
        absoluteDay: Int,
    ): GameTeam = gameTeamOf(state, sheet, teamId, absoluteDay)

    /** 국제대회로 빠진 선수는 라인업에서 아예 제외한다 (docs/12). */
    private fun buildSheets(state: SeasonState): Map<TeamId, DirectiveSheet> =
        league.teams.associate { team ->
            // 국제대회 차출·단장 지시 휴식(돌발 이벤트)은 라인업에서 아예 뺀다
            val roster = state.playersOf(team.id).filterNot { state.isOnInternationalDuty(it.id) || it.id in state.restingThisWeek }
            val base = managerAI.buildSheet(state.tendenciesOf(team.id), roster, state.season)
            team.id to managerAI.applyPolicy(base, state.policyOf(team.id))
        }

    /**
     * 단장 방침을 감독 성향에 섞는다 (docs/06 방침 반영률).
     * 한 번에 목표값까지 가지 않고 매주 조금씩 움직이므로, 계속 같은 방침을 유지하면 점점 가까워진다.
     */
    private fun applyGmDirections(state: SeasonState) {
        state.gmDirections.forEach { (teamId, requested) ->
            state.managerTendencies[teamId] =
                managerAI.blendTendencies(state.tendenciesOf(teamId), requested)
        }
    }

    // ---------- 경기 뒤 컨디션 ----------

    /** 경기에 나선 선수의 피로를 올리고 부상을 판정한다 (docs/08). */
    private fun applyGameEffects(
        state: SeasonState,
        box: BoxScore,
        absoluteDay: Int,
        week: Int,
        random: Random,
        playedToday: MutableSet<PlayerId>,
        onInjury: (Player, baseballgm.model.Injury) -> Unit,
    ) {
        listOf(box.home, box.away).forEach { team ->
            val doctorGrade = medicalGrade(team.teamId, MedicalRole.TEAM_DOCTOR)

            team.pitching.forEach { (pitcherId, line) ->
                val pitches = line.total.pitches
                if (pitches <= 0 && line.total.outs <= 0) return@forEach
                val pitcher = state.player(pitcherId)
                val consecutive = state.usage.consecutiveDays(pitcherId, absoluteDay)
                state.usage.record(pitcherId, absoluteDay, pitches)
                playedToday += pitcherId
                val updated = pitcher.withCondition(
                    pitcher.condition.copy(
                        fatigue = fatigueModel.afterPitching(pitcher.condition.fatigue, pitches, consecutive),
                    ),
                )
                state.update(updated)
                rollInjury(state, updated, week, doctorGrade, random, onInjury)
            }

            team.batting.forEach { (batterId, line) ->
                if (line.total.plateAppearances <= 0) return@forEach
                val batter = state.player(batterId)
                playedToday += batterId
                val position = (batter as? Batter)?.primaryPosition ?: Position.DESIGNATED_HITTER
                val updated = batter.withCondition(
                    batter.condition.copy(fatigue = fatigueModel.afterPlaying(batter.condition.fatigue, position)),
                )
                state.update(updated)
                rollInjury(state, updated, week, doctorGrade, random, onInjury)
            }
        }
    }

    private fun rollInjury(
        state: SeasonState,
        player: Player,
        week: Int,
        doctorGrade: Int,
        random: Random,
        onInjury: (Player, baseballgm.model.Injury) -> Unit,
    ) {
        val injury = injuryModel.roll(player, state.season, doctorGrade, random) ?: return
        state.update(player.withCondition(player.condition.copy(injury = injury)))
        onInjury(player, injury)
        state.inbox.add(
            week = week,
            category = InboxCategory.INJURY,
            teamId = player.teamId,
            text = "${player.registeredName} ${injury.part} 부상 (${injury.severity.label()}, ${injury.weeksRemaining}주)",
        )
    }

    /** 하루가 끝났다. 전원 피로 회복 + 1군 선수 폼 변화. */
    private fun applyDailyRecovery(state: SeasonState, playedToday: Set<PlayerId>, random: Random) {
        league.teams.forEach { team ->
            val conditioning = medicalGrade(team.id, MedicalRole.CONDITIONING_COACH)
            state.playersOf(team.id).forEach { player ->
                val played = player.id in playedToday
                var condition = player.condition.copy(
                    fatigue = fatigueModel.recover(player, state.season, played, conditioning),
                )
                if (player.rosterLevel == RosterLevel.FIRST_TEAM) {
                    val center = morale.formCenter(state, player)
                    condition = condition.copy(form = if (center == null) formModel.next(player, random) else formModel.next(player, random, center))
                }
                state.update(player.withCondition(condition))
            }
        }
    }

    // ---------- 주 결산 ----------

    private fun finishWeek(state: SeasonState, week: Int, games: List<BoxScore>, random: Random) {
        advanceInjuries(state, week, random)
        applyFanSentiment(state, week, games)
        applyBudgetCut(state, week)
        if (week % monthlyGrowthInterval == 0) applyMonthlyGrowth(state, random)
        runFuturesLeague(state, random)
        if (state.calendar.isAllStarBreakAfter(week)) applyAllStarBreak(state)
        addWeeklyMessages(state, week, games)
    }

    /** 부상 주차를 줄이고, 다 나은 선수는 영구 하락을 확정한 뒤 재활에 들어간다. */
    private fun advanceInjuries(state: SeasonState, week: Int, random: Random) {
        state.allPlayers().forEach { player ->
            val injury = player.condition.injury
            if (injury == null) {
                if (player.condition.relapseRiskWeeks > 0) {
                    state.update(
                        player.withCondition(
                            player.condition.copy(relapseRiskWeeks = player.condition.relapseRiskWeeks - 1),
                        ),
                    )
                }
                return@forEach
            }
            val next = injuryModel.advanceWeek(injury)
            if (next != null) {
                state.update(player.withCondition(player.condition.copy(injury = next)))
                return@forEach
            }
            // 완치: 영구 하락 판정 후 재발 위험 기간에 들어간다
            val doctorGrade = player.teamId?.let { medicalGrade(it, MedicalRole.TEAM_DOCTOR) } ?: 0
            val outcome = injuryModel.applyPermanentLoss(player, injury, state.season, doctorGrade, random)
            val healed = outcome.changedPlayer.withCondition(
                outcome.changedPlayer.condition.copy(injury = null, relapseRiskWeeks = injury.relapseRiskWeeks),
            )
            state.update(healed)
            val lossText = if (outcome.permanentLoss.isEmpty()) {
                ""
            } else {
                " (${outcome.permanentLoss.entries.joinToString { "${it.key.label} -${it.value}" }})"
            }
            state.inbox.add(
                week = week,
                category = InboxCategory.RETURN,
                teamId = player.teamId,
                text = "${player.registeredName} ${injury.part} 부상 회복$lossText",
            )
        }
    }

    /**
     * 시즌 중 월 1회 성장 (docs/09). 25세 이하만, 연간 성장량의 40%를 여섯 번에 나눠 받는다.
     * 시즌 중에 유망주가 눈에 띄게 크는 맛을 내기 위한 장치다.
     */
    private fun applyMonthlyGrowth(state: SeasonState, random: Random) {
        state.allPlayers().forEach { player ->
            val result = growthModel.monthly(player, state.season, growthContexts.contextOf(state, player), random)
            if (result.changes.isNotEmpty()) state.update(result.player)
        }
    }

    /** 2군은 경기를 돌리지 않고 추정 성적만 만든다 (docs/07). */
    private fun runFuturesLeague(state: SeasonState, random: Random) {
        state.allPlayers().filter { it.rosterLevel == RosterLevel.FUTURES && !it.condition.isInjured }
            .forEach { player ->
                when (player) {
                    is Batter -> state.stats.addFutures(player.id, futuresLeague.weeklyBatting(player, random))
                    is Pitcher -> state.stats.addFutures(player.id, futuresLeague.weeklyPitching(player, random))
                }
                // 2군 선수의 폼은 주 단위로만 움직이고, 말소 직후에는 평균으로 빠르게 돌아온다
                state.update(
                    player.withCondition(
                        player.condition.copy(form = formModel.afterDemotion(player.condition.form)),
                    ),
                )
            }
    }

    private fun applyAllStarBreak(state: SeasonState) {
        state.allPlayers().forEach { player ->
            state.update(
                player.withCondition(
                    player.condition.copy(fatigue = fatigueModel.afterAllStarBreak(player.condition.fatigue)),
                ),
            )
        }
    }

    /**
     * 개막 전 엔트리 정리. 리그 데이터의 개막 1군이 포지션 범위를 벗어나 있으면(유격수 다섯 같은) 화면에 보이기 전에 맞춘다.
     * 주 시작에 하는 정리와 같은 것이라, 미리 해 두면 1주차 시작에는 바꿀 게 없다. 난수를 쓰지 않는다.
     */
    fun prepareRosters(state: SeasonState, userTeam: TeamId? = null) = manageRosters(state, state.week, userTeam)

    /**
     * @param userTeam 유저 구단. 이 구단은 포지션을 맞추지 않는다 (유저 요청 2026-10-01)
     * @param hold 올리지 않고 둘 유저 구단 선수 (부상 복귀 돌발 이벤트로 물을 주전)
     */
    private fun manageRosters(state: SeasonState, week: Int, userTeam: TeamId?, hold: Set<PlayerId> = emptySet()) {
        league.teams.forEach { team ->
            manageTeamRoster(state, team.id, week, guided = team.id != userTeam, hold = if (team.id == userTeam) hold else emptySet())
        }
    }

    private fun manageTeamRoster(state: SeasonState, teamId: TeamId, week: Int, guided: Boolean, hold: Set<PlayerId> = emptySet()) {
        val moves = rosterManager.manage(
            teamId = teamId,
            roster = state.playersOf(teamId),
            week = week,
            state = state.roster,
            rank = { strength.overallOf(it) },
            onChange = { state.update(it) },
            guided = guided,
            hold = hold,
        )
        moves.forEach { move ->
            state.inbox.add(
                week = week,
                category = if (move.injury) InboxCategory.INJURY else InboxCategory.ROSTER,
                teamId = teamId,
                text = "${state.player(move.playerId).registeredName} ${if (move.promoted) "1군 등록" else "1군 말소"} — ${move.reason}",
            )
        }
    }

    /** 결과 나열로 끝내지 않고 다음 주 결정거리를 만든다 (docs/07). */
    private fun addWeeklyMessages(state: SeasonState, week: Int, games: List<BoxScore>) {
        val losingStreakLimit = autoStop.int("losingStreak")
        val winningStreakLimit = autoStop.int("winningStreak")
        league.teams.forEach { team ->
            val record = state.standings.record(team.id)
            if (record.streak <= -losingStreakLimit) {
                state.inbox.add(week, InboxCategory.STREAK, team.id, "${team.name} ${-record.streak}연패 — 팬심이 흔들린다")
            }
            if (record.streak >= winningStreakLimit) {
                state.inbox.add(week, InboxCategory.STREAK, team.id, "${team.name} ${record.streak}연승")
            }
            // 2군 호성적: 콜업 후보를 알린다
            state.futuresOf(team.id)
                .filterIsInstance<Batter>()
                .maxByOrNull { state.stats.futuresBattingOf(it.id).ops }
                ?.let { best ->
                    val line = state.stats.futuresBattingOf(best.id)
                    if (line.plateAppearances >= FUTURES_REPORT_PA && line.ops >= FUTURES_REPORT_OPS) {
                        state.inbox.add(
                            week,
                            InboxCategory.FUTURES,
                            team.id,
                            "2군 ${best.registeredName} OPS ${line.ops.fixed(3)} (${line.plateAppearances}타석)",
                        )
                    }
                }
        }
        if (state.calendar.isAllStarBreakAfter(week)) {
            state.inbox.add(week, InboxCategory.SCHEDULE, null, "올스타 브레이크 — 전반기 결산, 피로 회복")
        }
        if (state.calendar.isTradeDeadline(week)) {
            state.inbox.add(week, InboxCategory.SCHEDULE, null, "이번 주가 트레이드 마감 주간이다")
        }
        if (state.calendar.isDraftWeek(week)) {
            state.inbox.add(week, InboxCategory.SCHEDULE, null, "신인 드래프트 주간")
        }
    }

    private fun medicalGrade(teamId: TeamId, role: MedicalRole): Int =
        league.medicalStaffOf(teamId).firstOrNull { it.role == role }?.grade ?: 0

    private fun baseballgm.model.InjurySeverity.label(): String = when (this) {
        baseballgm.model.InjurySeverity.MINOR -> "경미"
        baseballgm.model.InjurySeverity.MODERATE -> "중간"
        baseballgm.model.InjurySeverity.MAJOR -> "중상"
        baseballgm.model.InjurySeverity.SEASON_ENDING -> "시즌 아웃"
    }

    private companion object {
        const val DAYS_IN_WEEK = 6
        const val FUTURES_REPORT_PA = 40
        const val FUTURES_REPORT_OPS = 0.900
        const val HIGHLIGHTS_PER_GAME = 2
        const val WEEK_HIGHLIGHTS = 5
        const val OWNER_WARNING_INTERVAL = 6
        const val BUDGET_CUT_SLUMP_YEARS = 2
        const val POSTSEASON_SPOTS = 5
        const val REACTIONS_PER_DECISION = 2

        /** 영웅 고르기: OPS 에 홈런 하나당 얹는 값 (표시용 — 경기 결과와 무관) */
        const val HERO_HOME_RUN_BONUS = 0.1
    }
}
