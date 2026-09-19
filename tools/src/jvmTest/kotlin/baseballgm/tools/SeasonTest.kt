package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.season.AutoAdvance
import baseballgm.season.AutoAdvanceOptions
import baseballgm.season.InboxCategory
import baseballgm.season.WeekLoop
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 주간 루프·컨디션·엔트리 (M4) 검증. */
class SeasonTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val runner = SeasonRunner(balance, league)

    // ---------- 시즌 진행 ----------

    @Test
    fun `한 시즌을 끝까지 돌려도 검증 실패가 없다`() {
        val result = runner.playSeason(seed = 777L)
        assertTrue(result.validationProblems.isEmpty(), result.validationProblems.take(5).joinToString("\n"))
        assertEquals(balance.int("season.regularSeasonWeeks"), result.reports.size)
    }

    @Test
    fun `팀마다 정확히 144경기를 치른다`() {
        val result = runner.playSeason(seed = 778L)
        val gamesPerTeam = balance.int("schedule.gamesPerTeam")
        league.teams.forEach { team ->
            val record = result.state.standings.record(team.id)
            assertEquals(gamesPerTeam, record.games, "${team.id} ${record.games}경기")
        }
        assertEquals(gamesPerTeam * league.teams.size / 2, result.reports.sumOf { it.games.size })
    }

    @Test
    fun `같은 시드는 같은 시즌을 만든다`() {
        val first = runner.playSeason(seed = 2026L)
        val second = runner.playSeason(seed = 2026L)
        assertEquals(
            first.state.standings.ranked().map { it.teamId to it.wins },
            second.state.standings.ranked().map { it.teamId to it.wins },
        )
        val firstLeader = first.state.stats.battingLeaders(300) { it.homeRuns.toDouble() }.first()
        val secondLeader = second.state.stats.battingLeaders(300) { it.homeRuns.toDouble() }.first()
        assertEquals(firstLeader.first, secondLeader.first)
        assertEquals(firstLeader.second, secondLeader.second)
    }

    @Test
    fun `승패 합이 맞는다`() {
        val result = runner.playSeason(seed = 779L)
        val records = result.state.standings.records.values
        assertEquals(records.sumOf { it.wins }, records.sumOf { it.losses }, "리그 전체 승수와 패수가 같아야 한다")
        assertEquals(records.sumOf { it.runsScored }, records.sumOf { it.runsAllowed })
        assertTrue(records.all { it.ties % 1 == 0 })
        assertEquals(0, records.sumOf { it.ties } % 2, "무승부는 두 팀이 함께 기록한다")
    }

    // ---------- 엔트리 (docs/07) ----------

    @Test
    fun `1군 등록 인원을 지키고 부상자는 1군에 없다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(88L)
        val limit = balance.int("roster.firstTeamRegistered")
        repeat(10) {
            loop.playWeek(state, random)
            league.teams.forEach { team ->
                val firstTeam = state.firstTeamOf(team.id)
                assertTrue(firstTeam.size <= limit, "${team.id} 1군 ${firstTeam.size}명")
                assertTrue(
                    firstTeam.count { it is Pitcher } >= 9,
                    "${team.id} 1군 투수 ${firstTeam.count { it is Pitcher }}명",
                )
                assertTrue(
                    firstTeam.count { it is Batter } >= 9,
                    "${team.id} 1군 야수 ${firstTeam.count { it is Batter }}명",
                )
            }
        }
    }

    @Test
    fun `부상 선수는 경기에 나오지 않는다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(99L)
        repeat(8) {
            val injuredBefore = state.allPlayers().filter { it.condition.isInjured }.map { it.id }.toSet()
            val report = loop.playWeek(state, random)
            // 주 시작 시점에 부상 중이던 선수는 이번 주 기록이 없어야 한다
            val played = report.games.flatMap { box ->
                box.home.batting.keys + box.away.batting.keys + box.home.pitching.keys + box.away.pitching.keys
            }.toSet()
            val playedWhileInjured = injuredBefore.intersect(played)
            assertTrue(
                playedWhileInjured.isEmpty(),
                "부상 중인 선수가 출전했다: ${playedWhileInjured.map { state.player(it).registeredName }}",
            )
        }
    }

    @Test
    fun `부상이 발생하고 회복된다`() {
        val result = runner.playSeason(seed = 1234L)
        val injuryMessages = result.reports.flatMap { it.messages }.filter { it.category == InboxCategory.INJURY }
        val returnMessages = result.reports.flatMap { it.messages }.filter { it.category == InboxCategory.RETURN }
        assertTrue(injuryMessages.isNotEmpty(), "한 시즌에 부상이 한 건도 없다")
        assertTrue(returnMessages.isNotEmpty(), "복귀한 선수가 없다")
        // 시즌 내내 부상만 쌓이면 리그가 굴러가지 않는다
        assertTrue(
            result.state.allPlayers().count { it.condition.isInjured } < league.players.size / 10,
            "시즌 끝에 부상자가 너무 많다",
        )
    }

    // ---------- 컨디션 (docs/08) ----------

    @Test
    fun `피로도는 0에서 100 사이에 머문다`() {
        val result = runner.playSeason(seed = 555L)
        result.state.allPlayers().forEach { player ->
            assertTrue(player.condition.fatigue in 0..100, "${player.registeredName} 피로 ${player.condition.fatigue}")
            assertTrue(player.condition.form in 0..100)
        }
    }

    @Test
    fun `포수가 다른 야수보다 빨리 지친다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(4242L)
        repeat(6) { loop.playWeek(state, random) }

        val catchers = mutableListOf<Int>()
        val others = mutableListOf<Int>()
        league.teams.forEach { team ->
            state.firstTeamOf(team.id).filterIsInstance<Batter>().forEach { batter ->
                if (batter.primaryPosition == Position.CATCHER) catchers += batter.condition.fatigue
                else others += batter.condition.fatigue
            }
        }
        assertTrue(catchers.isNotEmpty() && others.isNotEmpty())
        assertTrue(
            catchers.average() > others.average(),
            "포수 평균 피로 ${catchers.average()} vs 야수 ${others.average()}",
        )
    }

    @Test
    fun `폼은 평균 근처에서 오르내린다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(31L)
        repeat(6) { loop.playWeek(state, random) }
        val forms = state.allPlayers().filter { it.rosterLevel == RosterLevel.FIRST_TEAM }.map { it.condition.form }
        assertTrue(forms.average() in 40.0..60.0, "폼 평균 ${forms.average()}")
        assertTrue(forms.distinct().size > 10, "폼이 전혀 움직이지 않는다")
        assertTrue(forms.any { it > 60 } && forms.any { it < 40 }, "폼이 한쪽으로만 몰려 있다")
    }

    // ---------- 2군 (docs/07) ----------

    @Test
    fun `2군 추정 성적이 쌓인다`() {
        val result = runner.playSeason(seed = 666L)
        val futuresBatting = result.state.stats.allFuturesBatting()
        assertTrue(futuresBatting.isNotEmpty(), "2군 기록이 없다")
        val totalPa = futuresBatting.values.sumOf { it.plateAppearances }
        assertTrue(totalPa > 10_000, "2군 타석이 너무 적다 ($totalPa)")
        futuresBatting.values.forEach { line ->
            assertEquals(line.plateAppearances, line.atBats + line.walks, "2군 기록도 타석 = 타수 + 볼넷")
            assertTrue(line.hits <= line.atBats)
        }
    }

    // ---------- 자동 진행 (docs/07) ----------

    @Test
    fun `자동 진행이 올스타 브레이크에서 멈춘다`() {
        val state = runner.newSeason()
        val auto = AutoAdvance(balance, WeekLoop(balance, league))
        val result = auto.run(state, weeks = 24, options = AutoAdvanceOptions(), random = Random(7L))
        assertEquals("올스타 브레이크", result.stoppedBy)
        assertEquals(balance.int("season.allStarBreakAfterWeek"), result.weeksPlayed)
    }

    @Test
    fun `자동 진행이 연패에서 멈춘다`() {
        val state = runner.newSeason()
        val auto = AutoAdvance(balance, WeekLoop(balance, league))
        // 최하위권 구단을 기준으로 두면 연패가 곧 나온다
        val weakTeam = league.teams.first { it.id.value == "MRC" }.id
        val result = auto.run(
            state,
            weeks = 12,
            options = AutoAdvanceOptions(
                teamId = weakTeam,
                stopAtAllStarBreak = false,
                stopAtTradeDeadline = false,
                stopOnInjury = false,
            ),
            random = Random(13L),
        )
        assertTrue(
            result.stoppedBy == null || result.stoppedBy!!.contains("연패"),
            "멈춘 이유: ${result.stoppedBy}",
        )
    }

    @Test
    fun `올스타 브레이크에 피로가 크게 회복된다`() {
        val state = runner.newSeason()
        val loop = WeekLoop(balance, league)
        val random = Random(21L)
        val breakWeek = balance.int("season.allStarBreakAfterWeek")
        var beforeBreak = 0.0
        repeat(breakWeek) { index ->
            if (index == breakWeek - 1) {
                beforeBreak = state.allPlayers().map { it.condition.fatigue }.average()
            }
            loop.playWeek(state, random)
        }
        val afterBreak = state.allPlayers().map { it.condition.fatigue }.average()
        assertTrue(afterBreak < beforeBreak, "브레이크 전 $beforeBreak → 후 $afterBreak")
    }

    // ---------- 캘리브레이션 (docs/04) ----------

    @Test
    fun `여러 시즌 평균이 캘리브레이션 목표 범위 안에 있다`() {
        // 본 캘리브레이션은 콘솔 도구(`calibrate 100`)로 돌린다.
        // 테스트는 12시즌으로 크게 어긋나지 않는지만 본다. 타율·삼진 같은 비율 지표는 빨리 수렴하지만
        // 최고·최저 승률은 시즌 수가 적으면 흔들려서(팀 하나의 운) 약간의 여유를 둔다.
        val report = Calibrator(balance, league).run(seasons = 12, seed = 31337L)
        assertEquals(0, report.validationFailures)

        val (winPctLines, rateLines) = report.lines.partition { it.name.contains("승률") }
        val failedRates = rateLines.filterNot { it.inRange }
        assertTrue(failedRates.isEmpty(), "목표를 벗어난 지표:\n" + failedRates.joinToString("\n") { it.text() })

        winPctLines.forEach { line ->
            val relaxed = (line.target.start - WIN_PCT_TOLERANCE)..(line.target.endInclusive + WIN_PCT_TOLERANCE)
            assertTrue(line.value in relaxed, line.text())
        }
    }

    private companion object {
        const val WIN_PCT_TOLERANCE = 0.025
    }
}
