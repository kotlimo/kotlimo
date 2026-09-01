package io.kotlimo.http

import io.kotlimo.container.Container
import io.kotlimo.routing.Router
import io.kotlimo.session.ArraySessionStore
import io.kotlimo.session.SessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionCsrfKernelTest {
    private fun kernel(): Pair<HttpKernel, SessionManager> {
        val manager = SessionManager(ArraySessionStore(), cookieName = "kotlimo_session", key = "test-key")
        val router = Router()
        router.get("/form") { request ->
            Response.html("token=${request.csrf()}")
        }
        router.post("/submit") { request ->
            Response.json(mapOf("ok" to true, "name" to request.string("name")))
        }
        val kernel = HttpKernel(
            Container(),
            router,
            listOf(StartSession(manager), VerifyCsrfToken())
        )
        return kernel to manager
    }

    @Test
    fun `session cookie is issued and csrf is required on post`() {
        val (kernel, manager) = kernel()
        val first = kernel.handle(Request.get("/form"))
        assertEquals(200, first.status)
        val cookie = first.cookies.single { it.name == "kotlimo_session" }.value
        val token = manager.readToken(cookie)
        assertTrue(!token.isNullOrBlank())
        assertTrue(first.content.contains(token!!))

        val rejected = kernel.handle(
            Request(method = "POST", uri = "/submit", body = mapOf("name" to "Ada"), cookies = mapOf("kotlimo_session" to cookie))
        )
        assertEquals(419, rejected.status)

        val accepted = kernel.handle(
            Request(
                method = "POST",
                uri = "/submit",
                body = mapOf("name" to "Ada", "_token" to token),
                cookies = mapOf("kotlimo_session" to cookie)
            )
        )
        assertEquals(200, accepted.status)
        assertTrue(accepted.content.contains("Ada"))
    }

    @Test
    fun `csrf header is accepted`() {
        val (kernel, manager) = kernel()
        val first = kernel.handle(Request.get("/form"))
        val cookie = first.cookies.single { it.name == "kotlimo_session" }.value
        val token = manager.readToken(cookie)!!
        val accepted = kernel.handle(
            Request(
                method = "POST",
                uri = "/submit",
                body = mapOf("name" to "Ada"),
                headers = mapOf("X-CSRF-TOKEN" to token),
                cookies = mapOf("kotlimo_session" to cookie)
            )
        )
        assertEquals(200, accepted.status)
    }
}
