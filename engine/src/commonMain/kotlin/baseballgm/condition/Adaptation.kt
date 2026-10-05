package baseballgm.condition

import baseballgm.io.BalanceConfig
import baseballgm.model.Origin
import baseballgm.model.Player
import kotlin.math.max
import kotlin.math.min

/**
 * 외국인 선수의 KBO 적응 (docs/12).
 *
 * 적응력(숨김 수치)이 낮으면 **능력치가 좋아도 성적이 안 나온다.** 그 차이를 능력치 감점으로
 * 표현하고, 감점은 시즌이 갈수록 줄어들지만 첫 시즌에 완전히 사라지지는 않는다.
 *
 * 이렇게 둔 이유: 감점이 6주만에 사라지면 *"기록이 나쁘면 조금만 기다리면 된다"* 가 되어
 * 교체 결정이 의미를 잃는다. 반대로 영구 감점이면 뽑는 순간 끝나 버린다. 그래서
 * **초반에 크게, 시즌 중에 조금씩, 두 번째 시즌부터 대부분** 사라지게 했다 —
 * 적응력이 낮은 선수를 붙잡고 기다릴지 갈아치울지가 시즌 초의 진짜 고민이 된다.
 */
class AdaptationModel(balance: BalanceConfig) {

    private val section = balance.section("foreignPlayers.adaptation")
    private val maxPenalty = section.double("maxRatingPenalty")
    private val firstSeasonRecovery = section.double("firstSeasonRecovery")
    private val weeksToSettle = section.int("weeksToSettle")
    private val secondSeasonShare = section.double("secondSeasonShare")
    private val settledFromSeason = section.int("settledFromSeason")

    /**
     * 이번 주의 적응 감점.
     *
     * @param season 지금 시즌
     * @param week 주차 (1부터)
     */
    fun penaltyFor(player: Player, season: Int, week: Int): Double {
        if (player.origin != Origin.FOREIGN) return 0.0
        val adaptability = player.hidden.adaptability ?: return 0.0
        val kboSeason = season - player.debutSeason + 1
        if (kboSeason >= settledFromSeason) return 0.0

        // 적응력 100 이면 감점 0, 0 이면 최대 감점
        val base = maxPenalty * (1.0 - adaptability / MAX_RATING)
        return when {
            kboSeason <= 1 -> {
                val settled = min(1.0, max(0, week - 1).toDouble() / weeksToSettle)
                base * (1.0 - firstSeasonRecovery * settled)
            }

            else -> base * secondSeasonShare
        }
    }

    /** 적응이 어디까지 왔는지 보여 줄 문구. 숨김 수치를 그대로 노출하지 않는다 */
    fun label(penalty: Double): String = when {
        penalty <= 0.5 -> "적응 완료"
        penalty <= 3.0 -> "리그에 익숙해졌다"
        penalty <= 7.0 -> "아직 적응 중"
        else -> "리그에 적응하지 못하고 있다"
    }

    private companion object {
        const val MAX_RATING = 100.0
    }
}
