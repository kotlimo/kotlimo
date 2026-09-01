package io.kotlimo.routing

import io.kotlimo.container.Container
import io.kotlimo.http.HttpException
import io.kotlimo.http.HttpKernel
import io.kotlimo.http.Middleware
import io.kotlimo.http.Request
import io.kotlimo.http.RequestFactory
import io.kotlimo.http.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouterTest {
    @Test
    fun `matches static and parameterized routes`() {
        val router = Router()
        router.get("/users/{id}") { request -> Response.text(request.route("id") ?: "") }
        val route = router.match(Request.get("/users/42"))
        assertEquals("/users/{id}", route?.uri)
        assertEquals(mapOf("id" to "42"), route?.extractParameters("/users/42"))
    }

    @Test
    fun `groups apply prefix middleware and names`() {
        val router = Router()
        val marker = Middleware { request, next ->
            next(request).header("X-Group", "api")
        }
        router.group(prefix = "api", middleware = listOf(marker), name = "api.") {
            get("/users") { Response.json(listOf("ada")) }.named("users")
        }
        val route = router.findByName("api.users")
        assertEquals("/api/users", route?.uri)
        assertEquals(1, route?.middleware?.size)
        assertEquals("/api/users", router.url("api.users"))
    }

    @Test
    fun `named url generation substitutes parameters`() {
        val router = Router()
        router.get("/posts/{post}/comments/{comment}") { Response.text("ok") }.named("comments.show")
        assertEquals("/posts/1/comments/9", router.url("comments.show", mapOf("post" to 1, "comment" to 9)))
    }
}

class HttpKernelTest {
    class GreetingController {
        fun show(request: Request, name: String): Response = Response.text("Hello $name")
    }

    @Test
    fun `dispatches closures through global middleware`() {
        val router = Router()
        router.get("/") { Response.html("home") }
        val kernel = HttpKernel(
            Container(),
            router,
            listOf(Middleware { request, next -> next(request).header("X-Powered-By", "Kotlimo") })
        )
        val response = kernel.handle(Request.get("/"))
        assertEquals(200, response.status)
        assertEquals("home", response.content)
        assertEquals("Kotlimo", response.headers["X-Powered-By"])
    }

    @Test
    fun `returns 404 and 405`() {
        val router = Router()
        router.get("/only-get") { Response.text("ok") }
        val kernel = HttpKernel(Container(), router)
        assertEquals(404, kernel.handle(Request.get("/missing")).status)
        assertEquals(405, kernel.handle(Request.post("/only-get")).status)
    }

    @Test
    fun `invokes controller actions with route parameters`() {
        val router = Router()
        router.get("/hello/{name}", GreetingController::class, "show")
        val kernel = HttpKernel(Container(), router)
        val response = kernel.handle(Request.get("/hello/Ada"))
        assertEquals("Hello Ada", response.content)
    }

    @Test
    fun `json exceptions include a message`() {
        val router = Router()
        router.get("/boom") { throw HttpException(422, "Invalid") }
        val kernel = HttpKernel(Container(), router)
        val response = kernel.handle(Request.json("GET", "/boom"))
        assertEquals(422, response.status)
        assertTrue(response.content.contains("Invalid"))
    }
}

class RequestFactoryTest {
    @Test
    fun `parses query strings and cookies`() {
        val query = RequestFactory.parseQuery("name=Ada+Lovelace&id=1")
        assertEquals("Ada Lovelace", query["name"])
        val cookies = RequestFactory.parseCookies("session=abc; theme=dark")
        assertEquals("abc", cookies["session"])
        assertEquals("dark", cookies["theme"])
    }

    @Test
    fun `parses json bodies`() {
        val body = RequestFactory.parseBody("""{"email":"ada@example.com"}""", "application/json")
        assertEquals("ada@example.com", body["email"])
    }
}

class ResponseTest {
    @Test
    fun `json and redirect helpers`() {
        val json = Response.json(mapOf("ok" to true))
        assertTrue(json.content.contains("true"))
        assertEquals("application/json; charset=UTF-8", json.headers["Content-Type"])
        val redirect = Response.redirect("/login")
        assertEquals(302, redirect.status)
        assertEquals("/login", redirect.headers["Location"])
    }
}
