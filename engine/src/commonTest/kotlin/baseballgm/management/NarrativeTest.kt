package baseballgm.management

import baseballgm.events.FanMood
import baseballgm.events.NewsDesk
import baseballgm.events.NewsKind
import baseballgm.io.BalanceConfig
import baseballgm.league.Standings
import baseballgm.league.TeamRecord
import baseballgm.market.TEST_SEASON
import baseballgm.market.rosterFor
import baseballgm.market.testLeague
import baseballgm.market.testTeam
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.stats.BattingLine
import baseballgm.stats.BoxScore
import baseballgm.stats.PlayerBatting
import baseballgm.stats.TeamBoxScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val NARRATIVE_BALANCE = BalanceConfig.parse(
    """
    {
      "narrative": {
        "news": { "maxPerWeek": 6, "streakHeadline": 4, "blowoutMargin": 8, "weeklyHomeRunHeadline": 3,
                  "milestoneHomeRuns": [10, 20], "milestoneWins": [10], "milestoneSaves": [20], "milestoneHits": [100],
                  "strikeoutGame": 11, "injuryHeadlineWeeks": 4, "injuryHeadlineOverall": 65, "raceFromWeek": 12,
                  "raceGap": 2.0, "managerHeatStreak": 7, "slumpHeadlinePa": 20, "slumpHeadlineOps": 0.45, "maxMilestonesPerWeek": 3 },
        "fanPosts": { "perWeek": 4, "fanWeight": 0.5, "weekWeight": 0.5, "positiveAbove": 0.6, "negativeBelow": 0.4,
                      "heroMinPa": 10, "goatMinPa": 12, "goatMaxOps": 0.5, "bullpenMeltdownRuns": 5,
                      "likesBase": 40, "likesSpread": 900 },
        "echo": { "maxPerWeek": 2, "multiHomeRunWeek": 2 },
        "bonds": { "levels": [3, 5, 8] },
        "titles": { "postseasonRegular": 3, "dynasty": 3, "journeyman": 3, "oneClub": 8, "veteran": 10,
                    "rebuildRankJump": 5, "reputationStar": 80 }
      }
    }
    """.trimIndent(),
)

private val US = TeamId("AAA")
private val THEM = TeamId("BBB")

private fun season(year: Int, team: TeamId, rank: Int, champion: Boolean = false, commentary: Boolean = false) =
    CareerSeason(
        season = year, teamId = team, teamName = team.value, wins = 70, losses = 70, ties = 4, rank = rank,
        reachedRound = if (champion) baseballgm.season.PostseasonRound.KOREAN_SERIES else null,
        champion = champion, commentary = commentary,
    )

/** 홈런만 적힌 간단한 박스스코어. 이긴 쪽 점수가 크다 */
private fun box(
    home: TeamId,
    away: TeamId,
    homeRuns: Int,
    awayRuns: Int,
    homeBatting: Map<PlayerId, Int> = emptyMap(),
    awayBatting: Map<PlayerId, Int> = emptyMap(),
    walkOff: Boolean = false,
) = BoxScore(
    home = side(home, homeRuns, homeBatting),
    away = side(away, awayRuns, awayBatting),
    innings = 9,
    walkOff = walkOff,
    tie = homeRuns == awayRuns,
)

private fun side(team: TeamId, runs: Int, homers: Map<PlayerId, Int>) = TeamBoxScore(
    teamId = team,
    batting = homers.mapValues { (_, count) -> PlayerBatting(unsplit = BattingLine(homeRuns = count)) },
    pitching = emptyMap(),
    inningRuns = listOf(runs) + List(8) { 0 },
    halfInningOuts = List(9) { 3 },
    leftOnBase = 0,
    runnersOutOnBase = 0,
    errors = 0,
)

/** 단장 칭호 (docs/13). */
class GmTitlesTest {
    private val titles = GmTitles(NARRATIVE_BALANCE)

    @Test
    fun `첫 시즌 전에는 신임 단장`() {
        assertEquals("신임 단장", titles.headline(null).name)
        assertEquals("신임 단장", titles.headline(CareerRecord("김단장", 50)).name)
    }

    @Test
    fun `우승하면 우승 청부사가 대표 칭호`() {
        val career = CareerRecord("김단장", 60, seasons = listOf(season(2026, US, 3), season(2027, US, 1, champion = true)))
        assertEquals("우승 청부사", titles.headline(career).name)
    }

    @Test
    fun `한 해 만에 순위를 크게 올리면 리빌딩 장인`() {
        val career = CareerRecord("김단장", 55, seasons = listOf(season(2026, US, 9), season(2027, US, 3)))
        assertTrue(titles.earned(career).any { it.name == "리빌딩 장인" })
    }

    @Test
    fun `세 구단을 거치면 저니맨, 해설위원을 거치면 돌아온 단장`() {
        val career = CareerRecord(
            "김단장", 40,
            seasons = listOf(
                season(2026, US, 8),
                season(2027, THEM, 7),
                CareerSeason(2028, null, "", 0, 0, 0, 0, commentary = true),
                season(2029, TeamId("CCC"), 6),
            ),
        )
        val names = titles.earned(career).map { it.name }
        assertTrue("저니맨" in names)
        assertTrue("돌아온 단장" in names)
    }
}

/** 장기 재임 선수 유대 (docs/13). */
class BondsTest {
    private val bonds = Bonds(NARRATIVE_BALANCE)
    private val player = rosterFor("AAA").first()

    @Test
    fun `부임 전부터 있던 선수는 부임 시즌부터 센다`() {
        val career = CareerRecord("김단장", 50, seasons = listOf(season(2026, US, 5), season(2027, US, 4)))
        val bond = bonds.of(player, US, season = 2028, career = career, decisions = emptyList())!!
        assertEquals(3, bond.seasons)
        assertEquals(BondLevel.TEAMMATE, bond.level)
    }

    @Test
    fun `다른 구단 시절은 부임 기간에 넣지 않는다`() {
        val career = CareerRecord("김단장", 50, seasons = listOf(season(2025, THEM, 5), season(2026, US, 4)))
        assertEquals(2027 - 2026 + 1, bonds.of(player, US, 2027, career, emptyList())!!.seasons)
    }

    @Test
    fun `지명한 선수는 다음 시즌 입단부터 센다`() {
        val drafted = GmDecision(2026, 20, DecisionKind.DRAFT, player.id, player.name, US)
        assertEquals(1, bonds.of(player, US, 2027, CareerRecord("김단장", 50, seasons = listOf(season(2026, US, 5))), listOf(drafted))!!.seasons)
    }

    @Test
    fun `남의 팀 선수와는 유대가 없다`() {
        val other = rosterFor("BBB").first()
        assertEquals(null, bonds.of(other, US, 2027, null, emptyList()))
    }

    @Test
    fun `유대 단계는 3·5·8 시즌`() {
        assertEquals(BondLevel.NONE, bonds.levelOf(2))
        assertEquals(BondLevel.TRUSTED, bonds.levelOf(5))
        assertEquals(BondLevel.FRANCHISE, bonds.levelOf(9))
    }
}

/** 과거 선택의 메아리 (docs/13). */
class EchoesTest {
    private val echoes = Echoes(NARRATIVE_BALANCE)
    private val traded = PlayerId("X1")
    private val signed = PlayerId("X2")
    private val rookie = rosterFor("AAA").first()

    private val decisions = listOf(
        GmDecision(TEST_SEASON - 2, 10, DecisionKind.TRADE_OUT, traded, "박보냄", US),
        GmDecision(TEST_SEASON - 1, 1, DecisionKind.FREE_AGENT, signed, "최영입", US),
        GmDecision(TEST_SEASON - 1, 20, DecisionKind.DRAFT, rookie.id, rookie.name, US),
    )

    @Test
    fun `보낸 선수가 우리 상대로 홈런을 치면 비서가 꺼낸다`() {
        val game = box(home = US, away = THEM, homeRuns = 2, awayRuns = 5, awayBatting = mapOf(traded to 1))
        val lines = echoes.find(TEST_SEASON, US, decisions, listOf(game), emptySet()) { null }
        assertTrue(lines.single().contains("2년 전 트레이드로 보낸 박보냄"))
    }

    @Test
    fun `데려온 선수가 한 주에 홈런 여럿이면 보람 있는 메아리`() {
        val games = listOf(
            box(US, THEM, 5, 1, homeBatting = mapOf(signed to 1)),
            box(US, THEM, 4, 2, homeBatting = mapOf(signed to 1)),
        )
        val lines = echoes.find(TEST_SEASON, US, decisions, games, emptySet()) { null }
        assertTrue(lines.single().contains("최영입") && lines.single().contains("홈런 2개"))
    }

    @Test
    fun `지명한 신인의 첫 1군 경기`() {
        val lines = echoes.find(TEST_SEASON, US, decisions, emptyList(), setOf(rookie.id)) { rookie }
        assertTrue(lines.single().contains("드래프트에서 뽑은"))
    }

    @Test
    fun `결정이 없으면 조용하다`() {
        assertEquals(emptyList(), echoes.find(TEST_SEASON, US, emptyList(), listOf(box(US, THEM, 1, 0)), emptySet()) { null })
    }
}

/** 뉴스 데스크 (docs/16 뉴스 탭). */
class NewsDeskTest {
    private val desk = NewsDesk(NARRATIVE_BALANCE)
    private val league = testLeague(
        teams = listOf(testTeam("AAA").copy(rival = THEM), testTeam("BBB").copy(rival = US), testTeam("CCC")),
        players = rosterFor("AAA") + rosterFor("BBB"),
    )

    @Test
    fun `연승 기사와 라이벌전 기사가 나오고 우리 팀 기사가 앞에 온다`() {
        val standings = Standings(
            mapOf(
                US to TeamRecord(US, wins = 5, losses = 1, streak = 2),
                THEM to TeamRecord(THEM, wins = 1, losses = 5, streak = -2),
                TeamId("CCC") to TeamRecord(TeamId("CCC"), wins = 6, losses = 0, streak = 6),
            ),
        )
        val games = listOf(box(US, THEM, 3, 1), box(US, THEM, 4, 2), box(THEM, US, 6, 5))
        val news = desk.headlines(league, 3, games, standings, previousLeader = null, userTeam = US, playerName = { "?" }, teamOfPlayer = { null })

        assertEquals(NewsKind.RIVAL, news.first().kind, "우리 팀이 나오는 라이벌전이 먼저")
        assertTrue(news.first().headline.contains("2승 1패"))
        assertTrue(news.any { it.kind == NewsKind.STREAK && it.headline.contains("6연승") })
    }

    @Test
    fun `같은 매치업의 끝내기 두 번은 기사 하나로 묶는다`() {
        val cccc = TeamId("CCC")
        val games = listOf(box(cccc, THEM, 3, 2, walkOff = true), box(cccc, THEM, 5, 4, walkOff = true))
        val news = desk.headlines(league, 1, games, Standings(emptyMap()), null, US, { "?" }, { null })
        val walkOffs = news.filter { it.kind == NewsKind.GAME }
        assertEquals(1, walkOffs.size)
        assertTrue(walkOffs.single().headline.contains("끝내기만 2번"))
    }

    @Test
    fun `라이벌 시리즈는 팀마다 따로 센다`() {
        val series = desk.rivalSeries(league, listOf(box(US, THEM, 3, 1), box(US, THEM, 4, 2), box(THEM, US, 6, 5)))
        assertEquals(2, series.getValue(US).wins)
        assertEquals(1, series.getValue(US).losses)
        assertEquals(2, series.getValue(THEM).losses)
        assertTrue(TeamId("CCC") !in series)
    }

    @Test
    fun `팬 글은 같은 주차 시드면 같고 팬심이 높고 이기면 좋은 글이 많다`() {
        fun posts(fan: Int, wins: Int, losses: Int) = desk.fanPosts(
            TEST_SEASON, 5, league.team(US), fan, wins, losses, streak = 0, rival = null, rivalName = null, hero = null,
            random = desk.randomFor(league, 5),
        )
        assertEquals(posts(90, 6, 0), posts(90, 6, 0))
        val happy = posts(95, 6, 0).count { it.mood == FanMood.POSITIVE }
        val angry = posts(10, 0, 6).count { it.mood == FanMood.NEGATIVE }
        assertTrue(happy >= 2, "좋은 주인데 응원 글이 $happy 개")
        assertTrue(angry >= 2, "나쁜 주인데 불만 글이 $angry 개")
    }

    @Test
    fun `뉴스 난수는 시즌 난수와 따로 논다`() {
        val a = desk.randomFor(league, 3).nextInt()
        val b = desk.randomFor(league, 3).nextInt()
        assertEquals(a, b)
        assertTrue(desk.randomFor(league, 4).nextInt() != a, "주차가 바뀌면 다른 문장이 고를 수 있어야 한다")
    }
}
