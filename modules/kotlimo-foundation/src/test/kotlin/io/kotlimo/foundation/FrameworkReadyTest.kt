package io.kotlimo.foundation

import io.kotlimo.database.DatabaseManager
import io.kotlimo.foundation.facades.Auth
import io.kotlimo.foundation.facades.Config
import io.kotlimo.foundation.facades.Hash
import io.kotlimo.foundation.facades.Mail
import io.kotlimo.foundation.facades.Queue
import io.kotlimo.foundation.facades.Route
import io.kotlimo.foundation.facades.Storage
import io.kotlimo.hash.Hash as HashHasher
import io.kotlimo.http.HttpKernel
import io.kotlimo.http.Request
import io.kotlimo.http.Response
import io.kotlimo.mail.ArrayMailer
import io.kotlimo.queue.Job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FrameworkReadyTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `sessions csrf auth mail queue and storage work together`() {
        Files.writeString(
            dir.resolve(".env"),
            "APP_NAME=Ready\nAPP_ENV=testing\nAPP_KEY=unit-test-key\nMAIL_DRIVER=array\nQUEUE_DRIVER=array\nSESSION_DRIVER=array\n"
        )
        Files.createDirectories(dir.resolve("database/migrations"))
        Files.writeString(
            dir.resolve("database/migrations/0001_create_users_table.json"),
            """
            {
              "name": "0001_create_users_table",
              "up": {
                "create": "users",
                "columns": [
                  {"name": "id", "type": "id"},
                  {"name": "email", "type": "string"},
                  {"name": "password", "type": "string"}
                ]
              },
              "down": { "drop": "users" }
            }
            """.trimIndent()
        )
        val app = Application.create(dir.toString()).withDefaultProviders().boot()
        Route.get("/form") { request -> Response.html("token=${request.csrf()}") }
        Route.post("/login") { request ->
            val ok = Auth.attempt(
                mapOf("email" to request.string("email"), "password" to request.string("password"))
            )
            if (ok) Response.json(mapOf("id" to Auth.id())) else Response.json(mapOf("ok" to false), 422)
        }
        Route.get("/me") { request ->
            val user = request.user()
            if (user == null) Response.json(mapOf("guest" to true), 401)
            else Response.json(mapOf("id" to user.getAuthIdentifier()))
        }

        assertEquals("Ready", Config.string("app.name"))
        val console = app.withConsole()
        assertEquals(0, console.handle(arrayOf("migrate")))
        assertTrue(console.commands().containsKey("migrate:rollback"))
        assertTrue(console.commands().containsKey("make:migration"))

        val password = Hash.make("secret", HashHasher.BCRYPT)
        app.make(DatabaseManager::class).table("users")
            .insert(mapOf("email" to "ada@example.com", "password" to password))

        val kernel = app.make(HttpKernel::class)
        val cookies = mutableMapOf<String, String>()
        fun call(request: Request): Response {
            val incoming = Request(
                method = request.method,
                uri = request.uri,
                headers = request.headers,
                body = request.body,
                cookies = cookies + request.cookies
            )
            val response = kernel.handle(incoming)
            response.cookies.forEach { cookies[it.name] = it.value }
            return response
        }

        val form = call(Request.get("/form"))
        assertEquals(200, form.status)
        val token = form.content.substringAfter("token=")
        val login = call(
            Request(
                method = "POST",
                uri = "/login",
                body = mapOf("email" to "ada@example.com", "password" to "secret", "_token" to token)
            )
        )
        assertEquals(200, login.status)
        assertTrue(login.content.contains("\"id\""))

        val me = call(Request.get("/me"))
        assertEquals(200, me.status)
        assertTrue(me.content.contains("\"id\""))

        Mail.send("ada@example.com", "Welcome", "<p>Hi</p>")
        assertEquals(1, app.make(ArrayMailer::class).messages.size)

        val hits = mutableListOf<Int>()
        Queue.push(Job { hits += 1 })
        assertEquals(0, console.handle(arrayOf("queue:work", "--once")))
        assertEquals(listOf(1), hits)

        assertTrue(Storage.put("notes/hello.txt", "hi"))
        assertEquals("hi", Storage.get("notes/hello.txt"))
    }
}

class ConsoleMigrationTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `make migration writes a json file`() {
        val app = Application.create(dir.toString()).withDefaultProviders()
        val kernel = app.withConsole()
        app.boot()
        assertEquals(0, kernel.handle(arrayOf("make:migration", "create_posts_table")))
        val files = Files.list(dir.resolve("database/migrations")).use { it.toList() }
        assertEquals(1, files.size)
        assertTrue(Files.readString(files.single()).contains("\"create\": \"posts\""))
    }
}
