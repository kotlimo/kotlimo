package io.kotlimo.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FilesystemTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `put get exists and delete`() {
        val disk = LocalFilesystem(dir)
        assertTrue(disk.put("notes/hello.txt", "hi"))
        assertTrue(disk.exists("notes/hello.txt"))
        assertEquals("hi", disk.get("notes/hello.txt"))
        assertTrue(disk.delete("notes/hello.txt"))
        assertFalse(disk.exists("notes/hello.txt"))
    }

    @Test
    fun `rejects path traversal`() {
        val disk = LocalFilesystem(dir)
        assertThrows(SecurityException::class.java) {
            disk.put("../escape.txt", "nope")
        }
    }

    @Test
    fun `manager selects disks`() {
        val manager = FilesystemManager()
        manager.add("local", LocalFilesystem(dir.resolve("local")))
        manager.add("private", LocalFilesystem(dir.resolve("private")))
        manager.disk("private").put("a.txt", "secret")
        assertEquals("secret", manager.disk("private").get("a.txt"))
        assertFalse(manager.disk().exists("a.txt"))
    }
}
