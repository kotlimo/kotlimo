package io.kotlimo.foundation

import io.kotlimo.console.ConsoleKernel
import io.kotlimo.foundation.facades.Config
import io.kotlimo.foundation.facades.Route
import io.kotlimo.http.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ApplicationTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `boots default providers and facades`() {
        Files.writeString(dir.resolve(".env"), "APP_NAME=FromEnv\nAPP_ENV=testing\n")
        val app = Application.create(dir.toString()).withDefaultProviders().boot()
        assertEquals("FromEnv", Config.string("app.name"))
        assertEquals("testing", app.environment())
        Route.get("/health") { Response.json(mapOf("ok" to true)) }
        assertEquals("/health", Route.list().single().uri)
    }

    @Test
    fun `console lists commands`() {
        val app = Application.create(dir.toString()).withDefaultProviders()
        val kernel = app.withConsole()
        app.boot()
        val code = kernel.handle(arrayOf("list"))
        assertEquals(0, code)
        assertTrue(kernel.commands().containsKey("serve"))
        assertTrue(kernel.commands().containsKey("route:list"))
        assertTrue(kernel.commands().containsKey("make:controller"))
    }

    @Test
    fun `make controller writes a file`() {
        val app = Application.create(dir.toString()).withDefaultProviders()
        val kernel = app.withConsole()
        app.boot()
        val code = kernel.handle(arrayOf("make:controller", "HealthController"))
        assertEquals(0, code)
        assertTrue(Files.exists(dir.resolve("src/main/kotlin/controllers/HealthController.kt")))
    }

    @Test
    fun `unknown commands fail`() {
        val app = Application.create(dir.toString()).withDefaultProviders().boot()
        val kernel = app.withConsole()
        assertEquals(1, kernel.handle(arrayOf("does:not-exist")))
    }
}

class ConsoleKernelStandaloneTest {
    @Test
    fun `help is the default command`() {
        val app = Application.create(".")
        val kernel = ConsoleKernel(app)
        assertEquals(0, kernel.handle(arrayOf()))
    }
}
