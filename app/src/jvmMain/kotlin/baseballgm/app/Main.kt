package baseballgm.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * 데스크톱(맥) 진입점. 개발 중 가장 빨리 띄워 보는 용도라 창 크기를 폰 비율로 둔다.
 * 세이브 저장소는 S3.5(첫 플레이어블)에서 다시 붙인다.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = AppInfo.TITLE,
        state = rememberWindowState(size = DpSize(430.dp, 900.dp)),
    ) {
        App()
    }
}
