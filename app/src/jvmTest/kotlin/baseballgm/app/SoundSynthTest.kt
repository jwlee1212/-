package baseballgm.app

import baseballgm.app.audio.Sfx
import baseballgm.app.audio.SoundSynth
import baseballgm.app.audio.encodeWav
import baseballgm.tools.ProjectFiles
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 합성한 효과음이 깨지지 않았는지 확인하고, 들어 볼 수 있게 app/build/sounds/ 에 wav 로 남긴다 */
class SoundSynthTest {
    @Test
    fun `모든 효과음이 정상 범위의 소리다`() {
        val sounds = SoundSynth.all()
        assertEquals(Sfx.entries.toSet(), sounds.keys)
        val dir = File(ProjectFiles.root, "app/build/sounds").apply { mkdirs() }
        for ((sfx, samples) in sounds) {
            assertTrue(samples.isNotEmpty(), "$sfx 비어 있음")
            assertTrue(samples.none { it.isNaN() }, "$sfx NaN")
            val peak = samples.maxOf { kotlin.math.abs(it) }
            assertTrue(peak in 0.05f..1.0f, "$sfx 최대 진폭 $peak")
            File(dir, "${sfx.name.lowercase()}.wav").writeBytes(encodeWav(samples, SoundSynth.SAMPLE_RATE))
        }
    }

    @Test
    fun `같은 소리는 매번 똑같이 합성된다`() {
        assertTrue(SoundSynth.all().getValue(Sfx.CRACK_SOLID).contentEquals(SoundSynth.all().getValue(Sfx.CRACK_SOLID)))
    }
}
