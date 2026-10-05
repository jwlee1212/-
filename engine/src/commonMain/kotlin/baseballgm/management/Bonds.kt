package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.model.Player
import baseballgm.model.TeamId

/** 장기 재임 선수와의 유대 단계 (docs/13). */
enum class BondLevel(val label: String) {
    NONE(""),
    TEAMMATE("동료"),
    TRUSTED("믿음"),
    FRANCHISE("프랜차이즈"),
}

/** 선수 한 명과 단장의 유대. [seasons] 는 이 단장 밑에서 함께한 시즌 수(올 시즌 포함) */
data class Bond(val seasons: Int, val level: BondLevel) {
    fun label(): String = if (level == BondLevel.NONE) "함께한 ${seasons}시즌" else "${level.label} · 함께한 ${seasons}시즌"
}

/**
 * 장기 재임 선수 유대 (docs/13).
 *
 * "이 단장 밑에서 몇 시즌을 함께했나"만 센다. 선수가 우리 팀에 온 시점은 결정 기록에서 찾고
 * (트레이드·FA·외국인 영입 = 그 시즌, 드래프트 = 다음 시즌 입단), 기록이 없으면 단장 부임 전부터
 * 있던 선수라 부임 시즌부터 센다.
 *
 * 지금은 보여 주기용이다 — 선수 상세의 칩, 커리어 연대기, 비서 브리핑. 능력치·팬심은 바꾸지 않는다.
 */
class Bonds(balance: BalanceConfig) {
    private val levels = balance.section("narrative").section("bonds").intList("levels")

    fun of(
        player: Player,
        userTeam: TeamId,
        season: Int,
        career: CareerRecord?,
        decisions: List<GmDecision>,
    ): Bond? {
        if (player.teamId != userTeam) return null
        val tenureStart = tenureStart(userTeam, season, career)
        val joined = decisions
            .filter { it.playerId == player.id && it.teamId == userTeam }
            .maxByOrNull { it.season * 100 + it.week }
            ?.let { if (it.kind == DecisionKind.DRAFT) it.season + 1 else it.season }
            ?: tenureStart
        val seasons = season - maxOf(tenureStart, joined) + 1
        if (seasons <= 0) return null
        return Bond(seasons, levelOf(seasons))
    }

    fun levelOf(seasons: Int): BondLevel = when {
        seasons >= levels[2] -> BondLevel.FRANCHISE
        seasons >= levels[1] -> BondLevel.TRUSTED
        seasons >= levels[0] -> BondLevel.TEAMMATE
        else -> BondLevel.NONE
    }

    companion object {
        /** 지금 구단에 부임한 시즌. 끝난 시즌 기록에서 같은 구단이 끊김 없이 이어진 첫 해를 찾는다 */
        fun tenureStart(userTeam: TeamId, season: Int, career: CareerRecord?): Int {
            var start = season
            career?.seasons.orEmpty().sortedByDescending { it.season }.forEach { past ->
                if (past.season == start - 1 && past.teamId == userTeam) start = past.season else return start
            }
            return start
        }
    }
}
