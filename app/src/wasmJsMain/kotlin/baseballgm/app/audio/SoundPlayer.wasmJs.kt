package baseballgm.app.audio

/**
 * 웹: Web Audio API. 지연이 짧아 타격음에 맞다 (HTML audio 태그는 iOS 에서 늦게 울린다).
 *
 * iOS 사파리는 사용자가 화면을 만진 뒤에야 소리를 켜 준다. 그래서 문서 전체에 터치 끝·클릭 리스너를 달아
 * 처음 만질 때 오디오를 깨우고, 화면 탭마다 [unlock] 도 부른다.
 * 아이폰 무음 스위치가 켜져 있으면 웹 소리는 나지 않는다.
 */
actual fun createSoundPlayer(): SoundPlayer = WebSoundPlayer()

private class WebSoundPlayer : SoundPlayer {
    private val context: JsAny? = createContext()
    private val buffers = mutableMapOf<Sfx, JsAny>()

    init {
        context?.let { installUnlock(it) }
    }

    override fun prepare(sounds: Map<Sfx, FloatArray>, sampleRate: Int) {
        val ctx = context ?: return
        for ((sfx, samples) in sounds) {
            val buffer = createBuffer(ctx, samples.size, sampleRate)
            val channel = channelData(buffer)
            for (i in samples.indices) setSample(channel, i, samples[i].toDouble())
            buffers[sfx] = buffer
        }
    }

    override fun play(sfx: Sfx, volume: Float) {
        val ctx = context ?: return
        val buffer = buffers[sfx] ?: return
        playBuffer(ctx, buffer, volume.toDouble())
    }

    override fun unlock() {
        context?.let { resume(it) }
    }
}

private fun createContext(): JsAny? =
    js("(window.AudioContext || window.webkitAudioContext) ? new (window.AudioContext || window.webkitAudioContext)() : null")

private fun createBuffer(ctx: JsAny, length: Int, rate: Int): JsAny = js("ctx.createBuffer(1, length, rate)")

private fun channelData(buffer: JsAny): JsAny = js("buffer.getChannelData(0)")

private fun setSample(channel: JsAny, index: Int, value: Double): Unit = js("{ channel[index] = value; }")

private fun playBuffer(ctx: JsAny, buffer: JsAny, gain: Double): Unit = js(
    """{
        if (ctx.state !== 'running') ctx.resume();
        const s = ctx.createBufferSource();
        s.buffer = buffer;
        const g = ctx.createGain();
        g.gain.value = gain;
        s.connect(g);
        g.connect(ctx.destination);
        s.start();
    }""",
)

private fun resume(ctx: JsAny): Unit = js("{ if (ctx.state !== 'running') ctx.resume(); }")

/** 처음 터치할 때 무음 버퍼를 한 번 틀어 오디오를 깨운다 (iOS 사파리 규칙) */
private fun installUnlock(ctx: JsAny): Unit = js(
    """{
        const wake = () => {
            if (ctx.state !== 'running') ctx.resume();
            const s = ctx.createBufferSource();
            s.buffer = ctx.createBuffer(1, 1, 22050);
            s.connect(ctx.destination);
            s.start();
        };
        ['touchend', 'click', 'keydown'].forEach((e) => document.addEventListener(e, wake, { passive: true }));
    }""",
)
