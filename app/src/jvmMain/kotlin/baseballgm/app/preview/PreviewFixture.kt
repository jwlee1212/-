package baseballgm.app.preview

import baseballgm.app.GameHost
import baseballgm.app.GameSession
import baseballgm.app.HostFactory
import baseballgm.app.SaveStore
import baseballgm.app.ui.TeamColorSpec
import baseballgm.model.Batter
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking

/**
 * 화면 미리보기용 고정 데이터 (2026-10-01, 절제 작업).
 *
 * 실제 리그 파일로 세션을 만들고 몇 주 진행해 기록·뉴스·부상이 쌓인 상태를 쓴다. 시드가 고정이라
 * 고치기 전과 후에 **같은 데이터**로 그려진다 — 그래야 화면 차이만 비교된다.
 * 만드는 데 몇 초 걸려서 한 번만 만든다.
 */
object PreviewFixture {

    /** 미리보기에서 보는 구단 */
    private const val TEAM = "SWR"

    /** 이 주차까지 진행한 상태를 보여 준다 */
    private const val WEEKS = 8

    val host: GameHost by lazy {
        HostFactory.create(
            ProjectFiles.read(ProjectFiles.BALANCE_PATH),
            ProjectFiles.read(ProjectFiles.TEAMS_PATH),
            { ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)) },
            object : SaveStore {
                override fun read(): String? = null
                override fun write(text: String) = Unit
            },
            null,
        )
    }

    val teamColors: List<TeamColorSpec> by lazy { host.teams.map { TeamColorSpec(it.id, it.color, it.colorDark) } }

    private fun newSession(): GameSession {
        val league = runBlocking { host.loadStartingLeague() }
        return host.newSession(league, league.teams.first { it.id.value == TEAM }.id, "김단장")
    }

    /** 마지막 주를 시작할 때의 순위 (결과 공개 화면의 "4위 → 3위") */
    var revealRankBefore: Int? = null
        private set

    /** 몇 주 진행한 세션. 돌발 이벤트는 비서 추천대로 처리해 둔다 */
    val session: GameSession by lazy {
        newSession().also { session ->
            repeat(WEEKS - 1) { session.advanceWeek(delegate = true) }
            revealRankBefore = session.rank()
            session.advanceWeek(delegate = true)
        }
    }

    /** 세 시즌을 넘긴 세션 (연도별 능력치 그래프 미리보기). 만드는 데 오래 걸린다 */
    val veteranSession: GameSession by lazy {
        val session = newSession()
        val weeks = session.balance.int("season.regularSeasonWeeks")
        repeat(3) {
            session.advanceUntil(weeks, delegate = true)
            session.autoDraft()
            session.advanceUntil(weeks, delegate = true)
            session.startNextSeason()
            session.skipFreeAgency()
        }
        session
    }

    /** 드래프트를 막 마친 세션 (우리 신인 화면 미리보기) */
    val draftSession: GameSession by lazy {
        val session = newSession()
        session.advanceUntil(session.balance.int("season.regularSeasonWeeks"), delegate = true)
        session.autoDraft()
        session
    }

    /**
     * FA 시장이 열린 세션 (2026-10-03 협상 현황 미리보기). 상위 FA 셋에 조건을 내고 한 라운드 진행해 둔다 —
     * 넉넉하게 / 희망 연봉 그대로 / 헐값 — 그래서 1순위·밀림 상태가 섞여 보인다.
     */
    val faSession: GameSession by lazy {
        val session = newSession()
        val weeks = session.balance.int("season.regularSeasonWeeks")
        session.advanceUntil(weeks, delegate = true)
        session.autoDraft()
        session.advanceUntil(weeks, delegate = true)
        session.startNextSeason()
        session.faAgents().take(3).forEachIndexed { index, agent ->
            val salary = agent.askingSalary * listOf(1.4, 1.0, 0.6)[index]
            session.submitFaOffer(agent, (salary * 10).toInt() / 10.0, agent.askingYears)
        }
        session.advanceFaRound()
        session
    }

    /**
     * 협상 테이블 미리보기 (2026-10-04): FA 시장이 열린 세션에서 경쟁이 붙은 상위 FA 에게 요구보다 10% 싸게 한 번 제안해 둔다
     * (에이전트 역제안과 대화가 보이게)
     */
    val faTalkSession: GameSession by lazy {
        val session = newSession()
        val weeks = session.balance.int("season.regularSeasonWeeks")
        session.advanceUntil(weeks, delegate = true)
        session.autoDraft()
        session.advanceUntil(weeks, delegate = true)
        session.startNextSeason()
        // 최저 연봉이 걸리지 않는 선수에게 요구보다 15% 싸게 — 역제안이 오는 모습
        val agent = session.faAgents().firstOrNull { each ->
            val wanted = session.faDemand(each)!!
            (wanted.salary + wanted.signingBonus / wanted.years) * 0.85 > each.salaryFloor
        } ?: session.faAgents().first()
        talkPlayer = agent.playerId
        val demand = session.faDemand(agent)!!
        val short = demand.asOffer(session.userTeamId, agent.playerId)
            .let { it.copy(salary = (it.salary * 0.85 * 10).toInt() / 10.0, signingBonus = (it.signingBonus * 0.85 * 10).toInt() / 10.0) }
        session.proposeFa(agent, short)
        session
    }

    private var talkPlayer: PlayerId? = null

    /** 협상 테이블 미리보기의 선수 */
    val faTalkPlayer: PlayerId get() = faTalkSession.let { talkPlayer!! }

    /** 결과 공개 미리보기 주차 (마지막으로 끝난 주) */
    val revealWeek: Int by lazy { session.lastReport!!.week }

    /** 돌발 이벤트가 걸려 멈춘 세션 (돌발 이벤트 카드·창 미리보기용). 못 만들면 null */
    val incidentSession: GameSession? by lazy {
        val session = newSession()
        // 경기 뒤에 생긴 이벤트(주중 멈춤)를 찾는다 — 결과 공개의 "이벤트 대기" 모습에 쓴다
        repeat(WEEKS * 3) {
            if (session.pendingIncident != null && session.weekGamesSoFar().isNotEmpty()) return@lazy session
            if (session.pendingIncident != null) session.delegateIncident()
            session.advanceWeek(delegate = false)
        }
        session.takeIf { it.pendingIncident != null }
    }

    /**
     * 선수가 보낸 메시지(이적 요청)가 걸려 멈춘 세션 (2026-10-04 선수 메시지 카드 미리보기용). 못 만들면 null.
     * 선수단을 몇 주째 불만인 상태로 만들어 이적 요청을 부른다.
     */
    val playerMessageSession: GameSession? by lazy {
        val session = newSession()
        repeat(WEEKS * 2) {
            if (session.pendingIncident?.kind == baseballgm.events.IncidentKind.TRADE_REQUEST) return@lazy session
            if (session.pendingIncident != null) {
                session.delegateIncident()
            } else {
                session.state.playersOf(session.userTeamId).forEach {
                    session.state.morale[it.id] = baseballgm.management.PlayerMorale(12, lowWeeks = 5)
                }
            }
            session.advanceWeek(delegate = false)
        }
        session.takeIf { it.pendingIncident?.kind == baseballgm.events.IncidentKind.TRADE_REQUEST }
    }

    /** 이적 요청에 "거절"로 답하고 한 주 더 간 세션 (선수단 대화 미리보기용). 못 만들면 null */
    val answeredPlayerSession: GameSession? by lazy {
        val session = playerMessageSessionFresh() ?: return@lazy null
        session.resolveIncident("refuse")
        while (session.pendingIncident != null || session.weekPaused) {
            if (session.pendingIncident != null) session.delegateIncident()
            session.advanceWeek(delegate = true)
        }
        session.advanceWeek(delegate = true)
        session
    }

    private fun playerMessageSessionFresh(): GameSession? {
        val session = newSession()
        repeat(WEEKS * 2) {
            if (session.pendingIncident?.kind == baseballgm.events.IncidentKind.TRADE_REQUEST) return session
            if (session.pendingIncident != null) {
                session.delegateIncident()
            } else {
                session.state.playersOf(session.userTeamId).forEach {
                    session.state.morale[it.id] = baseballgm.management.PlayerMorale(12, lowWeeks = 5)
                }
            }
            session.advanceWeek(delegate = false)
        }
        return null
    }

    /** 기회형 이벤트가 걸려 멈춘 세션 (기회 카드 미리보기용). 못 만들면 null */
    val opportunitySession: GameSession? by lazy {
        val session = newSession()
        repeat(WEEKS * 3) {
            val pending = session.pendingIncident
            if (pending != null && pending.kind.opportunity) return@lazy session
            if (pending != null) session.delegateIncident()
            session.advanceWeek(delegate = false)
        }
        null
    }

    /** 우리 팀 주전 타자 */
    val ownBatter: PlayerId by lazy {
        session.roster(RosterLevel.FIRST_TEAM).first { it is Batter }.id
    }

    /** 타 팀 하나 (범위로만 보이는 선수들) */
    val otherTeam: baseballgm.model.TeamId by lazy { session.league.teams.first { it.id != session.userTeamId }.id }

    /** 타 팀 1군 타자 (범위로만 보이는 선수) */
    val otherBatter: PlayerId by lazy {
        val other = session.league.teams.first { it.id != session.userTeamId }.id
        session.roster(RosterLevel.FIRST_TEAM, other).first { it is Batter }.id
    }

    val compareIds: List<PlayerId> by lazy {
        session.roster(RosterLevel.FIRST_TEAM).filterIsInstance<Batter>().take(2).map { it.id } + otherBatter
    }

    val prospect: PlayerId by lazy { session.draftBoard(1).first().player.id }
}
