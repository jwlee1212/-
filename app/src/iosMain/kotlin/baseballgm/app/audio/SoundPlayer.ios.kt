package baseballgm.app.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * iOS 앱: AVAudioPlayer. 소리마다 플레이어를 몇 개 만들어 겹쳐 울릴 수 있게 한다.
 * 세션 종류는 Ambient — 다른 앱 음악을 끊지 않고, 무음 스위치를 따른다.
 */
actual fun createSoundPlayer(): SoundPlayer = IosSoundPlayer()

@OptIn(ExperimentalForeignApi::class)
private class IosSoundPlayer : SoundPlayer {
    private val players = mutableMapOf<Sfx, List<AVAudioPlayer>>()
    private val next = mutableMapOf<Sfx, Int>()

    init {
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
    }

    override fun prepare(sounds: Map<Sfx, FloatArray>, sampleRate: Int) {
        for ((sfx, samples) in sounds) {
            val data = encodeWav(samples, sampleRate).toNSData()
            players[sfx] = List(POOL) { AVAudioPlayer(data, null) }.onEach { it.prepareToPlay() }
        }
    }

    override fun play(sfx: Sfx, volume: Float) {
        val pool = players[sfx].orEmpty().ifEmpty { return }
        val index = next.getOrElse(sfx) { 0 }
        next[sfx] = (index + 1) % pool.size
        val player = pool[index]
        player.volume = volume
        player.currentTime = 0.0
        player.play()
    }

    private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

    private companion object {
        const val POOL = 3
    }
}
