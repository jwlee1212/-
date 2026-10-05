package baseballgm.app

import baseballgm.events.BaselineRole
import baseballgm.events.DecisionFocus
import baseballgm.events.IncidentKind
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 기회형 이벤트 (2026-10-03, 재미 개선 3번)와 "관련 지표만" 성적표.
 *
 * 기회형이 오면 일부러 **행동하는 쪽**(받는다·데려온다·써 본다·연장한다)을 골라 효과가 실제로 들어가는지 본다.
 * 위기형은 비서 추천대로 넘긴다.
 */
class OpportunityTest {

    private val host = HostFactory.create(
        ProjectFiles.read(ProjectFiles.BALANCE_PATH),
        ProjectFiles.read(ProjectFiles.TEAMS_PATH),
        { ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)) },
        object : SaveStore {
            override fun read(): String? = null
            override fun write(text: String) = Unit
        },
        null,
    )

    /** 한 결정의 전후 */
    /** 이 테스트가 트레이드 문의에 첫 선택지(보낸다)를 골라 내보낸 선수인가 */
    private fun tradedAway(taken: List<Taken>, id: baseballgm.model.PlayerId): Boolean =
        taken.any { it.kind == IncidentKind.TRADE_INQUIRY && it.playerId == id }

    private class Taken(val kind: IncidentKind, val optionId: String, val playerId: baseballgm.model.PlayerId?, val ratingBefore: Double?)

    private fun play(team: String, weeks: Int, act: Boolean): Pair<GameSession, List<Taken>> {
        val start = runBlocking { host.loadStartingLeague() }
        val session = host.newSession(start, TeamId(team), "테스트")
        val taken = mutableListOf<Taken>()
        repeat(weeks) {
            session.advanceWeek()
            while (session.pendingIncident != null || session.weekPaused) {
                val incident = session.pendingIncident
                if (incident != null && incident.kind.opportunity && act) {
                    val option = incident.options.first()
                    val before = incident.playerId?.let { id -> session.player(id).ratingsMap().values.average() }
                    taken += Taken(incident.kind, option.id, incident.playerId, before)
                    session.resolveIncident(option.id)
                } else if (incident != null) {
                    session.delegateIncident()
                }
                session.advanceWeek()
            }
        }
        return session to taken
    }

    @Test
    fun `기회형은 위기형이 없는 주에 오고 시즌 상한을 넘지 않는다`() {
        val (session, _) = play("DSK", 24, act = false)
        val log = session.incidentLog()
        val opportunities = log.filter { it.kind.opportunity }
        assertTrue(opportunities.isNotEmpty(), "강팀에도 기회형이 와야 한다")
        assertTrue(opportunities.size <= ProjectFiles.loadBalanceConfig().int("incidents.opportunity.maxPerSeason"))
        // 기회형이 온 주차의 "주 시작" 이벤트는 그것 하나뿐이다
        opportunities.forEach { opp ->
            val weekStart = log.filter { it.week == opp.week && it.day == null }
            assertEquals(listOf(opp), weekStart, "${opp.week}주 시작에 기회형과 다른 이벤트가 같이 왔다")
        }
    }

    @Test
    fun `행동을 고르면 효과가 들어간다 - 영입·시험·연장·트레이드`() {
        val seen = mutableSetOf<IncidentKind>()
        listOf("SWR", "DSK", "MRC").forEach { team ->
            val (session, taken) = play(team, 18, act = true)
            taken.forEach { t ->
                seen += t.kind
                val id = assertNotNull(t.playerId)
                val player = session.player(id)
                when (t.kind) {
                    IncidentKind.RELEASED_VETERAN -> if (!tradedAway(taken, id)) {
                        assertEquals(session.userTeamId, player.teamId, "데려온 선수가 우리 팀이 아니다")
                    }
                    IncidentKind.PROSPECT_TRIAL -> assertTrue(player.ratingsMap().values.average() > t.ratingBefore!!, "유망주 능력치가 안 올랐다")
                    IncidentKind.EXTENSION -> assertTrue(player.contract.seasonsToFreeAgency > 0 || player.teamId != session.userTeamId, "연장했는데 FA 대상 그대로")
                    IncidentKind.TRADE_INQUIRY -> {
                        // 트레이드가 성사됐으면 우리 선수는 떠났다 (그사이 조건이 바뀌어 무산될 수는 있다)
                        val record = session.incidentLog().first { it.kind == IncidentKind.TRADE_INQUIRY && it.playerId == id }
                        val base = assertNotNull(record.baseline)
                        assertTrue(DecisionFocus.TRADE_ACCEPTED in base.focus)
                        assertTrue(base.players.any { it.role == BaselineRole.ACQUIRED } && base.players.any { it.role == BaselineRole.DEPARTED })
                    }
                    else -> Unit
                }
            }
        }
        assertTrue(seen.size >= 3, "세 구단 18주씩이면 기회형 종류가 여럿 나와야 한다: $seen")
    }

    @Test
    fun `같은 시드·같은 선택이면 같은 기회형이 온다 (재현성)`() {
        val a = play("SWR", 12, act = true).first.incidentLog()
        val b = play("SWR", 12, act = true).first.incidentLog()
        assertEquals(a.map { it.kind to it.headline }, b.map { it.kind to it.headline })
    }

    @Test
    fun `성적표는 그 결정이 건드린 지표만 - 트레이드는 받은·보낸 선수, 선수 결정엔 팀 승패가 없다`() {
        listOf("SWR", "DSK", "MRC").forEach { team ->
            val (session, _) = play(team, 14, act = true)
            session.incidentLog().forEach { record ->
                val review = session.decisionReviewer.review(session, record) ?: return@forEach
                val focus = record.baseline!!.focus
                when {
                    DecisionFocus.TRADE_ACCEPTED in focus -> {
                        assertTrue(review.main.startsWith("받은 "), review.main)
                        assertTrue(review.extras.any { it.startsWith("보낸 ") }, review.extras.toString())
                    }
                    DecisionFocus.TRADE_DECLINED in focus && record.kind == IncidentKind.TRADE_INQUIRY ->
                        assertTrue(review.main.startsWith("지킨 "), review.main)
                    DecisionFocus.PLAYER in focus && record.kind != IncidentKind.FATIGUE ->
                        assertTrue(!review.main.contains("승 ") && !review.main.contains("경기당"), "선수 결정에 팀 성적이 섞였다: ${review.main}")
                }
            }
        }
    }

    @Test
    fun `데려온 방출 매물은 시즌 내내 우리 팀 선수로 남는다`() {
        listOf("SWR", "DSK", "MRC").forEach { team ->
            val (session, taken) = play(team, 18, act = true)
            // 나중에 이 테스트가 트레이드 문의에 "보낸다"를 골라 내보낸 선수는 뺀다
            taken.filter { it.kind == IncidentKind.RELEASED_VETERAN && !tradedAway(taken, it.playerId!!) }.forEach { t ->
                val player = session.player(t.playerId!!)
                // 그 뒤 엔트리 정리로 내려갈 수는 있지만, 우리 팀 선수여야 한다
                assertEquals(session.userTeamId, player.teamId)
                assertTrue(player.rosterLevel == RosterLevel.FIRST_TEAM || player.rosterLevel == RosterLevel.FUTURES)
            }
        }
    }
}
