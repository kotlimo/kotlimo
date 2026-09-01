package io.kotlimo.foundation.providers

import io.kotlimo.cache.ArrayStore
import io.kotlimo.cache.CacheRepository
import io.kotlimo.config.ConfigRepository
import io.kotlimo.config.Environment
import io.kotlimo.events.Dispatcher
import io.kotlimo.foundation.Application
import io.kotlimo.foundation.ServiceProvider
import io.kotlimo.logging.Logger
import java.nio.file.Files

class CoreServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        val env = Environment.load(app.basePath.resolve(".env"))
        app.instance(Environment::class, env)

        val config = if (Files.isDirectory(app.configPath())) {
            ConfigRepository.loadDirectory(app.configPath())
        } else {
            ConfigRepository()
        }
        if (!config.has("app.name")) {
            config.load(
                "app",
                mapOf(
                    "name" to (env.get("APP_NAME") ?: "Kotlimo"),
                    "env" to (env.get("APP_ENV") ?: "local"),
                    "debug" to env.boolean("APP_DEBUG", true),
                    "url" to (env.get("APP_URL") ?: "http://localhost:8000"),
                    "key" to (env.get("APP_KEY") ?: "")
                )
            )
        }
        app.instance(ConfigRepository::class, config)
        app.singleton(Dispatcher::class) { Dispatcher() }
        app.singleton(CacheRepository::class) { CacheRepository(ArrayStore()) }
        app.singleton(Logger::class) { Logger("kotlimo") }
    }
}
