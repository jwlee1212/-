package baseballgm.sim

/**
 * Log5 매치업 확률 (docs/04).
 *
 * ```
 * p = (B × P / L) / (B × P / L + (1 − B)(1 − P) / (1 − L))
 * ```
 * B = 타자의 해당 결과 비율, P = 투수의 허용 비율, L = 리그 평균.
 *
 * 단순 평균과 달리 **극단 매치업에서도 0~1 을 벗어나지 않고**, 리그 평균 선수끼리 붙으면
 * 정확히 리그 평균이 나온다. 삼진·볼넷·홈런처럼 "비율"로 표현되는 결과마다 따로 적용한다.
 */
object Log5 {

    private const val EPSILON = 1e-6

    fun rate(batter: Double, pitcher: Double, league: Double): Double {
        val b = batter.coerceIn(EPSILON, 1.0 - EPSILON)
        val p = pitcher.coerceIn(EPSILON, 1.0 - EPSILON)
        val l = league.coerceIn(EPSILON, 1.0 - EPSILON)
        val numerator = b * p / l
        val denominator = numerator + (1 - b) * (1 - p) / (1 - l)
        return (numerator / denominator).coerceIn(0.0, 1.0)
    }
}
