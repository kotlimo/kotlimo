package io.kotlimo.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigRepositoryTest {
    @Test
    fun `nested keys and typed getters`() {
        val config = ConfigRepository.fromMaps(
            mapOf("app" to mapOf("name" to "Kotlimo", "debug" to true, "timeout" to 30))
        )
        assertEquals("Kotlimo", config.string("app.name"))
        assertTrue(config.boolean("app.debug"))
        assertEquals(30, config.int("app.timeout"))
        assertTrue(config.has("app.name"))
        assertFalse(config.has("app.missing"))
    }

    @Test
    fun `loads json files from a directory`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("app.json"), """{"name":"FromFile","env":"testing"}""")
        val config = ConfigRepository.loadDirectory(dir)
        assertEquals("FromFile", config.string("app.name"))
        assertEquals("testing", config.string("app.env"))
    }
}

class EnvironmentTest {
    @Test
    fun `parses env files`(@TempDir dir: Path) {
        val file = dir.resolve(".env")
        Files.writeString(
            file,
            """
            APP_NAME=Kotlimo
            APP_DEBUG=true
            # comment
            export DB_HOST="127.0.0.1"
            EMPTY=
            """.trimIndent()
        )
        val env = Environment.load(file)
        assertEquals("Kotlimo", env.get("APP_NAME"))
        assertTrue(env.boolean("APP_DEBUG"))
        assertEquals("127.0.0.1", env.get("DB_HOST"))
        assertEquals("", env.get("EMPTY"))
    }

    @Test
    fun `falls back to default`() {
        val env = Environment()
        assertEquals("local", env.get("MISSING", "local"))
        assertFalse(env.boolean("MISSING"))
    }
}
