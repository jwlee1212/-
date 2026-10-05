package baseballgm.util

import kotlin.random.Random

/**
 * 시드 파생 (불변 원칙 2 — "주차마다 시드를 세이브에 저장해 같은 결정 → 같은 결과").
 *
 * 게임 시드 하나에서 (시즌, 주차, 단계)마다 독립된 시드를 뽑는다. 이렇게 하면
 * - 세이브에는 게임 시드 하나만 있으면 되고(주차 시드는 언제든 다시 계산된다),
 * - 화면을 열어 보는 일(FA 소문, 스태프 시장)이 다음 주 경기 결과를 바꾸지 못하고,
 * - 저장 → 불러오기를 해도 다음 주가 똑같이 흘러간다.
 *
 * 섞는 함수는 SplitMix64 (잘 알려진 64비트 해시). 인접한 입력(주차 3, 4)도 전혀 다른 시드가 된다.
 */
object Seeds {

    /** 단계 표시. 같은 주차라도 단계가 다르면 다른 난수를 쓴다 */
    enum class Phase { WEEK, DRAFT, POSTSEASON, OFFSEASON, STAFF_MARKET, RUMOR, OFFSEASON_PLAN, EXTENSION }

    fun derive(base: Long, vararg parts: Long): Long {
        var h = mix(base)
        parts.forEach { h = mix(h xor mix(it + GOLDEN)) }
        return h
    }

    fun random(base: Long, season: Int, phase: Phase, vararg parts: Long): Random =
        Random(derive(base, season.toLong(), phase.ordinal.toLong(), *parts))

    private fun mix(input: Long): Long {
        var z = input + GOLDEN
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    private const val GOLDEN = -7046029254386353131L
}
