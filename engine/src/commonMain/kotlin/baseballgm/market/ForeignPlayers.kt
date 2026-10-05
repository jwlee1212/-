package baseballgm.market

import baseballgm.util.fixed
import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.model.Batter
import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.scouting.ScoutingPrecision
import baseballgm.scouting.ScoutingView
import baseballgm.util.nextInRange
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** 외국인 선수 후보 한 명 (docs/12). 계약하기 전에는 소속이 없다. */
@Serializable
data class ForeignCandidate(
    val player: Player,
    /** `balance.json` 의 `foreignPlayers.market.originLeagues` 키 */
    val originLeague: String,
    val originLabel: String,
    /** 요구 연봉(억원). 신규 계약 상한을 넘을 수 없다 */
    val askingSalary: Double,
) {
    val id: PlayerId get() = player.id

    val isPitcher: Boolean get() = player is Pitcher
}

/** 한 해의 외국인 시장 (docs/12). 매년 약 40명. */
@Serializable
data class ForeignPool(val season: Int, val candidates: List<ForeignCandidate> = emptyList()) {
    fun byId(id: PlayerId): ForeignCandidate? = candidates.firstOrNull { it.id == id }

    companion object {
        val EMPTY: ForeignPool = ForeignPool(0)
    }
}

/**
 * 외국인 선수를 만들어 주는 쪽. 엔진은 선수를 만들 수 없다 (불변 원칙 1).
 */
fun interface ForeignSupplier {
    fun create(season: Int, count: Int, random: kotlin.random.Random): List<ForeignCandidate>
}

/**
 * KBO 환산 기록 (docs/12).
 *
 * 출신 리그의 성적을 KBO 기준으로 환산해 보여준다. **환산에는 오차가 있다** — 리그마다 수준이
 * 다르고, 같은 리그 안에서도 구장·상대가 다르기 때문이다. 오차는 선수마다 고정이라
 * 여러 번 열어 평균을 내는 꼼수가 통하지 않는다 (docs/02 구현 규칙 1과 같은 이유).
 */
data class ConvertedLine(
    val isPitcher: Boolean,
    val battingAverage: Double = 0.0,
    val homeRuns: Int = 0,
    val ops: Double = 0.0,
    val era: Double = 0.0,
    val whip: Double = 0.0,
    val strikeoutsPer9: Double = 0.0,
) {
    fun text(): String = if (isPitcher) {
        "${era.fixed(2)} 평균자책 · WHIP ${whip.fixed(2)} · K/9 ${strikeoutsPer9.fixed(1)}"
    } else {
        "타율 ${battingAverage.fixed(3)} · ${homeRuns}홈런 · OPS ${ops.fixed(3)}"
    }
}

/** 외국인 보유 규칙 (docs/12). */
class ForeignRules(balance: BalanceConfig) {

    private val section = balance.section("foreignPlayers")
    val maxPerTeam: Int = section.int("maxPerTeam")
    val maxSameType: Int = section.int("maxSameType")
    val activeInGame: Int = section.int("activeInGame")
    val newContractMax: Double = section.double("newContractMaxTotal")
    val replacementsPerSeason: Int = section.int("inSeasonReplacements")
    val replacementDeadlineWeek: Int = section.int("replacementDeadlineWeek")

    fun foreignersOf(players: List<Player>): List<Player> = players.filter { it.isForeign }

    /**
     * 이 선수를 데려올 수 있는가.
     *
     * 팀당 3명이고 **한쪽으로 3명은 안 된다** (투수 3명 / 타자 3명 모두 불가). 그래서 마지막
     * 한 자리는 자동으로 "부족한 쪽"이 된다.
     */
    fun problemsForSigning(roster: List<Player>, candidate: Player, salary: Double, isNewContract: Boolean): List<String> {
        val problems = mutableListOf<String>()
        val foreigners = foreignersOf(roster)
        if (foreigners.size >= maxPerTeam) problems += "외국인 선수는 ${maxPerTeam}명까지만 보유할 수 있다"

        val samePitcher = foreigners.count { (it is Pitcher) == (candidate is Pitcher) }
        if (samePitcher >= maxSameType) {
            problems += if (candidate is Pitcher) {
                "외국인 투수는 ${maxSameType}명까지다 (한쪽으로 ${maxPerTeam}명 불가)"
            } else {
                "외국인 타자는 ${maxSameType}명까지다 (한쪽으로 ${maxPerTeam}명 불가)"
            }
        }
        if (isNewContract && salary > newContractMax) {
            problems += "신규 계약 연봉 상한은 ${newContractMax}억이다"
        }
        return problems
    }

    /** 경기에 동시에 낼 수 있는 인원 (docs/12). 규칙표가 라인업을 짤 때 쓴다 */
    fun canStartTogether(count: Int): Boolean = count <= activeInGame
}

/**
 * 외국인 시장 (docs/12).
 *
 * 값을 매기는 저울은 국내 선수와 같다([Valuation]) — 다만 외국인은 **1년 계약**이고 적응
 * 위험이 있어서, AI 는 같은 능력치라도 국내 선수보다 보수적으로 본다.
 */
class ForeignMarket(
    balance: BalanceConfig,
    private val valuation: Valuation,
    private val marketView: MarketView,
    private val positionNeed: PositionNeed,
) {
    private val potentialScale = baseballgm.scouting.PotentialScale.from(balance)

    val rules: ForeignRules = ForeignRules(balance)

    private val market = balance.section("foreignPlayers.market")
    private val outflow = balance.section("foreignPlayers.outflow")
    private val conversionError = market.double("conversionError")
    private val reSignRaise = market.doubleRange("reSignRaise")
    private val aiSignChance = market.double("aiSignChance")

    val outflowWarThreshold: Double = outflow.double("warThreshold")
    val outflowMultiplier: ClosedFloatingPointRange<Double> = outflow.doubleRange("offerMultiplier")
    val outflowChance: Double = outflow.double("chance")
    val signChance: Double = aiSignChance

    fun conversionOf(originLeague: String): Double = market.double("originLeagues.$originLeague.conversion")

    fun salaryRangeOf(originLeague: String): ClosedFloatingPointRange<Double> =
        market.doubleRange("originLeagues.$originLeague.salary")

    fun labelOf(originLeague: String): String = market.string("originLeagues.$originLeague.label")

    fun leagueKeys(): List<String> = market.section("originLeagues").keys.sorted()

    fun shareOf(originLeague: String): Double = market.double("originLeagues.$originLeague.share")

    /**
     * KBO 환산 기록.
     *
     * 능력치를 성적으로 되돌린 추정치에 **출신 리그 환산 계수와 고정 오차**를 얹는다.
     * 스카우트 범위와 달리 이건 "지난 시즌 기록"이라 정확도와 무관하게 같은 값이 보이지만,
     * 환산 오차 때문에 능력치와 딱 맞지는 않는다.
     */
    fun convertedLine(candidate: ForeignCandidate): ConvertedLine {
        val player = candidate.player
        val conversion = conversionOf(candidate.originLeague)
        val noise = ScoutingView.noise(player.hidden.scoutingNoiseSeed, salt = CONVERSION_SALT)
        val factor = conversion * (1.0 + noise * conversionError)

        return when (player) {
            is Pitcher -> {
                val quality = (player.ratings.stuff * 0.5 + player.ratings.control * 0.5) * factor
                ConvertedLine(
                    isPitcher = true,
                    era = (ERA_BASE - (quality - RATING_PIVOT) * ERA_PER_POINT).coerceIn(1.20, 7.50),
                    whip = (WHIP_BASE - (quality - RATING_PIVOT) * WHIP_PER_POINT).coerceIn(0.80, 2.00),
                    strikeoutsPer9 = (K9_BASE + (player.ratings.stuff * factor - RATING_PIVOT) * K9_PER_POINT)
                        .coerceIn(3.0, 14.0),
                )
            }

            is Batter -> {
                val contact = player.ratings.contact * factor
                val power = player.ratings.power * factor
                val average = (AVG_BASE + (contact - RATING_PIVOT) * AVG_PER_POINT).coerceIn(0.190, 0.370)
                val slug = average + (ISO_BASE + (power - RATING_PIVOT) * ISO_PER_POINT).coerceIn(0.060, 0.330)
                ConvertedLine(
                    isPitcher = false,
                    battingAverage = average,
                    homeRuns = ((power - HR_PIVOT) * HR_PER_POINT).roundToInt().coerceIn(1, 50),
                    ops = average + OBP_EXTRA + slug,
                )
            }
        }
    }

    /** 외국인은 1년 계약이라 스토브리그마다 조건을 다시 정한다. 재계약은 상한이 없다 (docs/12). */
    fun reSignSalary(player: Player, random: kotlin.random.Random): Double =
        round2(player.contract.salary * random.nextInRange(reSignRaise))

    /**
     * AI 가 이 후보에게 낼 수 있는 최대 연봉.
     *
     * 외국인은 1년 계약이라 **올해 기대 WAR 만** 값이 된다. 적응 위험까지 감안해 한 번 더 깎는다.
     */
    fun maximumOffer(candidate: ForeignCandidate, teamId: TeamId, league: League, mode: TeamMode): Double =
        maximumFor(candidate.player, teamId, league, mode, capped = true)

    /**
     * 이 구단이 이 외국인에게 낼 수 있는 최대 연봉.
     *
     * @param capped 신규 계약이면 상한이 걸린다. **재계약은 상한이 없다** (docs/12)
     */
    fun maximumFor(player: Player, teamId: TeamId, league: League, mode: TeamMode, capped: Boolean): Double {
        val estimate = marketView.estimate(player, teamId)
        val war = valuation.expectedWar(player, estimate.overall)
        val need = positionNeed.of(league.playersOf(teamId).filter { it.id != player.id }, player)
        val maximum = war * valuation.salaryPerWar * need * ADAPTATION_RISK *
            if (mode == TeamMode.REBUILD) REBUILD_SHARE else 1.0
        return round2(if (capped) minOf(maximum, rules.newContractMax) else maximum)
    }

    /** 일본 구단 제안액 (docs/12 해외 유출). */
    fun outflowOffer(player: Player, random: kotlin.random.Random): Double =
        round2(player.contract.salary * random.nextInRange(outflowMultiplier))

    /** 스카우트 시선으로 본 외국인 후보. 집중 관찰 슬롯을 쓸 수 있다 (docs/12) */
    fun scouted(candidate: ForeignCandidate, precision: ScoutingPrecision, season: Int) =
        ScoutingView.of(candidate.player, precision, season, potentialScale)

    private fun round2(value: Double): Double = (value * 100).roundToInt() / 100.0

    private companion object {
        const val CONVERSION_SALT = 31
        const val RATING_PIVOT = 50.0
        const val HR_PIVOT = 30.0
        const val ERA_BASE = 4.50
        const val ERA_PER_POINT = 0.075
        const val WHIP_BASE = 1.45
        const val WHIP_PER_POINT = 0.012
        const val K9_BASE = 7.2
        const val K9_PER_POINT = 0.11
        const val AVG_BASE = 0.265
        const val AVG_PER_POINT = 0.0026
        const val ISO_BASE = 0.135
        const val ISO_PER_POINT = 0.0042
        const val OBP_EXTRA = 0.075
        const val HR_PER_POINT = 0.75
        /** 외국인은 적응 위험이 있어 같은 능력치라도 보수적으로 본다 */
        const val ADAPTATION_RISK = 0.85
        const val REBUILD_SHARE = 0.85
    }
}
