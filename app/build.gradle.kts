// 화면 모듈 (Compose Multiplatform).
// 지금은 데스크톱(JVM)만 켠다 — 맥에서 바로 띄워 보기 위해서다.
// Android/iOS 타깃은 같은 `commonMain` 화면 코드를 그대로 쓰면서 나중에 추가한다.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
        }
        jvmMain.dependencies {
            // 파일 읽기와 신인 생성은 도구 모듈이 맡는다 (엔진·화면은 플랫폼을 모른다)
            implementation(project(":tools"))
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "baseballgm.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "BaseballGM"
            packageVersion = "1.0.0"
        }
    }
}
