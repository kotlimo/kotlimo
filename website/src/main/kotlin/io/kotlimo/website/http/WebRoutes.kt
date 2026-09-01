package io.kotlimo.website.http

import io.kotlimo.foundation.Application
import io.kotlimo.routing.Router
import io.kotlimo.website.controllers.DocsController
import io.kotlimo.website.controllers.HomeController

fun registerWebRoutes(app: Application) {
    val router = app.make(Router::class)
    router.get("/", HomeController::class, "index").named("home")
    router.get("/docs", DocsController::class, "index").named("docs")
    router.get("/docs/{page}", DocsController::class, "show").named("docs.show")
    router.get("/404.html", HomeController::class, "notFound").named("not-found")
}
