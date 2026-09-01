package io.kotlimo.website.controllers

import io.kotlimo.foundation.facades.view
import io.kotlimo.http.Request
import io.kotlimo.http.Response
import io.kotlimo.http.abort

class DocsController {
    private val pages = listOf(
        DocPage("installation", "Installation"),
        DocPage("configuration", "Configuration"),
        DocPage("routing", "Routing"),
        DocPage("http", "Requests & Responses"),
        DocPage("session", "Sessions"),
        DocPage("authentication", "Authentication"),
        DocPage("container", "Service Container"),
        DocPage("views", "Views"),
        DocPage("validation", "Validation"),
        DocPage("database", "Database"),
        DocPage("mail", "Mail"),
        DocPage("queue", "Queues"),
        DocPage("filesystem", "Filesystem"),
        DocPage("artisan", "Craft CLI")
    )

    fun index(request: Request): Response = show(request, "installation")

    fun show(request: Request, page: String): Response {
        val current = pages.firstOrNull { it.slug == page } ?: abort(404, "Documentation page not found")
        val viewName = "docs.$page"
        return view(
            viewName,
            mapOf(
                "title" to "${current.title} - Kotlimo",
                "page" to "docs",
                "current" to current.slug,
                "pages" to pages.map { mapOf("slug" to it.slug, "title" to it.title) }
            )
        )
    }

    data class DocPage(val slug: String, val title: String)
}
