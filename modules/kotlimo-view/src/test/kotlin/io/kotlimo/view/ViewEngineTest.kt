package io.kotlimo.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ViewEngineTest {
    @TempDir
    lateinit var dir: Path

    private fun engine(vararg templates: Pair<String, String>): ViewEngine {
        templates.forEach { (name, source) ->
            val file = dir.resolve(name.replace('.', '/') + ".kote")
            Files.createDirectories(file.parent)
            Files.writeString(file, source)
        }
        return ViewEngine().apply { addLocation(dir) }
    }

    @Test
    fun `echoes escaped and raw values`() {
        val view = engine("welcome" to "<h1>{{ name }}</h1>{!! html !!}")
        val html = view.render("welcome", mapOf("name" to "<Ada>", "html" to "<b>ok</b>"))
        assertEquals("<h1>&lt;Ada&gt;</h1><b>ok</b>", html)
    }

    @Test
    fun `renders conditionals and loops`() {
        val view = engine(
            "list" to """
                @if(show)
                <ul>
                @foreach(items as item)
                <li>{{ item }}</li>
                @endforeach
                </ul>
                @else
                empty
                @endif
            """.trimIndent()
        )
        val html = view.render("list", mapOf("show" to true, "items" to listOf("one", "two")))
        assertTrue(html.contains("<li>one</li>"))
        assertTrue(html.contains("<li>two</li>"))
    }

    @Test
    fun `layouts yield sections`() {
        val view = engine(
            "layouts.app" to "<html>@yield('content')</html>",
            "page" to """
                @extends('layouts.app')
                @section('content')
                Hello {{ name }}
                @endsection
            """.trimIndent()
        )
        val html = view.render("page", mapOf("name" to "Kotlimo")).replace(Regex("\\s+"), " ").trim()
        assertTrue(html.contains("Hello Kotlimo"))
        assertTrue(html.startsWith("<html>"))
        assertTrue(html.endsWith("</html>"))
    }

    @Test
    fun `includes nested templates`() {
        val view = engine(
            "partials.alert" to "<p>{{ message }}</p>",
            "page" to "@include('partials.alert')"
        )
        assertEquals("<p>Saved</p>", view.render("page", mapOf("message" to "Saved")))
    }

    @Test
    fun `dotted map access`() {
        val view = engine("show" to "{{ user.name }}")
        assertEquals("Ada", view.render("show", mapOf("user" to mapOf("name" to "Ada"))))
    }

    @Test
    fun `shared data is available to every view`() {
        val view = engine("token" to "{{ csrf }}")
        view.share("csrf", "abc123")
        assertEquals("abc123", view.render("token"))
    }

    @Test
    fun `verbatim preserves blade-like examples`() {
        val view = engine("docs" to "@verbatim{{ name }}@endverbatim")
        assertEquals("{{ name }}", view.render("docs", mapOf("name" to "Ada")))
    }
}
