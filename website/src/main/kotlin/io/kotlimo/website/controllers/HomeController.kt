package io.kotlimo.website.controllers

import io.kotlimo.foundation.facades.view
import io.kotlimo.http.Request
import io.kotlimo.http.Response

class HomeController {
    fun index(request: Request): Response = view(
        "home",
        "title" to "Kotlimo - The Kotlin Framework for Web Artisans",
        "page" to "home"
    )

    fun notFound(request: Request): Response = view(
        "errors.404",
        "title" to "Page not found - Kotlimo",
        "page" to "404"
    ).also { it.status = 404 }
}
