package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRole
import baseballgm.model.Player
import baseballgm.model.Position

/**
 * 포지션 필요도 0.8 ~ 1.5 (docs/11).
 *
 * 드래프트 지명·FA 입찰·트레이드가 모두 같은 가중치를 쓴다. 그래서 "남는 포지션을 주고받는"
 * 거래가 AI끼리도 성립한다.
 *
 * **영입 이후 상태로 계산한다** — 한 번에 같은 포지션 두 명을 받으면 두 번째는 가치가 떨어진다
 * ([afterAdding] 으로 이미 받기로 한 선수를 명단에 넣고 다시 계산한다).
 */
class PositionNeed(balance: BalanceConfig, private val strength: StrengthCalculator) {

    private val section = balance.section("positionNeed")
    private val critical = section.doubleRange("critical")
    private val noBackup = section.double("noBackup")
    private val adequate = section.double("adequate")
    /** 같은 포지션이 넘칠 때의 배율. 한 드래프트에서 같은 자리를 연달아 뽑을 때도 쓴다 */
    val surplus: Double = section.double("surplus")

    private val starterLevel = balance.double("ratingScale.starterAverage")
    private val rotationSlots = balance.doubleList("teamStrength.rotationWeights").size
    private val bullpenSlots = balance.doubleList("teamStrength.bullpenWeights").size

    /** [candidate] 를 데려온다면 이 팀에 얼마나 필요한가. */
    fun of(roster: List<Player>, candidate: Player): Double = when (candidate) {
        is Batter -> forPosition(roster, candidate.primaryPosition)
        is Pitcher -> forPitcherRole(roster, candidate.role)
    }

    /** 이미 받기로 한 선수들을 명단에 넣은 뒤의 필요도. */
    fun afterAdding(roster: List<Player>, incoming: List<Player>, candidate: Player): Double =
        of(roster + incoming, candidate)

    fun forPosition(roster: List<Player>, position: Position): Double {
        if (position == Position.DESIGNATED_HITTER) return surplus
        val ranked = roster.asSequence()
            .filterIsInstance<Batter>()
            .filter { it.military.isAvailable && it.primaryPosition == position }
            .map { strength.overallOf(it) }
            .sortedDescending()
            .toList()

        val best = ranked.firstOrNull() ?: 0.0
        val second = ranked.getOrNull(1) ?: 0.0
        return when {
            best < starterLevel -> criticalValue(starterLevel - best)
            second < BACKUP_SHARE * starterLevel -> noBackup
            second >= starterLevel -> surplus
            else -> adequate
        }
    }

    fun forPitcherRole(roster: List<Player>, role: PitcherRole): Double {
        val slots = if (role == PitcherRole.STARTER) rotationSlots else bullpenSlots
        val qualified = roster.asSequence()
            .filterIsInstance<Pitcher>()
            .filter { it.military.isAvailable && it.role.isReliever == role.isReliever }
            .count { strength.overallOf(it) >= starterLevel }

        val coverage = qualified.toDouble() / slots
        return when {
            coverage < CRITICAL_COVERAGE -> criticalValue((CRITICAL_COVERAGE - coverage) * starterLevel)
            coverage < THIN_COVERAGE -> noBackup
            coverage <= FULL_COVERAGE -> adequate
            else -> surplus
        }
    }

    /** 모자랄수록 1.5 에 가까워진다. */
    private fun criticalValue(deficit: Double): Double {
        val ratio = (deficit / CRITICAL_FULL_DEFICIT).coerceIn(0.0, 1.0)
        return critical.start + (critical.endInclusive - critical.start) * ratio
    }

    private companion object {
        /** 주전 기준의 이 비율에 못 미치는 두 번째 선수는 "백업이 없다"로 본다. */
        const val BACKUP_SHARE = 0.85
        const val CRITICAL_COVERAGE = 0.4
        const val THIN_COVERAGE = 0.8
        const val FULL_COVERAGE = 1.0
        const val CRITICAL_FULL_DEFICIT = 15.0
    }
}
