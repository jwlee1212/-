import SwiftUI
import UIKit
import FirstPickApp

// Kotlin 의 MainViewController() (app/src/iosMain) 를 SwiftUI 화면으로 감싼다.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
