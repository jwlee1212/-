import SwiftUI

// 아이폰 앱 진입점. 화면은 전부 Kotlin(Compose) 쪽에 있고, 여기서는 띄우기만 한다.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
