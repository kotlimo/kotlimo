plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("io.kotlimo.console.KotlimoConsoleKt")
}

dependencies {
    implementation(project(":kotlimo-foundation"))
    implementation(libs.slf4j.simple)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
