package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.Standings
import baseballgm.events.TournamentResult
import baseballgm.market.DraftResult
import baseballgm.market.DraftRights
import baseballgm.market.DraftState
import baseballgm.market.NegotiationFatigue
import baseballgm.market.TradeRecord
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.model.ManagerTendencies
import baseballgm.scouting.ScoutingDepartment
import baseballgm.stats.SeasonStats
import baseballgm.tactics.DirectivePreset
import baseballgm.tactics.WeeklyPolicy

/**
 * 한 시즌이 진행되는 동안 바뀌는 상태 전부.
 *
 * `League` 는 시즌 시작 시점의 고정 데이터고, 경기를 치르며 달라지는 것(컨디션, 엔트리, 기록, 순위)은
 * 여기 모여 있다. 선수는 불변 객체라 바뀔 때마다 [update] 로 새 사본을 넣는다 —
 * "어딘가에서 몰래 선수를 고쳐 놓는" 버그를 막기 위해서다.
 */
class SeasonState(
    val league: League,
    val calendar: SeasonCalendar,
    /** 스카우트 투자 단계. 스토브리그에서 정한 값을 그대로 이어받는다 (docs/10) */
    scoutingLevels: Map<TeamId, Int> = emptyMap(),
    defaultScoutingLevel: Int = DEFAULT_SCOUTING_LEVEL,
) {
    // 등번호는 여기서 채운다: 리그 파일·예전 세이브에는 없고, 스토브리그가 만든 새 리그에는 신인·이적생이 섞여 있다
    private val playerMap: MutableMap<PlayerId, Player> =
        baseballgm.model.UniformNumbers.assign(league.players).associateBy { it.id }.toMutableMap()
    private val teamRosters: MutableMap<TeamId, MutableList<PlayerId>> =
        league.teams.associate { team -> team.id to league.playersOf(team.id).map { it.id }.toMutableList() }
            .toMutableMap()
    private val rotationIndex: MutableMap<TeamId, Int> = mutableMapOf()

    var week: Int = 1
        internal set

    var standings: Standings = Standings.empty(league.teams.map { it.id })
        internal set

    val stats: SeasonStats = SeasonStats()
    val roster: RosterState = RosterState()
    val usage: PitcherUsage = PitcherUsage()
    val inbox: Inbox = Inbox()
    val policies: MutableMap<TeamId, WeeklyPolicy> = mutableMapOf()

    /**
     * 팀별 감독 성향. 감독은 캐릭터라서 시즌 중에도 값이 움직인다 —
     * 단장이 방침을 내리면 반영률만큼 이쪽으로 끌려온다 (docs/06).
     */
    val managerTendencies: MutableMap<TeamId, ManagerTendencies> = league.teams.associate { team ->
        team.id to (league.managerOf(team.id)?.tendencies ?: DirectivePreset.STANDARD.tendencies())
    }.toMutableMap()

    /** 단장이 내린 방침(목표 성향). 매주 조금씩 감독 성향에 섞인다. */
    val gmDirections: MutableMap<TeamId, ManagerTendencies> = mutableMapOf()

    /**
     * 팀별 스카우트 부서 (docs/10).
     *
     * 집중 관찰 주차가 여기 쌓이기 때문에 시즌 상태에 둔다 — 개막에 리셋되고, 한 시즌 내내
     * 같은 선수를 보고 있으면 드래프트 때 훨씬 정확한 리포트를 갖게 된다.
     */
    val scouting: Map<TeamId, ScoutingDepartment> = league.teams.associate { team ->
        team.id to ScoutingDepartment(team.id, scoutingLevels[team.id] ?: defaultScoutingLevel)
    }

    /** 진행 중인 신인 드래프트. 드래프트 주차에만 값이 있다 */
    var draft: DraftState? = null
        internal set

    /** 올해 드래프트 결과. 스토브리그가 이걸 보고 신인을 입단시킨다 */
    var draftResult: DraftResult? = null
        internal set

    /** 유저 구단이 받은 스카우트 정기 리포트 (2026-10-04). 받은 순서대로 */
    val scoutDigests: MutableList<baseballgm.scouting.ScoutDigest> = mutableListOf()

    /**
     * 시즌 중에 바뀌는 구단 자산.
     *
     * 트레이드로 선수·지명권·현금이 오가므로, 시즌이 시작된 뒤로는 [league] 가 아니라 여기가 진짜다.
     * 스토브리그는 이 값들을 읽어 다음 시즌 리그를 만든다.
     */
    var draftRights: DraftRights = league.draftRights
        internal set

    var tradeHistory: List<TradeRecord> = league.tradeHistory
        internal set

    /** 트레이드 연봉 보조 (2026-10-05). [currentLeague] 로 리그에 들어가 세이브·스토브리그로 이어진다 */
    var retainedSalaries: List<baseballgm.market.RetainedSalary> = league.retainedSalaries
        internal set

    val funds: MutableMap<TeamId, Double> =
        league.teams.associate { it.id to it.operatingFunds }.toMutableMap()

    /**
     * 팬심 (docs/13). 시즌 중에 승패·사건으로 움직이므로 시즌 상태가 들고 있는다.
     * 스토브리그 결산이 이 값을 받아 다음 시즌 구단 데이터에 넣는다.
     */
    val fanSupport: MutableMap<TeamId, Int> =
        league.teams.associate { it.id to it.fanSupport }.toMutableMap()

    /** 올 시즌 뉴스 (docs/16 뉴스 탭). 최근 것이 뒤에 붙는다 */
    val news: MutableList<baseballgm.events.NewsItem> = mutableListOf()

    /** 올 시즌 유저 구단 팬 SNS 글 */
    val fanPosts: MutableList<baseballgm.events.FanPost> = mutableListOf()

    /** 올 시즌 유저 단장의 결정. 스토브리그에 커리어 기록(ManagementState.decisions)으로 옮겨진다 */
    val decisionLog: MutableList<baseballgm.management.GmDecision> = mutableListOf()

    /**
     * 유저 단장의 결정 전부 (docs/13 메아리·유대·연대기).
     *
     * 지난 시즌까지는 커리어 기록에 저장돼 있고, 올 시즌 것은 **이미 있는 기록에서 뽑는다** —
     * 트레이드는 [tradeHistory], 드래프트는 [draftResult]. 화면에서 따로 적을 필요가 없는 것은 적지 않는다.
     * FA·외국인 영입처럼 기록이 따로 없는 결정만 [decisionLog] 에 직접 쌓인다.
     */
    fun userDecisions(userTeam: TeamId?): List<baseballgm.management.GmDecision> {
        val past = league.management.decisions
        if (userTeam == null) return (past + decisionLog).distinct()
        val nameOf = { id: PlayerId -> playerMap[id]?.registeredName ?: id.value }
        val trades = tradeHistory.filter { it.season == season && it.involves(userTeam) }.flatMap { record ->
            val incoming = if (record.teamA == userTeam) record.playersToA else record.playersToB
            val outgoing = if (record.teamA == userTeam) record.playersToB else record.playersToA
            incoming.map { baseballgm.management.GmDecision(season, record.week, baseballgm.management.DecisionKind.TRADE_IN, it, nameOf(it), userTeam) } +
                outgoing.map { baseballgm.management.GmDecision(season, record.week, baseballgm.management.DecisionKind.TRADE_OUT, it, nameOf(it), userTeam) }
        }
        val drafted = draftResult?.selections.orEmpty().filter { it.teamId == userTeam }.map {
            baseballgm.management.GmDecision(season, calendar.draftWeek, baseballgm.management.DecisionKind.DRAFT, it.playerId, it.playerName, userTeam)
        }
        return (past + decisionLog + trades + drafted).distinct()
    }

    /**
     * 유저 구단 선수의 만족도 (docs/13, 2026-10-04). 시즌 사이에는 [baseballgm.management.ManagementState.morale] 에 있고,
     * [currentLeague] 가 그쪽으로 다시 내보낸다 — 그래서 세이브(지금 리그)에 저절로 들어간다.
     */
    val morale: MutableMap<PlayerId, baseballgm.management.PlayerMorale> = league.management.morale.toMutableMap()

    /** 상대 전적: (A, B) → A 가 B 에게 이긴 수 */
    val headToHead: MutableMap<Pair<TeamId, TeamId>, Int> = mutableMapOf()

    /** 지난주 1위. 선두 교체 기사에 쓴다 */
    var lastLeader: TeamId? = null
        internal set

    /** [team] 의 [opponent] 상대 전적 (승, 패). 무승부는 세지 않는다 */
    fun headToHeadOf(team: TeamId, opponent: TeamId): Pair<Int, Int> =
        (headToHead[team to opponent] ?: 0) to (headToHead[opponent to team] ?: 0)

    /** 비FA 다년계약 협상에서 거절당한 횟수 (docs/11). 시즌이 바뀌면 풀린다 */
    val extensionRejections: MutableMap<PlayerId, Int> = mutableMapOf()

    /** 비FA 다년계약 협상 대화 (2026-10-05 협상 테이블). 화면용이라 세이브에 남기지 않는다 */
    val extensionTalks: MutableMap<PlayerId, MutableList<baseballgm.market.FaTalkLine>> = mutableMapOf()

    /** 올 시즌 비FA 다년계약을 맺은 선수. 한 시즌에 두 번 맺지 않는다 */
    val extendedThisSeason: MutableSet<PlayerId> = mutableSetOf()

    /** 협상 피로도 (docs/11 꼼수 방지). 시즌이 바뀌면 풀린다 */
    val negotiationFatigue: NegotiationFatigue = NegotiationFatigue()

    /**
     * 국제대회 차출 (docs/12). 선수 → 몇 주차까지 빠지는가.
     *
     * 부상·복무와 달리 **몇 주만 빠지는 상태**라서 컨디션이나 군 복무 상태에 넣지 않고 따로 둔다.
     */
    val internationalDuty: MutableMap<PlayerId, Int> = mutableMapOf()

    /** 올 시즌 국제대회 결과. 대회가 없는 해에는 null */
    var tournamentResult: TournamentResult? = null
        internal set

    /** 포스트시즌 결과 (docs/14). 정규시즌이 끝나고 치른 뒤에 값이 들어간다 */
    var postseason: PostseasonResult? = null
        internal set

    /** 진행 중인 포스트시즌 (2026-10-03 한 경기씩 진행). 시작 전이면 null, 끝나도 마지막 상태가 남는다 */
    var postseasonProgress: PostseasonProgress? = null
        internal set

    /** 단장이 다음 포스트시즌 경기의 우리 팀 방침·휴식을 정한다. 포스트시즌 중이 아니면 아무 일도 안 한다 */
    fun updatePostseasonOrders(change: (PostseasonProgress) -> PostseasonProgress) {
        postseasonProgress = postseasonProgress?.takeUnless { it.isFinished }?.let(change) ?: postseasonProgress
    }

    /**
     * 유저의 스토브리그 계획 (2026-10-01). 정규시즌·포스트시즌이 끝난 뒤 준비 화면에서 채워지고,
     * 스토브리그가 유저 구단을 이대로 처리한다. 세이브에 들어간다
     */
    var offseasonPlan: OffseasonPlan? = null

    /** 시즌 사이에 경질한 스태프. [currentLeague] 에서 소속이 비워진다 (스토브리그 스태프 시장에 나간다) */
    val staffFired: MutableSet<baseballgm.model.StaffId> = mutableSetOf()

    /** 팀별 시즌 중 외국인 교체 횟수 (docs/12 제한) */
    val foreignReplacements: MutableMap<TeamId, Int> = mutableMapOf()

    /** 난이도. AI 의 트레이드 수익 요구치를 정한다 (docs/14). M11 에서 유저가 고른다 */
    var difficulty: String = "normal"

    /** AI 가 유저에게 건 트레이드 제안. 유저가 답할 때까지 들고 있는다 */
    var pendingTradeOffer: baseballgm.market.TradeProposal? = null

    /** [pendingTradeOffer] 가 들어온 주차. `incidents.tradeOffer.validWeeks` 가 지나면 철회된다 */
    var pendingTradeOfferWeek: Int? = null

    /**
     * 구단주 신뢰도 (docs/13). 시즌 중 돌발 이벤트(기자 답변·감독 거취)로 조금씩 움직이므로 시즌 상태가 들고
     * 있고, [currentLeague] 가 구단 데이터에 합쳐 스토브리그 평가로 넘긴다.
     */
    val ownerTrust: MutableMap<TeamId, Int> =
        league.teams.associate { it.id to it.ownerTrust }.toMutableMap()

    fun ownerTrustOf(teamId: TeamId): Int = ownerTrust[teamId] ?: league.team(teamId).ownerTrust

    // ---------- 돌발 이벤트 (docs/07·16 주중 개입) ----------

    /** 진행 중인 주. 돌발 이벤트로 주중에 멈췄을 때만 값이 있다 */
    var weekInProgress: WeekProgress? = null
        internal set

    /** 유저가 답해야 하는 돌발 이벤트. 앞에서부터 하나씩 묻는다 */
    val pendingIncidents: MutableList<baseballgm.events.Incident> = mutableListOf()

    /** 올 시즌 답한 돌발 이벤트. 뉴스·팬 반응과 커리어 연대기가 읽는다 */
    val incidentLog: MutableList<baseballgm.events.IncidentRecord> = mutableListOf()

    /** 같은 일을 한 시즌에 두 번 묻지 않기 위한 열쇠 ("prospect:P123" 같은) */
    val incidentKeys: MutableSet<String> = mutableSetOf()

    /** 이번 주 휴식을 지시받은 선수. 주가 끝나면 비운다 */
    val restingThisWeek: MutableSet<PlayerId> = mutableSetOf()

    /** 이번 주만 바꾼 방침의 원래 값. 주가 끝나면 되돌린다 (라이벌전 총력전 등) */
    val policyToRestore: MutableMap<TeamId, WeeklyPolicy> = mutableMapOf()

    /** 올 시즌 성사된 AI 끼리의 거래 수. 시즌당 몇 건으로 묶어 두기 위한 카운터 */
    var aiTradeCount: Int = 0
        internal set

    /**
     * 올 시즌 이미 AI 거래를 한 구단.
     *
     * 한 구단이 시즌 중에 여러 번 보강하면 상위권이 계속 강해져 승률 분포가 한쪽으로 쏠린다.
     * 현실의 구단도 시즌 중 트레이드는 한두 번이 고작이라, 구단당 한 번으로 묶었다.
     */
    val aiTradedTeams: MutableSet<TeamId> = mutableSetOf()

    val season: Int get() = league.season

    val isRegularSeasonOver: Boolean get() = week > calendar.regularSeasonWeeks

    /**
     * 선수 하나. 시즌 중 리그를 떠난 선수(외국인 교체)도 찾아 준다 — 올 시즌 기록·박스스코어에는
     * 그 선수가 남아 있어서, 기록실·문자 중계가 이름을 찾다가 멈추면 안 된다
     */
    fun player(id: PlayerId): Player = playerMap[id] ?: departed[id] ?: error("그런 선수가 없다: $id")

    /** 지금 리그에 있는가 (떠난 선수는 false) */
    fun isInLeague(id: PlayerId): Boolean = id in playerMap

    fun playersOf(teamId: TeamId): List<Player> = teamRosters.getValue(teamId).map { playerMap.getValue(it) }

    /** 시즌 중 외국인 시장. 교체로 계약된 선수는 빠진다 (docs/12) */
    private var foreignPool: baseballgm.market.ForeignPool = league.foreignPool

    fun foreignPool(): baseballgm.market.ForeignPool = foreignPool

    internal fun consumeForeignPoolEntry(playerId: PlayerId) {
        foreignPool = foreignPool.copy(candidates = foreignPool.candidates.filterNot { it.id == playerId })
    }

    /**
     * 시즌 중 리그를 떠난 선수. 소속 없이 보관만 한다 — [allPlayers]·[playersOf] 에는 안 나오고
     * [player] 로만 찾을 수 있다. 다음 시즌 리그에는 넘어가지 않는다
     */
    private val departed: MutableMap<PlayerId, Player> = mutableMapOf()

    /** 시즌 중 리그를 떠난다 (외국인 교체). */
    internal fun removePlayer(playerId: PlayerId) {
        val player = playerMap.remove(playerId) ?: return
        player.teamId?.let { teamRosters[it]?.remove(playerId) }
        departed[playerId] = player.withTeam(null, RosterLevel.FUTURES)
    }

    internal fun exportDeparted(): List<Player> = departed.values.toList()

    internal fun importDeparted(saved: List<Player>) {
        saved.forEach { departed[it.id] = it }
    }

    /** 시즌 중 새로 들어온다 (외국인 교체). */
    internal fun addPlayer(player: Player, teamId: TeamId) {
        playerMap[player.id] = baseballgm.model.UniformNumbers.forNewcomer(player, playersOf(teamId))
        teamRosters.getOrPut(teamId) { mutableListOf() }.add(player.id)
    }

    /** 트레이드로 소속이 바뀐다. 명단과 선수 기록을 한꺼번에 고쳐 어긋나지 않게 한다. */
    fun movePlayer(playerId: PlayerId, toTeam: TeamId) {
        val player = player(playerId)
        player.teamId?.let { teamRosters[it]?.remove(playerId) }
        // 새 팀에서 번호가 겹치면 바꿔 단다 (비어 있으면 원래 번호 그대로)
        val numbered = baseballgm.model.UniformNumbers.forNewcomer(player, playersOf(toTeam))
        teamRosters.getOrPut(toTeam) { mutableListOf() }.add(playerId)
        update(numbered.withTeam(toTeam, RosterLevel.FUTURES))
    }

    /**
     * 지금 이 순간의 리그.
     *
     * 시장(트레이드·FA) 코드는 [League] 를 받아 돌아가는데, 시즌 중에는 선수 소속·지명권·자금이
     * 시즌 상태 쪽에 있다. 그 둘을 합쳐 한 장의 스냅샷으로 만들어 넘긴다.
     */
    fun fanSupportOf(teamId: TeamId): Int = fanSupport[teamId] ?: league.team(teamId).fanSupport

    /**
     * 스태프 고용을 시즌 상태에 반영한다 (docs/13).
     *
     * 리그 데이터는 시즌 시작 시점의 고정값이라, 시즌 사이에 데려온 스태프는 여기에 얹어 두고
     * [currentLeague] 가 합쳐서 내보낸다. 스토브리그가 그 값을 다음 리그로 굳힌다.
     */
    private val staffHires: MutableMap<baseballgm.model.StaffId, Pair<TeamId, Double>> = mutableMapOf()

    fun applyStaffHire(staffId: baseballgm.model.StaffId, teamId: TeamId, salary: Double) {
        staffHires[staffId] = teamId to salary
    }

    fun currentLeague(): League = league.copy(
        players = allPlayers(),
        foreignPool = foreignPool,
        teams = league.teams.map { team ->
            team.copy(
                operatingFunds = funds[team.id] ?: team.operatingFunds,
                fanSupport = fanSupport[team.id] ?: team.fanSupport,
                ownerTrust = ownerTrust[team.id] ?: team.ownerTrust,
            )
        },
        draftRights = draftRights,
        tradeHistory = tradeHistory,
        retainedSalaries = retainedSalaries,
        management = league.management.copy(morale = morale.toMap()),
        coaches = league.coaches.map { coach ->
            if (coach.id in staffFired) return@map coach.copy(teamId = null)
            staffHires[coach.id]?.let { (teamId, salary) ->
                coach.copy(teamId = teamId, contract = coach.contract.copy(salary = salary, yearsRemaining = STAFF_YEARS))
            } ?: coach
        },
        medicalStaff = league.medicalStaff.map { staff ->
            if (staff.id in staffFired) return@map staff.copy(teamId = null)
            staffHires[staff.id]?.let { (teamId, salary) ->
                staff.copy(teamId = teamId, contract = staff.contract.copy(salary = salary, yearsRemaining = STAFF_YEARS))
            } ?: staff
        },
        managers = league.managers.map { manager ->
            if (manager.id in staffFired) return@map manager.copy(teamId = null)
            staffHires[manager.id]?.let { (teamId, salary) ->
                manager.copy(
                    teamId = teamId,
                    contract = manager.contract.copy(salary = salary, yearsRemaining = STAFF_YEARS),
                )
            } ?: manager
        },
    )

    fun firstTeamOf(teamId: TeamId): List<Player> =
        playersOf(teamId).filter { it.rosterLevel == RosterLevel.FIRST_TEAM }

    fun futuresOf(teamId: TeamId): List<Player> =
        playersOf(teamId).filter { it.rosterLevel == RosterLevel.FUTURES }

    fun allPlayers(): List<Player> = playerMap.values.toList()

    fun update(player: Player) {
        playerMap[player.id] = player
    }

    fun policyOf(teamId: TeamId): WeeklyPolicy = policies[teamId] ?: WeeklyPolicy.NORMAL

    fun tendenciesOf(teamId: TeamId): ManagerTendencies =
        managerTendencies[teamId] ?: DirectivePreset.STANDARD.tendencies()

    /** 선발 로테이션은 팀마다 순서대로 돌린다. 경기마다 한 칸씩 전진한다. */
    /** 다음 경기에 나올 로테이션 순번을 **읽기만** 한다 (화면의 "예상 선발", 2026-10-03). 순번을 넘기지 않는다 */
    fun peekRotationIndex(teamId: TeamId): Int = rotationIndex.getOrElse(teamId) { 0 }

    fun nextRotationIndex(teamId: TeamId): Int {
        val index = rotationIndex.getOrElse(teamId) { 0 }
        rotationIndex[teamId] = index + 1
        return index
    }

    /** 시즌 시작부터 통산 며칠째인가 (연투·7일 투구수 계산용). */
    fun absoluteDay(week: Int, day: Int): Int = (week - 1) * DAYS_PER_WEEK + day

    /** 지금 국제대회로 빠져 있는가 (docs/12). */
    fun isOnInternationalDuty(playerId: PlayerId, week: Int = this.week): Boolean =
        (internationalDuty[playerId] ?: 0) >= week

    // ---------- 세이브 (docs/14) — io/SeasonSnapshot.kt 가 쓴다 ----------

    /** 구단별 명단 순서. 순서가 난수 소비 순서를 정하므로 그대로 담아야 같은 결과가 나온다 */
    internal fun exportRosters(): Map<TeamId, List<PlayerId>> = teamRosters.mapValues { it.value.toList() }

    internal fun importRosters(saved: Map<TeamId, List<PlayerId>>) {
        teamRosters.clear()
        saved.forEach { (team, ids) -> teamRosters[team] = ids.filter { it in playerMap }.toMutableList() }
    }

    internal fun exportRotation(): Map<TeamId, Int> = rotationIndex.toMap()


    internal fun importRotation(saved: Map<TeamId, Int>) {
        rotationIndex.clear(); rotationIndex.putAll(saved)
    }

    fun scoutingOf(teamId: TeamId): ScoutingDepartment =
        scouting[teamId] ?: error("그런 구단이 없다: $teamId")

    companion object {
        private const val DAYS_PER_WEEK = 7

        /** 유저가 시즌 사이에 데려온 스태프의 기본 계약 연수 */
        private const val STAFF_YEARS = 2

        /** 리그 데이터에 스카우트 정보가 없을 때의 기본 단계. */
        const val DEFAULT_SCOUTING_LEVEL: Int = 3

        /** `balance.json` 의 기본 단계로 시즌 상태를 만든다. */
        fun of(
            league: League,
            calendar: SeasonCalendar,
            balance: BalanceConfig,
            scoutingLevels: Map<TeamId, Int> = emptyMap(),
        ): SeasonState {
            // 이름난 유망주는 풀마다 한 번 정한다 (새 게임·새 시즌·세이브 복원 모두 여기를 지난다. 이미 정해졌으면 그대로)
            val marked = league.copy(
                draftPool = baseballgm.scouting.ScoutingBudget(balance).markKnownProspects(league.draftPool),
            )
            return SeasonState(marked, calendar, scoutingLevels, balance.int("scouting.defaultLevel"))
        }
    }
}
