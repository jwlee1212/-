package baseballgm.io

import baseballgm.events.FanPost
import baseballgm.events.IncidentRecord
import baseballgm.events.NewsItem
import baseballgm.events.TournamentResult
import baseballgm.league.League
import baseballgm.league.Standings
import baseballgm.management.GmDecision
import baseballgm.market.DraftPool
import baseballgm.market.DraftResult
import baseballgm.market.DraftSelection
import baseballgm.market.DraftSlot
import baseballgm.market.DraftState
import baseballgm.market.TradeProposal
import baseballgm.model.ManagerTendencies
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.season.InboxMessage
import baseballgm.season.PostseasonResult
import baseballgm.season.SeasonCalendar
import baseballgm.season.SeasonState
import baseballgm.stats.BoxScore
import baseballgm.stats.SeasonStatsSnapshot
import baseballgm.tactics.WeeklyPolicy
import kotlinx.serialization.Serializable

/** 진행 중이던 드래프트 */
@Serializable
data class DraftSnapshot(
    val season: Int,
    val order: List<DraftSlot>,
    val pool: DraftPool,
    val available: List<PlayerId>,
    val selections: List<DraftSelection>,
    val index: Int,
)

/** 스카우트 부서 하나 */
@Serializable
data class ScoutingSnapshot(
    val level: Int,
    val observedWeeks: Map<PlayerId, Int>,
    val focused: List<PlayerId>,
    /** 자동 집중 관찰 (2026-10-03 추가 — 옛 세이브는 기본값) */
    val autoFocus: Boolean = true,
    val draftPolicy: baseballgm.market.DraftPolicy = baseballgm.market.DraftPolicy.BALANCED,
    val autoAssigned: List<PlayerId> = emptyList(),
)

/**
 * 시즌 진행 스냅샷 (docs/14 세이브, 2026-10-01).
 *
 * **주차 경계에서만 찍는다** — 주중 진행(돌발 이벤트로 멈춘 주)은 담지 않는다. 앱을 닫았다 열면 그 주 시작으로
 * 돌아가고, 주차 시드가 같아서 같은 경기·같은 돌발 이벤트가 다시 나온다.
 *
 * 선수·자금·팬심·구단주 신뢰·지명권·트레이드 이력·외국인 시장은 여기 없다 — 세이브에 같이 들어가는
 * **"지금 리그"**([SeasonState.currentLeague])에 이미 들어 있어서 두 번 담지 않는다 (폰 웹 저장 한도 5MB).
 * 여기는 리그에 없는, 시즌을 진행하며 쌓인 것만 담는다.
 *
 * **순서를 지킨다.** 명단·관찰 슬롯·드래프트 후보 순서는 동점 처리와 난수 소비 순서를 정해서,
 * 순서가 바뀌면 같은 시드라도 다른 시즌이 나온다.
 */
@Serializable
data class SeasonSnapshot(
    val week: Int,
    val standings: Standings,
    val rosters: Map<TeamId, List<PlayerId>>,
    val rotation: Map<TeamId, Int> = emptyMap(),
    val stats: SeasonStatsSnapshot = SeasonStatsSnapshot(),
    val demotedAtWeek: Map<PlayerId, Int> = emptyMap(),
    val rehabReadyWeek: Map<PlayerId, Int> = emptyMap(),
    /** 단장이 직접 올린 선수의 자동 정리 보호가 끝나는 주차 (2026-10-03). 옛 세이브엔 없다 */
    val userPickedUntil: Map<PlayerId, Int> = emptyMap(),
    val pitcherUsage: Map<PlayerId, List<List<Int>>> = emptyMap(),
    val inbox: List<InboxMessage> = emptyList(),
    val policies: Map<TeamId, WeeklyPolicy> = emptyMap(),
    val managerTendencies: Map<TeamId, ManagerTendencies> = emptyMap(),
    val gmDirections: Map<TeamId, ManagerTendencies> = emptyMap(),
    val scouting: Map<TeamId, ScoutingSnapshot> = emptyMap(),
    val draft: DraftSnapshot? = null,
    val draftResult: DraftResult? = null,
    /** 스카우트 정기 리포트 (2026-10-04 추가) */
    val scoutDigests: List<baseballgm.scouting.ScoutDigest> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val fanPosts: List<FanPost> = emptyList(),
    val decisionLog: List<GmDecision> = emptyList(),
    val headToHead: List<Triple<TeamId, TeamId, Int>> = emptyList(),
    val lastLeader: TeamId? = null,
    val negotiationRejections: List<Triple<TeamId, TeamId, Int>> = emptyList(),
    val negotiationRefusals: List<Triple<TeamId, TeamId, Int>> = emptyList(),
    val internationalDuty: Map<PlayerId, Int> = emptyMap(),
    val tournamentResult: TournamentResult? = null,
    val postseason: PostseasonResult? = null,
    /** 진행 중인 포스트시즌 (2026-10-03). 옛 세이브엔 없다 */
    val postseasonProgress: baseballgm.season.PostseasonProgress? = null,
    val foreignReplacements: Map<TeamId, Int> = emptyMap(),
    /** 시즌 중 리그를 떠난 선수 (외국인 교체). 올 시즌 기록에 이름이 남아 있어서 함께 저장한다. 옛 세이브엔 없다 */
    val departedPlayers: List<baseballgm.model.Player> = emptyList(),
    val difficulty: String = "normal",
    val pendingTradeOffer: TradeProposal? = null,
    val pendingTradeOfferWeek: Int? = null,
    val aiTradeCount: Int = 0,
    val aiTradedTeams: List<TeamId> = emptyList(),
    val incidentLog: List<IncidentRecord> = emptyList(),
    val incidentKeys: List<String> = emptyList(),
    val policyToRestore: Map<TeamId, WeeklyPolicy> = emptyMap(),
    /** 스토브리그 준비 중인 계획 (2026-10-01) */
    val offseasonPlan: baseballgm.season.OffseasonPlan? = null,
    val staffFired: List<baseballgm.model.StaffId> = emptyList(),
    /** 비FA 다년계약 협상 (2026-10-03) */
    val extensionRejections: Map<PlayerId, Int> = emptyMap(),
    val extendedThisSeason: List<PlayerId> = emptyList(),
)

/** 지난주 결산 (홈 브리핑·주간 브리핑용). 관전용 이벤트 스트림(문자 중계)은 크기 때문에 담지 않는다 */
@Serializable
data class ReportSnapshot(
    val week: Int,
    val games: List<BoxScore>,
    val messages: List<InboxMessage>,
    val allStarBreak: Boolean,
    val highlights: List<String> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val fanPosts: List<FanPost> = emptyList(),
    val echoes: List<String> = emptyList(),
    val incidents: List<IncidentRecord> = emptyList(),
)

object SeasonSnapshots {

    /** 주차 경계에서만 부른다. 주중(돌발 이벤트로 멈춘 상태)이면 null — 그 주 시작 세이브가 남아 있다 */
    fun capture(state: SeasonState): SeasonSnapshot? {
        if (state.weekInProgress != null || state.pendingIncidents.isNotEmpty()) return null
        val (demoted, rehab) = state.roster.export()
        val (rejections, refusals) = state.negotiationFatigue.export()
        return SeasonSnapshot(
            week = state.week,
            standings = state.standings,
            rosters = state.exportRosters(),
            rotation = state.exportRotation(),
            stats = state.stats.export(),
            demotedAtWeek = demoted,
            rehabReadyWeek = rehab,
            userPickedUntil = state.roster.exportPicks(),
            pitcherUsage = state.usage.export(),
            inbox = state.inbox.all(),
            policies = state.policies.toMap(),
            managerTendencies = state.managerTendencies.toMap(),
            gmDirections = state.gmDirections.toMap(),
            scouting = state.scouting.mapValues { (_, dept) ->
                val (observed, focused) = dept.export()
                ScoutingSnapshot(dept.level, observed, focused, dept.autoFocus, dept.draftPolicy, dept.exportAuto())
            },
            draft = state.draft?.let { draft ->
                DraftSnapshot(draft.season, draft.order, draft.pool, draft.availableProspects().map { it.id }, draft.selections, draft.index)
            },
            draftResult = state.draftResult,
            scoutDigests = state.scoutDigests.toList(),
            news = state.news.toList(),
            fanPosts = state.fanPosts.toList(),
            decisionLog = state.decisionLog.toList(),
            headToHead = state.headToHead.map { Triple(it.key.first, it.key.second, it.value) },
            lastLeader = state.lastLeader,
            negotiationRejections = rejections,
            negotiationRefusals = refusals,
            internationalDuty = state.internationalDuty.toMap(),
            tournamentResult = state.tournamentResult,
            postseason = state.postseason,
            postseasonProgress = state.postseasonProgress,
            foreignReplacements = state.foreignReplacements.toMap(),
            departedPlayers = state.exportDeparted(),
            difficulty = state.difficulty,
            pendingTradeOffer = state.pendingTradeOffer,
            pendingTradeOfferWeek = state.pendingTradeOfferWeek,
            aiTradeCount = state.aiTradeCount,
            aiTradedTeams = state.aiTradedTeams.toList(),
            incidentLog = state.incidentLog.toList(),
            incidentKeys = state.incidentKeys.toList(),
            policyToRestore = state.policyToRestore.toMap(),
            offseasonPlan = state.offseasonPlan,
            staffFired = state.staffFired.toList(),
            extensionRejections = state.extensionRejections.toMap(),
            extendedThisSeason = state.extendedThisSeason.toList(),
        )
    }

    /**
     * 되살린다. [currentLeague] 는 세이브에 함께 들어 있던 "지금 리그"다 —
     * 선수·자금·팬심·구단주 신뢰·지명권은 거기서 바로 들어오고, 나머지를 스냅샷에서 채운다.
     */
    fun restore(currentLeague: League, calendar: SeasonCalendar, balance: BalanceConfig, snapshot: SeasonSnapshot): SeasonState {
        val state = SeasonState.of(
            currentLeague,
            calendar,
            balance,
            snapshot.scouting.mapValues { it.value.level },
        )
        state.week = snapshot.week
        state.standings = snapshot.standings
        state.importRosters(snapshot.rosters)
        state.importRotation(snapshot.rotation)
        state.stats.import(snapshot.stats)
        state.roster.import(snapshot.demotedAtWeek, snapshot.rehabReadyWeek)
        state.roster.importPicks(snapshot.userPickedUntil)
        state.usage.import(snapshot.pitcherUsage)
        state.inbox.import(snapshot.inbox)
        state.policies.putAll(snapshot.policies)
        state.managerTendencies.putAll(snapshot.managerTendencies)
        state.gmDirections.putAll(snapshot.gmDirections)
        snapshot.scouting.forEach { (team, saved) ->
            state.scouting[team]?.let { dept ->
                dept.import(saved.observedWeeks, saved.focused)
                dept.importAuto(saved.autoAssigned)
                dept.autoFocus = saved.autoFocus
                dept.draftPolicy = saved.draftPolicy
            }
        }
        state.draft = snapshot.draft?.let { saved ->
            DraftState(saved.season, saved.order, saved.pool).also { it.restore(saved.available, saved.selections, saved.index) }
        }
        state.draftResult = snapshot.draftResult
        state.scoutDigests.addAll(snapshot.scoutDigests)
        state.news += snapshot.news
        state.fanPosts += snapshot.fanPosts
        state.decisionLog += snapshot.decisionLog
        snapshot.headToHead.forEach { state.headToHead[it.first to it.second] = it.third }
        state.lastLeader = snapshot.lastLeader
        state.negotiationFatigue.import(snapshot.negotiationRejections, snapshot.negotiationRefusals)
        state.internationalDuty.putAll(snapshot.internationalDuty)
        state.tournamentResult = snapshot.tournamentResult
        state.postseason = snapshot.postseason
        state.postseasonProgress = snapshot.postseasonProgress
        state.foreignReplacements.putAll(snapshot.foreignReplacements)
        state.importDeparted(snapshot.departedPlayers)
        state.difficulty = snapshot.difficulty
        state.pendingTradeOffer = snapshot.pendingTradeOffer
        state.pendingTradeOfferWeek = snapshot.pendingTradeOfferWeek
        state.aiTradeCount = snapshot.aiTradeCount
        state.aiTradedTeams += snapshot.aiTradedTeams
        state.incidentLog += snapshot.incidentLog
        state.incidentKeys += snapshot.incidentKeys
        state.policyToRestore.putAll(snapshot.policyToRestore)
        state.offseasonPlan = snapshot.offseasonPlan
        // 경질은 세이브의 "지금 리그"에 이미 반영돼 있다(소속 없음). 다시 적용해도 같다
        state.staffFired += snapshot.staffFired
        state.extensionRejections.putAll(snapshot.extensionRejections)
        state.extendedThisSeason += snapshot.extendedThisSeason
        return state
    }
}
