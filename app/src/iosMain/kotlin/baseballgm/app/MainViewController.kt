package baseballgm.app

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIViewController

/**
 * iOS 진입점. Xcode 프로젝트(iosApp/)의 Swift 코드가 이 함수를 불러 화면을 띄운다 (docs/ios-setup.md).
 *
 * 데이터는 앱에 넣어 둔 사본을 읽고, 세이브는 앱 문서 폴더의 파일에 둔다.
 * iOS 앱은 스스로 종료하지 않는 게 규칙이라 종료 확인은 없다.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(loadHost = { HostFactory.fromBundledResources(DocumentsSaveStore, exitApp = null) })
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private object DocumentsSaveStore : SaveStore {
    private val url: NSURL?
        get() = NSFileManager.defaultManager
            .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .firstOrNull()
            ?.let { it as NSURL }
            ?.URLByAppendingPathComponent("save.json")

    override fun read(): String? {
        val target = url ?: return null
        return NSString.stringWithContentsOfURL(target, NSUTF8StringEncoding, null)
    }

    override fun write(text: String) {
        val target = url ?: return
        // atomically = true: 임시 파일에 쓰고 바꿔 끼운다 (쓰다 꺼져도 이전 세이브가 남는다)
        NSString.create(string = text).writeToURL(target, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}
