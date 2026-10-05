package baseballgm.app

import baseballgm.market.FaTalkOutcome
import baseballgm.market.OptionClause
import baseballgm.tools.ProjectFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 비FA 다년계약 협상 테이블 (2026-10-05 유저 요청 "비FA 계약에도 똑같이 적용해줘") */
class ExtensionNegotiationTest {

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

    private fun newGame(): GameSession {
        val league = runBlocking { host.loadStartingLeague() }
        return host.newSession(league, league.teams.first().id, "테스트")
    }

    @Test
    fun `모자라면 역제안 · 맞추면 도장 · 옵션 조항은 다음 시즌부터 붙는다`() {
        val session = newGame()
        repeat(3) { session.advanceWeek(delegate = true) }
        val player = session.extensionCandidates().first()
        val terms = session.extensionTerms(player.id)
        assertEquals(3, terms.importance.size, "항목별 중요도가 있어야 한다")

        val short = session.proposeExtension(player.id, terms.demand * 0.9, terms.years)
        assertTrue(short.outcome == FaTalkOutcome.COUNTERED || short.accepted, "${short.outcome} ${short.message}")
        if (!short.accepted) {
            assertEquals(session.extensionMaxTalks - 1, short.talksLeft)
            val counter = assertNotNull(short.counter)
            assertEquals(2, session.extensionTalkLog(player.id).size)
            val kind = session.optionKindsFor(player).first()
            val signed = session.proposeExtension(player.id, counter, terms.years, 0.0, listOf(OptionClause(kind, 0.5)))
            assertTrue(signed.accepted, signed.message)
            val contract = session.player(player.id).contract
            assertEquals(listOf(OptionClause(kind, 0.5)), contract.nextOptions)
            assertTrue(contract.options.isEmpty(), "옵션은 다음 시즌부터")
        }
    }

    @Test
    fun `터무니없는 제안은 인내심 두 칸 · 바닥나면 결렬`() {
        val session = newGame()
        val player = session.extensionCandidates().first()
        val terms = session.extensionTerms(player.id)
        val first = session.proposeExtension(player.id, terms.demand * 0.3, terms.years)
        assertEquals(FaTalkOutcome.REJECTED, first.outcome, first.message)
        assertEquals(session.extensionMaxTalks - 2, first.talksLeft)
        var last = first
        repeat(3) { last = session.proposeExtension(player.id, terms.demand * 0.3, terms.years) }
        assertEquals(FaTalkOutcome.BROKEN_OFF, last.outcome, last.message)
    }

    @Test
    fun `지난 시즌 기록이 없어도 올 시즌 페이스로 옵션 가능성을 본다`() {
        val session = newGame()
        repeat(8) { session.advanceWeek(delegate = true) }
        val regular = session.roster(baseballgm.model.RosterLevel.FIRST_TEAM).first { session.batting(it.id).plateAppearances >= 150 }
        val (label, _) = session.extensionOptionOutlook(regular, baseballgm.market.OptionKind.PLATE_APPEARANCES)
        assertTrue(label.startsWith("지난 시즌") || label.startsWith("올 시즌 페이스"), label)
    }
}
