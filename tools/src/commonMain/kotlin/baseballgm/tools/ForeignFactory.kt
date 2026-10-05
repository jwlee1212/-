package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.market.ForeignCandidate
import baseballgm.market.ForeignSupplier
import baseballgm.model.PitcherRole
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.util.chance
import baseballgm.util.nextGaussian
import baseballgm.util.nextInRange
import baseballgm.util.weightedPick
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 외국인 시장 생성기 (docs/12).
 *
 * 출신 리그(트리플A·일본·대만·중남미)를 가중 추출하고, 리그마다 다른 요구 연봉대를 쓴다.
 * **능력치는 리그와 무관하게 뽑는다** — 리그가 정하는 것은 요구 연봉과 환산 계수(즉 정보의 질)이지
 * 실제 실력이 아니기 때문이다. 그래서 "싼 대만 리그 출신이 대박"인 경우가 생긴다.
 *
 * 적응력(숨김)은 [PlayerGenerator] 가 외국인에게만 채워 준다.
 */
class ForeignFactory(
    balance: BalanceConfig,
    strength: StrengthCalculator,
    seed: Long,
    private val startingIdNumber: Int,
) : ForeignSupplier {

    private val params = GenerationParams(balance)
    private val strengthCalculator = strength
    private val names = NameGenerator(Random(seed xor NAME_SEED_MIX))
    private val market = balance.section("foreignPlayers.market")
    private val pitcherShare = market.double("pitcherShare")
    private val newContractMax = balance.double("foreignPlayers.newContractMaxTotal")

    private val foreignPotential = Pair2.of(balance.section("leagueGeneration"), "potentialByRole.foreign")
    private val leagueKeys = market.section("originLeagues").keys.sorted()
    private val shares = leagueKeys.associateWith { market.double("originLeagues.$it.share") }

    private var nextId = startingIdNumber

    override fun create(season: Int, count: Int, random: Random): List<ForeignCandidate> {
        val generator = PlayerGenerator(
            params = params,
            strength = strengthCalculator,
            names = names,
            season = season,
            startingIdNumber = nextId,
        )
        nextId += count

        return (0 until count).map {
            val leagueKey = random.weightedPick(shares)
            val isPitcher = random.chance(pitcherShare)
            val player = generator.generate(
                PlayerSpec(
                    role = if (isPitcher) GenerationRole.ROTATION_STARTER else GenerationRole.LINEUP_STARTER,
                    rosterLevel = RosterLevel.FUTURES,
                    position = if (isPitcher) null else Position.fielding.random(random),
                    pitcherRole = if (isPitcher) {
                        if (random.chance(STARTER_SHARE)) PitcherRole.STARTER else PitcherRole.RELIEVER
                    } else {
                        null
                    },
                    foreign = true,
                    potentialOverride = random.nextGaussian(foreignPotential.first, foreignPotential.second),
                    debutSeasonOverride = season,
                ),
                teamId = null,
                flavor = TeamFlavor(),
                random = random,
            )
            // 요구 연봉은 출신 리그대로 부르지만 신규 계약 상한을 넘지는 않는다 (docs/12)
            val salaryRange = market.doubleRange("originLeagues.$leagueKey.salary")
            val asking = minOf(random.nextInRange(salaryRange), newContractMax)
            ForeignCandidate(
                player = player,
                originLeague = leagueKey,
                originLabel = market.string("originLeagues.$leagueKey.label"),
                askingSalary = (asking * 100).roundToInt() / 100.0,
            )
        }
    }

    private companion object {
        const val NAME_SEED_MIX = 0xF0BE1CL
        const val STARTER_SHARE = 0.7
    }
}
