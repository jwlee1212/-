package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.model.MilitaryStatus
import baseballgm.model.RosterLevel
import baseballgm.season.Offseason
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 시즌 넘김(성장·노화·은퇴·신인)과 장기 밸런스 (M5) 검증. */
class SeasonProgressionTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val strength = StrengthCalculator(balance)

    private fun playAndAdvance(seed: Long): Pair<baseballgm.league.League, baseballgm.season.OffseasonReport> {
        val result = SeasonRunner(balance, league).playSeason(seed)
        return Offseason(balance, strength).run(
            state = result.state,
            random = Random(seed),
            rookieSupplier = RookieFactory(balance, strength, league),
            prospectSupplier = ProspectFactory(
                balance = balance,
                strength = strength,
                seed = seed,
                startingIdNumber = nextPlayerIdNumber(league.players, league.draftPool.prospects),
            ),
        )
    }

    @Test
    fun `스토브리그를 지나면 다음 시즌 리그가 나온다`() {
        val (next, report) = playAndAdvance(101L)
        assertEquals(league.season + 1, next.season)
        assertEquals(league.season + 1, next.schedule.season)
        assertEquals(league.teams.size, next.teams.size)
        assertTrue(report.retired.isNotEmpty(), "은퇴한 선수가 없다")
        assertTrue(report.drafted.isNotEmpty(), "드래프트 입단 선수가 없다")
        assertEquals(league.draftPool.season + 1, next.draftPool.season, "다음 시즌 드래프트 풀이 없다")
    }

    @Test
    fun `은퇴 선수는 사라지고 신인이 자리를 채운다`() {
        val (next, report) = playAndAdvance(102L)
        val remaining = next.players.map { it.id }.toSet()
        assertTrue(report.retired.none { it in remaining }, "은퇴한 선수가 아직 리그에 있다")
        assertTrue(report.rookies.all { it in remaining })
        assertTrue(report.drafted.all { it in remaining }, "지명 선수가 입단하지 않았다")
        // 방출 선수는 리그를 떠나거나 FA 시장을 거쳐 **다른 팀**으로 간다 (docs/11)
        report.released.forEach { id ->
            val player = next.players.firstOrNull { it.id == id } ?: return@forEach
            val before = league.player(id).teamId
            assertTrue(player.teamId != before, "${player.name} 이 방출됐는데 원래 팀에 남아 있다")
        }
        // 팀마다 선수단 규모가 유지된다
        next.teams.forEach { team ->
            val size = next.playersOf(team.id).size
            assertTrue(
                abs(size - balance.int("rosterLimits.targetRosterSize")) <= 4,
                "${team.id} 선수 ${size}명",
            )
        }
        assertEquals(next.players.size, next.players.map { it.id }.toSet().size, "선수 id 가 겹친다")
    }

    @Test
    fun `신인은 어리고 2군에서 시작한다`() {
        val (next, report) = playAndAdvance(103L)
        (report.rookies + report.drafted).forEach { id ->
            val rookie = next.player(id)
            assertTrue(rookie.ageIn(next.season) in 18..24, "${rookie.name} ${rookie.ageIn(next.season)}세")
            assertEquals(RosterLevel.FUTURES, rookie.rosterLevel)
            assertEquals(next.season, rookie.debutSeason)
        }
    }

    @Test
    fun `시즌이 넘어가면 계약 연수가 줄고 컨디션이 초기화된다`() {
        val (next, _) = playAndAdvance(104L)
        next.players.forEach { player ->
            assertEquals(0, player.condition.fatigue, "${player.name} 피로가 남아 있다")
            assertEquals(50, player.condition.form)
            assertTrue(player.contract.yearsRemaining >= 1, "${player.name} 계약 연수 ${player.contract.yearsRemaining}")
        }
        // 긴 부상은 다음 시즌으로 이어질 수 있지만 대부분은 나아 있어야 한다
        assertTrue(next.players.count { it.condition.isInjured } < next.players.size / 20)
    }

    @Test
    fun `복무를 마친 선수가 돌아오고 기한이 된 선수는 입대한다`() {
        val (next, report) = playAndAdvance(105L)
        report.discharged.forEach {
            assertEquals(MilitaryStatus.Completed, next.player(it).military)
        }
        report.enlisted.forEach {
            val military = next.player(it).military
            assertTrue(military is MilitaryStatus.Serving, "${next.player(it).name} 가 입대하지 않았다")
        }
        assertTrue(report.enlisted.isNotEmpty() || report.discharged.isNotEmpty())
    }

    @Test
    fun `성장과 노화가 실제로 일어난다`() {
        val result = SeasonRunner(balance, league).playSeason(106L)
        val before = league.players.associateBy { it.id }
        val (next, _) = Offseason(balance, strength).run(
            state = result.state,
            random = Random(106L),
            rookieSupplier = RookieFactory(balance, strength, league),
        )
        val young = next.players.filter { it.ageIn(next.season) in 20..23 && before.containsKey(it.id) }
        val old = next.players.filter { it.ageIn(next.season) >= 35 && before.containsKey(it.id) }
        assertTrue(young.isNotEmpty() && old.isNotEmpty())

        val youngGrew = young.count { player ->
            strength.overallOf(player) > strength.overallOf(before.getValue(player.id))
        }
        val oldDeclined = old.count { player ->
            strength.overallOf(player) < strength.overallOf(before.getValue(player.id))
        }
        assertTrue(youngGrew > young.size / 2, "젊은 선수 ${young.size}명 중 ${youngGrew}명만 성장했다")
        assertTrue(oldDeclined > old.size / 2, "노장 ${old.size}명 중 ${oldDeclined}명만 하락했다")
    }

    @Test
    fun `여러 시즌을 이어서 돌려도 리그가 무너지지 않는다`() {
        // 30시즌 본 테스트는 콘솔 도구(`longterm 30`)로 돌린다. 여기서는 5시즌으로 뼈대를 확인한다
        val summaries = MultiSeasonRunner(balance, league).run(seasons = 5, seed = 2077L)
        assertEquals(5, summaries.size)
        assertTrue(summaries.all { it.validationFailures == 0 }, "검증 실패가 있다")
        var previous = league.players.size
        summaries.forEach { summary ->
            assertTrue(
                abs(summary.starterAverage - balance.double("ratingScale.starterAverage")) <= 4.0,
                "${summary.season} 주전 평균 ${summary.starterAverage}",
            )
            assertTrue(summary.playerCount in 540..620, "${summary.season} 선수 ${summary.playerCount}명")
            assertTrue(summary.offseason.drafted.isNotEmpty(), "${summary.season} 드래프트 입단이 없다")
            assertTrue(summary.offseason.freeAgents.isNotEmpty(), "${summary.season} FA 가 한 명도 안 나왔다")
            // 유입(드래프트·육성·보충)이 유출(은퇴·방출)과 균형을 이뤄 선수 수가 유지돼야 한다.
            // 방출 선수 일부는 FA 시장을 거쳐 리그에 남으므로 정확히 같지는 않다
            val inflow = with(summary.offseason) { drafted.size + developmentSignings.size + rookies.size }
            val outflow = with(summary.offseason) { retired.size + released.size }
            assertTrue(
                summary.playerCount <= previous + inflow - outflow + summary.offseason.released.size,
                "${summary.season} 선수 수가 맞지 않는다 (${summary.playerCount})",
            )
            previous = summary.playerCount
        }
        // 나이 분포가 한쪽으로 쏠리지 않는다
        assertTrue(summaries.last().averageAge in 24.0..30.0, "평균 나이 ${summaries.last().averageAge}")
    }

    @Test
    fun `같은 시드로 여러 시즌을 돌리면 같은 결과가 나온다`() {
        val first = MultiSeasonRunner(balance, league).run(seasons = 3, seed = 555L)
        val second = MultiSeasonRunner(balance, league).run(seasons = 3, seed = 555L)
        assertEquals(
            first.map { it.playerCount to it.offseason.retired.size },
            second.map { it.playerCount to it.offseason.retired.size },
        )
        assertEquals(first.map { it.starterAverage }, second.map { it.starterAverage })
    }
}
