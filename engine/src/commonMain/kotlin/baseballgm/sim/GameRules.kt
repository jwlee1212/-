package baseballgm.sim

import baseballgm.io.BalanceConfig

/**
 * 경기 종류별 규칙 (docs/05).
 *
 * 정규시즌은 11회까지 하고 동점이면 무승부, 포스트시즌은 15회까지 하고 무승부면 시리즈에 경기를 더한다.
 * 시뮬레이터는 이 규칙만 보고 돌기 때문에 정규·포스트시즌 코드가 따로 없다 (불변 원칙 5와 같은 이유).
 */
data class GameRules(
    val regulationInnings: Int,
    val maxInnings: Int,
    val tiesAllowed: Boolean,
) {
    companion object {
        const val REGULATION_INNINGS: Int = 9

        fun regularSeason(balance: BalanceConfig): GameRules = GameRules(
            regulationInnings = REGULATION_INNINGS,
            maxInnings = balance.int("gameRules.regularSeason.maxInnings"),
            tiesAllowed = balance.boolean("gameRules.regularSeason.tiesAllowed"),
        )

        fun postseason(balance: BalanceConfig): GameRules = GameRules(
            regulationInnings = REGULATION_INNINGS,
            maxInnings = balance.int("gameRules.postseason.maxInnings"),
            tiesAllowed = balance.boolean("gameRules.postseason.tiesAllowed"),
        )
    }
}
