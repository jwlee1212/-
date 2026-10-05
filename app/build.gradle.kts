// 화면 모듈 (Compose Multiplatform).
//
// 타깃 셋 (CLAUDE.md §2 테스트 기기):
// - jvm      : 맥 데스크톱 창. 개발 중 가장 빨리 띄워 보는 용도
// - wasmJs   : 웹 브라우저. 아이폰 사파리로 열어 보는 폰 테스트 1순위
// - iOS      : 아이폰 직접 설치 (Xcode 필요, docs/ios-setup.md)
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "firstpick"
        browser {
            commonWebpackConfig { outputFileName = "firstpick.js" }
        }
        binaries.executable()
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "FirstPickApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            // 신인·유망주·외국인 생성기. 폰에서도 돌아야 해서 공용 코드다
            implementation(project(":tools"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.navigationevent.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

// ---------- 리소스 ----------
//
// 글꼴(src/commonMain/composeResources)과 게임 데이터(config/balance.json, data/*.json)를
// 한 폴더로 모아 앱에 넣는다. 데이터 파일은 원본 한 벌만 두고 빌드할 때 복사한다 —
// 앱용 사본을 저장소에 따로 두면 언젠가 원본과 어긋난다.
val gameResources = tasks.register<Sync>("syncGameResources") {
    from("src/commonMain/composeResources")
    from(rootProject.file("config/balance.json")) { into("files") }
    from(rootProject.file("data/teams.json")) { into("files") }
    from(rootProject.file("data/league_2026.json")) { into("files") }
    into(layout.buildDirectory.dir("gameResources"))
}

compose.resources {
    // 리소스 접근 클래스의 패키지. 폴더 이름(baseball-gm)에서 자동으로 만들면 밑줄이 섞인다
    packageOfResClass = "baseballgm.app.generated.resources"
    publicResClass = false
    customDirectory("commonMain", layout.dir(gameResources.map { it.destinationDir }))
}

// 리소스를 다루는 작업은 모두 모으기가 끝난 뒤에 돈다
tasks.matching { it.name.contains("ComposeResources", ignoreCase = true) || it.name.contains("ResourceAccessors") }
    .configureEach { dependsOn(gameResources) }

compose.desktop {
    application {
        mainClass = "baseballgm.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "FirstPick"
            packageVersion = "1.0.0"
        }
    }
}
