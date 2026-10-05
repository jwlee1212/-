package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.util.iGa

/** 1군 포지션 칸. 투수는 선발/불펜, 야수는 주 포지션 */
enum class RosterSlot(val key: String, val label: String, val pitcher: Boolean) {
    SP("SP", "선발", true),
    RP("RP", "불펜", true),
    C("C", "포수", false),
    FIRST("1B", "1루수", false),
    SECOND("2B", "2루수", false),
    THIRD("3B", "3루수", false),
    SHORT("SS", "유격수", false),
    LEFT("LF", "좌익수", false),
    CENTER("CF", "중견수", false),
    RIGHT("RF", "우익수", false),
    DH("DH", "지명타자", false),
    ;

    companion object {
        fun of(player: Player): RosterSlot = when (player) {
            is Pitcher -> if (player.role.isReliever) RP else SP
            is Batter -> entries.first { !it.pitcher && it.key == player.primaryPosition.label }
        }
    }
}

/** 칸 하나의 지금 인원과 범위 */
data class SlotCount(val label: String, val count: Int, val healthy: Int, val range: IntRange) {
    val over: Boolean get() = count > range.last
    val under: Boolean get() = healthy < range.first
    val ok: Boolean get() = !over && !under
}

/**
 * 1군 포지션별 등록 인원 범위 (docs/07 엔트리 규칙, 2026-10-01 유저 요청).
 *
 * 28명만 지키고 "2군에서 제일 잘하는 선수"로 빈자리를 채우면 유격수가 다섯, 포수가 하나인 1군이 나온다.
 * 그래서 칸마다 [최소, 최대]를 두고, 투수 전체·야수 전체에도 범위를 둔다.
 *
 * 두 가지를 구분해서 센다.
 * - **최대**는 등록된 인원 전부로 센다 (부상자도 자리를 차지한다).
 * - **최소**는 뛸 수 있는(부상 아닌) 인원으로 센다 — 포수 둘 중 하나가 다치면 실제로는 포수가 하나뿐이다.
 *
 * 이미 범위를 벗어난 상태(부상이 겹쳤거나 개막 데이터가 그랬거나)에서는 **더 나빠지는 이동만** 막는다.
 * 그래야 "고치는 중"인 이동이 막혀서 오도 가도 못하는 일이 없다.
 */
class PositionLimits(balance: BalanceConfig) {

    private val section = balance.section("roster.positionLimits")
    private val slotRanges: Map<RosterSlot, IntRange> =
        RosterSlot.entries.associateWith { section.intRange("positions.${it.key}") }
    private val pitcherRange = section.intRange("groups.pitchers")
    private val batterRange = section.intRange("groups.batters")

    fun rangeOf(slot: RosterSlot): IntRange = slotRanges.getValue(slot)

    fun groupRange(pitcher: Boolean): IntRange = if (pitcher) pitcherRange else batterRange

    /** 칸별 인원 (화면 표시용). 투수 전체·야수 전체가 맨 앞 */
    fun counts(firstTeam: List<Player>): List<SlotCount> =
        listOf(groupCount(firstTeam, true), groupCount(firstTeam, false)) +
            RosterSlot.entries.map { slot ->
                val members = firstTeam.filter { RosterSlot.of(it) == slot }
                SlotCount(slot.label, members.size, members.count { !it.condition.isInjured }, rangeOf(slot))
            }

    private fun groupCount(firstTeam: List<Player>, pitcher: Boolean): SlotCount {
        val members = firstTeam.filter { (it is Pitcher) == pitcher }
        return SlotCount(if (pitcher) "투수" else "야수", members.size, members.count { !it.condition.isInjured }, groupRange(pitcher))
    }

    /** 범위를 벗어난 칸 (투수·야수 전체 포함) */
    fun violations(firstTeam: List<Player>): List<SlotCount> = counts(firstTeam).filterNot { it.ok }

    /**
     * [before] → [after] 로 바꿀 때 범위를 **새로 어기거나 더 어기게** 되면 그 이유. 괜찮으면 null.
     */
    fun problem(before: List<Player>, after: List<Player>): String? {
        val old = counts(before).associateBy { it.label }
        counts(after).forEach { now ->
            val was = old.getValue(now.label)
            if (now.over && now.count > was.count) return "1군 ${now.label.iGa()} 최대 ${now.range.last}명을 넘어요"
            if (now.under && now.healthy < was.healthy) return "1군 ${now.label.iGa()} 최소 ${now.range.first}명 아래로 떨어져요"
        }
        return null
    }

    /** 이 칸에 한 명 더 올려도 최대를 안 넘나 (칸·전체 모두) */
    fun hasRoom(firstTeam: List<Player>, player: Player): Boolean {
        val slot = RosterSlot.of(player)
        return firstTeam.count { RosterSlot.of(it) == slot } < rangeOf(slot).last &&
            firstTeam.count { (it is Pitcher) == (player is Pitcher) } < groupRange(player is Pitcher).last
    }

    /** 이 선수를 내려도 (뛸 수 있는 인원 기준) 최소를 안 깨나 */
    fun canSpare(firstTeam: List<Player>, player: Player): Boolean {
        if (player.condition.isInjured) return true
        val slot = RosterSlot.of(player)
        val healthy = firstTeam.filter { !it.condition.isInjured }
        return healthy.count { RosterSlot.of(it) == slot } > rangeOf(slot).first &&
            healthy.count { (it is Pitcher) == (player is Pitcher) } > groupRange(player is Pitcher).first
    }

    /** 이 선수를 내려도 그 칸의 최소를 안 깨나 (투수·야수 전체는 안 본다 — 같은 쪽끼리 맞바꿀 때) */
    fun canSpareSlot(firstTeam: List<Player>, player: Player): Boolean {
        if (player.condition.isInjured) return true
        val slot = RosterSlot.of(player)
        return firstTeam.count { !it.condition.isInjured && RosterSlot.of(it) == slot } > rangeOf(slot).first
    }

    /** 최소에 못 미치는 칸들 (뛸 수 있는 인원 기준), 모자란 수가 큰 순 */
    fun shortSlots(firstTeam: List<Player>): List<RosterSlot> =
        RosterSlot.entries.map { slot -> slot to rangeOf(slot).first - firstTeam.count { RosterSlot.of(it) == slot && !it.condition.isInjured } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

    /** 최대를 넘은 칸들 */
    fun crowdedSlots(firstTeam: List<Player>): List<RosterSlot> =
        RosterSlot.entries.filter { slot -> firstTeam.count { RosterSlot.of(it) == slot } > rangeOf(slot).last }

    /** 투수/야수 전체가 최소에 못 미치는 쪽 (null = 둘 다 괜찮음) */
    fun shortGroup(firstTeam: List<Player>): Boolean? = listOf(true, false).firstOrNull { pitcher ->
        firstTeam.count { (it is Pitcher) == pitcher && !it.condition.isInjured } < groupRange(pitcher).first
    }
}
