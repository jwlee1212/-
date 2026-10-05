package baseballgm.app

import baseballgm.io.BalanceConfig
import baseballgm.market.TradeVerdict

/** 상대 반응 단계. 색은 화면이 의미 색에서 고른다 */
enum class ReactionLevel(val label: String) {
    YES("받아들일 것 같아요"),
    CLOSE("조금 모자라요"),
    GAP("차이가 있어요"),
    FAR("차이가 커요"),
    BLOCKED("규칙에 걸려요"),
}

/**
 * 트레이드 교환대의 상대 반응 (2026-10-03 화면 개편 — 더쇼 교환대를 캐주얼하게).
 *
 * 숫자(억 단위 가치) 대신 [cells] 칸 게이지와 비서 한마디로 보여 준다. 근거는 기존 "미리 보기" 판정
 * ([GameSession.previewTrade] — 협상 피로도가 쌓이지 않는다)의 받는 값 / 요구하는 값 비율이다.
 * 칸 수·기준은 `balance.json` `tradeGauge`.
 */
data class TradeReaction(val level: ReactionLevel, val filled: Int, val cells: Int, val hint: String) {
    companion object {
        fun of(verdict: TradeVerdict, balance: BalanceConfig): TradeReaction {
            val cells = balance.int("tradeGauge.cells")
            val close = balance.double("tradeGauge.closeRatio")
            val far = balance.double("tradeGauge.farRatio")
            if (verdict.problems.isNotEmpty()) {
                return TradeReaction(ReactionLevel.BLOCKED, 0, cells, verdict.problems.first())
            }
            if (verdict.accepted) {
                return TradeReaction(ReactionLevel.YES, cells, cells, "이 정도면 사인할 것 같아요. 제안해 보세요.")
            }
            val ratio = if (verdict.requiredValue <= 0.0) 0.0 else (verdict.incomingValue / verdict.requiredValue).coerceIn(0.0, 1.0)
            // 수락 직전까지는 마지막 칸을 비워 둔다 — 꽉 찬 게이지는 "받아들인다"일 때만
            val filled = (ratio * (cells - 1)).toInt().coerceIn(if (ratio > 0.0) 1 else 0, cells - 1)
            val level = when {
                ratio >= close -> ReactionLevel.CLOSE
                ratio >= far -> ReactionLevel.GAP
                else -> ReactionLevel.FAR
            }
            val hint = when (level) {
                ReactionLevel.CLOSE -> "조금만 더 얹으면 될 것 같아요. 2군 유망주 한 명 정도면 어떨까요?"
                ReactionLevel.GAP -> "우리 쪽이 모자라요. 주전급 한 명은 더 있어야 할 것 같아요."
                else -> "${verdict.reason.ifBlank { "가치 차이가 커요" }}. 받을 선수를 줄이거나 다른 조합을 찾아봐요."
            }
            return TradeReaction(level, filled, cells, hint)
        }
    }
}
