package baseballgm.app.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** 효과음 종류 */
enum class Sfx {
    /** 투수가 공을 놓는 순간 */
    PITCH,
    SWING,
    /** 정타 "딱!" */
    CRACK_SOLID,
    /** 빗맞음 "퍽" */
    CRACK_WEAK,
    /** 파울 "틱" */
    CRACK_FOUL,
    /** 포수 미트 "팡" */
    MITT,
    /** 안타 함성 (짧게) */
    CHEER_SMALL,
    /** 홈런 함성 */
    CHEER_BIG,
}

/** 플랫폼별 소리 재생기. 소리는 [prepare] 로 한 번 넘겨 두고 [play] 로 재생한다 */
interface SoundPlayer {
    fun prepare(sounds: Map<Sfx, FloatArray>, sampleRate: Int)

    /** [volume] 0~1 */
    fun play(sfx: Sfx, volume: Float = 1f)

    /** 사용자가 화면을 만질 때마다 부른다. 웹(특히 iOS 사파리)은 사용자 입력 뒤에만 소리를 켤 수 있다 */
    fun unlock() {}
}

/** 아무 소리도 내지 않는 재생기 (테스트·소리 초기화 실패 시) */
object SilentPlayer : SoundPlayer {
    override fun prepare(sounds: Map<Sfx, FloatArray>, sampleRate: Int) {}
    override fun play(sfx: Sfx, volume: Float) {}
}

expect fun createSoundPlayer(): SoundPlayer

/**
 * 효과음 합성기. 음원 파일 없이 사인파·잡음·감쇠 곡선을 섞어 만든다 (저작권 걱정 없음, 크기 0).
 *
 * 잡음은 고정 시드 난수로 만든다 — 실행할 때마다 같은 소리가 난다 (불변 원칙 2).
 */
object SoundSynth {
    const val SAMPLE_RATE = 22_050

    fun all(): Map<Sfx, FloatArray> {
        val random = Random(20261005)
        return Sfx.entries.associateWith { synth(it, random) }
    }

    private fun synth(sfx: Sfx, random: Random): FloatArray = when (sfx) {
        // 짧고 부드러운 바람 소리
        Sfx.PITCH -> whoosh(0.14, centerStart = 0.15, centerEnd = 0.35, random).scale(0.35f)
        // 배트가 공기를 가르는 소리: 점점 높아지는 바람
        Sfx.SWING -> whoosh(0.22, centerStart = 0.08, centerEnd = 0.5, random).scale(0.6f)
        // 나무 배트 정타: 아주 짧은 딸깍 + 높은 공명음 몇 개 + 낮은 몸통 울림
        Sfx.CRACK_SOLID -> mix(
            0.32,
            click(0.004, random).scale(1.0f),
            tone(1850.0, 0.045, 0.6),
            tone(2650.0, 0.035, 0.45),
            tone(3900.0, 0.025, 0.3),
            tone(190.0, 0.07, 0.55),
        ).normalized(0.95f)
        // 빗맞음: 높은 공명 없이 둔탁하게
        Sfx.CRACK_WEAK -> mix(
            0.2,
            lowpass(click(0.012, random), 0.25).scale(0.9f),
            tone(320.0, 0.035, 0.7),
            tone(720.0, 0.02, 0.3),
        ).normalized(0.7f)
        // 파울 끝에 스친 소리: 가늘고 짧게
        Sfx.CRACK_FOUL -> mix(
            0.12,
            click(0.003, random).scale(0.6f),
            tone(2400.0, 0.015, 0.6),
            tone(1200.0, 0.02, 0.3),
        ).normalized(0.6f)
        // 미트에 꽂히는 "팡": 낮은 울림 + 가죽 잡음
        Sfx.MITT -> mix(
            0.14,
            tone(115.0, 0.03, 0.9),
            lowpass(click(0.015, random), 0.35).scale(0.8f),
        ).normalized(0.8f)
        Sfx.CHEER_SMALL -> crowd(0.9, attack = 0.08, random).scale(0.45f)
        Sfx.CHEER_BIG -> crowd(2.2, attack = 0.25, random).scale(0.7f)
    }

    // ---------- 재료 ----------

    private fun length(seconds: Double) = (seconds * SAMPLE_RATE).toInt()

    /** 지수 감쇠 사인파. [tau] 초마다 1/e 로 줄어든다 */
    private fun tone(freq: Double, tau: Double, gain: Double): FloatArray {
        val n = length(tau * 6)
        return FloatArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            (sin(2 * PI * freq * t) * exp(-t / tau) * gain).toFloat()
        }
    }

    /** 아주 짧은 잡음 덩어리 (타격 순간의 "딸깍") */
    private fun click(seconds: Double, random: Random): FloatArray {
        val n = length(seconds)
        return FloatArray(n) { i -> ((random.nextDouble() * 2 - 1) * (1 - i.toDouble() / n)).toFloat() }
    }

    /** 바람 소리: 잡음을 저역 통과시키되 통과 대역을 시간에 따라 바꾸고, 가운데가 불룩한 볼륨 곡선을 씌운다 */
    private fun whoosh(seconds: Double, centerStart: Double, centerEnd: Double, random: Random): FloatArray {
        val n = length(seconds)
        var y = 0.0
        return FloatArray(n) { i ->
            val p = i.toDouble() / n
            val a = centerStart + (centerEnd - centerStart) * p
            y += a * ((random.nextDouble() * 2 - 1) - y)
            (y * sin(PI * p) * 2.2).toFloat()
        }
    }

    /** 관중 함성: 잡음을 여러 번 걸러 웅성거림을 만들고 천천히 일렁이게 한다 */
    private fun crowd(seconds: Double, attack: Double, random: Random): FloatArray {
        val n = length(seconds)
        var lp1 = 0.0
        var lp2 = 0.0
        val wobble = DoubleArray(6) { random.nextDouble(3.0, 9.0) }
        return FloatArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val noise = random.nextDouble() * 2 - 1
            lp1 += 0.35 * (noise - lp1)
            lp2 += 0.08 * (noise - lp2)
            val band = lp1 - lp2 // 대략 중간 대역 (사람 목소리 근처)
            val env = (t / attack).coerceAtMost(1.0) * exp(-max(0.0, t - attack) / (seconds * 0.45))
            val mod = 0.75 + 0.25 * wobble.sumOf { sin(2 * PI * it * t + it) } / wobble.size
            (band * env * mod * 3.0).toFloat()
        }
    }

    private fun lowpass(input: FloatArray, a: Double): FloatArray {
        var y = 0.0
        return FloatArray(input.size) { i ->
            y += a * (input[i] - y)
            y.toFloat()
        }
    }

    private fun mix(seconds: Double, vararg parts: FloatArray): FloatArray {
        val out = FloatArray(length(seconds))
        for (part in parts) for (i in 0 until minOf(part.size, out.size)) out[i] += part[i]
        return out
    }

    private fun FloatArray.scale(gain: Float) = FloatArray(size) { this[it] * gain }

    private fun FloatArray.normalized(peak: Float): FloatArray {
        val m = maxOf(maxOrNull() ?: 0f, -(minOrNull() ?: 0f))
        return if (m == 0f) this else scale(peak / m)
    }
}

/** 16비트 모노 WAV 파일 바이트 (iOS·데스크톱 재생기가 쓴다) */
fun encodeWav(samples: FloatArray, sampleRate: Int): ByteArray {
    val dataBytes = samples.size * 2
    val out = ByteArray(44 + dataBytes)
    var pos = 0
    fun str(s: String) = s.forEach { out[pos++] = it.code.toByte() }
    fun int32(v: Int) = repeat(4) { out[pos++] = (v shr (8 * it)).toByte() }
    fun int16(v: Int) = repeat(2) { out[pos++] = (v shr (8 * it)).toByte() }
    str("RIFF"); int32(36 + dataBytes); str("WAVE")
    str("fmt "); int32(16); int16(1); int16(1); int32(sampleRate); int32(sampleRate * 2); int16(2); int16(16)
    str("data"); int32(dataBytes)
    for (s in samples) int16((s.coerceIn(-1f, 1f) * 32767).toInt())
    return out
}
