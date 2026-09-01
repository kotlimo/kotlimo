package io.kotlimo.website

import io.kotlimo.console.ConsoleKernel
import io.kotlimo.testing.HttpTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WebsiteTest : HttpTestCase() {
    @BeforeEach
    fun setUp() {
        app = createWebsite()
    }

    @Test
    fun `home page matches the marketing hero`() {
        get("/").assertOk()
            .assertSee("The Kotlin Framework")
            .assertSee("./gradlew :website:run")
            .assertSee("Web Artisans")
            .assertDontSee("kotlimo new my-app")
            .assertDontSee("Kotlimo Cloud")
            .assertDontSee("NORTHSTAR")
    }

    @Test
    fun `documentation pages render`() {
        get("/docs").assertOk().assertSee("Installation")
        get("/docs/routing").assertOk().assertSee("Route groups")
        get("/docs/database").assertOk().assertSee("Query builder")
        get("/docs/artisan").assertOk().assertSee("./gradlew :website:run --args='route:list'")
        get("/docs/missing").assertStatus(404)
    }

    @Test
    fun `removed marketing pages are gone`() {
        get("/ecosystem").assertStatus(404)
        get("/starter-kits").assertStatus(404)
    }

    @Test
    fun `static assets are served`() {
        get("/css/app.css").assertOk().assertSee("--lime: #D4FF00")
        get("/images/logo.svg").assertOk().assertSee("#D4FF00")
        get("/images/logo.png").assertOk()
    }

    @Test
    fun `site export writes GitHub Pages files`(@TempDir dir: Path) {
        val kernel = app.make(ConsoleKernel::class)
        assertEquals(0, kernel.handle(arrayOf("site:export", "--path=$dir")))
        assertTrue(Files.exists(dir.resolve("index.html")))
        assertTrue(Files.exists(dir.resolve(".nojekyll")))
        assertTrue(Files.exists(dir.resolve("CNAME")))
        assertTrue(Files.exists(dir.resolve("404.html")))
        assertTrue(Files.exists(dir.resolve("css/app.css")))
        assertTrue(Files.exists(dir.resolve("images/logo.svg")))
        assertTrue(Files.readString(dir.resolve("CNAME")).contains("kotlimo.github.io"))
        val home = Files.readString(dir.resolve("index.html"))
        assertTrue(home.contains("The Kotlin Framework"))
        assertTrue(!home.contains("Kotlimo Cloud"))
        assertTrue(Files.readString(dir.resolve("css/app.css")).contains("--lime: #D4FF00"))
    }
}
