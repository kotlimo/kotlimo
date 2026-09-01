package io.kotlimo.foundation.providers

import io.kotlimo.auth.AuthManager
import io.kotlimo.foundation.Application
import io.kotlimo.foundation.ServiceProvider
import io.kotlimo.http.AddSecurityHeaders
import io.kotlimo.http.AuthenticateSession
import io.kotlimo.http.HttpKernel
import io.kotlimo.http.ServePublicAssets
import io.kotlimo.http.StartSession
import io.kotlimo.http.TrimTrailingSlash
import io.kotlimo.http.VerifyCsrfToken
import io.kotlimo.routing.Router
import io.kotlimo.session.SessionManager
import io.kotlimo.view.ViewEngine
import java.nio.file.Files

class RoutingServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(Router::class) { Router() }
        app.singleton(HttpKernel::class) { container ->
            val publicPath = app.publicPath()
            val middleware = buildList {
                add(TrimTrailingSlash())
                if (container.bound(SessionManager::class)) {
                    add(
                        StartSession(container.make(SessionManager::class)) { session ->
                            if (container.bound(ViewEngine::class)) {
                                container.make(ViewEngine::class).share("csrf", session.token())
                            }
                        }
                    )
                    if (container.bound(AuthManager::class)) {
                        add(AuthenticateSession { request -> container.make(AuthManager::class).user(request) })
                    }
                    add(VerifyCsrfToken())
                }
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
