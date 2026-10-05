# 아이폰에 직접 설치하기 (무료 Apple 계정)

CLAUDE.md §2 테스트 기기 ② 절차. **처음 한 번만** Xcode 프로젝트를 만들면, 그다음부터는 Xcode 에서 ▶ 만 누르면 된다.
Kotlin 쪽 준비(iOS 타깃, `MainViewController`, 프레임워크 이름 `FirstPickApp`)는 저장소에 이미 있다.

> 지금 맥에는 Xcode 가 없다(Command Line Tools 만 있음). Kotlin 코드가 iOS 용으로 컴파일되는 것까지는
> 확인했지만, 앱으로 묶는 마지막 단계는 Xcode 가 있어야 돈다.

## 1. Xcode 설치 (한 번)
1. App Store 에서 **Xcode** 설치 (용량 큼, 수십 분)
2. 터미널: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`
3. Xcode 를 한 번 열어 추가 구성요소 설치 + iOS 플랫폼 내려받기
4. 확인: `xcodebuild -version`

## 2. Xcode 프로젝트 만들기 (한 번)
1. Xcode → File → New → Project → iOS **App**
   - Product Name: `FirstPick`, Interface: **SwiftUI**, Language: **Swift**
   - 저장 위치: 이 저장소의 `iosApp/` 폴더 (만들어지는 `FirstPick.xcodeproj` 가 `iosApp/` 바로 아래에 오게)
2. Xcode 가 만든 `ContentView.swift`, `FirstPickApp.swift`(또는 `…App.swift`)를 지우고,
   저장소의 `iosApp/iosApp/iOSApp.swift`, `iosApp/iosApp/ContentView.swift` 를 프로젝트에 끌어다 넣는다
3. 타깃 → **Build Phases** → `+` → New Run Script Phase → 맨 위(Compile Sources 앞)로 올리고 내용:
   ```sh
   cd "$SRCROOT/.."
   ./gradlew :app:embedAndSignAppleFrameworkForXcode
   ```
4. 타깃 → **Build Settings**
   - `User Script Sandboxing` = **No** (스크립트가 gradle 을 돌릴 수 있게)
   - `Framework Search Paths` 에 추가: `$(SRCROOT)/../app/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
   - `Other Linker Flags` 에 추가: `-framework FirstPickApp`
5. (선택) `Info.plist` 에 `CADisableMinimumFrameDurationOnPhone` = YES — 120Hz 화면에서 부드럽게

## 3. 아이폰에 설치
1. Xcode → Settings → Accounts 에 Apple ID 추가 (무료 계정이면 "Personal Team")
2. 타깃 → Signing & Capabilities → Team: Personal Team, Bundle Identifier 는 겹치지 않게 (예: `com.<이름>.firstpick`)
3. 아이폰을 케이블로 연결 → 아이폰 설정 → 개인정보 보호 및 보안 → **개발자 모드** 켜기 (재시동)
4. Xcode 상단에서 기기를 내 아이폰으로 고르고 ▶
5. 처음 실행 시 아이폰 설정 → 일반 → VPN 및 기기 관리 → 내 개발자 앱 **신뢰**

## 알아 둘 것
- 무료 계정 설치본은 **7일 뒤 실행이 안 된다.** 다시 연결해 ▶ 하면 된다 (세이브는 앱 문서 폴더라 그대로 남는다)
- 첫 빌드는 Kotlin/Native 컴파일 때문에 몇 분 걸린다
- 세이브 위치: 앱 문서 폴더의 `save.json`
- 출시 전에는 Apple 개발자 프로그램 가입 → TestFlight 로 옮긴다
