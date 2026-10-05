package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.model.TeamId
import baseballgm.util.interpolateAnchors

/**
 * 지명권 가치표 (docs/10).
 *
 * 개발 단계에서 드래프트를 수백 회 시뮬레이션해 "순번별 기대 가치"를 뽑아 `config/balance.json`
 * 에 넣어 둔 표다 (`cli pickvalue`). 트레이드 AI가 선수와 지명권을 같은 저울에 올릴 때 쓴다.
 *
 * 미래 지명권은 순번을 알 수 없으므로 **지금 순위로 추정**하고, 불확실한 만큼 할인한다.
 */
class DraftPickValue(balance: BalanceConfig) {

    private val section = balance.section("draftPickValue")
    private val anchors: Map<Int, Double> = section.numericMap("byPick")
    private val futureDiscount: Double = section.double("futurePickDiscount")

    /** 전체 순번(1번부터) 기준 가치. 1순위가 1.0 이다. */
    fun ofOverallPick(overallPick: Int): Double = interpolateAnchors(anchors, overallPick.toDouble())

    fun ofRoundAndSlot(round: Int, slot: Int, teams: Int): Double =
        ofOverallPick((round - 1) * teams + slot)

    /**
     * 지명권 한 장의 가치.
     *
     * @param slotOf 원소속팀의 지명 순번(1 = 전체 1순위). 미래 지명권은 지금 순위로 추정한 값이다
     * @param seasonsAhead 몇 년 뒤 지명권인가. 1년 뒤면 한 번 할인한다
     */
    fun of(pick: DraftPickRight, teams: Int, seasonsAhead: Int, slotOf: (TeamId) -> Int): Double {
        val base = ofRoundAndSlot(pick.round, slotOf(pick.originalTeam), teams)
        var value = base
        repeat(seasonsAhead.coerceAtLeast(0)) { value *= futureDiscount }
        return value
    }
}
