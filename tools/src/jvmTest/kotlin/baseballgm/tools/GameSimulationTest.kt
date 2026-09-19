package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.model.Hand
import baseballgm.model.Pitcher
import baseballgm.sim.GameEvent
import baseballgm.sim.HalfInningEnded
import baseballgm.sim.PlateAppearanceCompleted
import baseballgm.stats.BoxScore
import baseballgm.stats.BoxScoreValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 경기 시뮬레이션(M2) 검증. 실제 생성된 리그 데이터로 돌린다. */
class GameSimulationTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(templates.season)))
    private val runner = GameRunner(balance, league)

    // ---------- M2 완료 기준 ----------

    @Test
    fun `1000경기 박스스코어 검증 실패 0건`() {
        val problems = mutableListOf<String>()
        var games = 0
        runner.playSchedule(1000, seed = 20260302L) { played ->
            games++
            val found = BoxScoreValidator.validate(played.box)
            if (found.isNotEmpty() && problems.size < 10) problems += "${played.box.line()} → $found"
        }
        assertEquals(1000, games)
        assertTrue(problems.isEmpty(), "검증 실패:\n" + problems.joinToString("\n"))
    }

    // ---------- 재현성 (불변 원칙 2) ----------

    @Test
    fun `같은 시드는 같은 경기를 만든다`() {
        val game = league.schedule.games.first()
        val first = runner.play(game.home, game.away, rotationIndex = 0, seed = 42L)
        val second = runner.play(game.home, game.away, rotationIndex = 0, seed = 42L)
        assertEquals(first.events, second.events)
        assertEquals(first.box, second.box)
    }

    @Test
    fun `다른 시드는 다른 경기를 만든다`() {
        val game = league.schedule.games.first()
        val first = runner.play(game.home, game.away, rotationIndex = 0, seed = 1L)
        val second = runner.play(game.home, game.away, rotationIndex = 0, seed = 2L)
        assertTrue(first.events != second.events)
    }

    // ---------- 경기 규칙 (docs/05) ----------

    @Test
    fun `연장은 11회까지이고 그때까지 동점이면 무승부다`() {
        val maxInnings = balance.int("gameRules.regularSeason.maxInnings")
        var ties = 0
        var extra = 0
        runner.playSchedule(400, seed = 5L) { played ->
            val box = played.box
            assertTrue(box.innings <= maxInnings, "이닝 ${box.innings}")
            if (box.innings > 9) extra++
            if (box.tie) {
                ties++
                assertEquals(maxInnings, box.innings, "무승부인데 ${box.innings}회에 끝났다")
                assertEquals(box.homeScore, box.awayScore)
            }
        }
        assertTrue(extra > 0, "400경기에 연장이 한 번도 없다")
        assertTrue(ties > 0, "400경기에 무승부가 한 번도 없다")
    }

    @Test
    fun `끝내기는 홈팀이 이기고 9회말이 생략되기도 한다`() {
        var walkOffs = 0
        var skippedBottoms = 0
        runner.playSchedule(400, seed = 11L) { played ->
            val box = played.box
            if (box.walkOff) {
                walkOffs++
                assertTrue(box.homeScore > box.awayScore, "끝내기인데 홈팀이 이기지 않았다")
            }
            // 홈팀이 앞선 채 9회를 마치면 홈팀은 9회말을 치지 않는다
            val homeInnings = box.home.inningRuns.size
            if (!box.walkOff && box.homeScore > box.awayScore && box.innings == 9) {
                val homeHalfInnings = played.events.filterIsInstance<HalfInningEnded>()
                    .count { it.battingTeam == box.home.teamId }
                if (homeHalfInnings == 8) skippedBottoms++
                assertTrue(homeHalfInnings <= homeInnings)
            }
        }
        assertTrue(walkOffs > 0, "400경기에 끝내기가 없다")
        assertTrue(skippedBottoms > 0, "9회말을 생략한 경기가 없다")
    }

    // ---------- 투수 기록 판정 (docs/05) ----------

    @Test
    fun `승리 패전 세이브가 규칙대로 붙는다`() {
        var saves = 0
        runner.playSchedule(400, seed = 3L) { played ->
            val box = played.box
            if (box.tie) {
                assertTrue(box.winningPitcher == null && box.losingPitcher == null && box.savePitcher == null)
                return@playSchedule
            }
            val winnerTeam = if (box.homeScore > box.awayScore) box.home else box.away
            val loserTeam = if (box.homeScore > box.awayScore) box.away else box.home
            assertTrue(winnerTeam.pitching.containsKey(box.winningPitcher), "승리 투수가 이긴 팀 소속이 아니다")
            assertTrue(loserTeam.pitching.containsKey(box.losingPitcher), "패전 투수가 진 팀 소속이 아니다")
            box.savePitcher?.let { save ->
                saves++
                assertTrue(winnerTeam.pitching.containsKey(save), "세이브 투수가 이긴 팀 소속이 아니다")
                assertTrue(save != box.winningPitcher, "승리 투수가 세이브도 가져갔다")
            }
            box.holdPitchers.forEach { hold ->
                assertTrue(winnerTeam.pitching.containsKey(hold))
                assertTrue(hold != box.winningPitcher && hold != box.savePitcher)
            }
        }
        assertTrue(saves > 0, "400경기에 세이브가 하나도 없다")
    }

    @Test
    fun `선발이 5이닝을 못 채우면 승리 투수가 되지 않는다`() {
        runner.playSchedule(400, seed = 9L) { played ->
            val box = played.box
            val winner = box.winningPitcher ?: return@playSchedule
            val team = listOf(box.home, box.away).first { it.pitching.containsKey(winner) }
            val line = team.pitching.getValue(winner).total
            if (line.gamesStarted > 0) {
                assertTrue(line.outs >= 15, "선발이 ${line.inningsText()}이닝만 던지고 승리 투수가 됐다")
            }
        }
    }

    // ---------- 기록 (docs/04 1단계) ----------

    @Test
    fun `타자 타석 합과 투수 상대 타자 합이 같다`() {
        runner.playSchedule(100, seed = 13L) { played ->
            val box = played.box
            assertEquals(
                box.home.battingTotal.plateAppearances,
                box.away.pitchingTotal.battersFaced,
                "홈 타석 수 != 원정 투수 상대 타자 수",
            )
            assertEquals(box.away.battingTotal.plateAppearances, box.home.pitchingTotal.battersFaced)
        }
    }

    @Test
    fun `기록이 좌우로 분리돼 쌓인다`() {
        val game = league.schedule.games.first()
        val played = runner.play(game.home, game.away, rotationIndex = 0, seed = 77L)
        val box = played.box

        // 합계는 좌우 + 분리 불가 기록의 합이다
        box.home.batting.values.forEach { batting ->
            assertEquals(
                batting.vsLeft.plateAppearances + batting.vsRight.plateAppearances + batting.unsplit.plateAppearances,
                batting.total.plateAppearances,
            )
        }
        // 상대 선발이 우완이면 그 이닝 기록은 우투 상대 칸에 들어간다
        val starterId = box.away.pitching.entries.first { it.value.total.gamesStarted > 0 }.key
        val starter = league.player(starterId) as Pitcher
        val facedEvents = played.events.filterIsInstance<PlateAppearanceCompleted>()
            .filter { it.pitcherId == starterId }
        assertTrue(facedEvents.isNotEmpty())
        val expectedColumn = facedEvents.count { it.pitcherHand == starter.throwsWith }
        assertEquals(facedEvents.size, expectedColumn, "선발 투수의 손이 이벤트마다 다르다")

        val byHand = box.home.batting.values.sumOf {
            if (starter.throwsWith == Hand.LEFT) it.vsLeft.plateAppearances else it.vsRight.plateAppearances
        }
        assertTrue(byHand >= facedEvents.size, "상대 선발의 손과 다른 칸에 기록이 쌓였다")
    }

    // ---------- 캘리브레이션 (docs/04) ----------

    @Test
    fun `리그 평균이 목표 범위 안에 있다`() {
        // M4 에서 100시즌으로 다시 맞춘다. 여기서는 1시즌 분량(720경기)으로 크게 벗어나지 않는지만 본다
        val totals = LeagueTotals()
        val boxes = mutableListOf<BoxScore>()
        runner.playSchedule(720, seed = 101L) { played ->
            totals.add(played.box)
            boxes += played.box
        }
        val batting = boxes.flatMap { listOf(it.home.battingTotal, it.away.battingTotal) }
        val pa = batting.sumOf { it.plateAppearances }.toDouble()
        val average = batting.sumOf { it.hits }.toDouble() / batting.sumOf { it.atBats }
        val kRate = batting.sumOf { it.strikeouts } / pa
        val bbRate = batting.sumOf { it.walks } / pa
        val runsPerTeamGame = batting.sumOf { it.runs }.toDouble() / (boxes.size * 2)
        val homeRunsPerSeason = batting.sumOf { it.homeRuns }.toDouble() / (boxes.size * 2) * 144

        assertInRange("리그 타율", average, balance.doubleRange("leagueTargets.battingAverage"))
        assertInRange("삼진 비율", kRate, balance.doubleRange("leagueTargets.kRate"))
        assertInRange("볼넷 비율", bbRate, balance.doubleRange("leagueTargets.bbRate"))
        assertInRange("경기당 팀 득점", runsPerTeamGame, balance.doubleRange("leagueTargets.runsPerTeamGame"))
        assertInRange("팀당 시즌 홈런", homeRunsPerSeason, balance.doubleRange("leagueTargets.teamHomeRunsPerSeason"))
    }

    @Test
    fun `좋은 팀이 약한 팀보다 자주 이긴다`() {
        // 능력치가 실제 결과로 이어지는지 확인한다 (M4 에서 승률 분포까지 맞춘다)
        val strong = league.teams.first { it.id.value == "DSK" }.id
        val weak = league.teams.first { it.id.value == "DJR" }.id
        var strongWins = 0
        var played = 0
        repeat(200) { index ->
            val game = runner.playWithRotations(strong, weak, index, index, seed = 500L + index)
            if (!game.box.tie) {
                played++
                if (game.box.winner == strong) strongWins++
            }
        }
        val winRate = strongWins.toDouble() / played
        assertTrue(winRate > 0.55, "강팀 승률 $winRate")
    }

    private fun assertInRange(name: String, value: Double, range: ClosedFloatingPointRange<Double>) {
        assertTrue(value in range, "$name $value 가 목표 ${range.start}~${range.endInclusive} 밖이다")
    }
}

private fun List<GameEvent>.plateAppearances(): List<PlateAppearanceCompleted> =
    filterIsInstance<PlateAppearanceCompleted>()
