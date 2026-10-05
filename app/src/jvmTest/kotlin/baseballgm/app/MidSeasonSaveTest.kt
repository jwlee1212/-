package baseballgm.app

import baseballgm.io.SaveGameCodec
import baseballgm.model.RosterLevel
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 시즌 중 저장·이어하기 (docs/14, 2026-10-01).
 * 앱을 닫았다 켜도 그 주차부터 이어지고, 이어 간 시즌은 안 닫은 시즌과 똑같이 흘러간다.
 */
class MidSeasonSaveTest {

    private val host = HostFactory.create(
        ProjectFiles.read(ProjectFiles.BALANCE_PATH),
        ProjectFiles.read(ProjectFiles.TEAMS_PATH),
        { ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)) },
        object : SaveStore {
            var text: String? = null
            override fun read(): String? = text
            override fun write(text: String) {
                this.text = text
            }
        },
        null,
    )
    private val league = runBlocking { host.loadStartingLeague() }

    private fun newGame() = host.newSession(league, league.teams.first().id, "테스트")

    /** 저장 → 문자열 → 읽기 → 이어하기. 앱을 껐다 켠 것과 같다 */
    private fun reopen(session: GameSession): GameSession {
        val data = assertNotNull(session.saveData(), "저장할 수 있는 순간이어야 한다")
        val save = assertNotNull(SaveGameCodec.parseOrNull(SaveGameCodec.encode(data)))
        return host.resume(save)
    }

    @Test
    fun `몇 주 진행하고 껐다 켜도 그 주차부터 이어지고 결과가 같다`() {
        val original = newGame()
        repeat(6) { original.advanceWeek(delegate = true) }
        // 단장이 손댄 것도 저장돼야 한다: 엔트리 맞바꾸기
        val up = original.roster(RosterLevel.FUTURES).first { original.eligibilityProblem(it) == null }
        val down = original.swapOutCandidates(up).first()
        assertTrue(original.swap(up, down))

        val restored = reopen(original)
        assertEquals(original.week, restored.week)
        assertEquals(original.userTeamId, restored.userTeamId)
        assertEquals(original.record(), restored.record())
        assertEquals(original.lastReport?.week, restored.lastReport?.week, "지난주 브리핑이 사라졌다")
        assertEquals(RosterLevel.FIRST_TEAM, restored.player(up.id).rosterLevel, "맞바꾼 엔트리가 저장되지 않았다")
        assertEquals(original.news().size, restored.news().size)
        assertEquals(original.incidentLog(), restored.incidentLog())

        repeat(8) {
            original.advanceWeek(delegate = true)
            restored.advanceWeek(delegate = true)
        }
        assertEquals(original.teamsRanked(), restored.teamsRanked(), "이어 간 시즌이 다르게 흘렀다")
        assertEquals(original.lastReport?.games, restored.lastReport?.games)
    }

    @Test
    fun `주중에 멈췄을 때·드래프트 도중에는 저장하지 않는다`() {
        val session = newGame()
        var guard = 0
        while (session.pendingIncident == null && !session.seasonOver && guard++ < 40) {
            if (session.isDraftWeek && !session.draftDone) session.autoDraft()
            session.advanceWeek()
        }
        if (session.pendingIncident != null) assertNull(session.saveData(), "돌발 이벤트로 멈춘 주를 저장했다")

        val drafting = newGame()
        drafting.advanceUntil(drafting.state.calendar.draftWeek, delegate = true)
        if (drafting.isDraftWeek && !drafting.draftDone) {
            drafting.openDraft()
            if (drafting.state.draft?.isComplete == false) assertNull(drafting.saveData(), "드래프트 도중을 저장했다")
            drafting.autoDraft()
            assertNotNull(drafting.saveData(), "드래프트가 끝나면 저장돼야 한다")
        }
    }

    @Test
    fun `화면을 보기만 해서는 결과가 바뀌지 않는다 (스태프 시장)`() {
        val looked = newGame()
        looked.staffOffers()
        val untouched = newGame()
        repeat(4) {
            looked.advanceWeek(delegate = true)
            untouched.advanceWeek(delegate = true)
        }
        assertEquals(untouched.teamsRanked(), looked.teamsRanked())
    }

    @Test
    fun `정규시즌이 끝나고 포스트시즌까지 마친 뒤에도 저장된다`() {
        val session = newGame()
        session.advanceUntil(session.state.calendar.regularSeasonWeeks, delegate = true)
        if (session.isDraftWeek && !session.draftDone) {
            session.autoDraft()
            session.advanceUntil(session.state.calendar.regularSeasonWeeks, delegate = true)
        }
        session.runPostseason()
        val restored = reopen(session)
        assertTrue(restored.seasonOver)
        assertEquals(session.postseason(), restored.postseason())
        assertEquals(ProgressAction.of(session)::class, ProgressAction.of(restored)::class)
    }

    /** 포스트시즌을 진행 버튼으로 끝까지. 우리 시리즈는 한 번에 한 경기씩만 가야 한다 */
    private fun finishPostseason(session: GameSession) {
        var guard = 0
        while (!session.postseasonDone && guard++ < MAX_POSTSEASON_PRESSES) {
            when (val action = ProgressAction.of(session)) {
                is ProgressAction.PostseasonStep -> {
                    val before = session.postseasonGames.size
                    val ours = session.userInCurrentSeries
                    session.advancePostseason()
                    if (ours) assertEquals(before + 1, session.postseasonGames.size, "우리 시리즈는 한 경기씩 가야 한다 ($action)")
                }
                ProgressAction.PostseasonFinish -> session.runPostseason()
                else -> error("포스트시즌 중에 다른 진행 버튼: $action")
            }
        }
        assertTrue(session.postseasonDone)
    }

    @Test
    fun `포스트시즌을 한 경기씩 진행하다 껐다 켜도 결과가 같고 방침이 남는다`() {
        val original = newGame()
        original.advanceUntil(original.state.calendar.regularSeasonWeeks, delegate = true)
        if (original.isDraftWeek && !original.draftDone) {
            original.autoDraft()
            original.advanceUntil(original.state.calendar.regularSeasonWeeks, delegate = true)
        }
        val inPostseason = original.rank() <= original.postseasonSpots
        // 시작: 대진만 짜고 경기는 아직 없다
        original.advancePostseason()
        assertTrue(original.postseasonStarted)
        assertTrue(original.postseasonGames.isEmpty(), "시작 버튼은 경기를 치르지 않는다")
        if (inPostseason) original.setPostseasonPolicy(baseballgm.tactics.WeeklyPolicy.PROTECT)
        repeat(2) { if (ProgressAction.of(original) is ProgressAction.PostseasonStep) original.advancePostseason() }

        val restored = reopen(original)
        assertEquals(original.postseasonProgress, restored.postseasonProgress, "포스트시즌 진행 상태가 저장되지 않았다")
        if (inPostseason && restored.postseasonProgress!!.isAlive(restored.userTeamId)) {
            assertEquals(baseballgm.tactics.WeeklyPolicy.PROTECT, restored.postseasonPolicy())
        }
        finishPostseason(original)
        finishPostseason(restored)
        assertEquals(original.postseason(), restored.postseason(), "껐다 켠 포스트시즌 결과가 다르다")
    }

    private companion object {
        const val MAX_POSTSEASON_PRESSES = 60
    }
}
