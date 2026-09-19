package baseballgm.season

import baseballgm.development.AttributeGroup
import baseballgm.development.CoachAssignment
import baseballgm.development.GrowthContext
import baseballgm.development.PlayingExperience
import baseballgm.io.BalanceConfig
import baseballgm.model.CoachRole
import baseballgm.model.MilitaryStatus
import baseballgm.model.Player
import baseballgm.model.ServiceKind

/**
 * 선수의 성장 환경(출장 경험 + 코치)을 시즌 상태에서 뽑아낸다 (docs/09).
 *
 * 시즌 중 월별 성장([WeekLoop])과 시즌 후 성장([Offseason])이 같은 기준을 쓰도록 한 군데로 모았다.
 */
class GrowthContextResolver(balance: BalanceConfig) {

    private val thresholds = balance.section("growth.playingTimeThresholds")

    fun contextOf(state: SeasonState, player: Player): GrowthContext {
        val teamId = player.teamId ?: return GrowthContext(PlayingExperience.FUTURES_STARTER)
        val coaches = state.league.coachesOf(teamId)
        val assignments = buildMap {
            coaches.forEach { coach ->
                val focus = coach.focus ?: return@forEach
                put(AttributeGroup.of(focus.attribute), CoachAssignment(focus, coach.grade))
            }
        }
        return GrowthContext(
            experience = experienceOf(state, player),
            coaches = assignments,
            futuresManagerGrade = coaches.firstOrNull { it.role == CoachRole.FUTURES_MANAGER }?.grade ?: 0,
            managerSpecialty = state.league.managerOf(teamId)?.specialty,
        )
    }

    /**
     * 출장 경험 분류 (docs/09).
     *
     * 시즌 중에 부르면 "지금까지의 출장량"으로 판단하므로, 시즌 초에는 대부분 출장이 적은 쪽으로
     * 분류된다. 성장의 40%만 시즌 중에 나눠 주기 때문에 영향은 크지 않다.
     */
    fun experienceOf(state: SeasonState, player: Player): PlayingExperience {
        val military = player.military
        if (military is MilitaryStatus.Serving) {
            return if (military.kind == ServiceKind.SANGMU) PlayingExperience.SANGMU else PlayingExperience.ACTIVE_DUTY
        }
        val batting = state.stats.battingOf(player.id).total
        val pitching = state.stats.pitchingOf(player.id).total
        val futuresBatting = state.stats.futuresBattingOf(player.id)
        val futuresPitching = state.stats.futuresPitchingOf(player.id)

        // 시즌 중에 부를 때는 진행률만큼 기준을 낮춘다
        val progress = (state.week.toDouble() / state.calendar.regularSeasonWeeks).coerceIn(MIN_PROGRESS, 1.0)
        fun threshold(key: String): Int = (thresholds.int(key) * progress).toInt()

        return when {
            batting.plateAppearances >= threshold("firstTeamStarterPa") ||
                pitching.outs >= threshold("firstTeamStarterOuts") -> PlayingExperience.FIRST_TEAM_STARTER

            batting.plateAppearances >= threshold("firstTeamBenchPa") ||
                pitching.outs >= threshold("firstTeamBenchOuts") -> PlayingExperience.FIRST_TEAM_BENCH

            futuresBatting.plateAppearances >= threshold("futuresStarterPa") ||
                futuresPitching.outs >= threshold("futuresStarterOuts") -> PlayingExperience.FUTURES_STARTER

            // 1군에도 2군에도 거의 못 나온 선수는 부상 결장과 같은 취급을 한다
            else -> PlayingExperience.INJURED
        }
    }

    private companion object {
        const val MIN_PROGRESS = 0.2
    }
}
