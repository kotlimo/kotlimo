package io.kotlimo.console

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class NewApplicationCommandTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `scaffolds a usable application tree`() {
        val code = NewApplicationCommand().handle(listOf("blog", "--path=${dir.toAbsolutePath()}"))
        assertEquals(0, code)
        val root = dir.resolve("blog")
        assertTrue(Files.exists(root.resolve("src/main/kotlin/Application.kt")))
        assertTrue(Files.exists(root.resolve("config/app.json")))
        assertTrue(Files.exists(root.resolve("database/migrations/0001_create_users_table.json")))
        assertTrue(Files.exists(root.resolve("resources/views/home.kote")))
        assertTrue(Files.exists(root.resolve(".env.example")))
        assertTrue(Files.exists(root.resolve("build.gradle.kts")))
        val settings = Files.readString(root.resolve("settings.gradle.kts"))
        assertTrue(settings.contains("maven.pkg.github.com/kotlimo/kotlimo"))
        assertTrue(Files.readString(root.resolve("build.gradle.kts")).contains("io.kotlimo:kotlimo-foundation:0.1.0"))
        val readme = Files.readString(root.resolve("README.md"))
        assertTrue(readme.contains("./gradlew run"))
    }

    @Test
    fun `help lists the new command`() {
        val code = KotlimoConsole().handle(arrayOf("list"))
        assertEquals(0, code)
    }
}
