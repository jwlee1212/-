package baseballgm.scouting

import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.GrowthType
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RATING_MAX
import baseballgm.model.RATING_MIN
import kotlin.math.roundToInt

/**
 * 정보 정확도 (docs/02).
 *
 * @param halfWidth 표시 범위의 반폭. 0 이면 정확한 값
 * @param gradeSpread 잠재력 등급을 몇 단계까지 넓혀 보여줄지
 * @param growthTypeReliable 성장 타입 추정이 항상 맞는지
 */
enum class ScoutingAccuracy(
    val halfWidth: Int,
    val gradeSpread: Int,
    val growthTypeReliable: Boolean,
    val label: String,
) {
    /** 우리 팀 선수: 현재 능력치는 정확, 잠재력은 여전히 등급 */
    OWN_TEAM(0, 0, true, "정확"),

    /** 집중 관찰을 오래 한 선수 */
    HIGH(3, 0, true, "높음"),

    /** 타 팀 1군 */
    MEDIUM(5, 1, false, "중간"),

    /** 타 팀 2군 */
    LOW(10, 1, false, "낮음"),

    /** 아마추어 (드래프트 풀) */
    MINIMAL(15, 2, false, "매우 낮음"),
}

/** 능력치 표시 범위. [low] == [high] 면 정확한 값이다. */
data class RatingRange(val low: Int, val high: Int) {
    val isExact: Boolean get() = low == high

    override fun toString(): String = if (isExact) "$low" else "$low~$high"
}

/** 잠재력 등급 (docs/10 리포트). */
enum class PotentialGrade(val minimum: Int) {
    D(0), C(65), B(75), A(85);

    companion object {
        fun of(value: Double): PotentialGrade = entries.last { value >= it.minimum }
    }
}

/** 스카우트가 본 선수. 화면(콘솔·앱)은 [Player] 가 아니라 이 객체만 본다. */
data class ScoutedPlayer(
    val id: PlayerId,
    val name: String,
    val age: Int,
    val positionLabel: String,
    val accuracy: ScoutingAccuracy,
    val ratings: Map<Attribute, RatingRange>,
    val overall: RatingRange,
    val potentialLow: PotentialGrade,
    val potentialHigh: PotentialGrade,
    val growthTypeGuess: GrowthType,
    val durabilityComment: String,
) {
    val potentialLabel: String
        get() = if (potentialLow == potentialHigh) potentialLow.name else "${potentialLow.name}~${potentialHigh.name}"
}

/**
 * 숨김 수치와 타 팀 선수의 실제 능력치를 정확도에 따라 흐려서 내보내는 유일한 통로 (불변 원칙 4).
 *
 * **오차는 선수마다 고정**이다. 조회할 때마다 새로 뽑으면 여러 번 열어 평균을 내는 꼼수가 생기므로,
 * 선수가 가진 `scoutingNoiseSeed` 로 오차를 계산한다. 같은 선수·같은 정확도면 몇 번을 봐도 같은 범위다.
 * 또 오차가 범위의 중심 자체를 치우치게 해서, **진짜 값이 범위 한가운데 오지 않는다** (docs/02 구현 규칙 2).
 */
object ScoutingView {

    /** 중심 치우침 정도. 반폭의 이 비율만큼 범위 중심이 진짜 값에서 밀린다. */
    private const val CENTER_BIAS = 0.6

    fun of(player: Player, accuracy: ScoutingAccuracy, season: Int): ScoutedPlayer {
        val seed = player.hidden.scoutingNoiseSeed
        val ratings = player.ratingsMap().mapValues { (attribute, value) ->
            rangeOf(value, accuracy, seed, attribute.ordinal)
        }
        val potentialAverage = player.hidden.potential.values.average()
        val trueGrade = PotentialGrade.of(potentialAverage)
        val (low, high) = gradeRange(trueGrade, accuracy, seed)

        return ScoutedPlayer(
            id = player.id,
            name = player.registeredName,
            age = player.ageIn(season),
            positionLabel = positionLabelOf(player),
            accuracy = accuracy,
            ratings = ratings,
            overall = rangeOf(player.ratingsMap().values.average().roundToInt(), accuracy, seed, salt = 99),
            potentialLow = low,
            potentialHigh = high,
            growthTypeGuess = growthGuess(player.hidden.growthType, accuracy, seed),
            durabilityComment = durabilityComment(player.hidden.durability, accuracy, seed),
        )
    }

    private fun positionLabelOf(player: Player): String = when (player) {
        is Batter -> player.primaryPosition.label
        is Pitcher -> if (player.role.isReliever) "RP" else "SP"
    }

    private fun rangeOf(value: Int, accuracy: ScoutingAccuracy, seed: Int, salt: Int): RatingRange {
        if (accuracy.halfWidth == 0) return RatingRange(value, value)
        val shift = (noise(seed, salt) * accuracy.halfWidth * CENTER_BIAS).roundToInt()
        val center = value + shift
        return RatingRange(
            low = (center - accuracy.halfWidth).coerceIn(RATING_MIN, RATING_MAX),
            high = (center + accuracy.halfWidth).coerceIn(RATING_MIN, RATING_MAX),
        )
    }

    private fun gradeRange(
        trueGrade: PotentialGrade,
        accuracy: ScoutingAccuracy,
        seed: Int,
    ): Pair<PotentialGrade, PotentialGrade> {
        if (accuracy.gradeSpread == 0) return trueGrade to trueGrade
        val grades = PotentialGrade.entries
        val index = grades.indexOf(trueGrade)
        // 범위 중심도 한쪽으로 치우치게 해서 "가운데가 진짜"라는 추측을 막는다
        val shift = if (noise(seed, salt = 51) > 0.35) 1 else if (noise(seed, salt = 51) < -0.35) -1 else 0
        val low = (index - accuracy.gradeSpread + shift).coerceIn(0, grades.lastIndex)
        val high = (index + accuracy.gradeSpread + shift).coerceIn(0, grades.lastIndex)
        return grades[minOf(low, high)] to grades[maxOf(low, high)]
    }

    private fun growthGuess(actual: GrowthType, accuracy: ScoutingAccuracy, seed: Int): GrowthType {
        if (accuracy.growthTypeReliable) return actual
        // 정확도가 낮을수록 틀릴 확률이 높다. 틀리는 방향도 고정이다.
        val wrongChance = accuracy.halfWidth / 40.0
        if (noise(seed, salt = 71).let { (it + 1) / 2 } >= wrongChance) return actual
        val others = GrowthType.entries.filter { it != actual }
        return others[if (noise(seed, salt = 72) > 0) 1 else 0]
    }

    private fun durabilityComment(durability: Int, accuracy: ScoutingAccuracy, seed: Int): String {
        val blurred = if (accuracy.halfWidth == 0) {
            durability
        } else {
            durability + (noise(seed, salt = 83) * accuracy.halfWidth).roundToInt()
        }
        return when {
            blurred >= 75 -> "잔부상이 거의 없다"
            blurred >= 55 -> "무난한 편"
            blurred >= 40 -> "잔부상이 잦다"
            else -> "부상 이력이 많다"
        }
    }

    /**
     * 씨앗과 소금으로 -1.0 ~ 1.0 의 고정 난수를 만든다.
     * 시드 기반 결정적 계산이라 `Random` 인스턴스가 필요 없다 (불변 원칙 2 위반 아님).
     */
    private fun noise(seed: Int, salt: Int): Double {
        var h = seed * 73856093 xor (salt + 1) * 19349663
        h = h xor (h ushr 13)
        h *= -1640531527
        h = h xor (h ushr 16)
        return ((h and 0xFFFF) / 65535.0) * 2.0 - 1.0
    }
}
