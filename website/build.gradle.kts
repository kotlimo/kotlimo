plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("io.kotlimo.website.WebsiteApplicationKt")
}

dependencies {
    implementation(project(":kotlimo-foundation"))
    implementation(project(":kotlimo-testing"))
    implementation(libs.slf4j.simple)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.named<JavaExec>("run") {
    workingDir = project.projectDir
    args = listOf("serve", "--port=8000")
}
