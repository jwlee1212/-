package baseballgm.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 저장소에 들어 있는 실제 `config/balance.json`, `data/teams.json` 을 읽는 테스트. */
class ProjectDataTest {

    @Test
    fun `balance json 을 읽고 주요 값을 꺼낸다`() {
        val balance = ProjectFiles.loadBalanceConfig()

        assertEquals(1, balance.int("ratingScale.min"))
        assertEquals(100, balance.int("ratingScale.max"))
        assertEquals(50, balance.int("ratingScale.leagueAverage"))

        // 확인된 규정(CLAUDE.md §8): 정규시즌 연장 11회 + 무승부
        assertEquals(11, balance.int("gameRules.regularSeason.maxInnings"))
        assertTrue(balance.boolean("gameRules.regularSeason.tiesAllowed"))
        assertEquals(15, balance.int("gameRules.postseason.maxInnings"))

        // 능력치 앵커표는 능력치가 오를수록 삼진 비율이 내려가야 한다
        val contactToK = balance.numericMap("ratingTables.batterContactToK")
        assertTrue(contactToK.size >= 2)
        assertEquals(contactToK.values.sortedDescending(), contactToK.values.toList())

        val battingAverage = balance.doubleRange("leagueTargets.battingAverage")
        assertTrue(battingAverage.start > 0.2 && battingAverage.endInclusive < 0.35)
    }

    @Test
    fun `확인이 필요한 규정 섹션이 표시되어 있다`() {
        val balance = ProjectFiles.loadBalanceConfig()
        val needVerify = balance.sectionsNeedingVerification()
        // CLAUDE.md §8 의 임시값 섹션들. 규정이 확정되면 status 를 지우고 이 목록에서 뺀다.
        assertTrue(needVerify.containsAll(listOf("roster", "draft", "military", "softCap")), "현재: $needVerify")
    }

    @Test
    fun `teams json 을 읽고 10개 구단을 만든다`() {
        val templates = ProjectFiles.loadTeamTemplates()

        assertEquals(2026, templates.season)
        assertEquals(TeamTemplates.TEAM_COUNT, templates.teams.size)
        assertEquals(emptyList(), templates.validate())

        val knights = templates.byId("DSK")
        assertEquals("대성 나이츠", knights.name)
        assertEquals("strong", knights.tier)
        assertEquals(10, knights.draftPick)
        assertEquals(0.15, knights.generationHints.double("earlyGrowthBias"))
        assertTrue(knights.generationHints.boolean("longContractsHeavy"))
    }

    @Test
    fun `팀마다 다른 생성 힌트를 선택적으로 읽는다`() {
        val templates = ProjectFiles.loadTeamTemplates()

        val royals = templates.byId("SWR")
        assertTrue(royals.recommendedForTutorial)
        assertTrue(royals.generationHints.booleanOr("balanced", false))

        val comets = templates.byId("MRC")
        assertEquals("hardest", comets.difficulty)
        // 없는 힌트는 조용히 null
        assertEquals(null, comets.generationHints.doubleOrNull("earlyGrowthBias"))

        val machines = templates.byId("CWM")
        val bonus = assertNotNull(machines.foreignScoutingBonus)
        assertEquals(5, bonus.ratingBonus)

        // 모기업 미정 구단은 null 로 남아 있다 (CLAUDE.md §8, 유저 확인 필요)
        assertTrue(templates.teams.any { it.parentCompany == null })
    }

    @Test
    fun `연봉 총액과 소프트캡이 같은 단위다`() {
        val templates = ProjectFiles.loadTeamTemplates()
        val balance = ProjectFiles.loadBalanceConfig()

        assertEquals(templates.salaryCap, balance.int("softCap.cap"))
        assertTrue(templates.teams.all { it.payroll < templates.salaryCap * 2 })
    }
}
