package baseballgm.app.batting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import baseballgm.app.audio.Sfx
import baseballgm.batting.AtBat
import baseballgm.batting.AtBatResult
import baseballgm.batting.BatterSkills
import baseballgm.batting.BattingConfig
import baseballgm.batting.Contact
import baseballgm.batting.ContactQuality
import baseballgm.batting.Pitch
import baseballgm.batting.PitchCall
import baseballgm.batting.PitchOutcome
import baseballgm.io.BalanceConfig
import baseballgm.sim.PaOutcome
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** 화면 연출·소리 수치 (balance.json `battingPrototype.presentation`). 판정과는 무관하다 */
data class Presentation(
    val windupMs: Double,
    val pitchIntervalMs: Double,
    val resultHoldMs: Double,
    val hitFlightMs: Double,
    /** 배트를 휘두르는 시간 */
    val swingMs: Double,
    /** 맞는 순간 화면을 멈추는 시간 (히트스톱) */
    val hitStopMs: Double,
    val flashMs: Double,
    val burstMs: Double,
    val shakeMs: Double,
    val shakeSolidPx: Double,
    val shakeWeakPx: Double,
    val shakeHomeRunPx: Double,
    val confettiMs: Double,
    val sfxVolume: Float,
) {
    companion object {
        fun from(balance: BalanceConfig): Presentation {
            val s = balance.section("${BattingConfig.SECTION}.presentation")
            return Presentation(
                windupMs = s.double("windupMs"),
                pitchIntervalMs = s.double("pitchIntervalMs"),
                resultHoldMs = s.double("resultHoldMs"),
                hitFlightMs = s.double("hitFlightMs"),
                swingMs = s.double("swingMs"),
                hitStopMs = s.double("hitStopMs"),
                flashMs = s.double("flashMs"),
                burstMs = s.double("burstMs"),
                shakeMs = s.double("shakeMs"),
                shakeSolidPx = s.double("shakeSolidPx"),
                shakeWeakPx = s.double("shakeWeakPx"),
                shakeHomeRunPx = s.double("shakeHomeRunPx"),
                confettiMs = s.double("confettiMs"),
                sfxVolume = s.double("sfxVolume").toFloat(),
            )
        }
    }
}

/** 지금 화면이 어느 단계인가 */
sealed interface Phase {
    /** 다음 공을 기다리는 중 */
    data class Waiting(val since: TimeMark) : Phase

    /** 투수 와인드업. 이 동안은 탭해도 스윙하지 않는다 */
    data class Windup(val since: TimeMark, val pitch: Pitch) : Phase

    /** 공이 날아오는 중. [swing] 은 이미 휘둘렀으면 그 결과, [mittPlayed] 는 미트 소리를 냈는가 */
    data class Flight(val release: TimeMark, val pitch: Pitch, val swing: Swing?, val mittPlayed: Boolean = false) : Phase

    /** 맞은 공이 날아가는 중. [pitch]·[swingAtMs] 로 공이 배트에 맞은 위치를 다시 계산한다 */
    data class Hit(val since: TimeMark, val contact: Contact, val result: AtBatResult, val pitch: Pitch, val swingAtMs: Double) : Phase

    /** 타석 끝. 탭하면 다음 타석 */
    data class Over(val result: AtBatResult) : Phase
}

/** 스윙 기록. [atMs] 는 공을 놓은 뒤 몇 ms 에 탭했는가 */
data class Swing(val atMs: Double, val mark: TimeMark, val outcome: PitchOutcome)

/** 타구 분포도에 찍는 점 */
data class SprayDot(val angleDeg: Double, val distanceM: Double, val outcome: PaOutcome)

/** 공이 배트에 맞은 순간 (섬광·흔들림·파편 연출용) */
data class Impact(val mark: TimeMark, val quality: ContactQuality, val homeRun: Boolean)

/** 화면에 잠깐 띄우는 판정 문구 */
data class Callout(val text: String, val detail: String?, val since: TimeMark, val tone: CalloutTone, val delayMs: Double = 0.0)

enum class CalloutTone { GOOD, BAD, NEUTRAL, BIG }

/**
 * 타석 화면의 상태. 판정은 전부 엔진의 [AtBat] 이 하고, 여기서는 시간 흐름과 연출 단계만 관리한다.
 *
 * 시간은 [TimeSource.Monotonic] 하나로 잰다 — 공을 놓은 순간과 탭한 순간을 같은 시계로 재야
 * 타이밍 판정이 프레임 속도와 무관해진다. 시드는 타석마다 1, 2, 3… 으로 올라간다 (불변 원칙 2).
 */
class BattingSession(val config: BattingConfig, val presentation: Presentation) {
    private val clock = TimeSource.Monotonic
    private var seed = 0

    var skills by mutableStateOf(BatterSkills(contact = 50, power = 50, eye = 50))
        private set

    var atBat by mutableStateOf(newAtBat())
        private set

    var phase: Phase by mutableStateOf(Phase.Waiting(clock.markNow()))
        private set

    var callout by mutableStateOf<Callout?>(null)
        private set

    /** 마지막 스윙의 타이밍 차이 (ms, +면 늦음) */
    var lastTimingMs by mutableStateOf<Double?>(null)
        private set

    /** 마지막 타격 순간. 섬광·흔들림이 이 시각부터 계산된다 */
    var impact by mutableStateOf<Impact?>(null)
        private set

    /** 효과음을 내는 곳. 화면이 플랫폼 재생기를 꽂는다 */
    var onSound: (Sfx, Float) -> Unit = { _, _ -> }

    val spray = mutableStateListOf<SprayDot>()
    val tally = Tally()

    val seedLabel: Int get() = seed

    fun updateSkills(newSkills: BatterSkills) {
        skills = newSkills
        atBat.skills = newSkills
    }

    /** 매 프레임 호출. 시간이 지나 단계가 넘어가야 하면 넘긴다 */
    fun tick() {
        when (val p = phase) {
            is Phase.Waiting -> if (p.since.ms() >= presentation.pitchIntervalMs) {
                phase = Phase.Windup(clock.markNow(), atBat.nextPitch())
            }
            is Phase.Windup -> if (p.since.ms() >= presentation.windupMs) {
                phase = Phase.Flight(clock.markNow(), p.pitch, null)
                sound(Sfx.PITCH, 0.8f)
            }
            is Phase.Flight -> {
                // 안 쳤거나 헛스윙이면 공이 도착하는 순간 미트 소리
                val arrived = p.release.ms() >= p.pitch.flightMs
                val missed = p.swing == null || p.swing.outcome.call == PitchCall.SWINGING_STRIKE
                if (arrived && missed && !p.mittPlayed) {
                    sound(Sfx.MITT, 1f)
                    phase = p.copy(mittPlayed = true)
                    return
                }
                val passed = p.release.ms() >= p.pitch.flightMs + config.takeGraceMs
                if (p.swing == null && passed) {
                    afterPitch(atBat.take())
                } else if (p.swing != null && passed) {
                    afterPitch(p.swing.outcome)
                }
            }
            is Phase.Hit -> if (p.since.ms() >= presentation.hitStopMs + presentation.hitFlightMs) {
                // 타구가 떨어지는 순간 안타면 함성
                if (p.result.outcome.isHit && p.result.outcome != PaOutcome.HOME_RUN) sound(Sfx.CHEER_SMALL, 1f)
                finishAtBat(p.result)
            }
            is Phase.Over -> Unit
        }
    }

    /** 화면 탭. 공이 날아오는 중이면 스윙, 타석이 끝났으면 다음 타석 */
    fun tap() {
        when (val p = phase) {
            is Phase.Flight -> if (p.swing == null) {
                val atMs = p.release.ms()
                val outcome = atBat.swing(atMs)
                lastTimingMs = outcome.timingDiffMs
                val swing = Swing(atMs, clock.markNow(), outcome)
                playSwingSounds(outcome)
                if (outcome.call == PitchCall.IN_PLAY) {
                    val result = outcome.result!!
                    phase = Phase.Hit(clock.markNow(), outcome.contact!!, result, p.pitch, atMs)
                    showCallout(result)
                } else {
                    // 헛스윙·파울은 공이 끝까지 지나간 뒤 다음으로
                    phase = p.copy(swing = swing)
                    showCallout(outcome)
                }
            }
            is Phase.Over -> {
                atBat = newAtBat()
                phase = Phase.Waiting(clock.markNow())
            }
            else -> Unit
        }
    }

    private fun playSwingSounds(outcome: PitchOutcome) {
        sound(Sfx.SWING, 0.7f)
        val contact = outcome.contact ?: return
        val homeRun = outcome.result?.outcome == PaOutcome.HOME_RUN
        when (contact.quality) {
            ContactQuality.SOLID -> sound(Sfx.CRACK_SOLID, 1f)
            ContactQuality.WEAK -> sound(Sfx.CRACK_WEAK, 1f)
            ContactQuality.FOUL -> sound(Sfx.CRACK_FOUL, 1f)
            ContactQuality.MISS -> return
        }
        if (homeRun) sound(Sfx.CHEER_BIG, 1f)
        impact = Impact(clock.markNow(), contact.quality, homeRun)
    }

    private fun sound(sfx: Sfx, volume: Float) = onSound(sfx, volume * presentation.sfxVolume)

    private fun afterPitch(outcome: PitchOutcome) {
        if (outcome.call != PitchCall.SWINGING_STRIKE && outcome.call != PitchCall.FOUL) showCallout(outcome)
        val result = outcome.result
        if (result != null) finishAtBat(result) else phase = Phase.Waiting(clock.markNow())
    }

    private fun finishAtBat(result: AtBatResult) {
        tally.add(result.outcome)
        result.contact?.let { spray += SprayDot(it.angleDeg, it.distanceM, result.outcome) }
        if (result.contact == null) showCallout(result)
        phase = Phase.Over(result)
    }

    private fun showCallout(outcome: PitchOutcome) {
        val (text, tone) = when (outcome.call) {
            PitchCall.CALLED_STRIKE -> "스트라이크!" to CalloutTone.BAD
            PitchCall.BALL -> "볼" to CalloutTone.NEUTRAL
            PitchCall.SWINGING_STRIKE -> "헛스윙!" to CalloutTone.BAD
            PitchCall.FOUL -> "파울" to CalloutTone.NEUTRAL
            PitchCall.IN_PLAY -> return
        }
        callout = Callout(text, outcome.timingDiffMs?.let(::timingText), clock.markNow(), tone)
    }

    private fun showCallout(result: AtBatResult) {
        val tone = when {
            result.outcome == PaOutcome.HOME_RUN -> CalloutTone.BIG
            result.outcome.isHit || result.outcome.isWalk -> CalloutTone.GOOD
            else -> CalloutTone.BAD
        }
        val contact = result.contact
        val detail = contact?.let { "${directionText(it.angleDeg)} · ${it.distanceM.roundToInt()}m" + (lastTimingMs?.let { t -> " · ${timingText(t)}" } ?: "") }
        // 인플레이 타구는 공이 날아가는 장면을 먼저 보여 주고 결과를 띄운다
        val delay = if (contact != null) presentation.hitStopMs + presentation.hitFlightMs * CALLOUT_DELAY_SHARE else 0.0
        callout = Callout(outcomeText(result.outcome), detail, clock.markNow(), tone, delay)
    }

    private companion object {
        /** 타구가 이만큼 날아간 뒤에 결과 문구를 띄운다 (비행 시간 대비 비율) */
        const val CALLOUT_DELAY_SHARE = 0.55
    }

    private fun newAtBat(): AtBat {
        seed++
        return AtBat(config, skills, Random(seed))
    }

    private fun TimeMark.ms(): Double = elapsedNow().inWholeMicroseconds / 1000.0

    /** 이번 세션 누적 기록 */
    class Tally {
        var plateAppearances by mutableIntStateOf(0)
        var atBats by mutableIntStateOf(0)
        var hits by mutableIntStateOf(0)
        var homeRuns by mutableIntStateOf(0)
        var strikeouts by mutableIntStateOf(0)
        var walks by mutableIntStateOf(0)

        fun add(outcome: PaOutcome) {
            plateAppearances++
            if (outcome.isAtBat) atBats++
            if (outcome.isHit) hits++
            if (outcome == PaOutcome.HOME_RUN) homeRuns++
            if (outcome.isStrikeout) strikeouts++
            if (outcome.isWalk) walks++
        }

        val average: String
            get() = if (atBats == 0) "-" else ((hits * 1000.0 / atBats).roundToInt()).toString().padStart(3, '0').let { ".$it" }
    }
}

fun outcomeText(outcome: PaOutcome): String = when (outcome) {
    PaOutcome.SINGLE -> "안타!"
    PaOutcome.DOUBLE -> "2루타!"
    PaOutcome.TRIPLE -> "3루타!"
    PaOutcome.HOME_RUN -> "홈런!!"
    PaOutcome.WALK, PaOutcome.INTENTIONAL_WALK -> "볼넷"
    PaOutcome.STRIKEOUT, PaOutcome.STRIKEOUT_REACHED -> "삼진"
    PaOutcome.GROUND_OUT -> "땅볼 아웃"
    PaOutcome.FLY_OUT -> "뜬공 아웃"
    PaOutcome.LINE_OUT -> "직선타 아웃"
    else -> outcome.name
}

/** 타구 방향. 오른손 타자 기준 — 음수(일찍 침)는 좌측 */
fun directionText(angleDeg: Double): String = when {
    abs(angleDeg) > 45 -> if (angleDeg < 0) "3루 쪽 파울" else "1루 쪽 파울"
    angleDeg < -27 -> "좌측"
    angleDeg < -9 -> "좌중간"
    angleDeg <= 9 -> "중앙"
    angleDeg <= 27 -> "우중간"
    else -> "우측"
}

fun timingText(diffMs: Double): String {
    val ms = diffMs.roundToInt()
    return when {
        ms > 0 -> "${ms}ms 늦음"
        ms < 0 -> "${-ms}ms 빠름"
        else -> "딱 맞음"
    }
}
