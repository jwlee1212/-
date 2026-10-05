package baseballgm.stats

import baseballgm.io.BalanceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val BALANCE = BalanceConfig.parse(
    """
    {
      "sabermetrics": {
        "wobaWeights": {
          "walk": 0.69, "hitByPitch": 0.72, "single": 0.89, "double": 1.27, "triple": 1.62, "homeRun": 2.10
        },
        "runsPerWinFactor": 2.1,
        "stolenBaseRuns": 0.20,
        "caughtStealingRuns": -0.40,
        "defenseRunsPerPoint": 0.09,
        "defenseNeutralRating": 60,
        "positionalRunsScale": 11.0,
        "replacementRunsPer600Pa": 18.0,
        "replacementRunsPer9": { "starter": 0.95, "reliever": 0.50 },
        "qualifiedPaPerGame": 3.1,
        "qualifiedOutsPerGame": 3
      }
    }
    """.trimIndent(),
)

/** 리그 한 시즌쯤 되는 합계. 리그 상수를 만들 만큼 표본이 크다. */
private val LEAGUE_BATTING = BattingLine(
    plateAppearances = 55000, atBats = 48000, hits = 12900, doubles = 2300, triples = 200,
    homeRuns = 1200, runs = 6400, rbi = 6100, walks = 5200, intentionalWalks = 120,
    hitByPitch = 600, strikeouts = 10800, sacFlies = 450, sacBunts = 400,
    groundOuts = 14000, flyOuts = 12000, lineOuts = 3000, doublePlays = 1100,
)

private val LEAGUE_PITCHING = PitchingLine(
    outs = 38900, battersFaced = 55000, hits = 12900, homeRuns = 1200, walks = 5200,
    hitByPitch = 600, strikeouts = 10800, runs = 6400, earnedRuns = 5900,
)

class LeagueConstantsTest {

    private val constants = LeagueConstants.from(LEAGUE_BATTING, LEAGUE_PITCHING, BALANCE)

    @Test
    fun `리그 상수는 기록에서 계산한다`() {
        assertEquals(LEAGUE_BATTING.onBasePercentage, constants.leagueWoba, 0.0001)
        assertTrue(constants.runsPerWin in 8.0..12.0, "승당 득점 ${constants.runsPerWin}")
        assertTrue(constants.leagueEra in 3.5..5.5, "리그 평균자책 ${constants.leagueEra}")
    }

    @Test
    fun `FIP 상수는 리그 FIP 를 평균자책점에 맞춘다`() {
        val fip = Sabermetrics(BALANCE).fipOf(LEAGUE_PITCHING, constants)
        assertEquals(constants.leagueEra, fip, 0.01)
    }

    @Test
    fun `기록이 적으면 기본값으로 버틴다`() {
        val thin = LeagueConstants.from(BattingLine(plateAppearances = 10), PitchingLine(outs = 9), BALANCE)
        assertEquals(LeagueConstants.NEUTRAL, thin)
    }
}

class SabermetricsTest {

    private val sabermetrics = Sabermetrics(BALANCE)
    private val constants = LeagueConstants.from(LEAGUE_BATTING, LEAGUE_PITCHING, BALANCE)

    /** 리그 평균 수준의 타자 (리그 합계를 1/100 로 줄인 것). */
    private val averageBatter = BattingLine(
        plateAppearances = 550, atBats = 480, hits = 129, doubles = 23, triples = 2,
        homeRuns = 12, runs = 64, rbi = 61, walks = 52, intentionalWalks = 1,
        hitByPitch = 6, strikeouts = 108, sacFlies = 4, groundOuts = 140, flyOuts = 120,
        lineOuts = 30, doublePlays = 11,
    )

    @Test
    fun `리그 평균 타자의 wRC+ 는 100 근처다`() {
        val metrics = sabermetrics.batter(averageBatter, constants)
        assertEquals(100.0, metrics.wrcPlus, 5.0, "wRC+ ${metrics.wrcPlus}")
        assertEquals(0.0, metrics.wraa, 3.0, "wRAA ${metrics.wraa}")
        assertEquals(constants.leagueWoba, metrics.woba, 0.005)
    }

    @Test
    fun `잘 치면 wRC+ 가 올라간다`() {
        val slugger = averageBatter.copy(hits = 160, homeRuns = 35, doubles = 35, walks = 80, strikeouts = 90)
        val metrics = sabermetrics.batter(slugger, constants)
        assertTrue(metrics.wrcPlus > 140, "거포 wRC+ ${metrics.wrcPlus}")
        assertTrue(metrics.iso > 0.200, "ISO ${metrics.iso}")
        assertTrue(metrics.walkRate > 0.13)
    }

    @Test
    fun `타자 친화 구장의 기록은 깎인다`() {
        val neutral = sabermetrics.batter(averageBatter, constants, parkFactor = 1.0)
        val hitterPark = sabermetrics.batter(averageBatter, constants, parkFactor = 1.10)
        assertTrue(hitterPark.wrcPlus < neutral.wrcPlus, "${hitterPark.wrcPlus} < ${neutral.wrcPlus}")
    }

    @Test
    fun `삼진이 많고 볼넷이 적은 투수는 FIP 가 낮다`() {
        val ace = PitchingLine(outs = 540, battersFaced = 720, hits = 150, homeRuns = 12, walks = 40, strikeouts = 200, runs = 60, earnedRuns = 55)
        val journeyman = ace.copy(homeRuns = 28, walks = 90, strikeouts = 90)
        assertTrue(sabermetrics.pitcher(ace, constants).fip < sabermetrics.pitcher(journeyman, constants).fip)
        assertTrue(sabermetrics.pitcher(ace, constants).strikeoutsPer9 > 9.0)
    }

    @Test
    fun `ERA+ 는 100 이 평균이다`() {
        val innings = 540
        val average = PitchingLine(
            outs = innings,
            battersFaced = 760,
            hits = 178,
            homeRuns = 17,
            walks = 72,
            strikeouts = 150,
            runs = 89,
            earnedRuns = (constants.leagueEra * innings / 27.0).toInt(),
        )
        assertEquals(100.0, sabermetrics.pitcher(average, constants).eraPlus, 3.0)
    }
}
