package baseballgm.app

import baseballgm.app.screen.playerListSections
import baseballgm.app.ui.ListCell
import baseballgm.app.ui.MAX_STATUS_BADGES
import baseballgm.io.LeagueLoader
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 선수 목록·홈 목표 카드 (2026-10-02, 레퍼런스 리팩토링).
 */
class PlayerListTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(
        ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)),
    )

    private fun session() = GameSession(balance, league, TeamId("SWR"), seed = 4242L).also {
        repeat(3) { _ -> it.advanceWeek(delegate = true) }
    }

    private fun ratings(sections: List<baseballgm.app.ui.PlayerListSection>) =
        sections.flatMap { it.rows }.flatMap { it.cells }.filterIsInstance<ListCell.Rating>()

    @Test
    fun `우리 선수는 정확한 숫자 - 스카우트 값과 같다`() {
        val session = session()
        val players = session.roster(RosterLevel.FIRST_TEAM)
        val sections = playerListSections(session, players, RosterLevel.FIRST_TEAM)
        assertTrue(ratings(sections).all { it.range.isExact })
        sections.flatMap { it.rows }.forEach { row ->
            val overall = row.cells.first() as ListCell.Rating
            assertEquals(session.scout(session.player(row.id)).overall, overall.range)
        }
    }

    @Test
    fun `타 팀 선수는 ScoutingView 범위만 - 정확한 숫자가 하나도 없다 (불변 원칙 4)`() {
        val session = session()
        val other = session.league.teams.first { it.id != session.userTeamId }.id
        val players = session.roster(RosterLevel.FIRST_TEAM, other)
        val sections = playerListSections(session, players, RosterLevel.FIRST_TEAM)
        val cells = ratings(sections)
        assertTrue(cells.isNotEmpty())
        assertTrue(cells.none { it.range.isExact }, "타 팀 능력치가 정확한 값으로 나왔다")
        sections.flatMap { it.rows }.forEach { row ->
            val scouted = session.scout(session.player(row.id))
            assertEquals(scouted.overall, (row.cells.first() as ListCell.Rating).range)
        }
        // 피로(우리 팀만 아는 정보) 열이 없다
        assertTrue(sections.none { section -> section.columns.any { it.header == "피로" } })
    }

    @Test
    fun `한 줄의 상태 배지는 최대 두 개`() {
        val session = session()
        val sections = playerListSections(session, session.roster(RosterLevel.FIRST_TEAM), RosterLevel.FIRST_TEAM)
        assertTrue(sections.flatMap { it.rows }.all { it.tag.badges.size <= MAX_STATUS_BADGES })
    }

    @Test
    fun `목록 등번호는 엔진 등번호 그대로이고 팀 안에서 겹치지 않는다`() {
        val session = session()
        val players = session.roster(RosterLevel.FIRST_TEAM) + session.roster(RosterLevel.FUTURES)
        val rows = playerListSections(session, players, RosterLevel.FIRST_TEAM).flatMap { it.rows }
        rows.forEach { assertEquals(session.player(it.id).uniformNumber, it.tag.number) }
        assertEquals(rows.size, rows.map { it.tag.number }.toSet().size)
    }

    @Test
    fun `목표 진행률은 시즌이 지날수록 늘고 순위 목표는 지금 순위를 말한다`() {
        val session = GameSession(balance, league, TeamId("SWR"), seed = 4242L)
        val start = GoalProgress.of(session)
        assertEquals(0f, start.seasonFraction)
        repeat(4) { session.advanceWeek(delegate = true) }
        val later = GoalProgress.of(session)
        assertTrue(later.seasonFraction > start.seasonFraction)
        assertTrue(later.detail.isNotBlank())
    }
}
