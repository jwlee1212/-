// 도구: 리그 생성기, 캘리브레이터 등 JVM 전용 오프라인 도구.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":engine"))
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
