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
 * 정보 정확도 한 벌 (docs/02).
 *
 * 등급(enum)이 아니라 값으로 들고 있는 이유는, 집중 관찰을 이어 가면 정확도가 **연속적으로**
 * 좋아지기 때문이다 (docs/10 "관찰 기간이 길수록 범위가 좁아짐"). 등급만으로는
 * "4주 관찰"과 "12주 관찰"의 차이를 표현할 수 없다.
 *
 * @param halfWidth 표시 범위의 반폭. 0 이면 정확한 값
 * @param gradeSpread 잠재력 등급을 몇 단계까지 넓혀 보여줄지
 * @param growthTypeReliable 성장 타입 추정이 항상 맞는지
 * @param traitHalfWidth 숨김 성질(성장 타입·내구성·성향)을 흐리는 반폭 (2026-10-04). 보통 [halfWidth] 와 같고,
 *   준주전급 공개 기록 선수는 능력치는 정확(0)해도 숨김 성질은 여전히 흐리다
 */
data class ScoutingPrecision(
    val halfWidth: Int,
    val gradeSpread: Int,
    val growthTypeReliable: Boolean,
    val label: String,
    val traitHalfWidth: Int = halfWidth,
) {
    val isExact: Boolean get() = halfWidth == 0

    /** 숨김 성질까지 정확한가 (우리 팀) */
    val traitsExact: Boolean get() = traitHalfWidth == 0
}

/**
 * 정확도 기본 등급. 집중 관찰이 없을 때의 출발점이다.
 *
 * 아마추어(드래프트 풀)는 등급이 아니라 **스카우트 투자 단계**가 정확도를 정하므로
 * ([ScoutingBudget]) 여기의 [MINIMAL] 은 투자 정보가 없을 때의 기본값으로만 쓴다.
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

    /**
     * 준주전급 공개 기록 선수 (2026-10-04 유저 요청 "준주전급까지는 능력치를 다 보여주자"). 1군에서 충분히 뛰어 기록으로
     * 다 드러난 선수 — 현재 능력치는 정확, 잠재력·숨김 성질은 타 팀 1군과 같은 정도로 흐리다
     */
    ESTABLISHED(0, 1, false, "공개 기록"),

    /** 타 팀 2군 */
    LOW(10, 1, false, "낮음"),

    /** 아마추어 (드래프트 풀) */
    MINIMAL(15, 2, false, "매우 낮음"),
    ;

    val precision: ScoutingPrecision
        get() = ScoutingPrecision(
            halfWidth, gradeSpread, growthTypeReliable, label,
            traitHalfWidth = if (this == ESTABLISHED) MEDIUM.halfWidth else halfWidth,
        )
}

/** 능력치 표시 범위. [low] == [high] 면 정확한 값이다. */
data class RatingRange(val low: Int, val high: Int) {
    val isExact: Boolean get() = low == high

    /** 범위 중심. AI 평가처럼 "하나의 숫자"가 필요할 때만 쓴다 (진짜 값은 여기 없다). */
    val center: Double get() = (low + high) / 2.0

    override fun toString(): String = if (isExact) "$low" else "$low~$high"
}

/**
 * 잠재력 등급 (docs/02, 유저 정의 2026-10-04). 다 크면 팀에서 어떤 자리를 맡을 재목인가.
 * 등급을 가르는 **기준선은 balance.json `scouting.potentialGrades`** 에 있다 ([PotentialScale]).
 */
enum class PotentialGrade(val role: String) {
    D("백업"),
    C("준주전"),
    B("주전"),
    A("팀 에이스"),
    S("리그 정상급"),
    ;

    companion object {
        fun fromName(name: String): PotentialGrade =
            entries.firstOrNull { it.name == name } ?: error("알 수 없는 잠재력 등급: $name")
    }
}

/**
 * 잠재력 등급 기준선 (balance.json `scouting.potentialGrades`, 2026-10-04 코드에서 옮김).
 *
 * 전역에 두지 않고 **필요한 곳에 넘긴다** — 설정이 다른 테스트끼리 값이 섞이지 않게.
 * 엔진 클래스는 각자 받은 설정에서 [from] 으로 만든다.
 *
 * @param minimums D 를 뺀 등급별 최솟값 (잠재력 평균). 오름차순이어야 한다
 * @param edgeHalfBand 맨 아래(D)·맨 위(S) 구간은 한쪽이 열려 있어서, 가운데 값을 잡을 때 쓰는 반폭
 */
class PotentialScale(minimums: Map<PotentialGrade, Int>, private val edgeHalfBand: Double) {

    private val minimumByGrade: Map<PotentialGrade, Int> = PotentialGrade.entries.associateWith { grade ->
        if (grade == PotentialGrade.D) 0 else minimums[grade] ?: error("잠재력 등급 기준선이 없다: $grade")
    }

    init {
        val ordered = PotentialGrade.entries.map { minimumByGrade.getValue(it) }
        require(ordered.zipWithNext().all { (a, b) -> a < b }) { "잠재력 등급 기준선은 D < C < B < A < S 여야 한다: $ordered" }
    }

    fun minimumOf(grade: PotentialGrade): Int = minimumByGrade.getValue(grade)

    /** 잠재력 평균 → 등급 */
    fun gradeOf(potential: Double): PotentialGrade = PotentialGrade.entries.last { potential >= minimumOf(it) }

    /** 등급 구간의 가운데 값. 맨 아래는 C 아래로 반폭 2개, 맨 위는 S 위로 반폭 1개 */
    fun midpoint(grade: PotentialGrade): Double {
        val next = PotentialGrade.entries.getOrNull(grade.ordinal + 1)?.let(::minimumOf)
        return when {
            next == null -> minimumOf(grade) + edgeHalfBand
            grade == PotentialGrade.D -> next - edgeHalfBand * 2
            else -> (minimumOf(grade) + next) / 2.0
        }
    }

    companion object {
        fun from(balance: baseballgm.io.BalanceConfig): PotentialScale {
            val section = balance.section("scouting").section("potentialGrades")
            return PotentialScale(
                minimums = PotentialGrade.entries.filter { it != PotentialGrade.D }.associateWith { section.int(it.name) },
                edgeHalfBand = section.double("edgeHalfBand"),
            )
        }
    }
}

/** 스카우트가 본 선수. 화면(콘솔·앱)은 [Player] 가 아니라 이 객체만 본다. */
data class ScoutedPlayer(
    val id: PlayerId,
    val name: String,
    val age: Int,
    val positionLabel: String,
    val precision: ScoutingPrecision,
    val ratings: Map<Attribute, RatingRange>,
    val overall: RatingRange,
    val potentialLow: PotentialGrade,
    val potentialHigh: PotentialGrade,
    val growthTypeGuess: GrowthType,
    val durabilityComment: String,
    /** 성향 (docs/13, 2026-10-04). 내구성 코멘트처럼 정확도만큼 흐린 글자 */
    val personality: baseballgm.management.PersonalityReading,
) {
    val potentialLabel: String
        get() = if (potentialLow == potentialHigh) potentialLow.name else "${potentialLow.name}~${potentialHigh.name}"

    /** 현재 능력치를 정확히 아는 선수인가 (우리 팀 · 준주전급 공개 기록). */
    val isExactView: Boolean get() = precision.isExact

    /** 숨김 성질(성향·내구성·성장 타입)까지 정확히 아는가 (우리 팀만) */
    val traitsExact: Boolean get() = precision.traitsExact
}

/**
 * 숨김 수치와 타 팀 선수의 실제 능력치를 정확도에 따라 흐려서 내보내는 유일한 통로 (불변 원칙 4).
 *
 * **오차는 선수마다 고정**이다. 조회할 때마다 새로 뽑으면 여러 번 열어 평균을 내는 꼼수가 생기므로,
 * 선수가 가진 `scoutingNoiseSeed` 로 오차를 계산한다. 같은 선수·같은 정확도면 몇 번을 봐도 같은 범위다.
 * 또 오차가 범위의 중심 자체를 치우치게 해서, **진짜 값이 범위 한가운데 오지 않는다** (docs/02 구현 규칙 2).
 *
 * 정확도가 좋아지면(집중 관찰) 같은 고정 오차가 **비례해서 줄어든다** — 범위가 좁아지면서
 * 중심이 진짜 값 쪽으로 다가오지, 전혀 다른 곳으로 튀지 않는다 (docs/02 구현 규칙 1).
 */
object ScoutingView {

    /** 중심 치우침 정도. 반폭의 이 비율만큼 범위 중심이 진짜 값에서 밀린다. */
    private const val CENTER_BIAS = 0.6

    /** 성향 흐림 배율: 성향은 1~100 의 넓은 눈금이라 능력치 반폭의 두 배만큼 흔든다 */
    private const val PERSONALITY_BLUR = 2.0

    fun of(player: Player, accuracy: ScoutingAccuracy, season: Int, scale: PotentialScale): ScoutedPlayer =
        of(player, accuracy.precision, season, scale)

    /** @param scale 잠재력 등급 기준선 (balance.json, [PotentialScale.from]) */
    fun of(player: Player, precision: ScoutingPrecision, season: Int, scale: PotentialScale): ScoutedPlayer {
        val seed = player.hidden.scoutingNoiseSeed
        val ratings = player.ratingsMap().mapValues { (attribute, value) ->
            rangeOf(value, precision, seed, attribute.ordinal)
        }
        val potentialAverage = player.hidden.potential.values.average()
        val trueGrade = scale.gradeOf(potentialAverage)
        val (low, high) = gradeRange(trueGrade, precision, seed)

        return ScoutedPlayer(
            id = player.id,
            name = player.registeredName,
            age = player.ageIn(season),
            positionLabel = positionLabelOf(player),
            precision = precision,
            ratings = ratings,
            overall = rangeOf(player.ratingsMap().values.average().roundToInt(), precision, seed, salt = 99),
            potentialLow = low,
            potentialHigh = high,
            growthTypeGuess = growthGuess(player.hidden.growthType, precision, seed),
            durabilityComment = durabilityComment(player.hidden.durability, precision, seed),
            personality = personalityReading(player, precision, seed),
        )
    }

    /**
     * 잠재력 종합의 **범위**. 리포트와 AI 평가가 쓴다.
     *
     * 등급만으로는 "B~A" 안에서 어디쯤인지 알 수 없어 지명 순서를 정할 수 없다. 등급 범위와
     * 같은 고정 오차를 써서 숫자 범위로도 내보낸다 — 여전히 진짜 값은 범위 한가운데가 아니다.
     */
    fun potentialRange(player: Player, precision: ScoutingPrecision): RatingRange {
        val average = player.hidden.potential.values.average().roundToInt()
        return rangeOf(average, precision, player.hidden.scoutingNoiseSeed, salt = 61)
    }

    private fun positionLabelOf(player: Player): String = when (player) {
        is Batter -> player.primaryPosition.label
        is Pitcher -> if (player.role.isReliever) "RP" else "SP"
    }

    private fun rangeOf(value: Int, precision: ScoutingPrecision, seed: Int, salt: Int): RatingRange {
        if (precision.halfWidth == 0) return RatingRange(value, value)
        val shift = (noise(seed, salt) * precision.halfWidth * CENTER_BIAS).roundToInt()
        val center = value + shift
        return RatingRange(
            low = (center - precision.halfWidth).coerceIn(RATING_MIN, RATING_MAX),
            high = (center + precision.halfWidth).coerceIn(RATING_MIN, RATING_MAX),
        )
    }

    private fun gradeRange(
        trueGrade: PotentialGrade,
        precision: ScoutingPrecision,
        seed: Int,
    ): Pair<PotentialGrade, PotentialGrade> {
        if (precision.gradeSpread == 0) return trueGrade to trueGrade
        val grades = PotentialGrade.entries
        val index = grades.indexOf(trueGrade)
        // 범위 중심도 한쪽으로 치우치게 해서 "가운데가 진짜"라는 추측을 막는다
        val shift = if (noise(seed, salt = 51) > 0.35) 1 else if (noise(seed, salt = 51) < -0.35) -1 else 0
        val low = (index - precision.gradeSpread + shift).coerceIn(0, grades.lastIndex)
        val high = (index + precision.gradeSpread + shift).coerceIn(0, grades.lastIndex)
        return grades[minOf(low, high)] to grades[maxOf(low, high)]
    }

    private fun growthGuess(actual: GrowthType, precision: ScoutingPrecision, seed: Int): GrowthType {
        if (precision.growthTypeReliable) return actual
        // 정확도가 낮을수록 틀릴 확률이 높다. 틀리는 방향도 고정이다.
        val wrongChance = precision.traitHalfWidth / 40.0
        if (noise(seed, salt = 71).let { (it + 1) / 2 } >= wrongChance) return actual
        val others = GrowthType.entries.filter { it != actual }
        return others[if (noise(seed, salt = 72) > 0) 1 else 0]
    }

    /**
     * 성향 글자 (docs/13 "선수 성향과 만족도"). 우리 선수(정확도 최고)는 진짜 단계, 타 팀은 반폭 × 2 만큼 흔든 값의 단계.
     * 성향은 1~100 이라 능력치보다 넓게 흔든다. 숫자는 내보내지 않는다.
     */
    private fun personalityReading(player: Player, precision: ScoutingPrecision, seed: Int): baseballgm.management.PersonalityReading {
        val personality = baseballgm.model.Personality.of(player)
        fun blur(value: Int, salt: Int): Int =
            if (precision.traitHalfWidth == 0) value else value + (noise(seed, salt) * precision.traitHalfWidth * PERSONALITY_BLUR).roundToInt()
        val loyalty = blur(personality.loyalty, 91)
        val ambition = blur(personality.ambition, 92)
        val professionalism = blur(personality.professionalism, 93)
        val archetype = when {
            loyalty >= 70 && ambition < 60 -> "원클럽맨"
            ambition >= 70 && loyalty < 45 -> "야심가"
            professionalism <= 30 -> "다혈질"
            professionalism >= 70 -> "모범생"
            loyalty >= 65 -> "의리파"
            ambition >= 65 -> "승부사"
            else -> "무난한 성격"
        }
        fun grade(value: Int): String = when {
            value >= 65 -> "높음"
            value <= 35 -> "낮음"
            else -> "보통"
        }
        return baseballgm.management.PersonalityReading(archetype, grade(loyalty), grade(ambition), grade(professionalism))
    }

    private fun durabilityComment(durability: Int, precision: ScoutingPrecision, seed: Int): String {
        val blurred = if (precision.traitHalfWidth == 0) {
            durability
        } else {
            durability + (noise(seed, salt = 83) * precision.traitHalfWidth).roundToInt()
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
    internal fun noise(seed: Int, salt: Int): Double {
        var h = seed * 73856093 xor (salt + 1) * 19349663
        h = h xor (h ushr 13)
        h *= -1640531527
        h = h xor (h ushr 16)
        return ((h and 0xFFFF) / 65535.0) * 2.0 - 1.0
    }
}
