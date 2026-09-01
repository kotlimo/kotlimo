package io.kotlimo.hash

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HashTest {
    @Test
    fun `bcrypt hashes and verifies`() {
        val hashed = Hash.make("secret", Hash.BCRYPT)
        assertTrue(hashed.startsWith("$2"))
        assertTrue(Hash.check("secret", hashed))
        assertFalse(Hash.check("other", hashed))
        assertNotEquals(hashed, Hash.make("secret", Hash.BCRYPT))
    }

    @Test
    fun `argon2 hashes and verifies`() {
        val hashed = Hash.make("secret", Hash.ARGON2)
        assertTrue(hashed.startsWith("\$argon2id\$"))
        assertTrue(Hash.check("secret", hashed))
        assertFalse(Hash.check("other", hashed))
    }
}
