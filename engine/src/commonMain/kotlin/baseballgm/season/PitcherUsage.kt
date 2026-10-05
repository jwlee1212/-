package baseballgm.season

import baseballgm.model.PlayerId
import baseballgm.sim.PitcherAvailability

/**
 * 투수 등판 이력 (docs/06 ④ 연투 제한, 최근 7일 투구수).
 *
 * 경기 안에서는 규칙표가 "연투 며칠까지"만 보고, 실제로 며칠 연속 던졌는지는 여기서 센다.
 * 날짜는 시즌 시작부터의 통산 일수로 다룬다 (주차 × 7 + 요일).
 */
class PitcherUsage {

    private val appearances = mutableMapOf<PlayerId, MutableList<Appearance>>()

    private data class Appearance(val day: Int, val pitches: Int)

    fun record(pitcherId: PlayerId, day: Int, pitches: Int) {
        appearances.getOrPut(pitcherId) { mutableListOf() } += Appearance(day, pitches)
    }

    /** 어제까지 며칠 연속으로 던졌는가. */
    fun consecutiveDays(pitcherId: PlayerId, today: Int): Int {
        val days = appearances[pitcherId]?.map { it.day }?.toSet() ?: return 0
        var count = 0
        var day = today - 1
        while (day in days) {
            count++
            day--
        }
        return count
    }

    fun pitchesLast7Days(pitcherId: PlayerId, today: Int): Int =
        appearances[pitcherId].orEmpty().filter { it.day > today - WINDOW && it.day <= today }.sumOf { it.pitches }

    fun lastAppearanceDay(pitcherId: PlayerId): Int? = appearances[pitcherId]?.maxOfOrNull { it.day }

    /** 오늘 경기에 넘길 가용 상태를 만든다. */
    fun availabilityFor(pitcherIds: Collection<PlayerId>, today: Int, unavailable: Set<PlayerId>): PitcherAvailability =
        PitcherAvailability(
            unavailable = unavailable,
            consecutiveDays = pitcherIds.associateWith { consecutiveDays(it, today) },
            pitchesLast7Days = pitcherIds.associateWith { pitchesLast7Days(it, today) },
        )

    /** 세이브: 투수 → [날짜, 투구수] 목록 */
    internal fun export(): Map<PlayerId, List<List<Int>>> =
        appearances.mapValues { (_, list) -> list.map { listOf(it.day, it.pitches) } }

    internal fun import(saved: Map<PlayerId, List<List<Int>>>) {
        appearances.clear()
        saved.forEach { (id, list) -> appearances[id] = list.map { Appearance(it[0], it[1]) }.toMutableList() }
    }

    private companion object {
        const val WINDOW = 7
    }
}
