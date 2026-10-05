package baseballgm.stats

import baseballgm.io.BalanceConfig
import kotlin.math.max

/** 타자 2단계 지표 (docs/04). 카운트에서 계산해 내보내는 값이라 저장하지 않는다. */
data class BatterMetrics(
    val strikeoutRate: Double,
    val walkRate: Double,
    val iso: Double,
    val babip: Double,
    val woba: Double,
    val wrcPlus: Double,
    val wraa: Double,
    val groundBallRate: Double,
    val flyBallRate: Double,
)

/** 투수 2단계 지표 (docs/04). */
data class PitcherMetrics(
    val strikeoutsPer9: Double,
    val walksPer9: Double,
    val homeRunsPer9: Double,
    val strikeoutMinusWalkRate: Double,
    val babip: Double,
    val leftOnBaseRate: Double,
    val fip: Double,
    val eraPlus: Double,
)

/**
 * 세이버 지표 계산 (docs/04 2단계).
 *
 * 리그 상수를 받아서 쓴다 — 지표 하나하나가 "그 시즌 리그 평균 대비"로 정의되기 때문이다.
 * 구장 보정은 wRC+ 와 ERA+ 에만 건다 (docs/04 "wRC+, ERA+, FIP는 구장 계수로 보정").
 */
class Sabermetrics(balance: BalanceConfig) {

    private val weights = WobaWeights.from(balance)

    fun batter(line: BattingLine, constants: LeagueConstants, parkFactor: Double = 1.0): BatterMetrics {
        val pa = line.plateAppearances
        val rawWoba = weights.rawWoba(line)
        val woba = rawWoba * constants.wobaScale
        val wraa = if (pa == 0) 0.0 else (woba - constants.leagueWoba) / constants.wobaScale * pa
        val battedBalls = line.groundOuts + line.flyOuts + line.lineOuts +
            (line.hits - line.homeRuns) + line.doublePlays

        // wRC+ = 구장 보정한 타석당 창출 득점 / 리그 타석당 득점
        val wrcPerPa = if (pa == 0) {
            0.0
        } else {
            (wraa / pa + constants.leagueRunsPerPa) / parkFactor
        }

        return BatterMetrics(
            strikeoutRate = ratio(line.strikeouts, pa),
            walkRate = ratio(line.walks, pa),
            iso = line.sluggingPercentage - line.battingAverage,
            babip = ratio(
                line.hits - line.homeRuns,
                line.atBats - line.strikeouts - line.homeRuns + line.sacFlies,
            ),
            woba = woba,
            wrcPlus = if (constants.leagueRunsPerPa <= 0.0) 100.0 else 100.0 * wrcPerPa / constants.leagueRunsPerPa,
            wraa = wraa,
            groundBallRate = ratio(line.groundOuts + line.doublePlays, battedBalls),
            flyBallRate = ratio(line.flyOuts, battedBalls),
        )
    }

    fun pitcher(line: PitchingLine, constants: LeagueConstants, parkFactor: Double = 1.0): PitcherMetrics {
        val innings = line.outs / 3.0
        val fip = fipOf(line, constants)
        val era = line.era

        // LOB% = (출루 − 실점) / (출루 − 홈런×1.4). 관례적인 근사식이다
        val onBase = line.hits + line.walks + line.hitByPitch
        val lobDenominator = onBase - line.homeRuns * LOB_HR_FACTOR
        return PitcherMetrics(
            strikeoutsPer9 = per9(line.strikeouts, innings),
            walksPer9 = per9(line.walks, innings),
            homeRunsPer9 = per9(line.homeRuns, innings),
            strikeoutMinusWalkRate = ratio(line.strikeouts - line.walks, line.battersFaced),
            babip = ratio(
                line.hits - line.homeRuns,
                line.battersFaced - line.walks - line.hitByPitch - line.strikeouts - line.homeRuns,
            ),
            leftOnBaseRate = if (lobDenominator <= 0.0) 0.0 else (onBase - line.runs) / lobDenominator,
            fip = fip,
            eraPlus = if (era <= 0.0) {
                if (innings <= 0.0) 100.0 else ERA_PLUS_CAP
            } else {
                (100.0 * constants.leagueEra * parkFactor / era).coerceAtMost(ERA_PLUS_CAP)
            },
        )
    }

    /** FIP. 수비와 운을 걷어낸 투수의 실점 억제력이다. */
    fun fipOf(line: PitchingLine, constants: LeagueConstants): Double {
        val innings = line.outs / 3.0
        if (innings <= 0.0) return constants.leagueEra
        val raw = (
            LeagueConstants.FIP_HR * line.homeRuns +
                LeagueConstants.FIP_BB * (line.walks + line.hitByPitch) -
                LeagueConstants.FIP_K * line.strikeouts
            ) / innings
        return max(0.0, raw + constants.fipConstant)
    }

    private fun per9(count: Int, innings: Double): Double = if (innings <= 0.0) 0.0 else count * 9.0 / innings

    private companion object {
        const val LOB_HR_FACTOR = 1.4
        const val ERA_PLUS_CAP = 999.0
    }
}
