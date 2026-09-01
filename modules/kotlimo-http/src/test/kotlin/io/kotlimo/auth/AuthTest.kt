package io.kotlimo.auth

import io.kotlimo.hash.Hash
import io.kotlimo.http.Request
import io.kotlimo.session.Session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArrayUserProvider : UserProvider {
    val users = mutableMapOf<String, GenericUser>()

    override fun retrieveById(id: Any): Authenticatable? = users[id.toString()]

    override fun retrieveByCredentials(credentials: Map<String, Any?>): Authenticatable? {
        val email = credentials["email"]?.toString() ?: return null
        return users.values.firstOrNull { it["email"] == email }
    }
}

class AuthTest {
    @Test
    fun `attempt logs in and logout clears the session`() {
        val hashed = Hash.make("secret")
        val provider = ArrayUserProvider()
        provider.users["1"] = GenericUser(mapOf("id" to 1L, "email" to "ada@example.com", "password" to hashed))
        val session = Session("abc")
        val request = Request.post("/login")
        request.sessionStore = session
        val auth = AuthManager(provider)

        assertFalse(auth.attempt(request, mapOf("email" to "ada@example.com", "password" to "nope")))
        assertTrue(auth.attempt(request, mapOf("email" to "ada@example.com", "password" to "secret")))
        assertEquals(1L, (auth.user(request) as GenericUser)["id"])
        assertTrue(session.regenerateId)

        auth.logout(request)
        assertNull(auth.user(request))
        assertTrue(session.invalidated)
    }
}
