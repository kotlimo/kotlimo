package io.kotlimo.testing

import io.kotlimo.foundation.Application
import io.kotlimo.foundation.Facade
import io.kotlimo.foundation.withDefaultProviders
import io.kotlimo.http.HttpKernel
import io.kotlimo.http.Request
import io.kotlimo.http.Response
import io.kotlimo.routing.Router
import io.kotlimo.session.SessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertTrue as junitTrue

class TestResponse(val response: Response) {
    val status: Int get() = response.status
    val content: String get() = response.content
    val headers: Map<String, String> get() = response.headers
    val cookies get() = response.cookies

    fun assertOk(): TestResponse = assertStatus(200)

    fun assertStatus(code: Int): TestResponse {
        assertEquals(code, response.status, "Expected status $code but received ${response.status}. Body: ${response.content}")
        return this
    }

    fun assertSee(text: String): TestResponse {
        assertTrue(response.content.contains(text), "Did not see [$text] in response:\n${response.content}")
        return this
    }

    fun assertDontSee(text: String): TestResponse {
        assertFalse(response.content.contains(text), "Saw unexpected [$text] in response")
        return this
    }

    fun assertHeader(key: String, value: String): TestResponse {
        assertEquals(value, response.headers[key])
        return this
    }

    fun assertRedirect(uri: String): TestResponse {
        junitTrue(response.isRedirect())
        assertEquals(uri, response.headers["Location"])
        return this
    }

    fun assertCookie(name: String): TestResponse {
        assertTrue(response.cookies.any { it.name == name }, "Missing cookie [$name]")
        return this
    }

    fun json(): Any? = io.kotlimo.support.Json.decode(response.content)

    fun assertJsonPath(key: String, expected: Any?): TestResponse {
        val decoded = json()
        @Suppress("UNCHECKED_CAST")
        val map = decoded as? Map<String, Any?> ?: emptyMap()
        assertEquals(expected, io.kotlimo.support.Arr.get(map, key))
        return this
    }
}

open class HttpTestCase {
    lateinit var app: Application
    val cookies = mutableMapOf<String, String>()

    fun bootApplication(basePath: String, routes: Router.() -> Unit = {}): Application {
        app = Application.create(basePath).withDefaultProviders()
        app.boot()
        Facade.setFacadeApplication(app)
        app.make(Router::class).apply(routes)
        return app
    }

    fun get(uri: String, headers: Map<String, String> = emptyMap()): TestResponse =
        call(Request(method = "GET", uri = uri, headers = headers))

    fun post(uri: String, body: Map<String, Any?> = emptyMap(), headers: Map<String, String> = emptyMap()): TestResponse {
        val tokenized = attachCsrf(body, headers)
        return call(Request(method = "POST", uri = uri, body = tokenized.first, headers = tokenized.second))
    }

    fun put(uri: String, body: Map<String, Any?> = emptyMap()): TestResponse {
        val tokenized = attachCsrf(body, emptyMap())
        return call(Request(method = "PUT", uri = uri, body = tokenized.first, headers = tokenized.second))
    }

    fun delete(uri: String): TestResponse {
        val tokenized = attachCsrf(emptyMap(), emptyMap())
        return call(Request(method = "DELETE", uri = uri, body = tokenized.first, headers = tokenized.second))
    }

    fun json(method: String, uri: String, body: Map<String, Any?> = emptyMap()): TestResponse {
        val request = Request.json(method, uri, body)
        val headers = request.headers.toMutableMap()
        csrfToken()?.let { headers.putIfAbsent("X-CSRF-TOKEN", it) }
        return call(
            Request(
                method = request.method,
                uri = request.uri,
                headers = headers,
                body = request.body,
                rawBody = request.rawBody
            )
        )
    }

    fun call(request: Request): TestResponse {
        val kernel = app.make(HttpKernel::class)
        val incoming = Request(
            method = request.method,
            uri = request.uri,
            headers = request.headers,
            query = request.query,
            body = request.body,
            cookies = cookies + request.cookies,
            files = request.files,
            rawBody = request.rawBody,
            serverPort = request.serverPort,
            remoteAddress = request.remoteAddress
        )
        val response = TestResponse(kernel.handle(incoming))
        response.cookies.forEach { cookie ->
            cookies[cookie.name] = cookie.value
        }
        return response
    }

    fun csrfToken(): String? {
        if (!::app.isInitialized || !app.bound(SessionManager::class)) return null
        val manager = app.make(SessionManager::class)
        val raw = cookies[manager.cookieName] ?: return null
        return manager.readToken(raw)
    }

    private fun attachCsrf(
        body: Map<String, Any?>,
        headers: Map<String, String>
    ): Pair<Map<String, Any?>, Map<String, String>> {
        val token = csrfToken() ?: return body to headers
        if ("_token" in body || headers.keys.any { it.equals("X-CSRF-TOKEN", true) }) {
            return body to headers
        }
        return body + ("_token" to token) to headers
    }
}
