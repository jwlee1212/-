package baseballgm.model

import kotlin.random.Random

/**
 * 선수 성향 (docs/02 숨김 수치, docs/13 "선수 성향과 만족도", 2026-10-04 — FM 의 성격 수치가 모티브).
 *
 * - [loyalty] 충성심: 구단 애착. 함께한 시간의 효과가 커지고, FA 때 원소속 구단을 더 원하고, 연장 계약을 싸게 해 준다
 * - [ambition] 야망: 이기고 싶고 뛰고 싶다. 출장 기회·팀 성적이 만족도를 더 크게 흔들고, 불만이 쌓이면 이적을 요구한다
 * - [professionalism] 프로의식: 기분이 경기력에 덜 번진다. 낮으면 불만을 더 자주 말한다
 *
 * **숨김 수치다.** 프로퍼티가 `internal` 이라 엔진 밖에서는 읽을 수 없다 (불변 원칙 4). 화면은
 * `ScoutingView` 가 정확도만큼 흐린 글자(높음/보통/낮음)로만 받는다.
 *
 * 저장하지 않는다. 선수마다 고정된 [HiddenTraits.scoutingNoiseSeed] 에서 **결정적으로** 뽑으므로
 * 리그 파일·세이브를 바꾸지 않고도 모든 선수(신인·외국인 포함)에게 같은 성향이 생긴다.
 * 분포는 1~100 균등 난수 둘의 평균(삼각 분포) — 가운데가 많고 끝이 드물다. 조정할 수치가 없어 balance.json 에 두지 않는다.
 */
class Personality internal constructor(
    internal val loyalty: Int,
    internal val ambition: Int,
    internal val professionalism: Int,
) {
    /** 성향 값 하나를 0.5~1.5 배율로 (50 이면 1.0). 만족도 요인의 배율로 쓴다 */
    internal fun scale(value: Int): Double = 0.5 + value / 100.0

    override fun toString(): String = "Personality(숨김)"

    companion object {
        /** 같은 선수 → 같은 성향. 스카우트 오차와 다른 씨앗을 쓰도록 소금을 섞는다 */
        internal fun of(hidden: HiddenTraits): Personality {
            val random = Random(hidden.scoutingNoiseSeed.toLong() * 31 + SALT)
            fun draw(): Int = ((random.nextInt(1, 101) + random.nextInt(1, 101)) / 2.0).toInt().coerceIn(1, 100)
            return Personality(loyalty = draw(), ambition = draw(), professionalism = draw())
        }

        internal fun of(player: Player): Personality = of(player.hidden)

        private const val SALT = 0x5EED_0F_10L
    }
}
