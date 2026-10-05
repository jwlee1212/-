package baseballgm.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** iOS 진입점. Xcode 프로젝트(iosApp/)의 Swift 코드가 이 함수를 불러 화면을 띄운다 (docs/ios-setup.md) */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    App()
}
