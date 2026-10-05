package baseballgm.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/** 웹(Wasm) 진입점 — 아이폰 사파리로 열어 보는 폰 테스트용 (CLAUDE.md §2) */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}
