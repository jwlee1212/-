// 엔진: 순수 Kotlin. UI/플랫폼 API에 의존하지 않는다 (CLAUDE.md §4-1).
// M0에서는 JVM 타깃만 켠다. Android/iOS 타깃은 M10에서 추가한다.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm()

    // 폰 테스트용 (CLAUDE.md §2 테스트 기기): 웹 브라우저(Wasm) + 아이폰
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
