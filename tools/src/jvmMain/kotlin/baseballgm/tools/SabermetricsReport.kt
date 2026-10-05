package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.season.SeasonState
import baseballgm.stats.LeagueConstants
import baseballgm.stats.Sabermetrics
import baseballgm.stats.War
import baseballgm.stats.WarBreakdown

/** WAR 한 줄. */
data class WarRow(val player: Player, val breakdown: WarBreakdown)

/**
 * 세이버 지표 점검 도구 (M7).
 *
 * WAR 은 "리그 전체 합계"로 검산할 수 있다. 대체 선수로만 꾸린 팀의 승률을 .294 로 보면
 * 10팀 × 144경기에서 **리그 전체 WAR 합계는 약 290** 이 나와야 한다
 * (총 720승 − 대체 수준 10팀 × 42.3승). 이 숫자가 맞지 않으면 대체 수준 설정이 틀린 것이다.
 */
class SabermetricsReport(private val balance: BalanceConfig, private val league: League) {

    private val sabermetrics = Sabermetrics(balance)
    private val war = War(balance, sabermetrics)

    fun of(state: SeasonState): Report {
        val constants = LeagueConstants.from(state.stats, balance)
        val rows = state.allPlayers().map { player ->
            val park = player.teamId?.let { league.team(it).parkFactor } ?: 1.0
            WarRow(player, war.of(player, state.stats, constants, park))
        }
        return Report(constants, rows, sabermetrics, state, league)
    }

    class Report(
        val constants: LeagueConstants,
        val rows: List<WarRow>,
        private val sabermetrics: Sabermetrics,
        private val state: SeasonState,
        private val league: League,
    ) {
        val totalWar: Double get() = rows.sumOf { it.breakdown.war }

        val batterWar: Double get() = rows.filter { it.player is Batter }.sumOf { it.breakdown.war }

        val pitcherWar: Double get() = rows.filter { it.player is Pitcher }.sumOf { it.breakdown.war }

        fun top(count: Int, pitchers: Boolean): List<WarRow> = rows
            .filter { (it.player is Pitcher) == pitchers }
            .sortedByDescending { it.breakdown.war }
            .take(count)

        fun text(): String = buildString {
            appendLine("=== 리그 상수 (${league.season}) ===")
            appendLine(
                "리그 wOBA ${"%.3f".format(constants.leagueWoba)} · wOBA 배율 ${"%.3f".format(constants.wobaScale)} · " +
                    "FIP 상수 ${"%.2f".format(constants.fipConstant)}",
            )
            appendLine(
                "리그 평균자책 ${"%.2f".format(constants.leagueEra)} · 타석당 득점 ${"%.3f".format(constants.leagueRunsPerPa)} · " +
                    "승당 득점 ${"%.2f".format(constants.runsPerWin)}",
            )
            appendLine()
            appendLine("=== WAR 합계 ===")
            appendLine(
                "전체 ${"%.1f".format(totalWar)} (야수 ${"%.1f".format(batterWar)} · 투수 ${"%.1f".format(pitcherWar)})  " +
                    "목표 약 290 (야수 55~60%)",
            )
            appendLine()
            appendLine("[야수 WAR 상위 10]")
            appendLine("   선수          포지션  타석   wRC+   타격   수비   포지션  대체   WAR")
            top(10, pitchers = false).forEach { row ->
                val batter = row.player as Batter
                val line = state.stats.battingOf(batter.id).total
                val park = batter.teamId?.let { league.team(it).parkFactor } ?: 1.0
                val metrics = sabermetrics.batter(line, constants, park)
                val b = row.breakdown
                appendLine(
                    "  ${batter.registeredName.padEnd(10)} ${batter.primaryPosition.label.padEnd(5)} " +
                        "${line.plateAppearances.toString().padStart(4)} ${"%6.0f".format(metrics.wrcPlus)} " +
                        "${"%6.1f".format(b.batting)} ${"%6.1f".format(b.fielding)} ${"%6.1f".format(b.positional)} " +
                        "${"%5.1f".format(b.replacement)} ${"%5.1f".format(b.war)}",
                )
            }
            appendLine()
            appendLine("[투수 WAR 상위 10]")
            appendLine("   선수          보직   이닝    ERA   FIP   ERA+   WAR")
            top(10, pitchers = true).forEach { row ->
                val pitcher = row.player as Pitcher
                val line = state.stats.pitchingOf(pitcher.id).total
                val park = pitcher.teamId?.let { league.team(it).parkFactor } ?: 1.0
                val metrics = sabermetrics.pitcher(line, constants, park)
                appendLine(
                    "  ${pitcher.registeredName.padEnd(10)} ${(if (pitcher.role.isReliever) "RP" else "SP").padEnd(4)} " +
                        "${"%6.1f".format(line.inningsPitched)} ${"%5.2f".format(line.era)} ${"%5.2f".format(metrics.fip)} " +
                        "${"%6.0f".format(metrics.eraPlus)} ${"%5.1f".format(row.breakdown.war)}",
                )
            }
        }
    }
}

/**
 * 능력치 → 기대 WAR 표 생성기 (M7 가치 평가의 출발점).
 *
 * 트레이드·FA 가치 평가는 "이 선수는 앞으로 몇 승을 벌어 줄까"에서 시작한다. 그런데 엔진은
 * 능력치만 알고 있으므로, **여러 시즌을 돌려서 종합 능력치 구간별 실제 WAR 평균**을 뽑아
 * `config/balance.json` 에 넣는다. 출장 기회까지 포함된 값이라 "55짜리 백업은 거의 0" 같은
 * 현실이 저절로 반영된다.
 */
class WarCurveTool(private val balance: BalanceConfig, private val startingLeague: League) {

    private val strength = baseballgm.league.StrengthCalculator(balance)

    data class Bucket(val overall: Int, val samples: Int, val averageWar: Double)

    data class Curves(val batter: List<Bucket>, val starter: List<Bucket>, val reliever: List<Bucket>)

    fun run(seasons: Int, seed: Long): Curves {
        val batter = mutableMapOf<Int, MutableList<Double>>()
        val starter = mutableMapOf<Int, MutableList<Double>>()
        val reliever = mutableMapOf<Int, MutableList<Double>>()
        var league = startingLeague
        val offseason = baseballgm.season.Offseason(balance, strength)

        repeat(seasons) { index ->
            val result = SeasonRunner(balance, league).playSeason(seed + index, validate = false)
            val report = SabermetricsReport(balance, league).of(result.state)
            report.rows.forEach { row ->
                val overall = strength.overallOf(row.player)
                val bucket = (overall / BUCKET_SIZE).toInt() * BUCKET_SIZE
                val target = when {
                    row.player is Pitcher && row.player.role.isReliever -> reliever
                    row.player is Pitcher -> starter
                    else -> batter
                }
                target.getOrPut(bucket) { mutableListOf() } += row.breakdown.war
            }
            val (next, _) = offseason.run(
                state = result.state,
                random = kotlin.random.Random(seed + index * STRIDE),
                rookieSupplier = RookieFactory(balance, strength, league),
                prospectSupplier = ProspectFactory(
                    balance = balance,
                    strength = strength,
                    seed = seed + index * STRIDE * 2,
                    startingIdNumber = nextPlayerIdNumber(league.players, league.draftPool.prospects),
                ),
            )
            league = next
        }
        return Curves(batter.toBuckets(), starter.toBuckets(), reliever.toBuckets())
    }

    private fun Map<Int, MutableList<Double>>.toBuckets(): List<Bucket> =
        entries.sortedBy { it.key }
            .filter { it.value.size >= MIN_SAMPLES }
            .map { (overall, values) -> Bucket(overall, values.size, (values.average() * 100).toInt() / 100.0) }

    fun asConfigLine(buckets: List<Bucket>): String =
        buckets.joinToString(", ", prefix = "{ ", postfix = " }") { "\"${it.overall}\": ${it.averageWar}" }

    private companion object {
        const val BUCKET_SIZE = 5
        const val MIN_SAMPLES = 20
        const val STRIDE = 7919L
    }
}
