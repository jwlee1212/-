// 도구: 리그 생성기, 캘리브레이터 등 JVM 전용 오프라인 도구.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm()

    // 선수 생성기(신인·유망주·외국인)는 앱이 폰에서도 써야 해서 공용 코드로 둔다.
    // 파일 읽기·콘솔 보고서처럼 JVM 에서만 도는 것은 jvmMain 에 남긴다.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":engine"))
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// 테스트에서 저장소 루트의 config/, data/ 를 찾을 수 있게 한다.
tasks.withType<Test>().configureEach {
    systemProperty("baseballgm.root", rootProject.projectDir.absolutePath)
}
