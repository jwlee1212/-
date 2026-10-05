package baseballgm.app

import baseballgm.events.IncidentKind
import baseballgm.events.IncidentRecord
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import baseballgm.io.SaveGameCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 결정 성적표 (2026-10-02, 재미 개선 2번) */
class DecisionReviewTest {

    private val balance = ProjectFiles.loadBalanceConfig()

    /** 이벤트를 하나씩 비서에게 맡기며 [weeks] 주 진행한다. 답하는 순간의 팀 전적을 같이 적어 둔다 */
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

    private fun play(weeks: Int): Pair<GameSession, List<Pair<IncidentRecord, baseballgm.league.TeamRecord>>> {
        val start = kotlinx.coroutines.runBlocking { host.loadStartingLeague() }
        val session = host.newSession(start, TeamId("SWR"), "테스트")
        val decided = mutableListOf<Pair<IncidentRecord, baseballgm.league.TeamRecord>>()
        repeat(weeks) {
            session.advanceWeek()
            while (session.pendingIncident != null || session.weekPaused) {
                if (session.pendingIncident != null) {
                    val atDecision = session.record()
                    session.delegateIncident()
                    decided += session.incidentLog().first() to atDecision
                }
                session.advanceWeek()
            }
        }
        return session to decided
    }

    @Test
    fun `결정할 때 기준값을 찍는다 - 팀 전적·팬심·신뢰가 그 순간 값이다`() {
        val (_, decided) = play(6)
        assertTrue(decided.isNotEmpty(), "6주 안에 돌발 이벤트가 있어야 한다")
        decided.forEach { (record, atDecision) ->
            val base = assertNotNull(record.baseline, "${record.kind} 기준값")
            assertEquals(atDecision.wins, base.wins)
            assertEquals(atDecision.losses, base.losses)
            assertEquals(atDecision.runsScored, base.runsScored)
            if (record.kind in PLAYER_KINDS) assertTrue(base.players.isNotEmpty(), "${record.kind} 는 주인공 선수가 있다")
        }
    }

    @Test
    fun `성적표 숫자는 지금 값 − 기준값이고 표본이 작으면 판정하지 않는다`() {
        val (session, decided) = play(10)
        val reviewer = session.decisionReviewer
        val minGames = balance.int("decisionReview.minGames")
        decided.forEach { (record, _) ->
            val review = assertNotNull(reviewer.review(session, record))
            val base = record.baseline!!
            val now = session.record()
            assertEquals(now.wins + now.losses + now.ties - (base.wins + base.losses + base.ties), review.gamesSince)
            if (review.gamesSince < minGames && record.kind !in setOf(IncidentKind.CONTROVERSY)) {
                assertTrue(review.verdict == Verdict.TOO_EARLY || review.verdict == Verdict.MISSED, "표본이 작으면 판정 보류 (부상 재발만 예외)")
            }
            // 선수 기준 결정이면 "이후 N타석"이 실제 기록 차이와 같다
            val subject = base.players.firstOrNull()
            // (피로 누적은 타석이 아니라 피로 변화를 본다)
            if (record.kind in PLAYER_KINDS && record.kind != IncidentKind.FATIGUE && subject != null &&
                session.isPitcher(session.player(subject.playerId)).not()
            ) {
                val pa = (session.batting(subject.playerId) - subject.batting).plateAppearances
                if (pa > 0) assertTrue(review.main.contains("${pa}타석"), "${review.main} 에 ${pa}타석")
            }
        }
        // 오래된 결정은 판정이 난다
        assertTrue(
            decided.mapNotNull { reviewer.review(session, it.first) }.any { it.verdict != Verdict.TOO_EARLY },
            "10주 진행하면 판정 난 결정이 하나는 있다",
        )
    }

    @Test
    fun `최근 결정만 성적표에 오르고 비서 한 줄은 판정 난 것만 말한다`() {
        val (session, _) = play(8)
        val reviewer = session.decisionReviewer
        val recent = reviewer.recent(session)
        assertTrue(recent.all { it.record.week >= session.week - reviewer.reviewWeeks })
        reviewer.headline(session)?.let { line ->
            assertTrue(recent.any { (it.verdict == Verdict.WORKED || it.verdict == Verdict.MISSED) && line.contains(it.record.choice) })
        }
    }

    @Test
    fun `기준값은 세이브에 남고 불러온 뒤에도 같은 성적표가 나온다`() {
        val (session, decided) = play(4)
        assertTrue(decided.isNotEmpty())
        val data = assertNotNull(session.saveData(), "주 사이라 저장할 수 있어야 한다")
        val save = assertNotNull(SaveGameCodec.parseOrNull(SaveGameCodec.encode(data)))
        val restored = host.resume(save)
        assertEquals(session.incidentLog(), restored.incidentLog())
        assertTrue(restored.incidentLog().all { it.baseline != null })
        assertEquals(
            session.incidentLog().map { session.decisionReviewer.review(session, it) },
            restored.incidentLog().map { restored.decisionReviewer.review(restored, it) },
        )
    }

    private companion object {
        val PLAYER_KINDS = setOf(IncidentKind.INJURY_REPLACEMENT, IncidentKind.HOT_PROSPECT, IncidentKind.PLAYING_TIME, IncidentKind.PLAY_THROUGH, IncidentKind.FATIGUE)
    }
}
