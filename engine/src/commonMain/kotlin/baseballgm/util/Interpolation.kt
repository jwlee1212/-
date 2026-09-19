package baseballgm.util

/**
 * 앵커표 선형 보간.
 *
 * `{"20": 0.30, "50": 0.19, "80": 0.11}` 처럼 띄엄띄엄 적어 둔 표에서 사이값을 읽는다.
 * 표 밖의 값은 양 끝 앵커로 고정한다. `balance.json` 의 모든 "능력치 → 수치" 표가 이 방식이다.
 */
fun interpolateAnchors(anchors: Map<Int, Double>, x: Double): Double {
    require(anchors.isNotEmpty()) { "앵커표가 비어 있다" }
    val keys = anchors.keys.sorted()
    if (x <= keys.first()) return anchors.getValue(keys.first())
    if (x >= keys.last()) return anchors.getValue(keys.last())
    for (index in 0 until keys.lastIndex) {
        val low = keys[index]
        val high = keys[index + 1]
        if (x <= high) {
            val ratio = (x - low) / (high - low)
            return anchors.getValue(low) + (anchors.getValue(high) - anchors.getValue(low)) * ratio
        }
    }
    return anchors.getValue(keys.last())
}
