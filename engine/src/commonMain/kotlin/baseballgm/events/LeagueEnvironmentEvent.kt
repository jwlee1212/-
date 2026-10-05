package baseballgm.events

import baseballgm.io.BalanceConfig
import baseballgm.league.LeagueEnvironment
import baseballgm.util.chance
import baseballgm.util.nextInRange
import kotlin.random.Random

/**
 * 리그 환경 변동 (docs/04).
 *
 * @param announcement 발표 문구. 유저가 보는 것은 이것뿐이다
 * @param environment 실제로 적용되는 값
 */
data class LeagueEnvironmentChange(
    val announcement: String,
    val environment: LeagueEnvironment,
) {
    val changed: Boolean get() = environment != LeagueEnvironment.NEUTRAL
}

/**
 * 스토브리그 시작 때 발표되는 리그 환경 이벤트 (docs/04).
 *
 * 공인구 반발력이나 스트라이크존이 바뀌면 리그 전체의 홈런·삼진·볼넷이 움직인다. 유저는
 * **발표만 보고 다음 시즌 선수 구성을 판단**해야 한다 — 그래서 발표 내용과 실제 효과 사이에
 * 작은 오차를 둔다 (docs/04 "발표 내용과 실제 효과 사이에 작은 오차"). 반발력이 올랐다고
 * 거포를 모았는데 생각만큼 안 오르는 일이 생긴다.
 */
class LeagueEnvironmentEvents(balance: BalanceConfig) {

    private val section = balance.section("leagueEnvironmentEvents")
    private val noChangeChance = section.double("noChangeChance")
    private val homeRunDelta = section.doubleRange("ballLiveliness.homeRunDelta")
    private val kBbDelta = section.doubleRange("strikeZone.kBbDeltaPoints")
    private val announcementError = section.double("announcementError")

    fun next(random: Random): LeagueEnvironmentChange {
        if (random.chance(noChangeChance)) {
            return LeagueEnvironmentChange("리그 환경 변화 없음", LeagueEnvironment.NEUTRAL)
        }
        return if (random.chance(BALL_SHARE)) ballChange(random) else zoneChange(random)
    }

    /** 공인구 반발력: 홈런·장타가 함께 움직인다. */
    private fun ballChange(random: Random): LeagueEnvironmentChange {
        val up = random.chance(UP_SHARE)
        val announced = random.nextInRange(homeRunDelta)
        val actual = announced * (1.0 + random.nextInRange(-announcementError..announcementError))
        val multiplier = 1.0 + if (up) actual else -actual
        val direction = if (up) "높이기로" else "낮추기로"
        return LeagueEnvironmentChange(
            announcement = "공인구 반발계수를 $direction 했다 (홈런 ${percent(announced, up)} 예상)",
            environment = LeagueEnvironment(homeRunMultiplier = multiplier),
        )
    }

    /** 스트라이크존: 넓어지면 삼진이 늘고 볼넷이 줄어든다. */
    private fun zoneChange(random: Random): LeagueEnvironmentChange {
        val wider = random.chance(UP_SHARE)
        val announced = random.nextInRange(kBbDelta)
        val actual = announced * (1.0 + random.nextInRange(-announcementError..announcementError))
        val sign = if (wider) 1.0 else -1.0
        return LeagueEnvironmentChange(
            announcement = "스트라이크존을 ${if (wider) "넓게" else "좁게"} 적용한다 " +
                "(삼진 ${percent(announced, wider)} 예상)",
            environment = LeagueEnvironment(
                strikeoutDelta = sign * actual,
                walkDelta = -sign * actual * WALK_SHARE,
            ),
        )
    }

    private fun percent(value: Double, up: Boolean): String =
        (if (up) "+" else "−") + "${(value * 100).toInt()}%"

    private companion object {
        const val BALL_SHARE = 0.5
        const val UP_SHARE = 0.5
        /** 존이 넓어질 때 볼넷이 줄어드는 정도는 삼진이 늘어나는 정도보다 작다 */
        const val WALK_SHARE = 0.7
    }
}
