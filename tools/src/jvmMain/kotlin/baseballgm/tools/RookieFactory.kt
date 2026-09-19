package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.PitcherRole
import baseballgm.model.Player
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.season.RookieSupplier
import baseballgm.util.chance
import baseballgm.util.nextGaussian
import kotlin.random.Random

/**
 * 임시 신인 유입 (M5).
 *
 * 은퇴로 빠진 자리를 신인으로 채워 선수단 규모를 유지한다. **실제 드래프트가 아니다** —
 * 지명 순서도, 스카우트 정보도, 계약금도 없다. M6 에서 드래프트가 이 자리를 대신한다.
 *
 * 선수 id 는 기존 선수와 겹치지 않도록 리그에서 가장 큰 번호 뒤부터 이어 붙인다.
 */
class RookieFactory(
    private val balance: BalanceConfig,
    strength: StrengthCalculator,
    league: League,
) : RookieSupplier {

    private val params = GenerationParams(balance)
    private val names = NameGenerator(Random(league.seed xor NAME_SEED_MIX))
    private val section = balance.section("rookieIntake")
    private val potentialMean = Pair2.of(section, "potential")
    private val strengthCalculator = strength

    /** 기존 선수와 id 가 겹치지 않게 리그에서 가장 큰 번호 뒤부터 이어 붙인다. */
    private var nextId = nextIdNumber(league)

    override fun create(count: Int, season: Int, teamId: TeamId, random: Random): List<Player> {
        // 나이·데뷔 연도가 맞으려면 생성기가 "올해"를 알아야 한다
        val generator = PlayerGenerator(
            params = params,
            strength = strengthCalculator,
            names = names,
            season = season,
            startingIdNumber = nextId,
        )
        nextId += count
        return (0 until count).map { _ ->
            val isPitcher = random.chance(PITCHER_SHARE)
            generator.generate(
                PlayerSpec(
                    role = GenerationRole.FUTURES,
                    rosterLevel = RosterLevel.FUTURES,
                    position = if (isPitcher) null else Position.fielding.random(random),
                    pitcherRole = if (isPitcher) {
                        if (random.chance(STARTER_SHARE)) PitcherRole.STARTER else PitcherRole.RELIEVER
                    } else {
                        null
                    },
                    potentialOverride = random.nextGaussian(potentialMean.first, potentialMean.second),
                    ageRange = ROOKIE_AGE,
                    debutSeasonOverride = season,
                ),
                teamId,
                TeamFlavor(),
                random,
            )
        }
    }

    private fun nextIdNumber(league: League): Int =
        league.players.mapNotNull { it.id.value.removePrefix("P").toIntOrNull() }.maxOrNull()?.plus(1) ?: 1

    private companion object {
        val ROOKIE_AGE = 18..23
        const val PITCHER_SHARE = 0.45
        const val STARTER_SHARE = 0.45
        const val NAME_SEED_MIX = 0x5EEDL
    }
}
