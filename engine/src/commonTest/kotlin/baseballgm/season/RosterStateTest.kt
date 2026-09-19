package baseballgm.season

import baseballgm.model.PlayerId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 엔트리 재등록 제한 (docs/07). 임시값: 말소한 다음 주 불가, 그다음 주부터 가능. */
class RosterStateTest {

    private val player = PlayerId("P0001")

    @Test
    fun `말소한 다음 주에는 다시 올릴 수 없다`() {
        val state = RosterState()
        state.markDemoted(player, week = 3)
        assertFalse(state.canPromote(player, week = 3, blockWeeks = 1))
        assertFalse(state.canPromote(player, week = 4, blockWeeks = 1), "말소한 다음 주는 불가")
        assertTrue(state.canPromote(player, week = 5, blockWeeks = 1), "그다음 주부터 가능")
    }

    @Test
    fun `한 번도 말소된 적 없으면 언제든 올릴 수 있다`() {
        assertTrue(RosterState().canPromote(player, week = 1, blockWeeks = 1))
    }

    @Test
    fun `부상 재활 기간이 끝나야 올릴 수 있다`() {
        val state = RosterState()
        state.markDemoted(player, week = 2)
        state.markRehab(player, readyWeek = 9)
        assertFalse(state.canPromote(player, week = 6, blockWeeks = 1), "재활 기간 중에는 불가")
        assertTrue(state.canPromote(player, week = 9, blockWeeks = 1))
    }

    @Test
    fun `재활이 끝나 등록되면 기록이 지워진다`() {
        val state = RosterState()
        state.markDemoted(player, week = 2)
        state.markRehab(player, readyWeek = 9)
        state.clearRehab(player)
        assertTrue(state.canPromote(player, week = 5, blockWeeks = 1))
    }
}
