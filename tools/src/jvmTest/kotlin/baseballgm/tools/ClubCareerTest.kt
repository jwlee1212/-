package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.management.Achievements
import baseballgm.management.CareerRecord
import baseballgm.model.TeamId
import baseballgm.season.Offseason
import baseballgm.season.PostseasonRound
import baseballgm.season.SeasonState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 포스트시즌·경영·커리어가 실제 리그에서 도는지 확인한다 (M9). */
class ClubCareerTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val baseLeague = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val strength = StrengthCalculator(balance)
    private val userTeam = baseLeague.teams.first { it.id.value == "SWR" }.id

    private fun withCareer(league: League = baseLeague, team: TeamId = userTeam) = league.copy(
        management = league.management.copy(
            userTeam = team,
            career = CareerRecord("테스트 단장", balance.int("career.startReputation")),
            seasonGoals = league.teams.associate { it.id to it.ownerGoal },
        ),
    )

    private fun offseason(league: League, state: SeasonState, seed: Long) = Offseason(balance, strength).run(
        state = state,
        random = Random(seed),
        rookieSupplier = RookieFactory(balance, strength, league),
        prospectSupplier = ProspectFactory(
            balance, strength, seed, nextPlayerIdNumber(league.players, league.draftPool.prospects),
        ),
        foreignSupplier = ForeignFactory(
            balance, strength, seed, nextPlayerIdNumber(league.players, league.draftPool.prospects) + 500,
        ),
    )

    // ---------- 포스트시즌 ----------

    @Test
    fun `5강이 올라가며 우승팀이 나온다`() {
        val result = assertNotNull(SeasonRunner(balance, baseLeague).playSeason(91L, validate = false).postseason)
        val ranked = result.participants

        assertEquals(5, ranked.size, "5강이 아니다")
        assertEquals(4, result.series.size, "와일드카드·준PO·PO·KS 네 시리즈여야 한다")
        assertEquals(
            listOf(
                PostseasonRound.WILDCARD,
                PostseasonRound.SEMI_PLAYOFF,
                PostseasonRound.PLAYOFF,
                PostseasonRound.KOREAN_SERIES,
            ),
            result.series.map { it.round },
        )
        // 시드 배정: 1위가 한국시리즈, 2위가 플레이오프, 3위가 준PO, 4·5위가 와일드카드
        assertEquals(ranked[0], result.series.last().higherSeed)
        assertEquals(ranked[3], result.series.first().higherSeed)
        assertEquals(ranked[4], result.series.first().lowerSeed)
        assertTrue(result.champion == result.series.last().winner)
        assertTrue(result.champion != result.runnerUp)
    }

    @Test
    fun `와일드카드는 4위가 1승을 안고 시작한다`() {
        // 여러 시드를 돌려 4위가 진출한 경우와 5위가 올라간 경우를 모두 확인한다
        val results = (91L..100L).map { SeasonRunner(balance, baseLeague).playSeason(it, validate = false).postseason!! }
        results.forEach { result ->
            val wildcard = result.series.first()
            assertTrue(wildcard.games <= 3, "와일드카드가 ${wildcard.games}경기나 치러졌다")
            assertTrue(wildcard.higherWins >= 1, "4위가 1승 어드밴티지를 못 받았다")
            if (wildcard.winner == wildcard.lowerSeed) {
                // 5위가 올라가려면 2연승이 필요하다
                assertTrue(wildcard.lowerWins >= 2, "5위가 ${wildcard.lowerWins}승으로 올라갔다")
            }
        }
        assertTrue(results.any { it.series.first().winner == it.series.first().higherSeed }, "4위가 한 번도 안 올라갔다")
    }

    @Test
    fun `시리즈는 선승제로 끝난다`() {
        val result = SeasonRunner(balance, baseLeague).playSeason(92L, validate = false).postseason!!
        result.series.drop(1).forEach { series ->
            val needed = if (series.round == PostseasonRound.KOREAN_SERIES) 4 else 3
            val winnerWins = if (series.winner == series.higherSeed) series.higherWins else series.lowerWins
            assertEquals(needed, winnerWins, "${series.round.label} 이 ${winnerWins}승으로 끝났다")
            // 무승부가 나오면 경기가 늘어난다 (docs/14)
            assertTrue(series.games >= needed && series.games <= needed * 2 + 2)
        }
    }

    @Test
    fun `포스트시즌 기록은 정규시즌 기록과 섞이지 않는다`() {
        val runner = SeasonRunner(balance, baseLeague)
        val state = runner.newSeason()
        val withoutPs = runner.playSeason(93L, validate = false, state = state, playPostseason = false)
        val before = withoutPs.state.stats.allBatting().values.sumOf { it.total.plateAppearances }

        baseballgm.season.Postseason(balance, baseLeague).run(state, Random(93L))
        val after = state.stats.allBatting().values.sumOf { it.total.plateAppearances }
        assertEquals(before, after, "포스트시즌 타석이 정규시즌 기록에 섞였다")
    }

    // ---------- 경영 ----------

    @Test
    fun `시즌이 끝나면 재정 결산이 나온다`() {
        val league = withCareer()
        val result = SeasonRunner(balance, league).playSeason(94L, validate = false)
        val (next, report) = offseason(league, result.state, 94L)
        val review = assertNotNull(report.review, "경영 결산이 없다")

        assertEquals(league.teams.size, review.teams.size)
        review.teams.forEach { team ->
            val finance = team.finance
            assertTrue(finance.revenue.total > 0.0, "${team.teamId} 수입이 0")
            assertTrue(finance.expenses.payroll > 0.0, "${team.teamId} 연봉이 0")
            assertTrue(finance.attendanceRate in 0.45..1.30)
            // 결산은 그 자체로 앞뒤가 맞아야 한다.
            // 자금은 음수가 되지 않고, 부족분은 모기업이 메운다 (coveredByOwner)
            assertEquals(
                maxOf(0.0, finance.fundsBefore + finance.revenue.total - finance.expenses.total),
                finance.fundsAfter,
                0.02,
            )
            if (finance.fundsAfter <= 0.0) assertTrue(finance.coveredByOwner >= 0.0)
            // 결산 결과가 다음 시즌 구단 데이터에 반영된다.
            // 자금은 결산 뒤에 열리는 FA 시장(계약금·보상금)에서 더 움직이므로 정확히 같지는 않다
            assertTrue(next.team(team.teamId).operatingFunds >= 0.0)
            assertEquals(team.fanAfter, next.team(team.teamId).fanSupport)
            assertEquals(team.nextSeasonTrust, next.team(team.teamId).ownerTrust)
        }
    }

    @Test
    fun `성적이 좋으면 팬심과 신뢰도가 오른다`() {
        val league = withCareer()
        val result = SeasonRunner(balance, league).playSeason(95L, validate = false)
        val (_, report) = offseason(league, result.state, 95L)
        val review = report.review!!

        val best = review.teams.maxBy { result.state.standings.record(it.teamId).winPct }
        val worst = review.teams.minBy { result.state.standings.record(it.teamId).winPct }
        assertTrue(best.fanAfter > worst.fanAfter, "1위 팬심 ${best.fanAfter} vs 꼴찌 ${worst.fanAfter}")
        assertTrue(
            best.trust.trustAfter > worst.trust.trustAfter,
            "1위 신뢰도 ${best.trust.trustAfter} vs 꼴찌 ${worst.trust.trustAfter}",
        )
    }

    @Test
    fun `스태프 계약이 만료되면 시장에 나오고 AI 가 데려간다`() {
        var league = withCareer()
        var hires = 0
        // 계약 연수가 1~3년이라 세 시즌을 돌리면 만료가 생긴다
        repeat(3) { index ->
            val result = SeasonRunner(balance, league).playSeason(96L + index, validate = false)
            val (next, report) = offseason(league, result.state, 96L + index)
            hires += report.staffHires.size
            league = next
        }
        assertTrue(hires > 0, "세 시즌 동안 스태프 고용이 한 건도 없다")
        league.teams.forEach { team ->
            assertTrue(league.coachesOf(team.id).size <= 4, "${team.id} 코치가 너무 많다")
        }
    }

    // ---------- 커리어 ----------

    @Test
    fun `커리어 기록이 시즌마다 쌓인다`() {
        var league = withCareer()
        repeat(3) { index ->
            val result = SeasonRunner(balance, league).playSeason(97L + index, validate = false)
            val (next, _) = offseason(league, result.state, 97L + index)
            league = next
        }
        val career = assertNotNull(league.management.career)
        assertEquals(3, career.seasons.size)
        career.seasons.forEach { season ->
            assertTrue(season.line().isNotBlank())
            if (!season.commentary) assertTrue(season.wins + season.losses + season.ties > 0)
        }
        assertTrue(career.summary().contains("평판"))
        assertTrue(Achievements.hallOfFameGrade(career).isNotBlank())
    }

    @Test
    fun `해임되면 자리가 비고 커리어는 이어진다`() {
        // 목표가 가장 가혹한 구단(한국시리즈 진출)을 약팀으로 맡으면 해임까지 간다
        var league = withCareer(team = baseLeague.teams.first { it.id.value == "DJR" }.id)
        var fired = false
        var commentary = false

        repeat(6) { index ->
            val result = SeasonRunner(balance, league).playSeason(80L + index, validate = false)
            val (next, report) = offseason(league, result.state, 80L + index)
            val review = report.review!!
            if (review.userFired) fired = true
            if (next.management.userTeam == null) commentary = true
            league = next
        }
        val career = assertNotNull(league.management.career)
        assertTrue(career.seasons.isNotEmpty())
        if (fired) {
            assertTrue(commentary, "해임됐는데 무직 상태가 아니다")
            assertTrue(
                career.seasons.any { it.commentary } || league.management.userTeam != null,
                "해임 후 커리어가 이어지지 않았다",
            )
        }
    }

    @Test
    fun `업적은 한 번 달성하면 남는다`() {
        var league = withCareer(team = baseLeague.teams.first { it.id.value == "DSK" }.id)
        val seen = mutableSetOf<String>()
        repeat(4) { index ->
            val result = SeasonRunner(balance, league).playSeason(70L + index, validate = false)
            val (next, report) = offseason(league, result.state, 70L + index)
            report.review?.newAchievements?.forEach { achievement ->
                assertTrue(seen.add(achievement.id), "${achievement.label} 업적이 두 번 나왔다")
            }
            league = next
        }
        val career = league.management.career!!
        assertEquals(seen, career.unlockedAchievements.toSet())
    }
}
