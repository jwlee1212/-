// 콘솔 프로토타입 화면. 엔진이 만든 상태/이벤트를 소비하기만 한다 (CLAUDE.md §4-1).
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":engine"))
            implementation(project(":tools"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val jvmMainCompilation = kotlin.jvm().compilations.getByName("main") as KotlinJvmCompilation

// ./gradlew :cli:runCli  로 콘솔 프로토타입 실행
tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "콘솔 프로토타입을 실행한다."
    mainClass.set("baseballgm.cli.MainKt")
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    systemProperty("baseballgm.root", rootProject.projectDir.absolutePath)
    standardInput = System.`in`
    // ./gradlew :cli:runCli --args="generate" 처럼 인자를 넘긴다
}
