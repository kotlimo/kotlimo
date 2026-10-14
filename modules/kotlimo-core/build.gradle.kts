plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.kotlin.reflect)
    api(libs.jackson.databind)
    api(libs.jackson.kotlin)
    api(libs.slf4j.api)
    api(libs.bcrypt)
    api(libs.bcprov)
    api(libs.jedis)
    api(libs.jakarta.mail)
    api(libs.aws.s3)
    api(libs.aws.url)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}
