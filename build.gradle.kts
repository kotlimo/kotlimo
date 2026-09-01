plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "io.kotlimo"
    version = "0.1.0"
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = false
            }
        }
    }
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        if (!name.startsWith("kotlimo-")) {
            return@withPlugin
        }
        pluginManager.apply("maven-publish")
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
        }
    }
}

subprojects {
    afterEvaluate {
        if (!name.startsWith("kotlimo-") || !pluginManager.hasPlugin("maven-publish")) {
            return@afterEvaluate
        }
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    groupId = "io.kotlimo"
                    artifactId = project.name
                    version = project.version.toString()
                    pom {
                        name.set(project.name)
                        description.set("Kotlimo ${project.name.removePrefix("kotlimo-")} module")
                        url.set("https://github.com/kotlimo/kotlimo")
                        licenses {
                            license {
                                name.set("MIT License")
                                url.set("https://opensource.org/licenses/MIT")
                            }
                        }
                        scm {
                            url.set("https://github.com/kotlimo/kotlimo")
                            connection.set("scm:git:https://github.com/kotlimo/kotlimo.git")
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/kotlimo/kotlimo")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR").orEmpty()
                        password = System.getenv("GITHUB_TOKEN").orEmpty()
                    }
                }
            }
        }
    }
}
