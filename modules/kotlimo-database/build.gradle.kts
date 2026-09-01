plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":kotlimo-core"))
    api(libs.h2)
    api(libs.sqlite)
    api(libs.postgresql)
    api(libs.mysql)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}
