package baseballgm.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.localStorage

/**
 * 웹(Wasm) 진입점 — 아이폰 사파리로 열어 보는 폰 테스트용 (CLAUDE.md §2).
 *
 * 데이터는 앱에 넣어 둔 사본을 읽고, 세이브는 브라우저 localStorage 에 둔다
 * (같은 브라우저·같은 주소에서만 이어진다). 웹 탭은 스스로 닫을 수 없어 종료 확인은 없다.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App(loadHost = { HostFactory.fromBundledResources(LocalStorageSaveStore, exitApp = null) })
    }
}

/** 브라우저 localStorage 세이브. 세이브 하나가 1MB 안팎이라 기본 한도(약 5MB) 안에 들어간다 */
private object LocalStorageSaveStore : SaveStore {
    private const val KEY = "firstpick.save"

    override fun read(): String? = localStorage.getItem(KEY)

    override fun write(text: String) {
        localStorage.setItem(KEY, text)
    }
}
