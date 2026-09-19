package baseballgm.sim

import baseballgm.io.BalanceConfig
import baseballgm.io.ConfigException

/**
 * 능력치 → 비율 변환표 (docs/04).
 *
 * `balance.json` 의 `ratingTables` 에 있는 앵커 포인트를 선형 보간해서 읽는다.
 * 예: `batterContactToK` 가 `{20: 0.30, 50: 0.19, 80: 0.11}` 이면 컨택 65 는 0.15.
 * 표 밖의 값(1, 100)은 양 끝 앵커 값으로 고정한다.
 *
 * 시뮬레이션 코드에는 숫자를 직접 쓰지 않고 전부 이 표를 거친다 (불변 원칙 3).
 */
class RatingTables(balance: BalanceConfig) {

    private val tables: Map<String, List<Pair<Int, Double>>> = run {
        val section = balance.section("ratingTables")
        section.keys.filter { it != "status" }.associateWith { name ->
            section.numericMap(name).entries.map { it.key to it.value }.sortedBy { it.first }
        }
    }

    /** 능력치를 넣으면 비율이 나온다. 없는 표 이름이면 바로 예외를 던져 오타를 잡는다. */
    fun value(table: String, rating: Int): Double = value(table, rating.toDouble())

    fun value(table: String, rating: Double): Double {
        val anchors = tables[table] ?: throw ConfigException("그런 변환표가 없다: ratingTables.$table")
        if (rating <= anchors.first().first) return anchors.first().second
        if (rating >= anchors.last().first) return anchors.last().second
        for (index in 0 until anchors.lastIndex) {
            val (lowRating, lowValue) = anchors[index]
            val (highRating, highValue) = anchors[index + 1]
            if (rating <= highRating) {
                val ratio = (rating - lowRating) / (highRating - lowRating)
                return lowValue + (highValue - lowValue) * ratio
            }
        }
        return anchors.last().second
    }

    /** 표에 들어 있는 이름들. 설정 오타 검사용. */
    val tableNames: Set<String> get() = tables.keys
}
