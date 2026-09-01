package io.kotlimo.foundation

import io.kotlimo.container.Container
import java.nio.file.Path
import java.nio.file.Paths

open class ServiceProvider(val app: Application) {
    open fun register() {}
    open fun boot() {}
}

class Application(basePath: String) : Container() {
    val basePath: Path = Paths.get(basePath).toAbsolutePath().normalize()
    private val providers = mutableListOf<ServiceProvider>()
    var isBooted: Boolean = false
        private set

    init {
        instance(Application::class, this)
        instance(Container::class, this)
        alias(key(Application::class), "app")
    }

    fun path(vararg segments: String): Path = segments.fold(basePath) { acc, segment -> acc.resolve(segment) }

    fun basePath(vararg segments: String): Path = path(*segments)
    fun configPath(): Path = path("config")
    fun databasePath(): Path = path("database")
    fun resourcePath(): Path = path("resources")
    fun storagePath(): Path = path("storage")
    fun publicPath(): Path = path("public")
    fun viewPath(): Path = path("resources", "views")

    fun register(provider: ServiceProvider): Application {
        providers += provider
        if (isBooted) {
            provider.register()
            provider.boot()
        }
        return this
    }

    fun register(factory: (Application) -> ServiceProvider): Application = register(factory(this))

    fun boot(): Application {
        if (isBooted) return this
        providers.forEach { it.register() }
        providers.forEach { it.boot() }
        isBooted = true
        Facade.app = this
        return this
    }

    fun environment(): String = make(io.kotlimo.config.ConfigRepository::class).string("app.env", "local")

    fun isProduction(): Boolean = environment() == "production"
    fun isLocal(): Boolean = environment() == "local"

    companion object {
        fun create(basePath: String): Application = Application(basePath)
    }
}

abstract class Facade {
    companion object {
        lateinit var app: Application

        fun setFacadeApplication(application: Application) {
            app = application
        }
    }
}
