package io.kotlimo.foundation

import io.kotlimo.auth.Gate
import io.kotlimo.auth.PasswordBroker
import io.kotlimo.cache.CacheRepository
import io.kotlimo.cache.MemoryKeyValue
import io.kotlimo.cache.RedisStore
import io.kotlimo.database.DatabaseManager
import io.kotlimo.database.SchemaBuilder
import io.kotlimo.foundation.facades.Auth
import io.kotlimo.foundation.facades.Hash
import io.kotlimo.hash.Hash as HashHasher
import io.kotlimo.http.HttpKernel
import io.kotlimo.http.JettyServer
import io.kotlimo.http.Request
import io.kotlimo.http.Response
import io.kotlimo.queue.DatabaseQueue
import io.kotlimo.queue.NamedJob
import io.kotlimo.queue.RedisQueue
import io.kotlimo.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AuthDriversTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `remember me password reset tokens policies and drivers`() {
        Files.writeString(
            dir.resolve(".env"),
            "APP_KEY=unit-test-key\nAPP_ENV=testing\nSESSION_DRIVER=array\nDB_URL=jdbc:h2:mem:auth_drivers;MODE=MySQL;DB_CLOSE_DELAY=-1\n"
        )
        val app = Application.create(dir.toString()).withDefaultProviders().boot()
        val schema = SchemaBuilder(app.make(io.kotlimo.database.Connection::class))
        schema.create("users") {
            id()
            string("email")
            string("password")
            string("remember_token").nullable()
        }
        schema.create("password_resets") {
            string("email")
            string("token")
            string("created_at").nullable()
        }
        schema.create("personal_access_tokens") {
            id()
            string("tokenable_id")
            string("name")
            string("token")
            string("created_at").nullable()
        }
        schema.create("jobs") {
            id()
            string("name")
            text("payload")
            bigInteger("available_at")
        }
        val password = Hash.make("secret", HashHasher.BCRYPT)
        app.make(DatabaseManager::class).table("users").insert(mapOf("email" to "ada@example.com", "password" to password))

        val taken = Validator.make(mapOf("email" to "ada@example.com"), mapOf("email" to "unique:users,email"))
        assertTrue(taken.fails())
        val missing = Validator.make(mapOf("email" to "missing@example.com"), mapOf("email" to "exists:users,email"))
        assertTrue(missing.fails())

        io.kotlimo.foundation.facades.Route.post("/login") { request ->
            val ok = Auth.attempt(
                mapOf("email" to request.string("email"), "password" to request.string("password")),
                remember = request.string("remember") == "1"
            )
            if (ok) Response.redirect("/") else Response.html("no", 422)
        }
        io.kotlimo.foundation.facades.Route.post("/tokens") {
            val current = Auth.user() ?: return@post Response.json(mapOf("message" to "no"), 401)
            Response.json(mapOf("token" to Auth.createToken(current)))
        }
        io.kotlimo.foundation.facades.Route.get("/me") {
            val current = Auth.user()
            if (current == null) Response.json(mapOf("guest" to true), 401)
            else Response.json(mapOf("id" to current.getAuthIdentifier()))
        }
        io.kotlimo.foundation.facades.Route.get("/health") { Response.json(mapOf("status" to "ok")) }

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

        val form = call(Request.get("/health"))
        val token = form.cookies.first { it.name == "kotlimo_session" }
        val sessionToken = app.make(io.kotlimo.session.SessionManager::class).readToken(token.value)
        val login = call(
            Request(
                method = "POST",
                uri = "/login",
                body = mapOf(
                    "email" to "ada@example.com",
                    "password" to "secret",
                    "remember" to "1",
                    "_token" to sessionToken
                )
            )
        )
        assertEquals(302, login.status)
        assertTrue(login.cookies.any { it.name == "kotlimo_remember" })
        val csrf = app.make(io.kotlimo.session.SessionManager::class).readToken(cookies.getValue("kotlimo_session"))

        val issued = call(
            Request(
                method = "POST",
                uri = "/tokens",
                body = mapOf("_token" to csrf)
            )
        )
        assertEquals(200, issued.status)
        val plain = issued.content.substringAfter("\"token\":\"").substringBefore("\"")
        assertTrue(plain.isNotBlank())

        val bearer = kernel.handle(
            Request(
                method = "GET",
                uri = "/me",
                headers = mapOf("Authorization" to "Bearer $plain", "Accept" to "application/json")
            )
        )
        assertEquals(200, bearer.status)
        assertTrue(bearer.content.contains("\"id\""))

        val broker = app.make(PasswordBroker::class)
        val reset = broker.sendResetLink("ada@example.com")
        assertNotNull(reset)
        assertTrue(broker.reset("ada@example.com", reset!!, "new-secret-1"))
        assertFalse(broker.reset("ada@example.com", reset, "another"))

        val gate = app.make(Gate::class)
        gate.policy(String::class) { _, ability, _ -> ability == "view" }
        assertTrue(gate.allows(null, "view", "post"))
        assertThrows(io.kotlimo.http.HttpException::class.java) { gate.authorize(null, "edit", "post") }

        val queue = DatabaseQueue({ app.make(DatabaseManager::class).connection() })
        var seen = ""
        queue.listen("ping") { seen = it }
        queue.push(NamedJob("ping", "hi"))
        queue.pop()?.handle()
        assertEquals("hi", seen)

        val keys = MemoryKeyValue()
        val cache = CacheRepository(RedisStore(keys))
        cache.put("name", "ada")
        assertEquals("ada", cache.get("name"))
        val redisQueue = RedisQueue(keys)
        redisQueue.listen("ping") { seen = it }
        redisQueue.push(NamedJob("ping", "redis"))
        redisQueue.pop()?.handle()
        assertEquals("redis", seen)

        val server = JettyServer(kernel, "127.0.0.1", 0)
        server.start()
        try {
            val body = java.net.URI("http://127.0.0.1:${server.port()}/health").toURL().readText()
            assertTrue(body.contains("ok"))
        } finally {
            server.stop()
        }
    }
}
