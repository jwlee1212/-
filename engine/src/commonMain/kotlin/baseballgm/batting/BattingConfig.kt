package baseballgm.batting

import baseballgm.io.BalanceConfig
import baseballgm.io.JsonSection

/**
 * 타격 프로토타입 수치 (`config/balance.json` 의 `battingPrototype`).
 *
 * 손맛 튜닝은 전부 이 수치로 한다 — 코드를 고치지 않고 json 만 바꿔서 다시 빌드하면 된다.
 * 능력치에 따라 달라지는 값은 0일 때와 100일 때 두 값만 적고 사이는 선형 보간한다.
 */
class BattingConfig(
    val pitches: Map<PitchType, PitchSpec>,
    /** 공이 날아가는 동안 이 비율을 지나야 휘기 시작한다 (0~1) */
    val breakStartFraction: Double,
    val strikeChance: Double,
    val ballOffsetMin: Double,
    val ballOffsetMax: Double,
    private val revealFractionAt0: Double,
    private val revealFractionAt100: Double,
    /** 탭 시각에서 빼 주는 입력 지연 보정. 폰 웹에서 늘 늦게 맞는다면 이 값을 올린다 */
    val inputLatencyMs: Double,
    private val solidWindow: Pair<Double, Double>,
    private val weakWindow: Pair<Double, Double>,
    private val foulWindow: Pair<Double, Double>,
    val outOfZoneWindowFactor: Double,
    /** 공이 홈플레이트를 지나고 이만큼 더 기다려도 탭이 없으면 "안 침"으로 본다 */
    val takeGraceMs: Double,
    val solidMaxAngleDeg: Double,
    val weakMaxAngleDeg: Double,
    val foulAngleDeg: Double,
    val solidBaseM: Double,
    val solidPowerBonusM: Double,
    val solidQualityShare: Double,
    val solidJitterM: Double,
    val weakMinM: Double,
    val weakMaxM: Double,
    val fenceM: Double,
    private val solidFlyChance: Pair<Double, Double>,
    val lineHitChance: Double,
    val shortFlyHitChance: Double,
    val doubleMinM: Double,
    val deepFlyOutChance: Double,
    val tripleMinAngleDeg: Double,
    val tripleChance: Double,
    val weakGroundChance: Double,
    val weakHitChance: Double,
) {
    /** 구종이 보이기 시작하는 지점 (비행 진행률). 선구안이 높을수록 일찍 보인다 */
    fun revealFraction(eye: Int): Double = lerp(revealFractionAt0, revealFractionAt100, eye)

    fun solidWindowMs(contact: Int): Double = lerp(solidWindow, contact)
    fun weakWindowMs(contact: Int): Double = lerp(weakWindow, contact)
    fun foulWindowMs(contact: Int): Double = lerp(foulWindow, contact)
    fun solidFlyChance(power: Int): Double = lerp(solidFlyChance, power)

    companion object {
        const val SECTION = "battingPrototype"

        fun from(balance: BalanceConfig): BattingConfig {
            val s = balance.section(SECTION)
            fun pair(path: String) = s.double("${path}At0") to s.double("${path}At100")
            return BattingConfig(
                pitches = mapOf(
                    PitchType.FASTBALL to PitchSpec.from(s.section("pitches.fastball")),
                    PitchType.BREAKING to PitchSpec.from(s.section("pitches.breaking")),
                ),
                breakStartFraction = s.double("breakStartFraction"),
                strikeChance = s.double("zone.strikeChance"),
                ballOffsetMin = s.double("zone.ballOffsetMin"),
                ballOffsetMax = s.double("zone.ballOffsetMax"),
                revealFractionAt0 = s.double("eye.revealFractionAt0"),
                revealFractionAt100 = s.double("eye.revealFractionAt100"),
                inputLatencyMs = s.double("timing.inputLatencyMs"),
                solidWindow = pair("timing.solidWindowMs"),
                weakWindow = pair("timing.weakWindowMs"),
                foulWindow = pair("timing.foulWindowMs"),
                outOfZoneWindowFactor = s.double("timing.outOfZoneWindowFactor"),
                takeGraceMs = s.double("timing.takeGraceMs"),
                solidMaxAngleDeg = s.double("spray.solidMaxAngleDeg"),
                weakMaxAngleDeg = s.double("spray.weakMaxAngleDeg"),
                foulAngleDeg = s.double("spray.foulAngleDeg"),
                solidBaseM = s.double("distance.solidBaseM"),
                solidPowerBonusM = s.double("distance.solidPowerBonusM"),
                solidQualityShare = s.double("distance.solidQualityShare"),
                solidJitterM = s.double("distance.solidJitterM"),
                weakMinM = s.double("distance.weakMinM"),
                weakMaxM = s.double("distance.weakMaxM"),
                fenceM = s.double("distance.fenceM"),
                solidFlyChance = pair("outcomes.solidFlyChance"),
                lineHitChance = s.double("outcomes.lineHitChance"),
                shortFlyHitChance = s.double("outcomes.shortFlyHitChance"),
                doubleMinM = s.double("outcomes.doubleMinM"),
                deepFlyOutChance = s.double("outcomes.deepFlyOutChance"),
                tripleMinAngleDeg = s.double("outcomes.tripleMinAngleDeg"),
                tripleChance = s.double("outcomes.tripleChance"),
                weakGroundChance = s.double("outcomes.weakGroundChance"),
                weakHitChance = s.double("outcomes.weakHitChance"),
            )
        }

        private fun lerp(range: Pair<Double, Double>, rating: Int): Double = lerp(range.first, range.second, rating)

        private fun lerp(at0: Double, at100: Double, rating: Int): Double {
            val t = rating.coerceIn(0, 100) / 100.0
            return at0 + (at100 - at0) * t
        }
    }
}

/** 구종 하나의 수치 */
data class PitchSpec(
    val label: String,
    /** 구종 선택 가중치 */
    val weight: Double,
    /** 투수 손을 떠나 홈플레이트까지 걸리는 시간 */
    val flightMs: Double,
    val flightJitterMs: Double,
    /** 막판에 휘는 양 (존 반폭 단위). 가로는 좌우 무작위, 세로는 아래로 */
    val breakX: Double,
    val breakY: Double,
) {
    companion object {
        fun from(s: JsonSection) = PitchSpec(
            label = s.string("label"),
            weight = s.double("weight"),
            flightMs = s.double("flightMs"),
            flightJitterMs = s.double("flightJitterMs"),
            breakX = s.double("breakX"),
            breakY = s.double("breakY"),
        )
    }
}
