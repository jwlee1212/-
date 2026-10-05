package baseballgm.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import baseballgm.app.audio.SoundSynth
import baseballgm.app.audio.createSoundPlayer
import baseballgm.app.batting.BattingScreen
import baseballgm.app.batting.BattingSession
import baseballgm.app.batting.Presentation
import baseballgm.app.generated.resources.Res
import baseballgm.app.ui.ScoutTheme
import baseballgm.app.ui.T
import baseballgm.batting.BattingConfig
import baseballgm.io.BalanceConfig
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** 게임 이름 (가제) */
object AppInfo {
    const val TITLE = "타격 프로토타입"
}

/**
 * 앱 루트. 지금은 컨셉 검증용 타석 화면 하나만 띄운다.
 *
 * 수치는 빌드 때 앱에 넣은 `config/balance.json` 사본에서 읽는다 (app/build.gradle.kts 의 syncGameResources).
 * 수치를 바꾸면 다시 빌드해야 반영된다.
 */
@Composable
fun App() {
    ScoutTheme {
        val player = remember { createSoundPlayer().also { it.prepare(SoundSynth.all(), SoundSynth.SAMPLE_RATE) } }
        val loaded by produceState<Result<BattingSession>?>(null) {
            value = runCatching { loadSession() }
        }
        when (val result = loaded) {
            null -> Message("불러오는 중…")
            else -> result.fold(
                onSuccess = { BattingScreen(it, player) },
                onFailure = { Message("설정을 읽지 못했어요: ${it.message}") },
            )
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadSession(): BattingSession {
    val balance = BalanceConfig.parse(Res.readBytes("files/balance.json").decodeToString())
    return BattingSession(BattingConfig.from(balance), Presentation.from(balance))
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().background(T.color.background).padding(T.space.lg), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
