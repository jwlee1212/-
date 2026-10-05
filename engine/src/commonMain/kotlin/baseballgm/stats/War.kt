package baseballgm.stats

import baseballgm.io.BalanceConfig
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRole
import baseballgm.model.Player
import baseballgm.model.Position

/**
 * WAR 을 뜯어본 값 (docs/04 3단계).
 *
 * 합계만 보여주면 "왜 이 선수가 3.2 인가"를 설명할 수 없어서, 부문별로 나눠서 들고 있는다.
 * 화면 세이버 탭이 그대로 표로 보여준다.
 */
data class WarBreakdown(
    val batting: Double = 0.0,
    val baseRunning: Double = 0.0,
    val fielding: Double = 0.0,
    val positional: Double = 0.0,
    val pitching: Double = 0.0,
    val replacement: Double = 0.0,
    val runsPerWin: Double = 10.0,
) {
    /** 대체 선수 대비 득점 기여. */
    val runsAboveReplacement: Double
        get() = batting + baseRunning + fielding + positional + pitching + replacement

    val war: Double get() = runsAboveReplacement / runsPerWin

    companion object {
        val EMPTY: WarBreakdown = WarBreakdown()
    }
}

/**
 * WAR (docs/04 3단계).
 *
 * 수비 기여는 **"수비 능력치 × 포지션 난이도"로 단순 추정**한다 (기획 확정). 수비 지표를
 * 경기에서 뽑아내려면 타구 위치 추적이 필요한데 이 게임은 타석 단위라 그런 값이 없다.
 * 대신 포지션 난이도([Position.defenseDifficulty])를 곱해, 같은 수비 능력치라도
 * 유격수가 1루수보다 더 큰 값을 받게 한다.
 *
 * 투수는 실점이 아니라 **FIP** 로 계산한다. 수비 도움이 좋은 팀의 투수가 과대평가되지 않게 하려는
 * 것이고, 타자 쪽 수비 기여와 이중 계산이 되는 것도 막는다.
 */
class War(balance: BalanceConfig, private val sabermetrics: Sabermetrics) {

    private val section = balance.section("sabermetrics")
    private val stolenBaseRuns = section.double("stolenBaseRuns")
    private val caughtStealingRuns = section.double("caughtStealingRuns")
    private val defenseRunsPerPoint = section.double("defenseRunsPerPoint")
    private val defenseNeutral = section.double("defenseNeutralRating")
    private val positionalRunsScale = section.double("positionalRunsScale")
    private val replacementPer600 = section.double("replacementRunsPer600Pa")
    private val replacementStarter = section.double("replacementRunsPer9.starter")
    private val replacementReliever = section.double("replacementRunsPer9.reliever")

    fun of(player: Player, stats: SeasonStats, constants: LeagueConstants, parkFactor: Double = 1.0): WarBreakdown =
        when (player) {
            is Batter -> forBatter(player, stats.battingOf(player.id).total, constants, parkFactor)
            is Pitcher -> forPitcher(player, stats.pitchingOf(player.id).total, constants, parkFactor)
        }

    fun forBatter(
        batter: Batter,
        line: BattingLine,
        constants: LeagueConstants,
        parkFactor: Double = 1.0,
    ): WarBreakdown {
        if (line.plateAppearances == 0) return WarBreakdown(runsPerWin = constants.runsPerWin)
        val metrics = sabermetrics.batter(line, constants, parkFactor)
        val share = line.plateAppearances / FULL_SEASON_PA
        val difficulty = batter.primaryPosition.defenseDifficulty

        // 구장 보정: 타자 친화 구장의 기록은 깎는다 (wRAA 를 구장 계수로 나눈다)
        val battingRuns = metrics.wraa / parkFactor

        return WarBreakdown(
            batting = battingRuns,
            baseRunning = line.stolenBases * stolenBaseRuns + line.caughtStealing * caughtStealingRuns,
            fielding = if (batter.primaryPosition == Position.DESIGNATED_HITTER) {
                0.0
            } else {
                (batter.ratings.defense - defenseNeutral) * defenseRunsPerPoint * difficulty * share
            },
            positional = (difficulty - NEUTRAL_DIFFICULTY) * positionalRunsScale * share,
            replacement = replacementPer600 * share,
            runsPerWin = constants.runsPerWin,
        )
    }

    fun forPitcher(
        pitcher: Pitcher,
        line: PitchingLine,
        constants: LeagueConstants,
        parkFactor: Double = 1.0,
    ): WarBreakdown {
        val innings = line.outs / 3.0
        if (innings <= 0.0) return WarBreakdown(runsPerWin = constants.runsPerWin)

        val fip = sabermetrics.fipOf(line, constants)
        // 구장 보정: 투수 친화 구장의 기록은 깎는다
        val adjustedFip = fip / parkFactor
        val replacementPer9 = if (pitcher.role == PitcherRole.STARTER) replacementStarter else replacementReliever

        return WarBreakdown(
            // 리그 평균 투수 대비 막아낸 실점
            pitching = (constants.leagueEra - adjustedFip) * innings / INNINGS_PER_GAME,
            replacement = replacementPer9 * innings / INNINGS_PER_GAME,
            runsPerWin = constants.runsPerWin,
        )
    }

    private companion object {
        const val FULL_SEASON_PA = 600.0
        const val INNINGS_PER_GAME = 9.0
        const val NEUTRAL_DIFFICULTY = 1.0
    }
}
