package io.kotlimo.foundation.providers

import io.kotlimo.foundation.Application
import io.kotlimo.foundation.ServiceProvider
import io.kotlimo.http.AddSecurityHeaders
import io.kotlimo.http.HttpKernel
import io.kotlimo.http.ServePublicAssets
import io.kotlimo.http.TrimTrailingSlash
import io.kotlimo.routing.Router
import io.kotlimo.view.ViewEngine
import java.nio.file.Files

class RoutingServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(Router::class) { Router() }
        app.singleton(HttpKernel::class) { container ->
            val publicPath = app.publicPath()
            val middleware = buildList {
                add(TrimTrailingSlash())
                add(AddSecurityHeaders())
                if (Files.isDirectory(publicPath)) add(ServePublicAssets(publicPath))
            }
            HttpKernel(container, container.make(Router::class), middleware)
        }
    }
}

class ViewServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(ViewEngine::class) {
            ViewEngine().apply {
                if (Files.isDirectory(app.viewPath())) addLocation(app.viewPath())
            }
        }
    }
}
