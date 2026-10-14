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
            "database/migrations/0002_create_password_resets_table.json" to passwordResetsMigration(),
            "database/migrations/0003_create_personal_access_tokens_table.json" to tokensMigration(),
            "database/migrations/0004_create_posts_table.json" to postsMigration(),
            "database/migrations/0005_create_jobs_table.json" to jobsMigration(),
            "resources/views/layouts/app.kote" to layout(),
            "resources/views/home.kote" to home(ident),
            "resources/views/auth/login.kote" to authForm("Login", "/login", "email", "password"),
            "resources/views/auth/register.kote" to registerForm(),
            "resources/views/auth/forgot.kote" to authForm("Forgot password", "/forgot-password", "email"),
            "resources/views/auth/reset.kote" to resetForm(),
            "resources/views/posts/index.kote" to postsIndex(),
            "public/css/app.css" to "body{font-family:system-ui,sans-serif;margin:2rem;color:#1b1b18;background:#fff;}a{color:#3a4700;}\n",
            "routes/web.kt" to routesKt(),
            "src/main/kotlin/app/Application.kt" to applicationKt(),
            "src/main/kotlin/app/http/controllers/HomeController.kt" to controllerKt(),
            "src/main/kotlin/app/http/controllers/AuthController.kt" to authController(),
            "src/main/kotlin/app/http/controllers/PostController.kt" to postController(),
            "src/main/kotlin/app/models/User.kt" to userModelKt(),
            "src/main/kotlin/app/models/Post.kt" to postModelKt(),
            "src/main/kotlin/app/policies/PostPolicy.kt" to postPolicyKt(),
            "tests/HomeTest.kt" to homeTest(),
            "Dockerfile" to dockerfile(name)
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
            testImplementation("io.kotlimo:kotlimo-testing:0.1.0")
            testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        }

        application {
            mainClass.set("app.ApplicationKt")
        }

        kotlin {
            sourceSets.named("main") {
                kotlin.srcDir("routes")
            }
            sourceSets.named("test") {
                kotlin.srcDir("tests")
            }
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
              {"name": "remember_token", "type": "string", "nullable": true},
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
        <p><a href="/register">Register</a> · <a href="/login">Login</a> · <a href="/posts">Posts</a></p>
        @endsection
    """.trimIndent() + "\n"

    private fun applicationKt(): String = """
        package app

        import app.http.registerRoutes
        import app.models.Post
        import app.policies.PostPolicy
        import io.kotlimo.auth.Gate
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
            val migrator = app.make(io.kotlimo.database.Migrator::class)
            val migrations = io.kotlimo.database.MigrationLoader.fromDirectory(
                java.nio.file.Paths.get(basePath).resolve("database").resolve("migrations")
            )
            migrator.migrate(migrations)
            app.make(Gate::class).policy(Post::class, PostPolicy())
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

        import app.http.controllers.AuthController
        import app.http.controllers.HomeController
        import app.http.controllers.PostController
        import io.kotlimo.foundation.Application
        import io.kotlimo.foundation.facades.Auth
        import io.kotlimo.http.Response
        import io.kotlimo.routing.Router

        fun registerRoutes(app: Application) {
            val router = app.make(Router::class)
            router.get("/health") { io.kotlimo.http.Response.json(mapOf("status" to "ok")) }.named("health")
            router.get("/", HomeController::class, "index").named("home")
            router.get("/register", AuthController::class, "register").named("register")
            router.post("/register", AuthController::class, "store").named("register.store")
            router.get("/login", AuthController::class, "login").named("login")
            router.post("/login", AuthController::class, "authenticate").named("login.store")
            router.post("/logout", AuthController::class, "logout").named("logout")
            router.get("/forgot-password", AuthController::class, "forgot").named("password.forgot")
            router.post("/forgot-password", AuthController::class, "sendReset").named("password.email")
            router.get("/reset-password", AuthController::class, "resetForm").named("password.reset")
            router.post("/reset-password", AuthController::class, "reset").named("password.update")
            router.get("/posts", PostController::class, "index").named("posts.index")
            router.post("/posts", PostController::class, "store").named("posts.store")
            router.post("/api/tokens") { request ->
                val current = Auth.user()
                    ?: return@post Response.json(mapOf("message" to "Unauthenticated."), 401)
                Response.json(mapOf("token" to Auth.createToken(current, request.string("name", "default"))))
            }.named("tokens.store")
            router.get("/api/user") {
                val current = Auth.user()
                    ?: return@get Response.json(mapOf("message" to "Unauthenticated."), 401)
                Response.json(mapOf("id" to current.getAuthIdentifier(), "email" to (current as? io.kotlimo.auth.GenericUser)?.get("email")))
            }.named("api.user")
        }
    """.trimIndent() + "\n"

    private fun controllerKt(): String = """
        package app.http.controllers

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

    private fun postModelKt(): String = """
        package app.models

        import io.kotlimo.database.Model
        import io.kotlimo.database.ModelCompanion

        class Post : Model() {
            override val table = "posts"
            override val fillable = listOf("user_id", "title", "body")

            companion object : ModelCompanion<Post>(Post::class)
        }
    """.trimIndent() + "\n"

    private fun postPolicyKt(): String = """
        package app.policies

        import io.kotlimo.auth.Authenticatable
        import io.kotlimo.auth.Policy

        class PostPolicy : Policy {
            override fun allows(user: Authenticatable?, ability: String, model: Any?): Boolean {
                return when (ability) {
                    "create" -> user != null
                    else -> true
                }
            }
        }
    """.trimIndent() + "\n"

    private fun authController(): String = """
        package app.http.controllers

        import io.kotlimo.foundation.facades.Auth
        import io.kotlimo.foundation.facades.Hash
        import io.kotlimo.foundation.facades.Password
        import io.kotlimo.foundation.facades.redirect
        import io.kotlimo.foundation.facades.view
        import io.kotlimo.http.Request
        import io.kotlimo.http.Response
        import io.kotlimo.validation.validate
        import app.models.User

        class AuthController {
            fun register(request: Request): Response = view("auth/register", "title" to "Register")

            fun store(request: Request): Response {
                val data = validate(
                    request.body,
                    mapOf(
                        "name" to "required|string|max:255",
                        "email" to "required|email|unique:users,email",
                        "password" to "required|string|min:8|confirmed"
                    )
                )
                User.create(
                    mapOf(
                        "name" to data["name"],
                        "email" to data["email"],
                        "password" to Hash.make(data["password"].toString())
                    )
                )
                Auth.attempt(
                    mapOf("email" to data["email"], "password" to request.string("password")),
                    remember = request.string("remember") == "1"
                )
                return redirect("/")
            }

            fun login(request: Request): Response = view("auth/login", "title" to "Login")

            fun authenticate(request: Request): Response {
                val ok = Auth.attempt(
                    mapOf("email" to request.string("email"), "password" to request.string("password")),
                    remember = request.string("remember") == "1"
                )
                if (!ok) return view("auth/login", "title" to "Login", "error" to "These credentials do not match our records.")
                return redirect("/")
            }

            fun logout(request: Request): Response {
                Auth.logout()
                return redirect("/")
            }

            fun forgot(request: Request): Response = view("auth/forgot", "title" to "Forgot password")

            fun sendReset(request: Request): Response {
                val email = request.string("email")
                val token = Password.sendResetLink(email)
                val status = if (token == null) {
                    "We can't find a user with that email address."
                } else {
                    "Reset token: ${'$'}token"
                }
                return view("auth/forgot", "title" to "Forgot password", "status" to status)
            }

            fun resetForm(request: Request): Response = view(
                "auth/reset",
                "title" to "Reset password",
                "token" to request.query["token"].orEmpty(),
                "email" to request.query["email"].orEmpty()
            )

            fun reset(request: Request): Response {
                val ok = Password.reset(request.string("email"), request.string("token"), request.string("password"))
                if (!ok) return view("auth/reset", "title" to "Reset password", "error" to "This password reset token is invalid.")
                return redirect("/login")
            }
        }
    """.trimIndent() + "\n"

    private fun postController(): String = """
        package app.http.controllers

        import app.models.Post
        import io.kotlimo.foundation.facades.Auth
        import io.kotlimo.foundation.facades.Gate
        import io.kotlimo.foundation.facades.redirect
        import io.kotlimo.foundation.facades.view
        import io.kotlimo.http.Request
        import io.kotlimo.http.Response
        import io.kotlimo.validation.validate

        class PostController {
            fun index(request: Request): Response = view("posts/index", "title" to "Posts", "posts" to Post.all())

            fun store(request: Request): Response {
                if (Auth.guest()) return redirect("/login")
                Gate.authorize(Auth.user(), "create", Post())
                val data = validate(
                    request.body,
                    mapOf(
                        "title" to "required|string|max:255",
                        "body" to "required|string"
                    )
                )
                Post.create(
                    mapOf(
                        "user_id" to Auth.id(),
                        "title" to data["title"],
                        "body" to data["body"]
                    )
                )
                return redirect("/posts")
            }
        }
    """.trimIndent() + "\n"

    private fun passwordResetsMigration(): String = """
        {
          "name": "0002_create_password_resets_table",
          "up": {
            "create": "password_resets",
            "columns": [
              {"name": "email", "type": "string"},
              {"name": "token", "type": "string"},
              {"name": "created_at", "type": "string", "nullable": true}
            ]
          },
          "down": { "drop": "password_resets" }
        }
    """.trimIndent() + "\n"

    private fun tokensMigration(): String = """
        {
          "name": "0003_create_personal_access_tokens_table",
          "up": {
            "create": "personal_access_tokens",
            "columns": [
              {"name": "id", "type": "id"},
              {"name": "tokenable_id", "type": "string"},
              {"name": "name", "type": "string"},
              {"name": "token", "type": "string"},
              {"name": "created_at", "type": "string", "nullable": true}
            ]
          },
          "down": { "drop": "personal_access_tokens" }
        }
    """.trimIndent() + "\n"

    private fun postsMigration(): String = """
        {
          "name": "0004_create_posts_table",
          "up": {
            "create": "posts",
            "columns": [
              {"name": "id", "type": "id"},
              {"name": "user_id", "type": "bigint", "nullable": true},
              {"name": "title", "type": "string"},
              {"name": "body", "type": "text"},
              {"name": "timestamps", "type": "timestamps"}
            ]
          },
          "down": { "drop": "posts" }
        }
    """.trimIndent() + "\n"

    private fun jobsMigration(): String = """
        {
          "name": "0005_create_jobs_table",
          "up": {
            "create": "jobs",
            "columns": [
              {"name": "id", "type": "id"},
              {"name": "name", "type": "string"},
              {"name": "payload", "type": "text"},
              {"name": "available_at", "type": "bigint"}
            ]
          },
          "down": { "drop": "jobs" }
        }
    """.trimIndent() + "\n"

    private fun authForm(title: String, action: String, vararg fields: String): String {
        val inputs = fields.joinToString("\n") { field ->
            val type = if (field == "password") "password" else "email"
            """        <p><label>${field.replaceFirstChar { it.uppercase() }} <input name="$field" type="$type"></label></p>"""
        }
        val extra = if (action == "/login") {
            """
        <p><label><input type="checkbox" name="remember" value="1"> Remember me</label></p>
        <p><a href="/forgot-password">Forgot password</a></p>
            """.trimIndent()
        } else {
            ""
        }
        return """
        @extends('layouts.app')

        @section('content')
        <h1>$title</h1>
        @if(error)
        <p>{{ error }}</p>
        @endif
        @if(status)
        <p>{{ status }}</p>
        @endif
        <form method="POST" action="$action">
        <input type="hidden" name="_token" value="{{ csrf }}">
        $inputs
        $extra
        <button type="submit">$title</button>
        </form>
        @endsection
        """.trimIndent() + "\n"
    }

    private fun registerForm(): String = """
        @extends('layouts.app')

        @section('content')
        <h1>Register</h1>
        <form method="POST" action="/register">
        <input type="hidden" name="_token" value="{{ csrf }}">
        <p><label>Name <input name="name" type="text"></label></p>
        <p><label>Email <input name="email" type="email"></label></p>
        <p><label>Password <input name="password" type="password"></label></p>
        <p><label>Confirm password <input name="password_confirmation" type="password"></label></p>
        <p><label><input type="checkbox" name="remember" value="1"> Remember me</label></p>
        <button type="submit">Register</button>
        </form>
        <p><a href="/login">Login</a></p>
        @endsection
    """.trimIndent() + "\n"

    private fun resetForm(): String = """
        @extends('layouts.app')

        @section('content')
        <h1>Reset password</h1>
        @if(error)
        <p>{{ error }}</p>
        @endif
        <form method="POST" action="/reset-password">
        <input type="hidden" name="_token" value="{{ csrf }}">
        <input type="hidden" name="token" value="{{ token }}">
        <input type="hidden" name="email" value="{{ email }}">
        <p><label>Password <input name="password" type="password"></label></p>
        <p><label>Confirm password <input name="password_confirmation" type="password"></label></p>
        <button type="submit">Reset password</button>
        </form>
        @endsection
    """.trimIndent() + "\n"

    private fun postsIndex(): String = """
        @extends('layouts.app')

        @section('content')
        <h1>Posts</h1>
        @if(posts)
        @foreach(posts as post)
        <article>
            <h2>{{ post.title }}</h2>
            <p>{{ post.body }}</p>
        </article>
        @endforeach
        @else
        <p>No posts yet.</p>
        @endif
        <form method="POST" action="/posts">
        <input type="hidden" name="_token" value="{{ csrf }}">
        <p><label>Title <input name="title" type="text"></label></p>
        <p><label>Body <textarea name="body"></textarea></label></p>
        <button type="submit">Publish</button>
        </form>
        @endsection
    """.trimIndent() + "\n"

    private fun homeTest(): String = """
        package app

        import io.kotlimo.testing.HttpTestCase
        import org.junit.jupiter.api.BeforeEach
        import org.junit.jupiter.api.Test

        class HomeTest : HttpTestCase() {
            @BeforeEach
            fun setUp() {
                app = createApp()
            }

            @Test
            fun `health register login and posts`() {
                get("/health").assertOk().assertSee("ok")
                get("/register").assertOk().assertSee("Register")
                post(
                    "/register",
                    mapOf(
                        "name" to "Ada",
                        "email" to "ada@example.com",
                        "password" to "password123",
                        "password_confirmation" to "password123"
                    )
                ).assertRedirect("/")
                get("/posts").assertOk().assertSee("Posts")
                post("/posts", mapOf("title" to "Hello", "body" to "World")).assertRedirect("/posts")
                get("/posts").assertSee("Hello")
            }
        }
    """.trimIndent() + "\n"

    private fun dockerfile(name: String): String = """
        FROM eclipse-temurin:21-jdk
        WORKDIR /app
        COPY . .
        RUN ./gradlew installDist --no-daemon
        EXPOSE 8000
        CMD ["build/install/$name/bin/$name", "serve", "--host=0.0.0.0", "--port=8000"]
    """.trimIndent() + "\n"
}
