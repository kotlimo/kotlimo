rootProject.name = "kotlimo"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(
    "kotlimo-core",
    "kotlimo-http",
    "kotlimo-database",
    "kotlimo-view",
    "kotlimo-foundation",
    "kotlimo-testing",
    "website"
)

project(":kotlimo-core").projectDir = file("modules/kotlimo-core")
project(":kotlimo-http").projectDir = file("modules/kotlimo-http")
project(":kotlimo-database").projectDir = file("modules/kotlimo-database")
project(":kotlimo-view").projectDir = file("modules/kotlimo-view")
project(":kotlimo-foundation").projectDir = file("modules/kotlimo-foundation")
project(":kotlimo-testing").projectDir = file("modules/kotlimo-testing")
project(":website").projectDir = file("website")
