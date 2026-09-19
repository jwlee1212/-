package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRole
import baseballgm.sim.Log5
import baseballgm.sim.RatingTables
import baseballgm.stats.BattingLine
import baseballgm.stats.PitchingLine
import baseballgm.util.nextGaussian
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 2군 추정 성적 (docs/07).
 *
 * **2군 경기는 시뮬레이션하지 않는다.** 선수 한 명당 한 주치 기대 성적을 능력치로 계산하고
 * 랜덤 편차를 얹어서 만든다. 600명 가까운 2군 선수의 경기를 매주 돌리면 연산이 수십 배로 늘어나는데,
 * 2군 기록은 "누구를 올릴까"를 정하는 참고 자료라 이 정도 정밀도면 충분하다.
 *
 * 2군 투수는 1군보다 약하다고 보고 상대 능력치에 `levelPenalty` 만큼 깎아서 계산한다.
 */
class FuturesLeague(balance: BalanceConfig, private val tables: RatingTables) {

    private val section = balance.section("futuresLeague")
    private val gamesPerWeek = section.double("gamesPerWeek")
    private val paPerGame = section.double("plateAppearancesPerGame")
    private val starterInnings = section.double("starterInningsPerWeek")
    private val relieverInnings = section.double("relieverInningsPerWeek")
    private val playShare = section.double("playShare")
    private val spread = section.double("randomSpread")
    private val levelPenalty = section.double("levelPenalty")

    private val leagueK = balance.double("leagueAverages.kRate")
    private val leagueBb = balance.double("leagueAverages.bbRate")
    private val leagueHr = balance.double("leagueAverages.hrPerBattedBall")
    private val leagueBabip = balance.double("leagueAverages.babip")

    /** 상대 수준: 리그 평균에서 레벨 차이만큼 낮춘 가상의 투수·타자. */
    private val opponentRating = LEAGUE_AVERAGE - levelPenalty

    fun weeklyBatting(batter: Batter, random: Random): BattingLine {
        val pa = (gamesPerWeek * paPerGame * playShare * noise(random)).roundToInt()
        if (pa <= 0) return BattingLine.EMPTY

        val kRate = Log5.rate(
            (tables.value("batterContactToK", batter.ratings.contact) +
                tables.value("batterEyeToK", batter.ratings.eye)) / 2.0,
            tables.value("pitcherStuffToK", opponentRating),
            leagueK,
        )
        val bbRate = Log5.rate(
            tables.value("batterEyeToBB", batter.ratings.eye),
            tables.value("pitcherControlToBB", opponentRating),
            leagueBb,
        )
        val hrRate = Log5.rate(
            tables.value("batterPowerToHR", batter.ratings.power),
            tables.value("pitcherStuffToHR", opponentRating),
            leagueHr,
        )
        val babip = Log5.rate(
            tables.value("batterContactToBabip", batter.ratings.contact),
            tables.value("pitcherStuffToBabip", opponentRating),
            leagueBabip,
        ) + tables.value("batterSpeedToBabip", batter.ratings.speed)

        val strikeouts = (pa * kRate * noise(random)).roundToInt().coerceIn(0, pa)
        val walks = (pa * bbRate * noise(random)).roundToInt().coerceIn(0, pa - strikeouts)
        val inPlay = max(0, pa - strikeouts - walks)
        val homeRuns = (inPlay * hrRate * noise(random)).roundToInt().coerceIn(0, inPlay)
        val hits = (homeRuns + (inPlay - homeRuns) * babip * noise(random)).roundToInt().coerceIn(0, inPlay)
        val doubles = (hits * DOUBLE_SHARE).roundToInt()
        val triples = (hits * TRIPLE_SHARE).roundToInt()

        return BattingLine(
            plateAppearances = pa,
            atBats = pa - walks,
            hits = hits,
            doubles = doubles,
            triples = triples,
            homeRuns = homeRuns,
            runs = (hits * RUN_SHARE).roundToInt(),
            rbi = (hits * RBI_SHARE + homeRuns).roundToInt(),
            walks = walks,
            strikeouts = strikeouts,
            groundOuts = max(0, inPlay - hits),
        )
    }

    fun weeklyPitching(pitcher: Pitcher, random: Random): PitchingLine {
        val innings = if (pitcher.role == PitcherRole.STARTER) starterInnings else relieverInnings
        val outs = (innings * OUTS_PER_INNING * playShare * noise(random)).roundToInt()
        if (outs <= 0) return PitchingLine.EMPTY

        val kRate = Log5.rate(
            tables.value("pitcherStuffToK", pitcher.ratings.stuff),
            tables.value("batterContactToK", opponentRating),
            leagueK,
        )
        val bbRate = Log5.rate(
            tables.value("pitcherControlToBB", pitcher.ratings.control),
            tables.value("batterEyeToBB", opponentRating),
            leagueBb,
        )
        val babip = Log5.rate(
            tables.value("pitcherStuffToBabip", pitcher.ratings.stuff),
            tables.value("batterContactToBabip", opponentRating),
            leagueBabip,
        )
        val hrRate = Log5.rate(
            tables.value("pitcherStuffToHR", pitcher.ratings.stuff),
            tables.value("batterPowerToHR", opponentRating),
            leagueHr,
        )

        val battersFaced = (outs / (1.0 - (babip + bbRate) * BATTERS_FACED_FACTOR)).roundToInt()
        val strikeouts = (battersFaced * kRate * noise(random)).roundToInt()
        val walks = (battersFaced * bbRate * noise(random)).roundToInt()
        val inPlay = max(0, battersFaced - strikeouts - walks)
        val homeRuns = (inPlay * hrRate * noise(random)).roundToInt()
        val hits = (homeRuns + (inPlay - homeRuns) * babip * noise(random)).roundToInt()
        val runs = ((hits + walks) * RUN_PER_BASERUNNER * noise(random)).roundToInt()

        return PitchingLine(
            outs = outs,
            battersFaced = battersFaced,
            hits = hits,
            homeRuns = homeRuns,
            walks = walks,
            strikeouts = strikeouts,
            runs = runs,
            earnedRuns = (runs * EARNED_SHARE).roundToInt(),
            pitches = (battersFaced * PITCHES_PER_BATTER).roundToInt(),
            games = 1,
            gamesStarted = if (pitcher.role == PitcherRole.STARTER) 1 else 0,
        )
    }

    /** 능력치는 같아도 주마다 성적이 흔들리게 한다. */
    private fun noise(random: Random): Double = max(0.0, random.nextGaussian(1.0, spread))

    private companion object {
        const val LEAGUE_AVERAGE = 50.0
        const val OUTS_PER_INNING = 3
        const val DOUBLE_SHARE = 0.19
        const val TRIPLE_SHARE = 0.02
        const val RUN_SHARE = 0.45
        const val RBI_SHARE = 0.40
        const val RUN_PER_BASERUNNER = 0.34
        const val EARNED_SHARE = 0.92
        const val PITCHES_PER_BATTER = 3.9
        const val BATTERS_FACED_FACTOR = 0.9
    }
}
