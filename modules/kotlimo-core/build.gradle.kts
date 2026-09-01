plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.kotlin.reflect)
    api(libs.jackson.databind)
    api(libs.jackson.kotlin)
    api(libs.slf4j.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}
