package baseballgm.scouting

import baseballgm.model.PlayerId
import baseballgm.model.TeamId

/** 집중 관찰 한 건. */
data class FocusAssignment(val playerId: PlayerId, val weeks: Int)

/**
 * 한 구단의 스카우트 부서 (docs/10).
 *
 * 투자 단계와 "지금 누구를 집중 관찰하고 있는지", "몇 주째인지"를 들고 있다.
 * 관찰 주차는 **슬롯에서 빼도 사라지지 않는다** — 이미 본 것을 잊지는 않기 때문이다.
 * 대신 빼는 순간부터 더 이상 쌓이지 않는다.
 */
class ScoutingDepartment(
    val teamId: TeamId,
    level: Int,
) {
    var level: Int = level
        private set

    /**
     * 자동 집중 관찰 (유저 요청 2026-10-03). 켜져 있으면 매주 초 빈 슬롯을 추천 순으로 채우고,
     * 더 봐도 정확도가 오르지 않는 선수는 슬롯에서 뺀다. 유저 구단에만 쓴다
     */
    var autoFocus: Boolean = true

    /** 지명 추천 기준. 자동 관찰이 슬롯을 채우는 순서도 이 기준을 따른다 */
    var draftPolicy: baseballgm.market.DraftPolicy = baseballgm.market.DraftPolicy.BALANCED

    private val observedWeeks = mutableMapOf<PlayerId, Int>()
    private val active = mutableSetOf<PlayerId>()

    /** 자동 관찰이 붙인 선수 (붙인 순서). 유저가 직접 붙일 자리가 없으면 맨 나중 것부터 비켜 준다 */
    private val autoAssigned = linkedSetOf<PlayerId>()

    fun setLevel(next: Int) {
        level = next
    }

    fun activeFocus(): Set<PlayerId> = active.toSet()

    fun weeksOn(playerId: PlayerId): Int = observedWeeks[playerId] ?: 0

    fun isFocused(playerId: PlayerId): Boolean = playerId in active

    /** 슬롯이 남아 있으면 집중 관찰을 시작한다. 이미 보고 있으면 아무 일도 없다. */
    fun addFocus(playerId: PlayerId, slots: Int): Boolean {
        if (playerId in active) return true
        if (active.size >= slots) return false
        active += playerId
        return true
    }

    fun removeFocus(playerId: PlayerId) {
        active -= playerId
        autoAssigned -= playerId
    }

    /** 자동 관찰로 붙인다. 직접 붙인 선수와 구분해 둔다 */
    fun addAutoFocus(playerId: PlayerId, slots: Int): Boolean {
        if (playerId in active) return true
        return addFocus(playerId, slots).also { if (it) autoAssigned += playerId }
    }

    fun isAutoAssigned(playerId: PlayerId): Boolean = playerId in autoAssigned

    /** 유저가 직접 붙일 수 있나: 빈 슬롯이 있거나, 비켜 줄 자동 관찰 선수가 있다 */
    fun canAddManually(slots: Int): Boolean = active.size < slots || autoAssigned.isNotEmpty()

    /**
     * 유저가 직접 붙인다. 슬롯이 꽉 찼으면 **자동으로 붙었던 선수 중 맨 나중 것**을 빼고 자리를 만든다 —
     * 자동 관찰이 슬롯을 다 채워도 유저 선택이 우선이다. 직접 붙인 선수는 자동 관찰이 밀어내지 않는다.
     * 이미 자동으로 보고 있던 선수면 "직접 붙인 선수"로 바뀐다.
     */
    fun addFocusManually(playerId: PlayerId, slots: Int): Boolean {
        if (playerId in active) {
            autoAssigned -= playerId
            return true
        }
        if (active.size >= slots) autoAssigned.lastOrNull()?.let(::removeFocus)
        return addFocus(playerId, slots)
    }

    /** 슬롯이 줄어들면(투자 단계 하향) 오래 본 선수부터 남긴다. */
    fun trimToSlots(slots: Int) {
        if (active.size <= slots) return
        val keep = active.sortedByDescending { weeksOn(it) }.take(slots).toSet()
        active.retainAll(keep)
        autoAssigned.retainAll(keep)
    }

    /** 한 주가 지났다. 보고 있는 선수의 관찰 주차가 1 늘어난다. */
    fun observeWeek() {
        active.forEach { observedWeeks[it] = weeksOn(it) + 1 }
    }

    /** 세이브: 관찰 주차와 지금 관찰 중인 선수 (순서 그대로) */
    internal fun export(): Pair<Map<PlayerId, Int>, List<PlayerId>> = observedWeeks.toMap() to active.toList()

    internal fun import(observed: Map<PlayerId, Int>, focused: List<PlayerId>) {
        observedWeeks.clear(); observedWeeks.putAll(observed)
        active.clear(); active.addAll(focused)
    }

    /** 세이브: 자동으로 붙은 선수 (붙인 순서) */
    internal fun exportAuto(): List<PlayerId> = autoAssigned.toList()

    internal fun importAuto(ids: List<PlayerId>) {
        autoAssigned.clear(); autoAssigned.addAll(ids.filter { it in active })
    }

    fun assignments(): List<FocusAssignment> =
        active.map { FocusAssignment(it, weeksOn(it)) }.sortedByDescending { it.weeks }
}
