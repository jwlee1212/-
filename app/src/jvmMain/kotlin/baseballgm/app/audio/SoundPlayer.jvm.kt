package baseballgm.app.audio

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/** 데스크톱: javax.sound 의 Clip. 같은 소리가 겹쳐 나도록 소리마다 클립을 몇 개씩 돌려 쓴다 */
actual fun createSoundPlayer(): SoundPlayer = runCatching { JvmSoundPlayer() }.getOrDefault(SilentPlayer)

private class JvmSoundPlayer : SoundPlayer {
    private val clips = mutableMapOf<Sfx, List<Clip>>()
    private val next = mutableMapOf<Sfx, Int>()

    override fun prepare(sounds: Map<Sfx, FloatArray>, sampleRate: Int) {
        for ((sfx, samples) in sounds) {
            val wav = encodeWav(samples, sampleRate)
            clips[sfx] = runCatching {
                List(POOL) {
                    AudioSystem.getClip().apply { open(AudioSystem.getAudioInputStream(ByteArrayInputStream(wav))) }
                }
            }.getOrDefault(emptyList())
        }
    }

    override fun play(sfx: Sfx, volume: Float) {
        val pool = clips[sfx].orEmpty().ifEmpty { return }
        val index = next.getOrElse(sfx) { 0 }
        next[sfx] = (index + 1) % pool.size
        val clip = pool[index]
        clip.stop()
        (clip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl)?.let { gain ->
            // 0~1 볼륨을 데시벨로 (0 이면 가장 작게)
            val db = if (volume <= 0f) gain.minimum else (20 * log10(volume.toDouble())).toFloat()
            gain.value = db.coerceIn(gain.minimum, gain.maximum)
        }
        clip.framePosition = 0
        clip.start()
    }

    private companion object {
        const val POOL = 3
    }
}
