package io.kotlimo.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrTest {
    @Test
    fun `snake converts studly case`() {
        assertEquals("user_profile", Str.snake("UserProfile"))
        assertEquals("http-request", Str.kebab("HttpRequest"))
    }

    @Test
    fun `studly and camel convert delimited strings`() {
        assertEquals("UserProfile", Str.studly("user_profile"))
        assertEquals("userProfile", Str.camel("user-profile"))
    }

    @Test
    fun `slug strips punctuation`() {
        assertEquals("the-kotlin-framework", Str.slug("The Kotlin Framework!"))
    }

    @Test
    fun `limit truncates with ellipsis`() {
        assertEquals("Hello...", Str.limit("Hello world", 5))
        assertEquals("short", Str.limit("short", 10))
    }

    @Test
    fun `html is escaped`() {
        assertEquals("&lt;script&gt;", Str.escapeHtml("<script>"))
    }

    @Test
    fun `after and before extract substrings`() {
        assertEquals("kotlimo", Str.after("io.kotlimo", "."))
        assertEquals("io", Str.before("io.kotlimo", "."))
    }

    @Test
    fun `contains is case aware`() {
        assertTrue(Str.contains("Kotlimo", "limo"))
        assertFalse(Str.contains("Kotlimo", "LIMO"))
        assertTrue(Str.contains("Kotlimo", "LIMO", ignoreCase = true))
    }
}

class InflectorTest {
    @Test
    fun `pluralizes regular and irregular nouns`() {
        assertEquals("users", Inflector.pluralize("user"))
        assertEquals("categories", Inflector.pluralize("category"))
        assertEquals("people", Inflector.pluralize("person"))
        assertEquals("boxes", Inflector.pluralize("box"))
    }

    @Test
    fun `singularizes nouns`() {
        assertEquals("user", Inflector.singularize("users"))
        assertEquals("category", Inflector.singularize("categories"))
        assertEquals("person", Inflector.singularize("people"))
    }

    @Test
    fun `tableize class names`() {
        assertEquals("blog_posts", Inflector.tableize("BlogPost"))
        assertEquals("user_id", Inflector.foreignKey("User"))
    }
}

class ArrTest {
    @Test
    fun `dot notation get and set`() {
        val data = mutableMapOf<String, Any?>("app" to mutableMapOf("name" to "Kotlimo"))
        assertEquals("Kotlimo", Arr.get(data, "app.name"))
        Arr.set(data, "app.debug", true)
        assertEquals(true, Arr.get(data, "app.debug"))
        assertTrue(Arr.has(data, "app.name"))
        assertFalse(Arr.has(data, "app.missing"))
    }

    @Test
    fun `only and except filter keys`() {
        val data = mapOf<String, Any?>("name" to "Ada", "email" to "ada@example.com", "password" to "secret")
        assertEquals(mapOf("name" to "Ada"), Arr.only(data, listOf("name")))
        assertEquals(mapOf("name" to "Ada", "email" to "ada@example.com"), Arr.except(data, listOf("password")))
    }

    @Test
    fun `flatten nested lists`() {
        assertEquals(listOf(1, 2, 3, 4), Arr.flatten(listOf(1, listOf(2, listOf(3, 4)))))
    }
}

class CollectionTest {
    @Test
    fun `map filter and reduce helpers`() {
        val result = collect(1, 2, 3, 4, 4)
            .filter { it % 2 == 0 }
            .unique()
            .map { it * 10 }
        assertEquals(listOf(20, 40), result.toList())
        assertEquals(2, result.count())
        assertEquals(20, result.first())
    }

    @Test
    fun `chunk and groupBy`() {
        val chunks = collect(1, 2, 3, 4).chunk(2)
        assertEquals(2, chunks.count())
        val grouped = collect("ada", "alan", "grace").groupBy { it.first() }
        assertEquals(1, grouped['g']?.count())
    }
}

class HelperTest {
    @Test
    fun `tap returns original value`() {
        val value = tap("kotlimo") { assertEquals("kotlimo", it) }
        assertEquals("kotlimo", value)
    }

    @Test
    fun `blank and filled`() {
        assertTrue(blank(""))
        assertTrue(blank(emptyList<String>()))
        assertTrue(filled("ok"))
    }
}

class CarbonTest {
    @Test
    fun `adds and formats dates`() {
        val now = Carbon.now()
        val tomorrow = now.addDays(1)
        assertTrue(tomorrow.isFuture() || tomorrow.toDateString() != now.toDateString())
        assertEquals(10, now.toDateString().length)
    }
}
