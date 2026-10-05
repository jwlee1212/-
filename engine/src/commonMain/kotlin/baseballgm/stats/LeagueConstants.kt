package baseballgm.stats

import baseballgm.io.BalanceConfig

/**
 * 리그 상수 (docs/04 "기록 저장 방식").
 *
 * wOBA 평균·FIP 상수·승당 득점처럼 **그 시즌 리그 전체 기록에서 나오는 값**들이다.
 * 고정값으로 박아 두면 득점 환경이 바뀐 시즌(공인구 변화 등)에 지표가 전부 어긋나므로,
 * 시즌 기록을 넣어 그때그때 계산한다.
 *
 * @param wobaScale wOBA 를 출루율 눈금에 맞추는 배율. 리그 출루율 / 리그 원시 wOBA
 * @param fipConstant FIP 를 평균자책점 눈금에 맞추는 상수
 * @param runsPerWin 승 하나의 값(득점). 득점 환경이 높을수록 커진다
 */
data class LeagueConstants(
    val leagueWoba: Double,
    val wobaScale: Double,
    val fipConstant: Double,
    val leagueEra: Double,
    val leagueRunsPerPa: Double,
    val runsPerWin: Double,
    val leagueRunsPer9: Double,
    val plateAppearances: Int,
    val innings: Double,
) {
    companion object {
        /** 기록이 너무 적으면(개막 직후) 비율이 요동치므로 기본값으로 버틴다. */
        val NEUTRAL: LeagueConstants = LeagueConstants(
            leagueWoba = 0.330,
            wobaScale = 1.15,
            fipConstant = 3.10,
            leagueEra = 4.50,
            leagueRunsPerPa = 0.120,
            runsPerWin = 10.0,
            leagueRunsPer9 = 4.80,
            plateAppearances = 0,
            innings = 0.0,
        )

        fun from(stats: SeasonStats, balance: BalanceConfig): LeagueConstants =
            from(
                batting = stats.allBatting().values.fold(BattingLine.EMPTY) { sum, line -> sum + line.total },
                pitching = stats.allPitching().values.fold(PitchingLine.EMPTY) { sum, line -> sum + line.total },
                balance = balance,
            )

        fun from(batting: BattingLine, pitching: PitchingLine, balance: BalanceConfig): LeagueConstants {
            if (batting.plateAppearances < MINIMUM_PA || pitching.outs < MINIMUM_OUTS) return NEUTRAL
            val section = balance.section("sabermetrics")
            val weights = WobaWeights.from(balance)

            val rawWoba = weights.rawWoba(batting)
            val onBase = batting.onBasePercentage
            val innings = pitching.outs / 3.0
            val era = pitching.earnedRuns * 9.0 / innings
            val runsPer9 = pitching.runs * 9.0 / innings

            // FIP 상수: 리그 FIP 원시값이 리그 평균자책점과 같아지도록 맞춘다
            val rawFip = (
                FIP_HR * pitching.homeRuns + FIP_BB * (pitching.walks + pitching.hitByPitch) -
                    FIP_K * pitching.strikeouts
                ) / innings

            return LeagueConstants(
                leagueWoba = onBase,
                wobaScale = if (rawWoba <= 0.0) NEUTRAL.wobaScale else onBase / rawWoba,
                fipConstant = era - rawFip,
                leagueEra = era,
                leagueRunsPerPa = batting.runs.toDouble() / batting.plateAppearances,
                runsPerWin = RUNS_PER_WIN_BASE * runsPer9 / RUNS_PER_WIN_INNINGS *
                    section.double("runsPerWinFactor"),
                leagueRunsPer9 = runsPer9,
                plateAppearances = batting.plateAppearances,
                innings = innings,
            )
        }

        /** 지표를 믿을 만큼 기록이 쌓였는지. */
        private const val MINIMUM_PA = 2000
        private const val MINIMUM_OUTS = 3000

        internal const val FIP_HR = 13.0
        internal const val FIP_BB = 3.0
        internal const val FIP_K = 2.0

        /** 승당 득점 = 9 × (이닝당 리그 득점) × 계수. 관례적인 근사식이다. */
        private const val RUNS_PER_WIN_BASE = 9.0
        private const val RUNS_PER_WIN_INNINGS = 9.0
    }
}

/** wOBA 선형 가중치 (docs/04). 야구 통계의 관례값이라 `balance.json` 에 둔다. */
data class WobaWeights(
    val walk: Double,
    val hitByPitch: Double,
    val single: Double,
    val double: Double,
    val triple: Double,
    val homeRun: Double,
) {
    /** 눈금을 맞추기 전의 wOBA. 분모는 타수 + (고의사구를 뺀) 볼넷 + 희생플라이 + 사구다. */
    fun rawWoba(line: BattingLine): Double {
        val denominator = line.atBats + (line.walks - line.intentionalWalks) + line.sacFlies + line.hitByPitch
        if (denominator <= 0) return 0.0
        val numerator = walk * (line.walks - line.intentionalWalks) +
            hitByPitch * line.hitByPitch +
            single * line.singles +
            double * line.doubles +
            triple * line.triples +
            homeRun * line.homeRuns
        return numerator / denominator
    }

    companion object {
        fun from(balance: BalanceConfig): WobaWeights {
            val section = balance.section("sabermetrics.wobaWeights")
            return WobaWeights(
                walk = section.double("walk"),
                hitByPitch = section.double("hitByPitch"),
                single = section.double("single"),
                double = section.double("double"),
                triple = section.double("triple"),
                homeRun = section.double("homeRun"),
            )
        }
    }
}
