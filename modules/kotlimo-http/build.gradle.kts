plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":kotlimo-core"))
    api(libs.jetty.server)
    api(libs.jetty.servlet)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}
