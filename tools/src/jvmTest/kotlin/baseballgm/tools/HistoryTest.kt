package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.io.SaveGame
import baseballgm.io.SaveGameCodec
import baseballgm.league.AwardKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 리그 역사 — 통산 기록·시즌 결과·시상 (2026-10-01, 진단 3번). */
class HistoryTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))

    @Test
    fun `시즌마다 결과와 시상이 쌓이고 통산 기록이 이어진다`() {
        val seasons = 4
        val summaries = MultiSeasonRunner(balance, league).run(seasons = seasons, seed = 4040L)
        val history = assertNotNull(summaries.last().nextLeague).history

        assertEquals((0 until seasons).map { league.season + it }, history.seasons.map { it.season })
        history.seasons.forEach { record ->
            assertNotNull(record.champion, "${record.season} 우승팀이 없다")
            val kinds = record.awards.map { it.kind }
            assertTrue(AwardKind.MVP in kinds, "${record.season} MVP 가 없다")
            assertTrue(AwardKind.BATTING in kinds && AwardKind.ERA in kinds, "${record.season} 타격왕·평균자책 1위가 없다")
            assertEquals(
                1 + 1 + 1 + 1 + 1 + 1 + 3 + 1,
                record.awards.count { it.kind == AwardKind.GOLDEN_GLOVE },
                "${record.season} 골든글러브는 10자리: " + record.awards.filter { it.kind == AwardKind.GOLDEN_GLOVE }.map { it.position },
            )
            assertEquals(league.teams.size, record.standings.size)
        }

        // 리그에 남은 선수만 시즌별 기록을, 떠난 선수는 통산 한 줄을 갖는다.
        // 떠났다 돌아온 선수는 양쪽에 있고, 통산(totalOf)은 둘을 한 번씩 더한다
        val alive = summaries.last().nextLeague!!.players.map { it.id }.toSet()
        assertTrue(history.careers.keys.all { it in alive })
        history.careers.keys.filter { it in history.retired }.forEach { id ->
            val total = assertNotNull(history.totalOf(id))
            val expected = (history.retired.getValue(id).bat?.pa ?: 0) + history.careerOf(id).sumOf { it.bat?.pa ?: 0 }
            assertEquals(expected, total.bat?.pa ?: 0)
        }
        assertTrue(history.retired.isNotEmpty(), "은퇴·방출 선수의 통산이 없다")
        // 여러 시즌 뛴 선수가 있다 — 시즌 순서대로
        val veteran = history.careers.values.maxBy { it.size }
        assertEquals(seasons, veteran.size)
        assertEquals(veteran.sortedBy { it.season }, veteran)
    }

    @Test
    fun `여러 시즌 뒤에도 세이브가 폰 웹 저장 한도 안에 든다`() {
        val summaries = MultiSeasonRunner(balance, league).run(seasons = 6, seed = 5050L)
        val next = assertNotNull(summaries.last().nextLeague)
        val size = SaveGameCodec.encode(SaveGame(userTeam = next.teams.first().id, league = next)).length
        println("6시즌 뒤 개막 세이브: ${size / 1024}KB (역사 포함)")
        assertTrue(size < 3_000_000, "세이브가 ${size}자")
    }
}
