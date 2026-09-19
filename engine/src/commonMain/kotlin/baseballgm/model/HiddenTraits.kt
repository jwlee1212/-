package baseballgm.model

import kotlinx.serialization.Serializable

/**
 * 숨김 수치 (docs/02).
 *
 * **불변 원칙 4**: UI 계층은 이 값을 직접 읽을 수 없다. 모든 프로퍼티를 `internal` 로 두어
 * 엔진 밖(도구·콘솔·앱)에서는 컴파일 단계에서 접근이 막힌다. 생성기는 값을 "넣을" 수는 있지만
 * (생성자는 공개) "읽을" 수는 없다. 화면에 보여줄 때는 반드시 `scouting.ScoutingView` 를 거쳐
 * 정확도에 따른 범위·등급으로만 노출한다.
 *
 * @param potential 능력치별 최대 도달치
 * @param growthType 성장 타입 (조기 완성 / 일반 / 대기만성)
 * @param durability 내구도 1~100. 높을수록 부상 확률이 낮다
 * @param volatility 기복 1~100. 폼 변동 폭
 * @param platoonSplit 플래툰 민감도 1~100. 좌우 상성 보정 폭의 배율
 * @param adaptability 적응력 1~100. 외국인 선수만 가진다 (docs/12)
 * @param scoutingNoiseSeed 스카우트 오차의 고정 씨앗. 조회할 때마다 오차를 새로 뽑으면
 *   여러 번 열어 평균을 내는 꼼수가 생기므로, 선수마다 한 번 정해서 저장한다 (docs/02 구현 규칙 1)
 */
@Serializable
class HiddenTraits(
    internal val potential: Map<Attribute, Int>,
    internal val growthType: GrowthType,
    internal val durability: Int,
    internal val volatility: Int,
    internal val platoonSplit: Int,
    internal val adaptability: Int? = null,
    internal val scoutingNoiseSeed: Int,
) {
    /**
     * `data class` 를 쓰지 않는 이유: 자동 생성된 `toString` 이 숨김 수치를 그대로 찍어 버린다.
     * 화면 코드가 선수를 통째로 출력하는 순간 잠재력이 노출되므로, 비교만 직접 구현하고
     * 출력은 막는다.
     */
    override fun toString(): String = "HiddenTraits(숨김)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HiddenTraits) return false
        return potential == other.potential &&
            growthType == other.growthType &&
            durability == other.durability &&
            volatility == other.volatility &&
            platoonSplit == other.platoonSplit &&
            adaptability == other.adaptability &&
            scoutingNoiseSeed == other.scoutingNoiseSeed
    }

    override fun hashCode(): Int {
        var result = potential.hashCode()
        result = 31 * result + growthType.hashCode()
        result = 31 * result + durability
        result = 31 * result + volatility
        result = 31 * result + platoonSplit
        result = 31 * result + (adaptability ?: 0)
        result = 31 * result + scoutingNoiseSeed
        return result
    }
}
