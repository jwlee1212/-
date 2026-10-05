package baseballgm.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import baseballgm.condition.FormModel
import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.league.Standings
import baseballgm.management.Achievement
import baseballgm.management.Achievements
import baseballgm.management.CareerRecord
import baseballgm.management.ClubSeasonReview
import baseballgm.management.FinanceReport
import baseballgm.management.FinancialOutlook
import baseballgm.management.FinancialPlan
import baseballgm.management.OutlookChange
import baseballgm.management.PlannedContract
import baseballgm.management.JobOffer
import baseballgm.management.SeasonGoal
import baseballgm.management.StaffMarket
import baseballgm.management.StaffMarketState
import baseballgm.management.StaffOffer
import baseballgm.market.ContractOffer
import baseballgm.market.DraftPickRight
import baseballgm.market.DraftProspect
import baseballgm.market.DraftSelection
import baseballgm.market.DraftSlot
import baseballgm.market.ForeignCandidate
import baseballgm.market.FreeAgencyState
import baseballgm.market.FreeAgent
import baseballgm.season.withTeam
import baseballgm.season.withContract
import baseballgm.market.FaNegotiation
import baseballgm.market.FaStanding
import baseballgm.market.ProspectSupplier
import baseballgm.market.TeamMode
import baseballgm.market.TradePackage
import baseballgm.market.TradeProposal
import baseballgm.market.TradeVerdict
import baseballgm.model.Batter
import baseballgm.model.ManagerTendencies
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.scouting.ScoutReport
import baseballgm.scouting.ScoutedPlayer
import baseballgm.scouting.ScoutingPrecision
import baseballgm.events.TournamentResult
import baseballgm.season.DraftRunner
import baseballgm.season.ForeignService
import baseballgm.season.Postseason
import baseballgm.season.PostseasonResult
import baseballgm.season.Offseason
import baseballgm.season.OffseasonReport
import baseballgm.market.ForeignSupplier
import baseballgm.season.RookieSupplier
import baseballgm.season.SeasonCalendar
import baseballgm.season.SeasonState
import baseballgm.season.TradeService
import baseballgm.season.WeekLoop
import baseballgm.season.WeekReport
import baseballgm.stats.BattingLine
import baseballgm.stats.BatterMetrics
import baseballgm.stats.LeagueConstants
import baseballgm.stats.PitchingLine
import baseballgm.stats.PitcherMetrics
import baseballgm.stats.Sabermetrics
import baseballgm.stats.War
import baseballgm.stats.WarBreakdown
import baseballgm.tactics.DirectiveSheet
import baseballgm.tactics.ManagerAI
import baseballgm.tactics.WeeklyPolicy
import baseballgm.util.fixed
import baseballgm.util.Seeds
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 화면과 엔진 사이의 유일한 창구.
 *
 * 화면은 엔진 객체를 직접 만지지 않고 전부 여기를 거친다. 특히 **타 팀 선수 정보는
 * [scout] 로만** 볼 수 있다 — 숨김 수치는 엔진 밖에서 아예 읽히지 않고(컴파일 차단),
 * 타 팀 능력치는 정확도에 따라 흐려진 범위로만 나온다 (불변 원칙 4).
 */
class GameSession(
    val balance: BalanceConfig,
    startingLeague: League,
    userTeamId: TeamId,
    /** 신인을 만들어 주는 쪽. 엔진·화면은 선수를 만들 수 없어서 밖에서 받는다 (docs/09) */
    private val rookieSupplier: (League) -> RookieSupplier = { RookieSupplier { _, _, _, _ -> emptyList() } },
    /** 다음 시즌 드래프트 풀을 만들어 주는 쪽. 엔진·화면은 선수를 만들 수 없다 (docs/10) */
    private val prospectSupplier: (League) -> ProspectSupplier = { ProspectSupplier { _, _, _ -> emptyList() } },
    /** 다음 시즌 외국인 시장을 만들어 주는 쪽 (docs/12) */
    private val foreignSupplier: (League) -> ForeignSupplier = { ForeignSupplier { _, _, _ -> emptyList() } },
    /** 게임 시드. 주차·단계별 난수는 전부 여기서 파생한다 ([Seeds]). 세이브에 함께 담긴다 */
    val seed: Long = 20260401L,
    /** 유저가 타이틀 화면에서 정한 단장 이름. 비서가 이 이름으로 부른다 (CLAUDE.md §4-1). 없으면 리그 데이터의 단장 이름 */
    gmName: String? = null,
    /** 시즌 중 세이브에서 이어 할 때의 시즌 스냅샷. 이때 [startingLeague] 는 세이브에 담긴 "지금 리그"다 */
    savedSeason: baseballgm.io.SeasonSnapshot? = null,
    /** 세이브에 담긴 지난주 결산 (홈·주간 브리핑) */
    savedReport: baseballgm.io.ReportSnapshot? = null,
) {
    /** 유저가 맡은 구단. 해임 후 이직하면 바뀐다 (docs/13 커리어 모드) */
    var userTeamId: TeamId by mutableStateOf(userTeamId)
        private set

    val strength = StrengthCalculator(balance)
    private val formModel = FormModel(balance)
    private val managerAI = ManagerAI(balance, strength)
    private val calendar = SeasonCalendar.from(balance)
    /**
     * 이번 주 난수. (시즌, 주차)에서 파생해서, 저장했다 불러와도 그 주가 똑같이 흘러간다.
     * 돌발 이벤트로 주중에 멈췄다 이어 갈 때는 같은 인스턴스를 계속 쓴다 (주가 끝나야 바뀐다).
     */
    private var weekRandomKey: Pair<Int, Int>? = null
    private var weekRandomInstance: Random = Random(seed)

    private fun weekRandom(): Random {
        val key = state.season to state.week
        if (key != weekRandomKey) {
            weekRandomKey = key
            weekRandomInstance = Seeds.random(seed, state.season, Seeds.Phase.WEEK, state.week.toLong())
        }
        return weekRandomInstance
    }

    /** 드래프트 난수 — 지명 하나하나를 나눠서 해도(생중계) 한 번에 해도 같은 순서로 쓴다 */
    private var draftRandomSeason: Int? = null
    private var draftRandomInstance: Random = Random(seed)

    private fun draftRandom(): Random {
        if (draftRandomSeason != state.season) {
            draftRandomSeason = state.season
            draftRandomInstance = Seeds.random(seed, state.season, Seeds.Phase.DRAFT)
        }
        return draftRandomInstance
    }

    /** 스토브리그(성장·은퇴·FA 라운드) 난수. 스토브리그를 시작할 때 만들고 FA 가 닫힐 때까지 쓴다 */
    private var offseasonRandom: Random = Random(seed)

    private var loop = WeekLoop(balance, startingLeague)
    private val draftRunner = DraftRunner(balance, strength)
    private val tradeService = TradeService(balance, strength)
    private val foreignService = ForeignService(balance, strength)
    private val clubReview = ClubSeasonReview(balance, strength)
    private val outlook = FinancialOutlook(balance, clubReview.financeModel)
    private val staffMarket = StaffMarket(balance)
    internal val sabermetrics = Sabermetrics(balance)
    private val warCalculator = War(balance, sabermetrics)
    private val titles = baseballgm.management.GmTitles(balance)
    private val bonds = baseballgm.management.Bonds(balance)
    private val moraleService = baseballgm.management.MoraleService(balance, strength)
    private val incidentResolver = baseballgm.season.IncidentResolver(balance, strength)
    private val rosterActions = baseballgm.season.RosterActions(balance)
    private val extensionService = baseballgm.season.ContractExtensionService(balance, strength)
    private val strengthScouting = baseballgm.scouting.TeamStrengthScouting(strength, draftRunner.scouting, balance)

    /** 상태가 바뀔 때마다 올라간다. 화면은 이 값을 읽어 다시 그린다. */
    var revision by mutableStateOf(0)
        private set

    /**
     * [state] 를 읽으면 [revision] 도 함께 읽는다.
     *
     * 시즌 상태는 대부분 제자리에서 바뀌어서(집중 관찰 추가, 지명 등) 객체 자체는 그대로다. 그런데
     * Compose 의 strong skipping 은 인자(`session`)가 같은 인스턴스면 하위 composable 을 건너뛴다.
     * 화면 맨 위에서만 revision 을 읽으면 하위 카드·버튼은 옛 값 그대로 남고, 다른 탭에 다녀와야
     * 바뀌는 문제가 생긴다. 조회 함수가 거의 다 state 를 거치므로 여기서 읽어 두면, 조회한 composable
     * 이 스스로 revision 을 구독하게 된다.
     */
    private var currentState by mutableStateOf(
        if (savedSeason != null) {
            baseballgm.io.SeasonSnapshots.restore(startingLeague, SeasonCalendar.from(balance), balance, savedSeason)
        } else SeasonState.of(
            startingLeague.copy(
                management = startingLeague.management.copy(
                    userTeam = userTeamId,
                    career = startingLeague.management.career ?: CareerRecord(
                        gmName = gmName ?: startingLeague.generalManagerOf(userTeamId)?.name ?: "단장",
                        reputation = balance.int("career.startReputation"),
                    ),
                    // 이어하기로 2년차 이후 리그를 받으면 스토브리그가 정한 목표가 이미 들어 있다
                    seasonGoals = startingLeague.management.seasonGoals.ifEmpty {
                        startingLeague.teams.associate { it.id to it.ownerGoal }
                    },
                ),
            ),
            calendar,
            balance,
        ),
    )

    var state: SeasonState
        get() {
            @Suppress("UNUSED_VARIABLE")
            val tracked = revision
            return currentState
        }
        private set(value) {
            currentState = value
        }

    val league: League get() = state.league

    init {
        // 물려받은 개막 1군을 정리해 둔다 — 첫 화면부터 유격수 다섯 같은 1군이 보이지 않게.
        // 시즌 중에는 유저 구단 구성을 건드리지 않는다 (포지션 제한 없음, 2026-10-01).
        // 시즌 중 세이브에서 이어 할 때는 건드리지 않는다 — 저장된 엔트리가 곧 그 주의 엔트리다
        if (savedSeason == null) loop.prepareRosters(currentState)
    }

    var lastReport by mutableStateOf<WeekReport?>(savedReport?.let { saved ->
        WeekReport(
            week = saved.week,
            games = saved.games,
            messages = saved.messages,
            validationProblems = emptyList(),
            allStarBreak = saved.allStarBreak,
            highlights = saved.highlights,
            news = saved.news,
            fanPosts = saved.fanPosts,
            echoes = saved.echoes,
            incidents = saved.incidents,
        )
    })
        private set

    var busy by mutableStateOf(false)
        private set

    /** 직전 스토브리그 결과 (은퇴·신인·각성 등). 새 시즌 홈 화면에서 보여준다 */
    var lastOffseason by mutableStateOf<OffseasonReport?>(null)
        private set

    /** 진행 중인 FA 시장. 스토브리그 동안만 값이 있다 (docs/11) */
    private var currentFaState by mutableStateOf<FreeAgencyState?>(null)

    /** FA 제안도 제자리에서 바뀌므로 [state] 처럼 revision 을 함께 읽는다 */
    var faState: FreeAgencyState?
        get() {
            @Suppress("UNUSED_VARIABLE")
            val tracked = revision
            return currentFaState
        }
        private set(value) {
            currentFaState = value
        }

    var lastFaReport by mutableStateOf<baseballgm.market.FaRoundReport?>(null)
        private set

    private var pendingLeague: League? = null
    private var pendingOffseason: Offseason? = null
    private var pendingStandings: Standings = Standings.empty(emptyList())

    val inFreeAgency: Boolean get() = faState != null

    val userTeam get() = state.league.team(userTeamId)

    val week: Int get() = state.week

    val seasonOver: Boolean get() = state.isRegularSeasonOver

    fun calendarLabel(week: Int = state.week): String = state.calendar.label(week)

    // ---------- 진행 ----------

    /**
     * 한 주 진행. 유저 팀 경기는 관전할 수 있게 이벤트까지 남긴다.
     *
     * 돌발 이벤트가 생기면 **그 자리에서 멈춘다** ([pendingIncident]). 답한 뒤 다시 부르면 멈춘 다음 날부터 이어 간다.
     * @param delegate 돌발 이벤트를 전부 비서 추천대로 처리하고 주 끝까지 간다 (테스트·일괄 진행용)
     */
    fun advanceWeek(delegate: Boolean = false) {
        if (seasonOver && !weekPaused) return
        autoTarget = null
        busy = true
        var guard = 0
        while (guard++ < MAX_STEPS) {
            val pending = pendingIncident
            if (pending != null) {
                if (!delegate) break
                resolveRecommended(pending)
            }
            val report = advanceLoop()
            if (report != null) {
                lastReport = report
                break
            }
            if (!delegate) break
        }
        busy = false
        revision++
    }

    /**
     * 주를 시작할 때의 리그 순위 (2026-10-03, 재미 개선 5번). 순위표가 "지난주보다 2계단 ↑"를 보여 준다.
     * 화면 표시용이라 세이브하지 않는다 — 불러온 직후엔 화살표가 없다.
     */
    var ranksAtWeekStart: Map<TeamId, Int> = emptyMap()
        private set

    /** 엔진 주간 루프 한 걸음. 새 주를 시작하는 걸음이면 그 전에 순위를 찍어 둔다 */
    private fun advanceLoop(): baseballgm.season.WeekReport? {
        if (!weekPaused) ranksAtWeekStart = teamsRanked().mapIndexed { index, record -> record.teamId to index + 1 }.toMap()
        return loop.advance(state, weekRandom(), userTeamId)
    }

    /**
     * 목표 주차까지 자동 진행 (docs/07).
     *
     * - **드래프트 주차에는 반드시 멈춘다** — 지명은 유저가 해야 하기 때문이다 (docs/07).
     * - **계약이 걸린 돌발 이벤트(트레이드 제안·문의, 방출 매물 영입, 연장 계약)에도 멈춘다**.
     * - 나머지(주전 부상 대체·부상 투혼·콜업·연패·감독 거취·기자 질문·피로 등)는 비서가 추천대로 처리하고
     *   기록에 "비서 처리"로 남긴다 — 빠르게 시즌을 넘기기 위해서다 (유저 요청 2026-10-03).
     * @param delegate 계약 결정까지 전부 비서에게 맡긴다 (테스트·일괄 진행용)
     */
    fun advanceUntil(targetWeek: Int, delegate: Boolean = false) {
        busy = true
        autoTarget = null
        var guard = 0
        while (guard++ < MAX_STEPS) {
            val pending = pendingIncident
            if (pending != null) {
                if (delegate || !pending.kind.contract) {
                    resolveRecommended(pending)
                    continue
                }
                // 계약 결정에 멈춘다. 답하면 [resumeAutoAdvance] 가 여기서 이어 간다
                autoTarget = targetWeek
                break
            }
            if (!weekPaused) {
                if (seasonOver || state.week > targetWeek) break
                if (isDraftWeek && !draftDone) break
            }
            advanceLoop()?.let { lastReport = it }
        }
        busy = false
        revision++
    }

    /**
     * 자동 진행이 계약 결정에 멈췄을 때의 목표 주차 (2026-10-03, 유저 요청 "선택사항 결정 후에도 자동 진행이 끊이지 않게").
     * 답하고 나면 이 주차까지 이어 간다. 한 주씩 진행하면 지운다. 세이브하지 않는다 (멈춘 주중엔 어차피 저장하지 않는다)
     */
    var autoTarget by mutableStateOf<Int?>(null)
        private set

    /** 답할 돌발 이벤트가 더 없으면 멈췄던 자동 진행을 이어 간다 */
    private fun resumeAutoAdvance() {
        val target = autoTarget ?: return
        if (pendingIncident != null) return
        // 단장이 방금 고른 답의 후속 한마디가 이어서 비서가 처리한 일에 덮이지 않게 한다
        val decided = lastIncidentRecord
        advanceUntil(target)
        lastIncidentRecord = decided
    }

    // ---------- 세이브 (docs/14) ----------

    /**
     * 지금 저장할 수 있으면 세이브 한 벌, 아니면 null (직전 세이브가 그대로 남는다).
     *
     * 저장하지 않는 때: 무직(이직 고르기 전), 스토브리그·FA 시장 중, 주중에 멈춰 있을 때(돌발 이벤트),
     * 드래프트를 하는 도중. 이때 앱을 닫으면 직전 세이브로 돌아가고, 시드가 같아서 같은 일이 다시 일어난다.
     */
    fun saveData(): baseballgm.io.SaveGame? {
        if (unemployed || inFreeAgency || pendingOffseason != null) return null
        val draft = state.draft
        if (draft != null && !draft.isComplete) return null
        val snapshot = baseballgm.io.SeasonSnapshots.capture(state) ?: return null
        return baseballgm.io.SaveGame(
            userTeam = userTeamId,
            league = state.currentLeague(),
            seed = seed,
            season = snapshot,
            lastReport = lastReport?.let { report ->
                baseballgm.io.ReportSnapshot(
                    week = report.week,
                    games = report.games,
                    messages = report.messages,
                    allStarBreak = report.allStarBreak,
                    highlights = report.highlights,
                    news = report.news,
                    fanPosts = report.fanPosts,
                    echoes = report.echoes,
                    incidents = report.incidents,
                )
            },
        )
    }

    // ---------- 돌발 이벤트 (docs/07·16 주중 개입) ----------

    /** 지금 답해야 하는 돌발 이벤트. 없으면 null */
    val pendingIncident: baseballgm.events.Incident? get() = state.pendingIncidents.firstOrNull()

    /** 남은 돌발 이벤트 수 (지금 것 포함) */
    val pendingIncidentCount: Int get() = state.pendingIncidents.size

    /** 주중에 멈춰 있는가 (경기가 남아 있다) */
    val weekPaused: Boolean get() = state.weekInProgress != null

    /** 멈춘 주의 다음 경기 요일 (0=화). 멈춰 있지 않으면 null */
    val pausedNextDay: Int? get() = state.weekInProgress?.nextDay

    /**
     * 다음 경기 (단장실 허브 매치업, 2026-10-03). 주중에 멈춰 있으면 이어서 치를 날, 아니면 이번 주 첫 경기.
     * 시즌이 끝났으면 null.
     */
    fun nextGame(): baseballgm.league.ScheduledGame? {
        if (seasonOver) return null
        val day = pausedNextDay ?: 0
        return weekSchedule().sortedBy { it.day }.firstOrNull { it.day >= day }
    }

    /**
     * 예상 선발: 로테이션 다음 순번 (엔진이 경기마다 한 칸씩 넘긴다). 감독이 주마다 로테이션을 다시 짜므로 "예상"이다.
     * 우리 팀이 이번 주 [gamesAhead] 경기 뒤에 던질 투수를 보고 싶으면 그만큼 더 간다.
     */
    fun probableStarter(teamId: TeamId, gamesAhead: Int = 0): baseballgm.model.Player? {
        val starters = sheet(teamId).rotation.starters
        if (starters.isEmpty()) return null
        val id = starters[(state.peekRotationIndex(teamId) + gamesAhead).mod(starters.size)]
        return runCatching { player(id) }.getOrNull()
    }

    /** 상대 전적 (우리 승, 우리 패) */
    fun headToHead(opponent: TeamId): Pair<Int, Int> = state.headToHeadOf(userTeamId, opponent)

    /** 선수 이름. 리그에서 찾을 수 없으면(지난 시즌에 떠난 선수 등) id 그대로 */
    fun nameOf(id: PlayerId): String = runCatching { player(id).registeredName }.getOrElse { id.value }

    /** 이번 주 지금까지 치른 우리 경기 (주중에 멈췄을 때). 결과 공개가 주중에도 이어지게 한다 */
    fun weekGamesSoFar(): List<baseballgm.season.WatchedGame> = state.weekInProgress?.watchedSoFar.orEmpty()

    /** 이번 시즌 [week] 주차에 생긴 돌발 이벤트 기록 (답한 순서) */
    fun incidentsOf(week: Int): List<baseballgm.events.IncidentRecord> =
        state.incidentLog.filter { it.season == state.season && it.week == week }

    /** 방금 답한 돌발 이벤트 — 비서의 후속 한마디를 보여 주는 데 쓴다 */
    var lastIncidentRecord by mutableStateOf<baseballgm.events.IncidentRecord?>(null)
        private set

    fun resolveIncident(optionId: String) {
        val incident = pendingIncident ?: return
        lastIncidentRecord = incidentResolver.resolve(state, incident.id, optionId, delegated = false)
        revision++
        resumeAutoAdvance()
    }

    /** 비서에게 맡긴다 = 추천 선택지 */
    fun delegateIncident() {
        val incident = pendingIncident ?: return
        resolveRecommended(incident)
        revision++
        resumeAutoAdvance()
    }

    private fun resolveRecommended(incident: baseballgm.events.Incident) {
        lastIncidentRecord = incidentResolver.resolve(state, incident.id, incident.recommended.id, delegated = true)
    }

    fun dismissIncidentRecord() {
        lastIncidentRecord = null
    }

    /** 올 시즌 답한 돌발 이벤트, 최근 것부터 */
    fun incidentLog(): List<baseballgm.events.IncidentRecord> = state.incidentLog.asReversed().toList()

    /** 결정 성적표 (재미 개선 2번). 판정 기준은 balance.json decisionReview */
    val decisionReviewer: DecisionReviewer by lazy { DecisionReviewer(balance) }

    // ---------- 엔트리 직접 관리 (docs/07) ----------

    fun promoteProblem(player: Player, swappingOut: PlayerId? = null): String? =
        rosterActions.promoteProblem(state, userTeamId, player.id, swappingOut)

    fun demoteProblem(player: Player): String? = rosterActions.demoteProblem(state, userTeamId, player.id)

    /** 지금 말소하면 몇 주차부터 다시 올릴 수 있나 */
    fun reRegisterWeekIfDemoted(): Int = rosterActions.reRegisterWeekIfDemotedNow(state)

    /**
     * [player] 를 올리면서 대신 내릴 수 있는 1군 선수 (포지션 제한 없음 — 아무나).
     * 같은 포지션이 먼저, 그 안에서 약한 순.
     */
    fun swapOutCandidates(player: Player): List<Player> {
        val slot = baseballgm.season.RosterSlot.of(player)
        return state.firstTeamOf(userTeamId)
            .filter {
                rosterActions.promoteProblem(state, userTeamId, player.id, swappingOut = it.id) == null &&
                    rosterActions.demoteProblem(state, userTeamId, it.id, replacement = player.id) == null
            }
            .sortedWith(compareBy<Player> { if (baseballgm.season.RosterSlot.of(it) == slot) 0 else 1 }.thenBy { overall(it) })
    }

    /**
     * [player](1군)를 내리면서 대신 올릴 수 있는 2군 선수 (부상·복무·재등록 제한이 없는 선수).
     * 같은 포지션이 먼저, 그 안에서 강한 순.
     */
    fun swapInCandidates(player: Player): List<Player> {
        val slot = baseballgm.season.RosterSlot.of(player)
        return state.futuresOf(userTeamId)
            .filter {
                rosterActions.promoteProblem(state, userTeamId, it.id, swappingOut = player.id) == null &&
                    rosterActions.demoteProblem(state, userTeamId, player.id, replacement = it.id) == null
            }
            .sortedWith(compareBy<Player> { if (baseballgm.season.RosterSlot.of(it) == slot) 0 else 1 }.thenByDescending { overall(it) })
    }

    /** 인원·포지션과 상관없이 1군에 올릴 수 없는 이유 (부상·군 복무·재등록 제한·시즌 종료). 없으면 null */
    fun eligibilityProblem(player: Player): String? = when {
        seasonOver -> "정규시즌이 끝났어요"
        player.teamId != userTeamId -> "우리 선수가 아니에요"
        else -> rosterActions.eligibilityProblem(state, player.id)
    }

    /** 우리 1군의 포지션별 인원과 범위 (투수·야수 전체 포함) */
    fun slotCounts(): List<baseballgm.season.SlotCount> =
        stoveState()?.let { rosterActions.slotCounts(it, userTeamId) } ?: rosterActions.slotCounts(state, userTeamId)

    /** 이 선수의 1군 포지션 칸 이름 (선발·불펜·포수 …) */
    fun slotLabel(player: Player): String = baseballgm.season.RosterSlot.of(player).label

    val firstTeamLimit: Int get() = balance.int("roster.firstTeamRegistered")

    fun promote(player: Player): Boolean {
        val done = rosterActions.promote(state, userTeamId, player.id) != null
        if (done) moraleService.onUserRosterMove(state, userTeamId, player.id, promoted = true, wasCore = false)
        revision++
        return done
    }

    fun demote(player: Player): Boolean {
        // 주전이었는지는 내리기 전에 본다 — 벤치 선수를 내리는 건 일상이라 기억에 남지 않는다 (docs/13)
        val wasCore = player.id in moraleService.coreOf(state, userTeamId)
        val done = rosterActions.demote(state, userTeamId, player.id) != null
        if (done) moraleService.onUserRosterMove(state, userTeamId, player.id, promoted = false, wasCore = wasCore)
        revision++
        return done
    }

    fun swap(up: Player, down: Player): Boolean {
        val wasCore = down.id in moraleService.coreOf(state, userTeamId)
        val done = rosterActions.swap(state, userTeamId, up.id, down.id).isNotEmpty()
        if (done) {
            moraleService.onUserRosterMove(state, userTeamId, up.id, promoted = true, wasCore = false)
            moraleService.onUserRosterMove(state, userTeamId, down.id, promoted = false, wasCore = wasCore)
        }
        revision++
        return done
    }

    // ---------- 선수 비교 ----------

    /**
     * 비교함. 화면을 옮겨 다녀도 남아 있어야 해서(트레이드 목록에서 담고 → 로스터에서 우리 선수 담고 → 비교) 세션이 들고 있다.
     * 능력치는 비교 화면에서도 [scout] 를 거친다 — 타 팀 선수는 범위로만 보인다.
     */
    val compareList: androidx.compose.runtime.snapshots.SnapshotStateList<PlayerId> = androidx.compose.runtime.mutableStateListOf()

    fun inCompare(id: PlayerId): Boolean = id in compareList

    /** 담거나 뺀다. 꽉 찼으면(4명) 가장 먼저 담은 선수를 밀어낸다 */
    fun toggleCompare(id: PlayerId) {
        if (compareList.remove(id)) return
        if (compareList.size >= MAX_COMPARE) compareList.removeAt(0)
        compareList += id
    }

    fun clearCompare() = compareList.clear()

    /** 비교·목록에 쓰는 핵심 능력치 셋 (타자: 컨택·파워·수비 / 투수: 구위·제구·체력) — 범위로 */
    fun keyRatings(player: Player): List<Pair<String, baseballgm.scouting.RatingRange>> {
        val scouted = scout(player)
        val keys = if (player is Pitcher) KEY_PITCHER else KEY_BATTER
        return keys.map { it.label to scouted.ratings.getValue(it) }
    }

    /** 올 시즌 1군 기록 한 줄. 1군 기록이 없으면 2군 추정 성적 */
    fun seasonSummary(player: Player): String = when (player) {
        is Batter -> {
            val line = batting(player.id).takeIf { it.plateAppearances > 0 }
            val shown = line ?: futuresBatting(player.id)
            if (shown.plateAppearances == 0) {
                "기록 없음"
            } else {
                (if (line == null) "2군 " else "") +
                    "타율 ${shown.battingAverage.fixed(3)} ${shown.homeRuns}홈런 OPS ${shown.ops.fixed(3)}"
            }
        }
        is Pitcher -> {
            val line = pitching(player.id).takeIf { it.outs > 0 }
            val shown = line ?: futuresPitching(player.id)
            if (shown.outs == 0) {
                "기록 없음"
            } else {
                (if (line == null) "2군 " else "") +
                    "${shown.inningsText()}이닝 ERA ${shown.era.fixed(2)} ${shown.wins}승 ${shown.strikeouts}K"
            }
        }
    }

    /** 이번 주 휴식 지시를 받은 선수인가 */
    fun isResting(player: Player): Boolean = player.id in state.restingThisWeek

    // ---------- 포스트시즌 (docs/14, 2026-10-03 한 경기씩 진행) ----------

    private fun postseasonEngine() = Postseason(balance, league)

    /** 포스트시즌 경기마다 시드를 따로 뽑는다 — 저장·불러오기를 해도, 경기 사이에 결정을 바꿔도 다음 경기의 난수가 같다 */
    private fun postseasonRandom(gameIndex: Int) =
        Seeds.random(seed, state.season, Seeds.Phase.POSTSEASON, gameIndex.toLong())

    /** 진행 중인 포스트시즌. 시작 전이면 null */
    val postseasonProgress: baseballgm.season.PostseasonProgress? get() = state.postseasonProgress

    val postseasonStarted: Boolean get() = state.postseasonProgress != null

    /** 가을야구 진출 팀 수 (balance) */
    val postseasonSpots: Int get() = balance.int("postseason.spots")

    /** 지금 시리즈가 우리 팀 시리즈인가 */
    val userInCurrentSeries: Boolean get() = state.postseasonProgress?.current?.involves(userTeamId) == true

    /**
     * 진행 버튼 한 번 (docs/14).
     * - 시작 전: 대진만 짠다 (엔트리를 정할 시간을 준다)
     * - 우리 시리즈: **한 경기만** 치른다. 경기 사이에 방침·휴식을 바꿀 수 있다
     * - 남의 시리즈: 그 시리즈를 끝까지 치른다
     */
    fun advancePostseason() {
        if (!seasonOver || postseasonDone) return
        busy = true
        val engine = postseasonEngine()
        when {
            !postseasonStarted -> {
                engine.start(state)
                postseasonGames = emptyList()
            }
            userInCurrentSeries -> {
                val index = state.postseasonProgress!!.gamesPlayed
                engine.playNextGame(state, postseasonRandom(index))?.let { postseasonGames = postseasonGames + it }
            }
            else -> postseasonGames = postseasonGames + engine.playSeries(state, ::postseasonRandom)
        }
        busy = false
        revision++
    }

    /** 남은 포스트시즌을 끝까지 치른다 (5강 탈락 후 한 번에 보기, 스토브리그로 바로 넘어갈 때). 지금 정해 둔 방침은 그대로 쓴다 */
    fun runPostseason() {
        if (!seasonOver || postseasonDone) return
        busy = true
        val engine = postseasonEngine()
        if (!postseasonStarted) {
            engine.start(state)
            postseasonGames = emptyList()
        }
        while (!postseasonDone) postseasonGames = postseasonGames + engine.playSeries(state, ::postseasonRandom)
        busy = false
        revision++
    }

    /** 우리 팀 포스트시즌 방침 (총력전·정상·보호). 다음 경기부터 적용된다 */
    fun setPostseasonPolicy(policy: WeeklyPolicy) {
        state.updatePostseasonOrders { it.copy(policies = it.policies + (userTeamId to policy)) }
        revision++
    }

    fun postseasonPolicy(): WeeklyPolicy = state.postseasonProgress?.policyOf(userTeamId) ?: WeeklyPolicy.ALL_OUT

    /** 다음 경기 휴식 지시를 켜고 끈다. 그 경기를 치르면 풀린다 */
    fun togglePostseasonRest(playerId: PlayerId) {
        val player = state.player(playerId)
        if (player.teamId != userTeamId) return
        state.updatePostseasonOrders {
            it.copy(resting = if (playerId in it.resting) it.resting - playerId else it.resting + playerId)
        }
        revision++
    }

    fun isPostseasonResting(playerId: PlayerId): Boolean = state.postseasonProgress?.resting?.contains(playerId) == true

    /** 감독이 다음 경기에 낼 선발 (비서 브리핑의 "감독 구상") */
    fun postseasonPlannedStarter(teamId: TeamId = userTeamId): Player? =
        postseasonEngine().plannedStarter(state, teamId)?.let { state.player(it) }

    var postseasonGames by mutableStateOf<List<Postseason.PlayedGame>>(emptyList())
        private set

    fun postseason(): PostseasonResult? = state.postseason

    val postseasonDone: Boolean get() = state.postseason != null

    /**
     * 스토브리그를 시작한다 (docs/09, 11).
     *
     * 성장·노화 → 각성·급노쇠 → 은퇴 → 군 복무 → 계약 → 드래프트 입단 → 육성 → 방출까지 처리한 뒤
     * **FA 시장 앞에서 멈춘다.** FA 입찰은 유저가 해야 하는 결정이기 때문이다.
     */
    fun startNextSeason() {
        if (!seasonOver || inFreeAgency) return
        if (!postseasonDone) runPostseason()
        busy = true
        val offseason = Offseason(balance, strength)
        offseasonRandom = Seeds.random(seed, state.season, Seeds.Phase.OFFSEASON)
        val (nextLeague, report) = offseason.run(
            state = state,
            random = offseasonRandom,
            rookieSupplier = rookieSupplier(state.league),
            prospectSupplier = prospectSupplier(state.league),
            foreignSupplier = foreignSupplier(state.league),
            autoFreeAgency = false,
            // 유저 구단은 단장 계획대로 (아무것도 안 건드렸으면 비서 추천 계획)
            userTeam = userTeamId,
            plan = if (unemployed) null else offseasonPlan(),
        )
        pendingOffseason = offseason
        pendingLeague = nextLeague
        pendingStandings = state.standings
        faState = offseason.freeAgency.open(nextLeague, state.standings, offseasonRandom, report.released.toSet(), userTeam = userTeamId.takeIf { !unemployed })
        lastOffseason = report
        lastReport = null
        busy = false
        revision++
    }

    /** FA 한 라운드를 진행한다. 마지막 라운드가 끝나면 시장이 닫히고 새 시즌이 시작된다. */
    fun advanceFaRound() {
        val market = pendingOffseason?.freeAgency ?: return
        val current = faState ?: return
        val target = pendingLeague ?: return
        busy = true
        lastFaReport = market.runRound(current, target, pendingStandings, offseasonRandom, userTeam = userTeamId)
        if (current.round > market.rounds || current.isClosed) closeFreeAgency()
        busy = false
        revision++
    }

    /** 남은 라운드를 한 번에 돌린다 (FA 에 관심이 없을 때). */
    fun skipFreeAgency() {
        val market = pendingOffseason?.freeAgency ?: return
        busy = true
        while (faState != null) {
            val current = faState ?: break
            lastFaReport = market.runRound(
                current,
                pendingLeague ?: break,
                pendingStandings,
                offseasonRandom,
                userTeam = userTeamId,
            )
            if (current.round > market.rounds || current.isClosed) closeFreeAgency()
        }
        busy = false
        revision++
    }

    /** 시장을 닫고 다음 시즌을 연다. */
    private fun closeFreeAgency() {
        val offseason = pendingOffseason ?: return
        val market = offseason.freeAgency
        val current = faState ?: return
        val target = pendingLeague ?: return

        val signed = market.close(current, target, offseasonRandom)
        val report = lastOffseason
        val protectedIds = (report?.drafted.orEmpty() + report?.developmentSignings.orEmpty() +
            current.signings.map { it.playerId }).toSet()
        val (settledLeague, extraReleased) = offseason.settleRosters(signed, protectedIds, userTeamId)
        // FA 영입은 따로 남는 기록이 없어서 여기서 커리어 결정 기록에 적는다 (docs/13 메아리·연대기)
        val faDecisions = current.signings.filter { it.offer.teamId == userTeamId }.map { signing ->
            baseballgm.management.GmDecision(
                season = settledLeague.season,
                week = 0,
                kind = baseballgm.management.DecisionKind.FREE_AGENT,
                playerId = signing.playerId,
                playerName = settledLeague.players.firstOrNull { it.id == signing.playerId }?.registeredName ?: signing.playerId.value,
                teamId = userTeamId,
            )
        }
        val finalLeague = settledLeague.copy(
            management = settledLeague.management.copy(decisions = settledLeague.management.decisions + faDecisions),
        )

        lastOffseason = report?.copy(
            faSignings = current.signings.toList(),
            released = report.released + extraReleased,
        )
        // 해임됐다면 새 자리를 고를 때까지 시즌을 시작하지 않는다 (docs/13 커리어 모드)
        val review = report?.review
        jobOffers = review?.offers.orEmpty()
        val stillEmployed = finalLeague.management.userTeam
        if (stillEmployed != null) userTeamId = stillEmployed

        loop = WeekLoop(balance, finalLeague)
        val previous = state
        state = SeasonState.of(finalLeague, calendar, balance, report?.scoutingLevels.orEmpty())
        carryScoutingPrefs(previous, previous.league.management.userTeam)
        loop.prepareRosters(state)
        faState = null
        pendingLeague = null
        pendingOffseason = null
    }

    /** 이직 제안 (docs/13). 해임된 뒤 값이 채워진다 */
    var jobOffers by mutableStateOf<List<JobOffer>>(emptyList())
        private set

    val unemployed: Boolean get() = state.league.management.userTeam == null

    /** 제안을 받아들여 새 구단으로 간다. */
    fun acceptOffer(offer: JobOffer) {
        if (!unemployed) return
        val moved = state.league.copy(
            management = state.league.management.copy(userTeam = offer.teamId, onSabbatical = false),
        )
        val previous = state
        val previousTeam = userTeamId
        userTeamId = offer.teamId
        loop = WeekLoop(balance, moved)
        state = SeasonState.of(moved, calendar, balance)
        carryScoutingPrefs(previous, previousTeam)
        loop.prepareRosters(state)
        jobOffers = emptyList()
        revision++
    }

    // ---------- 조회 ----------

    fun teamsRanked() = state.standings.ranked()

    fun record(teamId: TeamId = userTeamId) = state.standings.record(teamId)

    fun rank(teamId: TeamId = userTeamId) = state.standings.rankOf(teamId)

    fun gamesBehind(teamId: TeamId = userTeamId) = state.standings.gamesBehind(teamId)

    /**
     * 화면에 보일 선수단 (2026-10-03).
     *
     * 평소엔 시즌 상태 그대로. **FA 시장 중(스토브리그)엔 다음 시즌 선수단**이다 — 시장에 나간 FA·은퇴·방출 선수는
     * 빠지고, 드래프트 신인은 들어오고, 이번 시장에서 계약한 FA 도 넣는다. 그래야 빈 포지션이 바로 보인다.
     * 보상선수 이동은 시장이 닫힐 때 반영된다.
     */
    private fun squadOf(teamId: TeamId): List<Player> =
        stoveState()?.playersOf(teamId) ?: state.playersOf(teamId)

    /** 스토브리그 선수단 캐시. 계약이 성사되거나 라운드가 넘어갈 때만 다시 만든다 */
    private var stoveCache: Pair<String, SeasonState>? = null

    /**
     * 스토브리그(FA 시장 중) 선수단: 다음 시즌 리그 + 이번 시장 계약 FA 에, **개막 엔트리를 미리 짜 둔** 상태.
     *
     * 다음 시즌 리그의 1·2군 구분은 지난 시즌 그대로라 낡았다 — FA 가 빠진 1군은 선발이 모자라고, 전력 계산이
     * 빈자리를 남은 선수로 채워 현재 전력이 베스트보다 높게 나오기도 했다. 시즌이 시작할 때와 같은 엔트리 정리
     * ([WeekLoop.prepareRosters], 난수 없음)를 미리 돌려 "이대로 개막하면"의 1군을 보여 준다.
     */
    private fun stoveState(): SeasonState? {
        val next = pendingLeague ?: return null
        val signings = faState?.signings.orEmpty()
        val key = "${next.season}:${signings.joinToString { it.playerId.value }}"
        stoveCache?.takeIf { it.first == key }?.let { return it.second }
        val moved = signings.associate { it.playerId to it.offer.teamId }
        val league = next.copy(
            players = next.players.map { player -> moved[player.id]?.let { player.withTeam(it, RosterLevel.FUTURES) } ?: player },
        )
        val stove = SeasonState.of(league, calendar, balance)
        WeekLoop(balance, league).prepareRosters(stove)
        stoveCache = key to stove
        return stove
    }

    /** 화면 기준 시즌: FA 시장 중엔 다음 시즌 (신인 표시·나이 등) */
    private val displaySeason: Int get() = pendingLeague?.season ?: state.season

    fun roster(level: RosterLevel, teamId: TeamId = userTeamId): List<Player> =
        squadOf(teamId)
            .filter { it.rosterLevel == level }
            .sortedByDescending { strength.overallOf(it) }

    /** FA 시장 중엔 다음 시즌 리그에서 먼저 찾는다 (새로 들어온 신인은 시즌 상태에 없다) */
    fun player(id: PlayerId): Player =
        stoveState()?.allPlayers()?.firstOrNull { it.id == id }
            ?: pendingLeague?.players?.firstOrNull { it.id == id }
            ?: state.player(id)

    fun overall(player: Player): Double = strength.overallOf(player)

    /** 우리 팀 현재 전력 (1군·뛸 수 있는 선수). 우리 팀이라 범위 폭이 0 이다 */
    fun currentStrength(): baseballgm.league.TeamStrengthRange =
        strengthScouting.current(squadOf(userTeamId), department(), userTeamId, displaySeason, ::recordOf)

    /** 우리 팀 베스트 전력 (1·2군 전체, 부상이 다 나았다고 칠 때) */
    fun bestStrength(): baseballgm.league.TeamStrengthRange =
        strengthScouting.best(squadOf(userTeamId), department(), userTeamId, displaySeason, ::recordOf)

    /** 리그 전력 비교. 타 팀은 스카우트 관측값으로 합산한 범위다 (불변 원칙 4) */
    fun powerBoard(): baseballgm.scouting.LeaguePowerBoard =
        strengthScouting.leagueBoard(
            state.league.teams.associate { it.id to squadOf(it.id) },
            department(),
            userTeamId,
            displaySeason,
            ::recordOf,
        )

    /**
     * 포지션 뎁스 (로스터 화면 다이아몬드). 우리 팀 선수만, 1군 먼저 · 뛸 수 있는 선수 먼저 · 종합 높은 순.
     * 첫 번째가 그 자리 주전이다 (1군에 뛸 수 있는 선수가 없으면 주전 없음).
     */
    fun depthOf(slot: baseballgm.season.RosterSlot): List<Player> =
        squadOf(userTeamId)
            .filter { baseballgm.season.RosterSlot.of(it) == slot }
            .sortedWith(
                compareBy<Player> { it.rosterLevel != RosterLevel.FIRST_TEAM }
                    .thenBy { it.condition.isInjured || !it.military.isAvailable }
                    .thenByDescending { strength.overallOf(it) },
            )

    fun formLabel(player: Player): String = formModel.levelOf(player.condition.form)

    fun batting(id: PlayerId): BattingLine = state.stats.battingOf(id).total

    fun pitching(id: PlayerId): PitchingLine = state.stats.pitchingOf(id).total

    fun futuresBatting(id: PlayerId): BattingLine = state.stats.futuresBattingOf(id)

    fun futuresPitching(id: PlayerId): PitchingLine = state.stats.futuresPitchingOf(id)

    /**
     * 선수 정보를 스카우트 시선으로 본다.
     * 우리 팀이면 현재 능력치가 정확하고, 타 팀이면 1군·2군에 따라 범위가 넓어진다 (docs/02).
     */
    fun scout(player: Player): ScoutedPlayer =
        draftRunner.scouting.view(department(), player, userTeamId, displaySeason, recordOf(player))

    /** 연도별 능력치 (선수 상세 그래프). 지난 시즌 기록 + 지금, 정확도만큼 흐린 값 */
    fun ratingHistory(player: Player): List<baseballgm.scouting.SeasonRatings> =
        draftRunner.scouting.ratingHistory(department(), player, userTeamId, state.league.history, state.season, recordOf(player))

    fun accuracyFor(player: Player): ScoutingPrecision =
        draftRunner.scouting.precisionFor(department(), player, userTeamId, record = recordOf(player))

    /**
     * 공개 기록 (2026-10-04 유저 요청 "준주전급까지는 능력치를 다 보여주자"). 올 시즌(스토브리그면 막 끝난 시즌) 기록과
     * 그 전 시즌 기록으로 준주전급인지 본다. FA 시장의 프로 선수는 아마추어처럼 흐리게 보이지 않는다
     */
    fun recordOf(player: Player): baseballgm.scouting.PublicRecord =
        draftRunner.scouting.publicRecordOf(player, state.stats, (pendingLeague ?: state.league).history, displaySeason)

    fun isOwn(player: Player): Boolean = player.teamId == userTeamId

    /**
     * 올해 데뷔한 국내 신인인가 (드래프트·육성선수로 이번 시즌 팀에 합류). 데뷔 시즌은 공개 정보다.
     * 외국인은 데뷔 첫해여도 신인으로 치지 않는다 (신인왕 자격과 같은 기준)
     */
    private var coreCache: Pair<Int, MutableMap<TeamId, Set<PlayerId>>>? = null

    /**
     * 팀내 핵심 유망주인가 (2026-10-04). 우리 스카우트 시선으로 본 그 구단의 어린 선수 상위 몇 명.
     * 선수단 전체를 봐야 해서 상태가 바뀔 때만 구단별로 다시 계산한다.
     */
    fun isCoreProspect(player: Player): Boolean {
        val team = player.teamId ?: return false
        val key = revision
        val cache = coreCache?.takeIf { it.first == key } ?: (key to mutableMapOf<TeamId, Set<PlayerId>>()).also { coreCache = it }
        val ids = cache.second.getOrPut(team) {
            draftRunner.scouting.coreProspects(department(), squadOf(team), userTeamId, displaySeason, ::recordOf)
        }
        return player.id in ids
    }

    fun isRookie(player: Player): Boolean =
        !player.isForeign && player.teamId != null && player.debutSeason == displaySeason

    /** 이번 주 유저 팀 일정. */
    fun weekSchedule(week: Int = state.week) =
        state.league.schedule.gamesInWeek(week).filter { it.involves(userTeamId) }

    fun sheet(teamId: TeamId = userTeamId): DirectiveSheet =
        managerAI.applyPolicy(
            managerAI.buildSheet(state.tendenciesOf(teamId), state.playersOf(teamId), state.season),
            state.policyOf(teamId),
        )

    fun manager(teamId: TeamId = userTeamId) = state.league.managerOf(teamId)

    fun tendencies(teamId: TeamId = userTeamId): ManagerTendencies = state.tendenciesOf(teamId)

    // ---------- 지시 ----------

    fun setPolicy(policy: WeeklyPolicy) {
        state.policies[userTeamId] = policy
        revision++
    }

    fun policy(): WeeklyPolicy = state.policyOf(userTeamId)

    /** 단장 방침. 감독 성향이 매주 이쪽으로 조금씩 끌려온다 (docs/06 반영률). */
    fun setDirection(direction: ManagerTendencies?) {
        if (direction == null) state.gmDirections.remove(userTeamId) else state.gmDirections[userTeamId] = direction
        revision++
    }

    fun direction(): ManagerTendencies? = state.gmDirections[userTeamId]

    // ---------- 기록실 ----------

    /** 규정 타석 (우리 팀 경기 × 3.1) */
    fun qualifyingPa(): Int = (record().games * PA_PER_GAME).toInt()

    /** 규정 이닝을 아웃으로 (우리 팀 경기 × 1이닝) */
    fun qualifyingOuts(): Int = record().games * OUTS_PER_GAME

    fun battingLeaders(limit: Int = 10): List<Pair<Player, BattingLine>> {
        val minimum = qualifyingPa()
        return state.stats.battingLeaders(minimum) { it.battingAverage }
            .take(limit)
            .map { state.player(it.first) to it.second }
    }

    fun homeRunLeaders(limit: Int = 10): List<Pair<Player, BattingLine>> =
        state.stats.battingLeaders(0) { it.homeRuns.toDouble() }
            .take(limit)
            .map { state.player(it.first) to it.second }

    fun eraLeaders(limit: Int = 10): List<Pair<Player, PitchingLine>> {
        val minimum = qualifyingOuts()
        return state.stats.pitchingLeaders(minimum) { -it.era }
            .take(limit)
            .map { state.player(it.first) to it.second }
    }

    fun winLeaders(limit: Int = 10): List<Pair<Player, PitchingLine>> =
        state.stats.pitchingLeaders(0) { it.wins.toDouble() }
            .take(limit)
            .map { state.player(it.first) to it.second }

    fun isPitcher(player: Player): Boolean = player is Pitcher

    fun positionLabel(player: Player): String = when (player) {
        is Batter -> player.primaryPosition.label
        is Pitcher -> if (player.role.isReliever) "RP" else "SP"
    }

    // ---------- 스카우트 (docs/10) ----------

    private fun department() = state.scoutingOf(userTeamId)

    val scoutingLevel: Int get() = department().level

    val focusSlots: Int get() = draftRunner.scouting.focusSlots(department())

    fun setScoutingLevel(level: Int) {
        val department = department()
        department.setLevel(level)
        department.trimToSlots(draftRunner.scouting.focusSlots(department))
        revision++
    }

    fun scoutingCost(level: Int): Double = draftRunner.scouting.budget.annualCost(level)

    /**
     * 자동 집중 관찰 (유저 요청 2026-10-03). 켜면 그 자리에서 한 번 돌려 빈 슬롯을 채운다 —
     * 다음 주까지 기다리면 "켰는데 왜 아무 일도 없지"가 된다.
     */
    var autoFocus: Boolean
        get() = department().autoFocus
        set(value) {
            department().autoFocus = value
            if (value) draftRunner.autoFocus(state, userTeamId)
            revision++
        }

    /** 지명 추천 기준. 바꾸면 추천과 자동 관찰 순서가 같이 바뀐다 */
    var draftPolicy: baseballgm.market.DraftPolicy
        get() = department().draftPolicy
        set(value) {
            department().draftPolicy = value
            revision++
        }

    private var adviceCache: Pair<Int, List<baseballgm.market.RoundAdvice>>? = null

    /**
     * 남은 우리 순번마다 지명 추천. 모의 드래프트를 돌려서 무겁기 때문에(JVM 약 50ms) 상태가 바뀔 때만 다시 계산한다.
     * [refresh] 가 false 면 마지막 계산을 그대로 준다 — 생중계로 다른 구단이 지명하는 동안 매 장마다 다시 돌리면
     * 폰에서 화면이 끊긴다. 드래프트가 끝났으면 빈 목록.
     */
    fun draftAdvice(refresh: Boolean = true): List<baseballgm.market.RoundAdvice> {
        val key = revision
        adviceCache?.takeIf { it.first == key || !refresh }?.let { return it.second }
        return draftRunner.advise(state, userTeamId).also { adviceCache = key to it }
    }

    /** 받은 스카우트 정기 리포트 (받은 순서) */
    fun scoutDigests(): List<baseballgm.scouting.ScoutDigest> = state.scoutDigests.toList()

    /** 다음 정기 리포트가 오는 주차. 드래프트가 끝났으면 null */
    fun nextDigestWeek(): Int? {
        if (draftDone) return null
        // 최종 리포트는 드래프트 직전 주. 지금 주차의 리포트는 이 주 경기를 시작할 때 쓴다
        val finalWeek = state.calendar.draftWeek - 1
        val every = draftRunner.digestEveryWeeks
        val week = state.week
        val next = if (week % every == 0) week else (week / every + 1) * every
        return if (week > finalWeek) null else minOf(next, finalWeek)
    }

    /** 이 후보가 지금 어떻게 됐나 (리포트는 고정이라 화면이 현재 상태를 덧붙인다) */
    fun prospectById(id: PlayerId): DraftProspect? = league.draftPool.byId(id)

    /** 직접 관찰을 붙일 수 있나. 꽉 찼어도 자동으로 붙은 선수가 있으면 그 자리를 비켜 준다 */
    val canAddFocus: Boolean get() = department().canAddManually(focusSlots)

    /** 자동 관찰이 붙인 선수인가 */
    fun isAutoFocused(prospect: DraftProspect): Boolean = department().isAutoAssigned(prospect.id)

    /** 이 선수는 더 봐도 정확도가 오르지 않는가 (자동 관찰이 빼는 기준) */
    fun isFullyScouted(prospect: DraftProspect): Boolean =
        draftRunner.scouting.isFullyScouted(department(), prospect.player, userTeamId)

    /** 시즌이 바뀌거나 이직해도 스카우트 설정(자동 관찰·추천 기준)은 단장을 따라간다 */
    private fun carryScoutingPrefs(previous: SeasonState, previousTeam: TeamId?) {
        val old = previousTeam?.let { previous.scouting[it] } ?: return
        val now = state.scouting[userTeamId] ?: return
        now.autoFocus = old.autoFocus
        now.draftPolicy = old.draftPolicy
    }

    /** 쓰고 있는 슬롯 수. 드래프트 후보·외국인 후보가 같은 슬롯을 나눠 쓴다 (docs/10) */
    val focusUsed: Int get() = department().activeFocus().size

    fun focusedProspects(): List<DraftProspect> =
        department().activeFocus().mapNotNull { league.draftPool.byId(it) }
            .sortedByDescending { department().weeksOn(it.id) }

    fun isFocused(prospect: DraftProspect): Boolean = department().isFocused(prospect.id)

    fun focusWeeks(prospect: DraftProspect): Int = department().weeksOn(prospect.id)

    /** 관찰을 붙일 수 있는 후보인가. 이미 지명됐거나 드래프트가 끝났으면 못 붙인다 */
    fun canFocus(prospect: DraftProspect): Boolean =
        !draftDone && (state.draft?.isAvailable(prospect.id) ?: true)

    /** 한 번이라도 관찰한 적이 있나. 지명돼 슬롯에서 빠진 뒤에도 남는다 */
    fun wasWatched(playerId: PlayerId): Boolean = department().weeksOn(playerId) > 0

    /** 이 후보의 지명 결과. 아직 안 뽑혔으면 null */
    fun selectionOf(prospect: DraftProspect): DraftSelection? =
        state.draft?.selections?.firstOrNull { it.playerId == prospect.id }

    /** 집중 관찰 슬롯을 켜고 끈다. 슬롯이 꽉 찼거나 관찰할 수 없는 후보면 false. */
    fun toggleFocus(prospect: DraftProspect): Boolean {
        val department = department()
        val added = if (department.isFocused(prospect.id)) {
            department.removeFocus(prospect.id)
            true
        } else if (!canFocus(prospect)) {
            false
        } else {
            department.addFocusManually(prospect.id, draftRunner.scouting.focusSlots(department))
        }
        revision++
        return added
    }

    /** 우리 팀 기준 지명 후보 순위 (docs/10 리포트). */
    fun draftBoard(limit: Int = 40): List<DraftProspect> =
        draftRunner.board(state, userTeamId, limit).map { it.first }

    fun prospectReport(prospect: DraftProspect): ScoutReport = draftRunner.report(state, userTeamId, prospect)

    // ---------- 드래프트 (docs/10) ----------

    val isDraftWeek: Boolean get() = state.calendar.isDraftWeek(state.week)

    val draftDone: Boolean get() = state.draftResult != null

    fun draftSlot(): DraftSlot? = state.draft?.current()

    /** 우리가 가진 지명권 (docs/10 지명권 트레이드). */
    fun myPicks(): List<DraftPickRight> = state.draftRights.ofOwner(userTeamId)

    fun draftSelections(): List<DraftSelection> = state.draft?.selections.orEmpty()

    fun myDraftPicks(): List<DraftSelection> = state.draft?.selectionsOf(userTeamId).orEmpty()

    /**
     * 올해 우리 신인: 지명 순서대로 (지명 + 리포트). 우리가 뽑은 선수라 리포트의 현재 능력치는 정확하고
     * 잠재력은 등급이다 (docs/10 "지명 이후"). 다음 시즌 개막 전까지만 있다
     */
    fun myDraftClass(): List<Pair<DraftSelection, ScoutReport>> =
        myDraftPicks().mapNotNull { selection ->
            league.draftPool.byId(selection.playerId)?.let { selection to prospectReport(it) }
        }

    /** 지명 전 우리 스카우트가 보던 리포트 (범위). 지명 후 정확한 값과 견줘 볼 때 쓴다 */
    fun preDraftReport(playerId: PlayerId): ScoutReport? =
        league.draftPool.byId(playerId)?.let { draftRunner.scoutingReport(state, userTeamId, it) }

    fun availableProspects(): List<DraftProspect> =
        state.draft?.availableProspects() ?: league.draftPool.prospects

    val isMyDraftTurn: Boolean get() = draftSlot()?.ownerTeam == userTeamId

    /** 전체 순번 수 (라운드 × 구단). 드래프트가 아직 안 열렸으면 0 */
    val draftTotalPicks: Int get() = state.draft?.order?.size ?: 0

    /** 드래프트 순번표를 연다 (지명은 하지 않는다). 드래프트 화면에 들어오면 부른다 */
    fun openDraft() {
        if (!isDraftWeek || draftDone || state.draft != null) return
        draftRunner.begin(state)
        revision++
    }

    /**
     * 다른 구단의 지명 **한 건**. 드래프트 생중계에서 화면이 한 장씩 넘기며 부른다.
     * 내 차례거나 끝났으면 아무것도 하지 않고 null.
     */
    fun advanceDraftPick(): DraftSelection? {
        if (!isDraftWeek || draftDone) return null
        val pick = draftRunner.step(state, stopForTeam = userTeamId, random = draftRandom())
        revision++
        return pick
    }

    /** 내 차례가 올 때까지 AI 가 지명한다. 끝나면 true. */
    fun advanceDraft(): Boolean {
        busy = true
        val done = draftRunner.advance(state, stopForTeam = userTeamId, random = draftRandom())
        busy = false
        revision++
        return done
    }

    /**
     * 지명 한 건, **우리 차례도 멈추지 않는다** (지명 자동 진행). 우리 차례면 스카우트팀이 추천 기준대로 고른다.
     * 드래프트가 끝났으면 null.
     */
    fun advanceDraftPickAuto(): DraftSelection? {
        if (!isDraftWeek || draftDone) return null
        val pick = draftRunner.stepDelegated(state, userTeamId, draftRandom())
        revision++
        return pick
    }

    /** 남은 지명을 끝까지 한 번에 — 우리 지명은 스카우트팀에 맡긴다 */
    fun autoDraft() {
        if (!isDraftWeek || draftDone) return
        busy = true
        while (draftRunner.stepDelegated(state, userTeamId, draftRandom()) != null) Unit
        busy = false
        revision++
    }

    /** 이번 드래프트에서 아직 쓰지 않은 우리 순번 수 */
    fun remainingUserPicks(): Int {
        val draft = state.draft ?: return myPicks().size
        return draft.order.drop(draft.index).count { it.ownerTeam == userTeamId }
    }


    /** 내 차례의 지명. */
    fun draftPlayer(prospect: DraftProspect) {
        if (draftRunner.isTurnOf(state, userTeamId) && state.draft?.isAvailable(prospect.id) == true) {
            draftRunner.select(state, prospect.id)
            revision++
        }
    }

    // ---------- 외국인 (docs/12) ----------

    fun foreigners(teamId: TeamId = userTeamId): List<Player> =
        state.playersOf(teamId).filter { it.isForeign }

    fun foreignCandidates(pitchersOnly: Boolean? = null): List<ForeignCandidate> =
        foreignService.candidates(state, pitchersOnly)

    /** KBO 환산 기록. 환산 오차가 섞여 있다 (docs/12) */
    fun convertedLine(candidate: ForeignCandidate) = foreignService.market.convertedLine(candidate)

    /** 외국인 후보도 스카우트 리포트를 볼 수 있다 (집중 관찰 슬롯 사용 가능) */
    fun foreignReport(candidate: ForeignCandidate) =
        draftRunner.scouting.report(
            department = state.scoutingOf(userTeamId),
            player = candidate.player,
            viewerTeam = userTeamId,
            season = state.season,
        )

    fun isFocused(candidate: ForeignCandidate): Boolean = state.scoutingOf(userTeamId).isFocused(candidate.id)

    fun toggleFocus(candidate: ForeignCandidate): Boolean {
        val department = state.scoutingOf(userTeamId)
        val added = if (department.isFocused(candidate.id)) {
            department.removeFocus(candidate.id)
            true
        } else {
            department.addFocusManually(candidate.id, draftRunner.scouting.focusSlots(department))
        }
        revision++
        return added
    }

    /** 적응 상태 문구. 숨김 수치를 그대로 보여주지 않는다 (docs/12) */
    fun adaptationLabel(player: Player): String? =
        if (!player.isForeign) null else foreignService.adaptation.label(player.condition.adaptationPenalty)

    val foreignReplacementOpen: Boolean get() = foreignService.isOpen(state)

    fun foreignReplacementsLeft(): Int = foreignService.replacementsLeft(state, userTeamId)

    fun foreignBuyout(player: Player): Double = foreignService.buyoutOf(player, state.week)

    /** 팀당 외국인 보유 한도 (balance.json) */
    val foreignMaxPerTeam: Int get() = foreignService.market.rules.maxPerTeam

    /** 외국인 보유 인원이 다 차지 않아 내보내지 않고 바로 영입할 수 있는가 */
    fun hasOpenForeignSlot(): Boolean = foreignService.hasOpenSlot(state, userTeamId)

    fun foreignReplacementProblems(outgoing: Player?, candidate: ForeignCandidate): List<String> =
        foreignService.problemsForReplacement(state, userTeamId, outgoing?.id, candidate, candidate.askingSalary)

    /**
     * 시즌 중 외국인 영입 (docs/12). [outgoing] 이 null 이면 빈 자리에 그냥 데려온다.
     * 막히는 이유가 있으면 바꾸지 않고 이유만 돌려준다
     */
    fun replaceForeign(outgoing: Player?, candidate: ForeignCandidate): List<String> {
        val problems = foreignReplacementProblems(outgoing, candidate)
        if (problems.isNotEmpty()) return problems
        val replacement = foreignService.replace(state, userTeamId, outgoing?.id, candidate, candidate.askingSalary)
        state.decisionLog += baseballgm.management.GmDecision(
            state.season, state.week, baseballgm.management.DecisionKind.FOREIGN,
            candidate.player.id, candidate.player.registeredName, userTeamId,
        )
        state.inbox.add(
            week = state.week,
            category = baseballgm.season.InboxCategory.MARKET,
            teamId = userTeamId,
            text = replacement.message(outgoing?.registeredName, candidate.player.registeredName),
        )
        revision++
        return emptyList()
    }

    // ---------- 국제대회·군 복무 (docs/12) ----------

    fun tournamentResult(): TournamentResult? = state.tournamentResult

    fun onInternationalDuty(teamId: TeamId = userTeamId): List<Player> =
        state.playersOf(teamId).filter { state.isOnInternationalDuty(it.id) }

    fun isOnInternationalDuty(player: Player): Boolean = state.isOnInternationalDuty(player.id)

    fun militaryLabel(player: Player): String = when (val military = player.military) {
        is baseballgm.model.MilitaryStatus.Completed -> "군필"
        is baseballgm.model.MilitaryStatus.Exempt -> "병역 면제"
        is baseballgm.model.MilitaryStatus.NotRequired -> "해당 없음"
        is baseballgm.model.MilitaryStatus.Unfulfilled -> "미필 (기한 ${military.deadlineAge}세)"
        is baseballgm.model.MilitaryStatus.Serving -> {
            val kind = if (military.kind == baseballgm.model.ServiceKind.SANGMU) "상무" else "현역"
            "$kind 복무 중 (${military.returnSeason}시즌 ${military.returnWeek}주차 복귀)"
        }
    }

    // ---------- 세이버 지표 (docs/04 2·3단계) ----------

    /** 리그 상수는 시즌 기록에서 계산한다. 시즌 초에는 기본값으로 버틴다 */
    fun leagueConstants(): LeagueConstants = LeagueConstants.from(state.stats, balance)

    fun parkFactorOf(player: Player): Double =
        player.teamId?.let { state.league.team(it).parkFactor } ?: 1.0

    fun batterMetrics(player: Player): BatterMetrics =
        sabermetrics.batter(batting(player.id), leagueConstants(), parkFactorOf(player))

    fun pitcherMetrics(player: Player): PitcherMetrics =
        sabermetrics.pitcher(pitching(player.id), leagueConstants(), parkFactorOf(player))

    fun war(player: Player): WarBreakdown =
        warCalculator.of(player, state.stats, leagueConstants(), parkFactorOf(player))

    fun warLeaders(limit: Int = 10, pitchers: Boolean = false): List<Pair<Player, WarBreakdown>> {
        val constants = leagueConstants()
        return state.allPlayers()
            .filter { (it is Pitcher) == pitchers && it.teamId != null }
            .map { it to warCalculator.of(it, state.stats, constants, parkFactorOf(it)) }
            .sortedByDescending { it.second.war }
            .take(limit)
    }

    // ---------- FA (docs/11) ----------

    fun faAgents(): List<FreeAgent> = faState?.remaining.orEmpty()

    fun faRound(): Int = faState?.round ?: 0

    fun faRounds(): Int = pendingOffseason?.freeAgency?.rounds ?: 0

    /** 직전 시즌 우리 팀 소속이었던 FA (계약 만료·방출) */
    fun isOurFormer(agent: FreeAgent): Boolean = agent.previousTeam == userTeamId

    fun faPlayer(agent: FreeAgent): Player =
        pendingLeague?.player(agent.playerId) ?: state.player(agent.playerId)

    fun faOffer(agent: FreeAgent): ContractOffer? = faState?.offerOf(userTeamId, agent.playerId)

    /** 우리 구단의 제안. 같은 라운드 안에서는 고쳐 낼 수 있다 */
    fun submitFaOffer(agent: FreeAgent, salary: Double, years: Int, signingBonus: Double = 0.0) {
        faState?.putOffer(ContractOffer(userTeamId, agent.playerId, salary, years, signingBonus))
        revision++
    }

    // ---------- FA 협상 테이블 (2026-10-04, FM식) ----------

    private val faTalks: baseballgm.market.FaTalks?
        get() = pendingOffseason?.freeAgency?.let { market -> baseballgm.market.FaTalks(balance, market) }

    /** 시장에 남은 FA 하나. 이미 계약했으면 null */
    fun faAgent(playerId: PlayerId): FreeAgent? = faAgents().firstOrNull { it.playerId == playerId }

    /** 에이전트가 내미는 요구 조건 (항목별 중요도 포함) */
    fun faDemand(agent: FreeAgent): baseballgm.market.FaDemand? {
        val current = faState ?: return null
        val league = pendingLeague ?: return null
        return faTalks?.demand(current, agent.playerId, userTeamId, league, pendingStandings, bonusBudget = faBonusBudget(agent))
    }

    /** [agent] 에게 계약금으로 쓸 수 있는 운용 자금 (다른 FA 에 건 계약금·이미 계약한 FA 계약금을 뺀다) */
    fun faBonusBudget(agent: FreeAgent): Double {
        val current = faState ?: return 0.0
        val committed = current.offersBy(userTeamId).filter { it.playerId != agent.playerId }.sumOf { it.signingBonus } +
            current.signings.filter { it.offer.teamId == userTeamId }.sumOf { it.offer.signingBonus }
        return (currentFunds() - committed).coerceAtLeast(0.0)
    }

    /** 이번 라운드 남은 에이전트 인내심 */
    fun faPatience(playerId: PlayerId): Int = faState?.let { faTalks?.patienceLeft(it, playerId) } ?: 0

    val faMaxPatience: Int get() = balance.int("faNegotiation.patience")

    private val optionRules by lazy { baseballgm.market.OptionRules(balance) }

    /** 이 FA 에게 걸 수 있는 옵션 조항 */
    fun faOptionKinds(agent: FreeAgent): List<baseballgm.market.OptionKind> = optionRules.applicable(faPlayer(agent))

    fun faOptionLabel(kind: baseballgm.market.OptionKind): String = optionRules.label(kind)

    /**
     * 옵션 조항을 선수가 어떻게 보나: (지난 시즌 기록 글자, 달성 가능성 0~1). 지난 시즌 1군 기록(공개 정보)으로 정해진다.
     * 팀 포스트시즌은 우리 팀 지난 시즌 순위
     */
    fun faOptionOutlook(agent: FreeAgent, kind: baseballgm.market.OptionKind): Pair<String, Double> {
        val league = pendingLeague ?: return "" to 0.0
        val player = faPlayer(agent)
        val chance = optionRules.chance(kind, player, league, pendingStandings, userTeamId)
        val last = if (kind == baseballgm.market.OptionKind.TEAM_POSTSEASON) {
            "지난 시즌 우리 ${pendingStandings.rankOf(userTeamId)}위"
        } else {
            optionRules.lastSeason(player, league)?.let { stats -> optionRules.valueOf(kind, stats)?.let { "지난 시즌 ${optionRules.valueText(kind, it)}" } }
                ?: "지난 시즌 1군 기록 부족"
        }
        return last to chance
    }

    /** 옵션 상한 (연봉 대비) */
    val faMaxOptionRate: Double get() = balance.double("faNegotiation.maxOptionRate")

    /** 협상 대화 (우리 말 · 에이전트 말). 계약한 뒤에도 남는다 */
    fun faTalkLog(playerId: PlayerId): List<baseballgm.market.FaTalkLine> = faState?.talksOf(playerId).orEmpty()

    /** 이 조건이면 선수 마음이 어떤가 (내기 전에 미리 본다) */
    fun faPreview(agent: FreeAgent, offer: ContractOffer): FaNegotiation? {
        val market = pendingOffseason?.freeAgency ?: return null
        val current = faState ?: return null
        val league = pendingLeague ?: return null
        return market.negotiation(current, agent.playerId, userTeamId, league, pendingStandings, hypothetical = offer)
    }

    /** 이번 스토브리그에 우리와 계약한 FA 라면 그 계약 */
    fun faOurSigning(playerId: PlayerId): baseballgm.market.FaSigning? =
        faState?.signings?.firstOrNull { it.playerId == playerId && it.offer.teamId == userTeamId }

    /**
     * 협상 테이블에서 조건을 내민다 — 에이전트가 그 자리에서 답한다. 합의하면 바로 도장(경쟁 구단보다 좋을 때).
     * 계약금은 운용 자금에서 나가니 이미 다른 FA 에 건 계약금까지 합쳐 모자라면 내지 않는다.
     */
    fun proposeFa(agent: FreeAgent, offer: ContractOffer): baseballgm.market.FaTalkResult? {
        val talks = faTalks ?: return null
        val current = faState ?: return null
        val league = pendingLeague ?: return null
        val budget = faBonusBudget(agent)
        if (offer.signingBonus > budget + 1e-6) {
            return baseballgm.market.FaTalkResult(
                baseballgm.market.FaTalkOutcome.REJECTED,
                "운용 자금이 모자라요. 다른 FA 에 건 계약금까지 치면 계약금은 ${budget.oneDecimalText()}억까지 쓸 수 있어요.",
                patienceLeft = faPatience(agent.playerId),
            )
        }
        val result = talks.propose(current, offer.copy(teamId = userTeamId), league, pendingStandings)
        revision++
        return result
    }

    fun withdrawFaOffer(agent: FreeAgent) {
        faState?.putOffer(ContractOffer(userTeamId, agent.playerId, 0.0, 0))
        revision++
    }

    /** 에이전트가 전하는 소문 (우리를 뺀 최고 제시액). 과장이 섞여 있다 (docs/11) */
    fun faRumor(agent: FreeAgent): String =
        // 소문 문장은 볼 때마다 같게, 그리고 게임 난수를 건드리지 않게 따로 만든다
        pendingOffseason?.freeAgency?.rumor(
            faState ?: return "",
            agent.playerId,
            userTeamId,
            Seeds.random(seed, state.season, Seeds.Phase.RUMOR, agent.playerId.value.hashCode().toLong(), faRound().toLong()),
        ) ?: ""

    /** 역제안. 선수가 무엇이 아쉬운지, 얼마면 되는지 알려준다 (docs/11) */
    fun faCounter(agent: FreeAgent): Pair<ContractOffer, String>? {
        val market = pendingOffseason?.freeAgency ?: return null
        val current = faState ?: return null
        val league = pendingLeague ?: return null
        val offer = current.offerOf(userTeamId, agent.playerId) ?: return null
        return market.counterProposal(current, offer, faPlayer(agent), league, pendingStandings)
    }

    /** 이 FA 를 두고 우리 협상이 어디쯤인가 — 1순위/밀림, 계약 확률, 얼마면 1순위 */
    fun faNegotiation(agent: FreeAgent): FaNegotiation? {
        val market = pendingOffseason?.freeAgency ?: return null
        val current = faState ?: return null
        val league = pendingLeague ?: return null
        return market.negotiation(current, agent.playerId, userTeamId, league, pendingStandings)
    }

    /** 우리가 조건을 낸 FA 들의 현황. 밀린 것부터 */
    fun faMyNegotiations(): List<Pair<FreeAgent, FaNegotiation>> =
        faAgents().mapNotNull { agent -> faNegotiation(agent)?.takeIf { it.ourOffer != null }?.let { agent to it } }
            // 손봐야 할 것(밀림·최저 연봉 미달)부터
            .sortedBy { it.second.standing.ordinal.let { order -> if (it.second.standing == FaStanding.OUTBID || it.second.standing == FaStanding.BELOW_FLOOR) -1 else order } }

    /** 구단 이름 (FA 시장 중엔 다음 시즌 리그 기준) */
    fun teamName(teamId: TeamId): String = (pendingLeague ?: state.league).team(teamId).name

    /** 계약을 마친 FA 이름. 시장에서 빠진 뒤에도 보여야 해서 리그에서 찾는다 */
    fun faSignedName(playerId: PlayerId): String =
        (pendingLeague?.players?.firstOrNull { it.id == playerId } ?: state.allPlayers().firstOrNull { it.id == playerId })
            ?.registeredName ?: playerId.value

    /** 이번 스토브리그에 끝난 계약 (최근 것부터) */
    fun faSignings(): List<baseballgm.market.FaSigning> = faState?.signings?.reversed().orEmpty()

    // ---------- 비FA 다년계약 (docs/11) ----------

    /** 다년계약을 낼 수 없는 이유. 낼 수 있으면 null. FA 시장 중엔 못 한다 */
    fun extensionBlockedReason(playerId: PlayerId): String? {
        if (inFreeAgency || pendingOffseason != null) return "FA 시장이 끝난 뒤에 협상할 수 있어요"
        if (unemployed) return "지금은 맡은 구단이 없어요"
        return extensionService.ineligibleReason(state, userTeamId, state.player(playerId))
    }

    /** 다년계약 조건표. [years] 를 바꾸면 요구 연봉이 바뀐다 */
    fun extensionTerms(playerId: PlayerId, years: Int? = null): baseballgm.season.ExtensionTerms {
        val player = state.player(playerId)
        return extensionService.terms(state, player, years ?: extensionService.preferredYears(player, state.season))
    }

    /** 다년계약 협상 대화 (우리 말 · 선수 측 말). 세이브에 남지 않는다 */
    fun extensionTalkLog(playerId: PlayerId): List<baseballgm.market.FaTalkLine> = extensionService.talksOf(state, playerId)

    /** 다년계약 협상 인내심 (= 남은 협상 기회) */
    val extensionMaxTalks: Int get() = extensionService.maxTalks

    /**
     * 다년계약 옵션 조항을 선수가 어떻게 보나: (지난 시즌 기록 글자, 달성 가능성). FA 와 같은 규칙.
     * 팀 포스트시즌은 지금 우리 순위로 본다
     */
    fun extensionOptionOutlook(player: Player, kind: baseballgm.market.OptionKind): Pair<String, Double> {
        val pace = extensionService.paceOf(state, userTeamId, player)
        val chance = optionRules.chance(kind, player, state.league, state.standings, userTeamId, pace)
        val last = if (kind == baseballgm.market.OptionKind.TEAM_POSTSEASON) {
            "지금 우리 ${state.standings.rankOf(userTeamId)}위"
        } else {
            optionRules.lastSeason(player, state.league)?.let { stats -> optionRules.valueOf(kind, stats)?.let { "지난 시즌 ${optionRules.valueText(kind, it)}" } }
                ?: pace?.let { stats -> optionRules.valueOf(kind, stats)?.let { "올 시즌 페이스 ${optionRules.valueText(kind, it)}" } }
                ?: "1군 기록 부족"
        }
        return last to chance
    }

    fun optionKindsFor(player: Player): List<baseballgm.market.OptionKind> = optionRules.applicable(player)

    /** 다년계약 옵션 조항을 선수가 한 해 몇 억으로 쳐 주나 (요구액 대비 게이지용) */
    fun extensionOptionValue(player: Player, options: List<baseballgm.market.OptionClause>): Double =
        extensionService.optionValue(state, userTeamId, player, options)

    /** 올 시즌 이미 다년계약을 맺었나 */
    fun extendedThisSeason(playerId: PlayerId): Boolean = playerId in state.extendedThisSeason

    /** 다년계약 후보: 우리 국내 선수 중 지금 협상할 수 있는 선수. FA 가 가까운 순 */
    fun extensionCandidates(): List<Player> =
        state.playersOf(userTeamId)
            .filter { extensionBlockedReason(it.id) == null }
            .sortedWith(compareBy<Player> { extensionService.seasonsToFa(it) }.thenByDescending { strength.overallOf(it) })

    /** 다년계약을 내민다. 받아들이면 계약이 바로 바뀐다 */
    fun proposeExtension(
        playerId: PlayerId,
        salary: Double,
        years: Int,
        signingBonus: Double = 0.0,
        /** 옵션 조항 (2026-10-05 협상 테이블). 다음 시즌부터 붙는다 */
        options: List<baseballgm.market.OptionClause> = emptyList(),
    ): baseballgm.season.ExtensionResult {
        extensionBlockedReason(playerId)?.let {
            val outcome = if (extensionService.talksLeft(state, playerId) <= 0) baseballgm.market.FaTalkOutcome.BROKEN_OFF else baseballgm.market.FaTalkOutcome.REJECTED
            return baseballgm.season.ExtensionResult(false, it, extensionService.talksLeft(state, playerId), outcome)
        }
        val result = extensionService.propose(
            state,
            userTeamId,
            playerId,
            salary,
            years,
            signingBonus,
            // 선수의 양보 폭은 시즌·선수마다 고정 — 몇 번을 물어도 기준선이 같다
            Seeds.random(seed, state.season, Seeds.Phase.EXTENSION, playerId.value.hashCode().toLong()),
            options,
        )
        if (result.accepted) {
            state.decisionLog += baseballgm.management.GmDecision(
                season = state.season,
                week = state.week,
                kind = baseballgm.management.DecisionKind.EXTENSION,
                playerId = playerId,
                playerName = state.player(playerId).registeredName,
                teamId = userTeamId,
            )
        }
        revision++
        return result
    }

    // ---------- 트레이드 (docs/11) ----------

    fun teamMode(teamId: TeamId = userTeamId): TeamMode = tradeService.modeOf(state, teamId)

    val tradeOpen: Boolean get() = tradeService.isOpen(state)

    fun myPicksForTrade(): List<DraftPickRight> = state.draftRights.ofOwner(userTeamId)

    fun picksOf(teamId: TeamId): List<DraftPickRight> = state.draftRights.ofOwner(teamId)

    fun buildProposal(
        partner: TeamId,
        giving: List<PlayerId>,
        receiving: List<PlayerId>,
        givingPicks: List<DraftPickRight> = emptyList(),
        receivingPicks: List<DraftPickRight> = emptyList(),
        cash: Double = 0.0,
        /** 연봉 보조: 보내는 선수 → 우리가 계속 내 줄 연봉(억/년) (2026-10-05) */
        retained: Map<PlayerId, Double> = emptyMap(),
    ): TradeProposal = TradeProposal(
        proposer = userTeamId,
        partner = partner,
        fromProposer = TradePackage(giving, givingPicks, cash, retained.filter { it.key in giving && it.value > 0.0 }),
        fromPartner = TradePackage(receiving, receivingPicks),
    )

    /** 연봉 보조 상한 (연봉 대비) */
    val tradeRetainRate: Double get() = balance.double("trade.salaryRetention.maxRate")

    /** 트레이드에 넣을 수 있는 현금 상한 */
    val tradeCashLimit: Double get() = balance.double("trade.cashLimit")

    /** 상대 단장 (이름 · 협상 성격). 성격은 업계에 알려진 평판이라 공개 정보다 (2026-10-05) */
    fun tradeGm(teamId: TeamId): Pair<String, baseballgm.model.GmStyle> {
        val league = state.currentLeague()
        return (league.generalManagerOf(teamId)?.name ?: "단장") to tradeService.ai.styleOf(league, teamId)
    }

    /** 거절된 제안에 상대가 내는 역제안. 없으면 null (2026-10-05) */
    fun tradeCounter(proposal: TradeProposal): baseballgm.market.TradeCounter? =
        tradeService.counterFor(state, proposal, state.difficulty)

    /** 역제안을 받아들인다 — 상대가 만든 조건이라 협상 거부 중이어도 다시 판정만 한다 */
    fun acceptTradeCounter(counter: TradeProposal): TradeVerdict {
        val verdict = tradeService.acceptCounter(state, counter, state.difficulty)
        revision++
        return verdict
    }

    /** 이번 시즌 들어온 트레이드 소문 (최근 것부터) */
    fun tradeRumors(): List<baseballgm.season.InboxMessage> =
        state.inbox.all().filter { it.category == baseballgm.season.InboxCategory.TRADE && it.teamId == userTeamId && it.text.startsWith("[소문") }
            .sortedByDescending { it.week }

    /** 우리 팀이 내거나 받는 연봉 보조 (구단 화면·로스터용) */
    fun retainedSalaries(): List<baseballgm.market.RetainedSalary> =
        state.retainedSalaries.filter { it.payer == userTeamId || runCatching { state.player(it.playerId).teamId }.getOrNull() == userTeamId }

    /** 제안을 넣기 전에 미리 판정을 본다. 협상 피로도는 쌓이지 않는다 */
    fun previewTrade(proposal: TradeProposal): TradeVerdict =
        tradeService.evaluate(state, proposal, state.difficulty)

    /** 실제로 제안한다. 거절당하면 협상 피로도가 쌓인다 (docs/11) */
    fun proposeTrade(proposal: TradeProposal): TradeVerdict {
        val verdict = tradeService.propose(state, proposal, state.difficulty)
        revision++
        return verdict
    }

    /** AI 가 걸어온 제안 */
    fun pendingTradeOffer(): TradeProposal? = state.pendingTradeOffer

    fun acceptPendingTrade(): String? {
        val proposal = state.pendingTradeOffer ?: return null
        val summary = tradeService.apply(state, proposal)
        clearTradeOffer()
        revision++
        resumeAutoAdvance()
        return summary
    }

    fun rejectPendingTrade() {
        clearTradeOffer()
        revision++
        resumeAutoAdvance()
    }

    /** 제안에 답하면 홈의 "트레이드 제안" 돌발 카드도 같이 내린다 */
    private fun clearTradeOffer() {
        state.pendingTradeOffer = null
        state.pendingTradeOfferWeek = null
        state.pendingIncidents.removeAll { it.kind == baseballgm.events.IncidentKind.TRADE_OFFER }
    }

    /** 제안이 철회되기 전 마지막 주차 */
    fun tradeOfferDeadlineWeek(): Int? =
        state.pendingTradeOfferWeek?.let { it + balance.int("incidents.tradeOffer.validWeeks") - 1 }

    fun describePackage(pack: TradePackage): String =
        pack.describe { id -> state.player(id).registeredName }

    // ---------- 구단 경영 (docs/13) ----------

    fun finance(): FinanceReport? = lastOffseason?.review?.of(userTeamId)?.finance

    /** 지금 이 순간의 예상 수입·지출. 시즌 중에도 재정을 볼 수 있게 한다 */
    fun projectedFinance(): FinanceReport = clubReview.financeModel.report(
        league = state.currentLeague(),
        teamId = userTeamId,
        season = state.season,
        record = record(),
        fanSupport = fanSupport(),
        ownerTrust = userTeam.ownerTrust,
        postseason = state.postseason,
        signingBonus = 0.0,
        scouting = scoutingCost(scoutingLevel),
        penalties = 0.0,
        fundsBefore = state.funds[userTeamId] ?: userTeam.operatingFunds,
    )

    // ---------- 연봉 계획 (앞으로 몇 년의 샐러리캡 여유·운용 자금) ----------

    /**
     * 지금 쓸 수 있는 운용 자금.
     *
     * 화면마다 이 숫자 하나만 쓴다. 시즌 중에는 시즌 상태가 진짜이고(트레이드 현금·계약금·삭감이 바로 반영),
     * FA 시장 중에는 결산을 마친 다음 시즌 리그가 진짜다. `userTeam.operatingFunds` 는 개막 때 값이라 쓰지 않는다
     */
    fun currentFunds(): Double =
        pendingLeague?.team(userTeamId)?.operatingFunds ?: state.funds[userTeamId] ?: userTeam.operatingFunds

    /** 샐러리캡(소프트캡) 금액 */
    val salaryCap: Double get() = balance.double("softCap.cap")

    /**
     * 앞으로 몇 년의 연봉 총액·캡 여유·운용 자금 전망.
     *
     * FA 시장 중에는 계약이 한 해 넘어간 다음 시즌 리그로 계산한다 — 그 시즌부터 FA 계약이 시작되기 때문이다.
     */
    fun financialPlan(change: OutlookChange = OutlookChange.NONE): FinancialPlan {
        val next = pendingLeague
        return if (next != null) {
            val team = next.team(userTeamId)
            outlook.project(
                league = next,
                teamId = userTeamId,
                funds = team.operatingFunds,
                record = baseballgm.league.TeamRecord(userTeamId),
                fanSupport = team.fanSupport,
                ownerTrust = team.ownerTrust,
                postseason = null,
                scoutingCost = scoutingCost(scoutingLevel),
                change = change,
            )
        } else {
            outlook.project(
                league = state.currentLeague(),
                teamId = userTeamId,
                funds = currentFunds(),
                record = record(),
                fanSupport = fanSupport(),
                ownerTrust = state.ownerTrustOf(userTeamId),
                postseason = state.postseason,
                scoutingCost = scoutingCost(scoutingLevel),
                change = change,
            )
        }
    }

    /**
     * 우리가 FA 시장에 낸 조건이 모두 성사됐다고 볼 때의 가정.
     * [target] 선수에게 낸 조건은 빼고, [offer] 가 있으면 그것으로 갈아 끼운다 (조건 창에서 슬라이더를 움직일 때)
     */
    fun faOffersChange(target: PlayerId? = null, offer: ContractOffer? = null): OutlookChange {
        val offers = faAgents().mapNotNull { agent ->
            val terms = if (agent.playerId == target) offer else faOffer(agent)
            terms?.takeIf { it.years > 0 && it.salary > 0.0 }?.let { agent to it }
        }
        return OutlookChange(
            contracts = offers.map { (agent, terms) ->
                PlannedContract(faPlayer(agent).registeredName, terms.salary, terms.years, terms.signingBonus, agent.playerId)
            },
        )
    }

    /**
     * 이 조건으로 비FA 다년계약을 맺으면 (ContractExtension 과 같은 방식: 이번 시즌은 지금 연봉, 다음 시즌부터 [years] 년 새 연봉).
     * 계약금은 지금 운용 자금에서 나간다
     */
    fun extensionChange(playerId: PlayerId, salary: Double, years: Int, signingBonus: Double): OutlookChange {
        val player = state.player(playerId)
        val contract = player.contract
        val extended = player.withContract(
            contract.copy(
                yearsRemaining = years + 1,
                nextSalary = salary,
                seasonsToFreeAgency = maxOf(contract.seasonsToFreeAgency, years),
            ),
        )
        return OutlookChange(outgoing = setOf(playerId), incoming = listOf(extended), cashOut = signingBonus)
    }

    /** 이 트레이드를 하면 (받는 선수는 원래 계약을 그대로 넘겨받는다) */
    fun tradeChange(proposal: TradeProposal): OutlookChange {
        val outgoing = proposal.outgoingOf(userTeamId)
        val incoming = proposal.incomingOf(userTeamId)
        return OutlookChange(
            outgoing = outgoing.playerIds.toSet(),
            incoming = incoming.playerIds.map { state.player(it) },
            cashOut = outgoing.cash - incoming.cash,
        )
    }

    fun fanSupport(): Int = state.fanSupportOf(userTeamId)

    fun fanLabel(): String = clubReview.fanModel.label(fanSupport())

    fun ownerTrust(): Int = state.ownerTrustOf(userTeamId)

    fun ownerTrustLabel(): String = clubReview.trustModel.label(ownerTrust())

    fun ownerWarning(): String? = clubReview.trustModel.warning(ownerTrust())

    fun seasonGoal(): SeasonGoal = SeasonGoal.fromOwnerGoal(
        state.league.management.seasonGoals[userTeamId] ?: userTeam.ownerGoal,
    )

    /** 이 금액의 계약에 구단주 승인이 필요한가 (docs/13) */
    fun needsOwnerApproval(salary: Double): Boolean =
        clubReview.trustModel.needsApproval(ownerTrust(), salary)

    fun staffSalary(): Double = clubReview.financeModel.staffSalaryOf(state.league, userTeamId)

    fun allowedDeficit(): Double = clubReview.financeModel.allowedDeficit(state.league, userTeamId)

    // ---------- 스태프 (docs/13) ----------

    private var staffState: StaffMarketState? = null

    /** 남아 있는 스태프 시장. AI 가 먼저 데려간 뒤 남은 사람들이다 */
    fun staffOffers(): List<StaffOffer> {
        val market = staffState ?: staffMarket.open(state.league, Seeds.random(seed, state.season, Seeds.Phase.STAFF_MARKET)).also { staffState = it }
        return market.offers()
    }

    fun coaches() = state.league.coachesOf(userTeamId).filter { it.id !in state.staffFired }

    fun medicalStaff() = state.league.medicalStaffOf(userTeamId).filter { it.id !in state.staffFired }

    fun manager() = state.league.managerOf(userTeamId)?.takeIf { it.id !in state.staffFired }

    /** 스태프 경질은 스토브리그 준비 때만 된다 (시즌 중 경질은 감독대행 등이 필요해 따로 다룬다) */
    val canFireStaff: Boolean get() = offseasonPrep

    /**
     * 스태프 경질 (docs/13). 남은 계약의 일부를 위약금으로 운용 자금에서 낸다. 그 사람은 스토브리그 스태프 시장에 나가고,
     * 빈자리는 스토브리그 뒤 구단 운영 화면에서 새로 데려온다. 경질했으면 위약금, 못 했으면 null
     */
    fun fireStaff(staffId: baseballgm.model.StaffId): Double? {
        if (!canFireStaff || staffId in state.staffFired) return null
        val contract = coaches().firstOrNull { it.id == staffId }?.contract
            ?: medicalStaff().firstOrNull { it.id == staffId }?.contract
            ?: manager()?.takeIf { it.id == staffId }?.contract
            ?: return null
        val cost = staffMarket.firingCost(contract)
        state.funds[userTeamId] = (state.funds[userTeamId] ?: userTeam.operatingFunds) - cost
        state.staffFired += staffId
        state.inbox.add(state.week, baseballgm.season.InboxCategory.MARKET, userTeamId, "스태프 경질 — 위약금 ${cost}억")
        revision++
        return cost
    }

    // ---------- 역사·시상 (2026-10-01, 진단 3번) ----------

    /** 지난 시즌까지의 리그 역사 (통산 기록·역대 우승·시상) */
    fun history(): baseballgm.league.LeagueHistory = state.league.history

    private var awardsCache: Pair<Int, List<baseballgm.league.AwardEntry>>? = null

    /**
     * 올 시즌 시상. 정규시즌이 끝나야 정해진다 (그 전엔 빈 목록). 스토브리그가 역사에 적는 것과 같은 계산이다
     */
    fun seasonAwards(): List<baseballgm.league.AwardEntry> {
        if (!seasonOver) return emptyList()
        awardsCache?.takeIf { it.first == state.season }?.let { return it.second }
        val awards = baseballgm.season.SeasonAwards(balance).compute(state, state.league.history)
        awardsCache = state.season to awards
        return awards
    }

    /** 시즌의 시상 — 올 시즌이면 지금 계산, 지난 시즌이면 역사에서 */
    fun awardsFor(season: Int): List<baseballgm.league.AwardEntry> =
        if (season == state.season) seasonAwards() else history().seasonOf(season)?.awards.orEmpty()

    /** 선수의 시즌별 1군 기록: 지난 시즌들(역사) + 올 시즌(진행 중이면 지금까지) */
    fun careerLines(player: Player): List<baseballgm.league.CareerSeason> {
        val past = history().careerOf(player.id).filter { it.season < state.season }
        val batting = batting(player.id)
        val pitching = pitching(player.id)
        if (batting.plateAppearances == 0 && pitching.outs == 0) return past
        val team = player.teamId ?: return past
        val now = baseballgm.league.CareerSeason(
            season = state.season,
            team = team,
            name = player.registeredName,
            bat = baseballgm.league.BatSeason.of(batting).takeIf { batting.plateAppearances > 0 },
            pitch = baseballgm.league.PitchSeason.of(pitching).takeIf { pitching.outs > 0 },
            war = war(player).war,
        )
        return past + now
    }

    /** 통산: 리그를 떠났던 시절 요약 + 시즌별 (올 시즌 포함) */
    fun careerTotal(player: Player): baseballgm.league.RetiredCareer? {
        val lines = careerLines(player)
        val earlier = history().retired[player.id]
        if (lines.isEmpty()) return earlier
        val now = baseballgm.league.RetiredCareer(
            name = player.registeredName,
            lastTeam = player.teamId,
            firstSeason = lines.first().season,
            lastSeason = lines.last().season,
            bat = lines.mapNotNull { it.bat }.reduceOrNull { a, b -> a + b },
            pitch = lines.mapNotNull { it.pitch }.reduceOrNull { a, b -> a + b },
            war = lines.sumOf { it.war },
        )
        return earlier?.let { it + now } ?: now
    }

    /** 선수가 받은 상 (지난 시즌 + 올 시즌이 끝났으면 올해 것) */
    fun awardsOf(player: Player): List<Pair<Int, baseballgm.league.AwardEntry>> =
        history().awardsOf(player.id).filter { it.first != state.season } +
            seasonAwards().filter { it.playerId == player.id }.map { state.season to it }

    /** 역대 통산 순위 (끝난 시즌들 기준). [key] 가 0 인 선수는 빠진다 */
    fun allTimeLeaders(limit: Int, key: (baseballgm.league.RetiredCareer) -> Int): List<Pair<baseballgm.model.PlayerId, baseballgm.league.RetiredCareer>> {
        val history = history()
        val ids = history.careers.keys + history.retired.keys
        return ids.mapNotNull { id -> history.totalOf(id)?.let { id to it } }
            .filter { key(it.second) > 0 }
            .sortedWith(compareByDescending<Pair<baseballgm.model.PlayerId, baseballgm.league.RetiredCareer>> { key(it.second) }.thenBy { it.first.value })
            .take(limit)
    }

    /** 선수가 아직 리그에 있는가 (역대 기록에서 상세로 갈 수 있는지) */
    fun inLeague(id: baseballgm.model.PlayerId): Boolean = state.isInLeague(id)

    // ---------- 스토브리그 준비 (2026-10-01, 진단 2번) ----------

    /** 정규시즌·포스트시즌이 끝났고 스토브리그는 아직 — 단장이 겨울 결정을 내리는 때 */
    val offseasonPrep: Boolean
        get() = seasonOver && postseasonDone && !inFreeAgency && pendingOffseason == null && !unemployed

    private var previewCache: Pair<Int, baseballgm.season.OffseasonPreview>? = null

    /** 비서가 브리핑할 겨울 결정거리. 외국인 요구액은 선수마다 고정 시드라 몇 번을 열어도 같다 */
    fun offseasonPreview(): baseballgm.season.OffseasonPreview {
        previewCache?.takeIf { it.first == state.season }?.let { return it.second }
        val preview = Offseason(balance, strength).preview(state, userTeamId) { id ->
            Seeds.random(seed, state.season, Seeds.Phase.OFFSEASON_PLAN, id.value.hashCode().toLong())
        }
        previewCache = state.season to preview
        return preview
    }

    /** 지금 계획. 처음 열면 비서 추천으로 채워진다 */
    fun offseasonPlan(): baseballgm.season.OffseasonPlan =
        state.offseasonPlan ?: Offseason(balance, strength).defaultPlan(offseasonPreview()).also { state.offseasonPlan = it }

    private fun updatePlan(change: (baseballgm.season.OffseasonPlan) -> baseballgm.season.OffseasonPlan) {
        if (!offseasonPrep) return
        state.offseasonPlan = change(offseasonPlan()).copy(confirmed = false)
        revision++
    }

    /** 외국인 재계약 여부. 재계약하면 미리보기 요구액 그대로 */
    fun setForeignResign(id: PlayerId, resign: Boolean) = updatePlan { plan ->
        val asking = offseasonPreview().foreign.firstOrNull { it.playerId == id }?.asking ?: return@updatePlan plan
        plan.copy(foreign = plan.foreign + (id to if (resign) asking else null))
    }

    fun setLowball(id: PlayerId, lowball: Boolean) = updatePlan { plan ->
        val case = offseasonPreview().salaries.firstOrNull { it.playerId == id } ?: return@updatePlan plan
        plan.copy(salaries = plan.salaries + (id to baseballgm.season.SalaryDecision(if (lowball) case.lowball else case.demand, lowball)))
    }

    fun toggleRelease(id: PlayerId) = updatePlan { plan ->
        plan.copy(releases = if (id in plan.releases) plan.releases - id else plan.releases + id)
    }

    /** 계획대로 했을 때 다음 시즌 예상 인원 (은퇴는 스토브리그에서 정해져서 빠져 있다) */
    fun projectedRoster(): Int {
        val preview = offseasonPreview()
        val plan = offseasonPlan()
        val foreignLeaving = preview.foreign.count { plan.foreign[it.playerId] == null }
        val recommendedForeignLeaving = preview.foreign.count { !it.recommendResign }
        return preview.projectedRoster + recommendedForeignLeaving - foreignLeaving - plan.releases.size
    }

    /** 계획을 확정하고 스토브리그를 시작한다 */
    fun confirmOffseasonPlan() {
        if (!offseasonPrep) return
        state.offseasonPlan = offseasonPlan().copy(confirmed = true)
        startNextSeason()
    }

    /** 스태프를 고용한다. 자금이 모자라거나 이미 나간 사람이면 실패한다 */
    fun hireStaff(offer: StaffOffer): Boolean {
        val market = staffState ?: return false
        val funds = state.funds[userTeamId] ?: return false
        // 연봉은 매년 재정 결산에서 빠진다 (docs/13). 여기서는 감당할 수 있는지만 본다
        val hire = staffMarket.hire(market, state.league, userTeamId, offer.id, funds) ?: return false
        state.applyStaffHire(offer.id, userTeamId, hire.salary)
        state.inbox.add(state.week, baseballgm.season.InboxCategory.MARKET, userTeamId, "${hire.roleLabel} ${hire.name} 영입 (${hire.salary}억)")
        revision++
        return true
    }

    fun firingCost(contract: baseballgm.model.StaffContract): Double = staffMarket.firingCost(contract)

    // ---------- 커리어·업적 (docs/13, 14) ----------

    fun career(): CareerRecord? = state.league.management.career

    fun careerLabel(): String = clubReview.careerModel.label(career()?.reputation ?: 0)

    // ---------- 서사 (docs/13 칭호·메아리·유대·라이벌, docs/16 뉴스) ----------

    /** 단장 이름. 비서가 "OOO 단장님" 으로 부른다 */
    val gmName: String get() = career()?.gmName ?: "단장"

    fun gmTitles(): List<baseballgm.management.GmTitle> = titles.earned(career())

    fun gmTitle(): baseballgm.management.GmTitle = titles.headline(career())

    /** 유저 단장의 결정 전부 (지난 시즌 + 올 시즌) */
    fun decisions(): List<baseballgm.management.GmDecision> = state.userDecisions(userTeamId)

    /** 우리 선수와의 유대. 남의 팀 선수면 null */
    fun bond(player: Player): baseballgm.management.Bond? =
        bonds.of(player, userTeamId, state.season, career(), decisions())

    /**
     * 우리 선수의 만족도 (docs/13 "선수 성향과 만족도"). 남의 팀 선수면 null.
     * 숨김 성향 숫자는 들어 있지 않다 — 요인 목록과 만족도 값(공개 정보)만
     */
    fun morale(player: Player): baseballgm.management.MoraleView? = moraleService.view(state, userTeamId, player)

    /** 만족도 막대 색을 고를 단계 (0 = 매우 만족 … 4 = 매우 불만) */
    fun moraleLevel(value: Int): baseballgm.management.MoraleLevel = moraleService.levelOf(value)

    /** 이번 주 비서가 꺼낼 과거 결정의 메아리 */
    fun echoes(): List<String> = lastReport?.echoes.orEmpty()

    /** 올 시즌 뉴스, 최근 것부터 */
    /** 올 시즌 뉴스. 최근 주차부터, 같은 주 안에서는 중요한 기사부터 (뉴스 데스크가 정한 순서 그대로) */
    fun news(): List<baseballgm.events.NewsItem> = state.news.sortedByDescending { it.week }

    /** 올 시즌 우리 팀 팬 글, 최근 것부터 */
    fun fanPosts(): List<baseballgm.events.FanPost> = state.fanPosts.sortedByDescending { it.week }

    fun rival(teamId: TeamId = userTeamId): baseballgm.model.Team? =
        state.league.team(teamId).rival?.let { state.league.team(it) }

    /** 라이벌 상대 전적 (승, 패). 라이벌이 없으면 null */
    fun rivalRecord(): Pair<Int, Int>? = rival()?.let { state.headToHeadOf(userTeamId, it.id) }

    /** 이번 주 일정에 라이벌전이 있나 */
    fun rivalGamesThisWeek(): Int {
        val rival = rival() ?: return 0
        return weekSchedule().count { it.opponentOf(userTeamId) == rival.id }
    }

    fun achievements(): List<Achievement> = Achievements.all

    fun unlockedAchievements(): Set<String> = career()?.unlockedAchievements?.toSet().orEmpty()

    fun hallOfFameGrade(): String = career()?.let { Achievements.hallOfFameGrade(it) } ?: "-"

    fun expectedWins(): Double =
        clubReview.careerModel.expectedWins(state.currentLeague(), userTeamId, balance.int("schedule.gamesPerTeam"))

    companion object {
        /** 비교함 최대 인원 — 폰 화면에 열 넷이 한계다 */
        const val MAX_COMPARE = 4
        private val KEY_BATTER = listOf(baseballgm.model.Attribute.CONTACT, baseballgm.model.Attribute.POWER, baseballgm.model.Attribute.DEFENSE)
        private val KEY_PITCHER = listOf(baseballgm.model.Attribute.STUFF, baseballgm.model.Attribute.CONTROL, baseballgm.model.Attribute.STAMINA)

        /** 자동 진행 한 번에 밟을 수 있는 최대 단계 (무한 루프 방지) */
        private const val MAX_STEPS = 2_000
        private const val PA_PER_GAME = 3.1
        private const val OUTS_PER_GAME = 3
    }
}

/** 0.1 단위 글자 (협상 테이블 안내 문장용) */
private fun Double.oneDecimalText(): String = ((this * 10).roundToLong() / 10.0).toString()
