package io.kotlimo.console

object AppSkeleton {
    fun files(name: String, includeBuild: String): Map<String, String> {
        val ident = name.replace(Regex("[^A-Za-z0-9_]+"), "").ifBlank { "app" }
        return linkedMapOf(
            "settings.gradle.kts" to settings(name, includeBuild),
            "build.gradle.kts" to buildFile(),
            "gradle.properties" to "org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8\nkotlin.code.style=official\n",
            ".gitignore" to gitignore(),
            ".env.example" to envExample(),
            "README.md" to readme(name),
            "config/app.json" to """{"name":"$ident","env":"local","debug":true,"url":"http://127.0.0.1:8000","key":""}""" + "\n",
            "config/database.json" to """{"url":"jdbc:h2:mem:${ident};MODE=MySQL;DB_CLOSE_DELAY=-1","username":"","password":""}""" + "\n",
            "config/session.json" to """{"driver":"file","cookie":"kotlimo_session","lifetime":120,"path":"/"}""" + "\n",
            "config/mail.json" to """{"driver":"log","from":"hello@example.com"}""" + "\n",
            "config/queue.json" to """{"driver":"sync"}""" + "\n",
            "config/filesystems.json" to """{"default":"local","disks":{"local":{"driver":"local","root":"storage/app"}}}""" + "\n",
            "config/hash.json" to """{"driver":"bcrypt"}""" + "\n",
            "database/migrations/0001_create_users_table.json" to usersMigration(),
            "resources/views/layouts/app.kote" to layout(),
            "resources/views/home.kote" to home(ident),
            "public/css/app.css" to "body{font-family:system-ui,sans-serif;margin:2rem;color:#1b1b18;background:#fff;}\n",
            "src/main/kotlin/Application.kt" to applicationKt(),
            "src/main/kotlin/http/WebRoutes.kt" to routesKt(),
            "src/main/kotlin/controllers/HomeController.kt" to controllerKt(),
            "src/main/kotlin/models/User.kt" to userModelKt()
        )
    }

    private fun settings(name: String, includeBuild: String): String = """
        rootProject.name = "$name"

        pluginManagement {
            repositories {
                gradlePluginPortal()
                mavenCentral()
            }
        }

        dependencyResolutionManagement {
            repositories {
                mavenCentral()
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/kotlimo/kotlimo")
                    credentials {
                        username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).getOrElse("")
                        password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).getOrElse("")
                    }
                }
            }
        }

        $includeBuild
    """.trimIndent() + "\n"

    private fun buildFile(): String = """
        plugins {
            kotlin("jvm") version "2.1.21"
            application
        }

        group = "app"
        version = "0.1.0"

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        dependencies {
            implementation("io.kotlimo:kotlimo-foundation:0.1.0")
        }

        application {
            mainClass.set("app.ApplicationKt")
        }

        tasks.test {
            useJUnitPlatform()
        }
    """.trimIndent() + "\n"

    private fun gitignore(): String = """
        .gradle/
        build/
        .idea/
        .kotlin/
        .DS_Store
        .env
        storage/framework/sessions/
        storage/app/
        storage/logs/
        *.db
    """.trimIndent() + "\n"

    private fun envExample(): String = """
        APP_NAME=Kotlimo
        APP_ENV=local
        APP_DEBUG=true
        APP_URL=http://127.0.0.1:8000
        APP_KEY=

        DB_URL=jdbc:h2:mem:kotlimo;MODE=MySQL;DB_CLOSE_DELAY=-1
        DB_USERNAME=
        DB_PASSWORD=

        SESSION_DRIVER=file
        QUEUE_DRIVER=sync
        MAIL_DRIVER=log
        HASH_DRIVER=bcrypt
    """.trimIndent() + "\n"

    private fun readme(name: String): String = """
        # $name

        A [Kotlimo](https://kotlimo.github.io) application.

        ```bash
        cp .env.example .env
        ./gradlew run
        ```

        Craft commands:

        ```bash
        ./gradlew run --args='list'
        ./gradlew run --args='migrate'
        ./gradlew run --args='route:list'
        ```

        This project depends on `io.kotlimo:kotlimo-foundation:0.1.0`. If it was generated next to the Kotlimo source tree, `settings.gradle.kts` includes that build as a composite so you do not need GitHub Packages. To use published artifacts instead, publish from the framework repo (`./gradlew publish`) and set `gpr.user` / `gpr.key` or `GITHUB_ACTOR` / `GITHUB_TOKEN`.
    """.trimIndent() + "\n"

    private fun usersMigration(): String = """
        {
          "name": "0001_create_users_table",
          "up": {
            "create": "users",
            "columns": [
              {"name": "id", "type": "id"},
              {"name": "name", "type": "string"},
              {"name": "email", "type": "string", "unique": true},
              {"name": "password", "type": "string"},
              {"name": "timestamps", "type": "timestamps"}
            ]
          },
          "down": {
            "drop": "users"
          }
        }
    """.trimIndent() + "\n"

    private fun layout(): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>{{ title }}</title>
            <link rel="stylesheet" href="/css/app.css">
        </head>
        <body>
            @yield('content')
        </body>
        </html>
    """.trimIndent() + "\n"

    private fun home(name: String): String = """
        @extends('layouts.app')

        @section('content')
        <h1>$name</h1>
        <p>Kotlimo is ready.</p>
        @endsection
    """.trimIndent() + "\n"

    private fun applicationKt(): String = """
        package app

        import app.http.registerRoutes
        import io.kotlimo.foundation.Application
        import io.kotlimo.foundation.run
        import io.kotlimo.foundation.withConsole
        import io.kotlimo.foundation.withDefaultProviders
        import java.nio.file.Files
        import java.nio.file.Paths
        import kotlin.system.exitProcess

        fun main(args: Array<String>) {
            val command = if (args.isEmpty()) arrayOf("serve") else args
            exitProcess(createApp().run(command))
        }

        fun createApp(basePath: String = detectBasePath()): Application {
            val app = Application.create(basePath).withDefaultProviders()
            app.boot()
            registerRoutes(app)
            app.withConsole()
            return app
        }

        private fun detectBasePath(): String {
            val userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
            return if (Files.isDirectory(userDir.resolve("resources").resolve("views"))) userDir.toString() else userDir.toString()
        }
    """.trimIndent() + "\n"

    private fun routesKt(): String = """
        package app.http

        import app.controllers.HomeController
        import io.kotlimo.foundation.Application
        import io.kotlimo.routing.Router

        fun registerRoutes(app: Application) {
            val router = app.make(Router::class)
            router.get("/", HomeController::class, "index").named("home")
        }
    """.trimIndent() + "\n"

    private fun controllerKt(): String = """
        package app.controllers

        import io.kotlimo.foundation.facades.view
        import io.kotlimo.http.Request
        import io.kotlimo.http.Response

        class HomeController {
            fun index(request: Request): Response = view(
                "home",
                "title" to "Kotlimo"
            )
        }
    """.trimIndent() + "\n"

    private fun userModelKt(): String = """
        package app.models

        import io.kotlimo.database.Model
        import io.kotlimo.database.ModelCompanion

        class User : Model() {
            override val table = "users"
            override val fillable = listOf("name", "email", "password")

            companion object : ModelCompanion<User>(User::class)
        }
    """.trimIndent() + "\n"
}
