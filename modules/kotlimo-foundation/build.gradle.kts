plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":kotlimo-core"))
    api(project(":kotlimo-http"))
    api(project(":kotlimo-database"))
    api(project(":kotlimo-view"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}
