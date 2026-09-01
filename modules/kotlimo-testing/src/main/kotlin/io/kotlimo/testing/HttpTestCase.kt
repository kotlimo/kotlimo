package io.kotlimo.testing

import io.kotlimo.foundation.Application
import io.kotlimo.foundation.Facade
import io.kotlimo.foundation.withDefaultProviders
import io.kotlimo.http.HttpKernel
import io.kotlimo.http.Request
import io.kotlimo.http.Response
import io.kotlimo.routing.Router
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertTrue as junitTrue

class TestResponse(val response: Response) {
    val status: Int get() = response.status
    val content: String get() = response.content
    val headers: Map<String, String> get() = response.headers

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

    fun bootApplication(basePath: String, routes: Router.() -> Unit = {}): Application {
        app = Application.create(basePath).withDefaultProviders()
        app.boot()
        Facade.setFacadeApplication(app)
        app.make(Router::class).apply(routes)
        return app
    }

    fun get(uri: String, headers: Map<String, String> = emptyMap()): TestResponse =
        call(Request(method = "GET", uri = uri, headers = headers))

    fun post(uri: String, body: Map<String, Any?> = emptyMap(), headers: Map<String, String> = emptyMap()): TestResponse =
        call(Request(method = "POST", uri = uri, body = body, headers = headers))

    fun put(uri: String, body: Map<String, Any?> = emptyMap()): TestResponse =
        call(Request(method = "PUT", uri = uri, body = body))

    fun delete(uri: String): TestResponse = call(Request(method = "DELETE", uri = uri))

    fun json(method: String, uri: String, body: Map<String, Any?> = emptyMap()): TestResponse =
        call(Request.json(method, uri, body))

    fun call(request: Request): TestResponse {
        val kernel = app.make(HttpKernel::class)
        return TestResponse(kernel.handle(request))
    }
}
