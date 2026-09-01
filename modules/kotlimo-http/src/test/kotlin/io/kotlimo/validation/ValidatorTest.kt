package io.kotlimo.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidatorTest {
    @Test
    fun `required email and min rules`() {
        val validator = Validator.make(
            mapOf("email" to "", "name" to "A"),
            mapOf("email" to "required|email", "name" to "required|string|min:3")
        )
        assertTrue(validator.fails())
        val errors = validator.errors()
        assertTrue(errors.containsKey("email"))
        assertTrue(errors.containsKey("name"))
    }

    @Test
    fun `validated returns accepted data`() {
        val data = mapOf<String, Any?>(
            "email" to "ada@example.com",
            "name" to "Ada Lovelace",
            "extra" to "ignored"
        )
        val validated = validate(data, mapOf("email" to "required|email", "name" to "required|min:3"))
        assertEquals("ada@example.com", validated["email"])
        assertFalse(validated.containsKey("extra"))
    }

    @Test
    fun `confirmed passwords must match`() {
        val validator = Validator.make(
            mapOf("password" to "secret", "password_confirmation" to "nope"),
            mapOf("password" to "required|confirmed")
        )
        assertTrue(validator.fails())
    }

    @Test
    fun `in rule restricts values`() {
        val validator = Validator.make(
            mapOf("role" to "admin"),
            mapOf("role" to "required|in:user,editor")
        )
        assertTrue(validator.fails())
    }

    @Test
    fun `throws when validation fails`() {
        assertThrows(ValidationException::class.java) {
            validate(emptyMap(), mapOf("name" to "required"))
        }
    }
}
