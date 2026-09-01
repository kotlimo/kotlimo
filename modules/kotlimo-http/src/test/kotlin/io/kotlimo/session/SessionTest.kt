package io.kotlimo.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SessionTest {
    @Test
    fun `array store round trips attributes`() {
        val manager = SessionManager(ArraySessionStore(), key = "test-key")
        val session = manager.start(null)
        session.put("name", "Ada")
        val token = session.token()
        manager.save(session)
        val restored = manager.start(manager.cookieValue(session))
        assertEquals("Ada", restored.string("name"))
        assertEquals(token, restored.token())
    }

    @Test
    fun `signed cookies reject tampering`() {
        val manager = SessionManager(ArraySessionStore(), key = "secret")
        val session = manager.start(null)
        manager.save(session)
        val tampered = manager.cookieValue(session).dropLast(2) + "ab"
        val fresh = manager.start(tampered)
        assertNotEquals(session.id, fresh.id)
        assertNull(fresh.get("name"))
    }

    @Test
    fun `file store persists json`(@TempDir dir: Path) {
        val store = FileSessionStore(dir)
        val manager = SessionManager(store, key = "")
        val session = manager.start(null)
        session.put("theme", "dark")
        manager.save(session)
        val restored = manager.start(session.id)
        assertEquals("dark", restored.string("theme"))
        assertTrue(session.id.matches(Regex("[A-Za-z0-9]+")))
    }

    @Test
    fun `regenerate issues a new id`() {
        val store = ArraySessionStore()
        val manager = SessionManager(store, key = "k")
        val session = manager.start(null)
        session.put("user", "1")
        val old = session.id
        session.regenerate()
        manager.save(session)
        assertNotEquals(old, session.id)
        assertEquals("1", manager.start(manager.cookieValue(session)).string("user"))
    }
}
