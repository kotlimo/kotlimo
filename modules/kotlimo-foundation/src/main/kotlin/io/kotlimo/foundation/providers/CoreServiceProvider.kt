package io.kotlimo.foundation.providers

import io.kotlimo.cache.ArrayStore
import io.kotlimo.cache.CacheRepository
import io.kotlimo.config.ConfigRepository
import io.kotlimo.config.Environment
import io.kotlimo.events.Dispatcher
import io.kotlimo.foundation.Application
import io.kotlimo.foundation.ServiceProvider
import io.kotlimo.hash.Hash
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
        ensureDefaults(config, env)
        overlayEnvironment(config, env)
        Hash.driver = env.get("HASH_DRIVER") ?: config.string("hash.driver", Hash.BCRYPT)
        app.instance(ConfigRepository::class, config)
        app.singleton(Dispatcher::class) { Dispatcher() }
        app.singleton(CacheRepository::class) { CacheRepository(ArrayStore()) }
        app.singleton(Logger::class) { Logger("kotlimo") }
    }

    private fun ensureDefaults(config: ConfigRepository, env: Environment) {
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
        if (!config.has("database.url")) {
            config.load(
                "database",
                mapOf(
                    "url" to (env.get("DB_URL") ?: "jdbc:h2:mem:kotlimo;MODE=MySQL;DB_CLOSE_DELAY=-1"),
                    "username" to (env.get("DB_USERNAME") ?: ""),
                    "password" to (env.get("DB_PASSWORD") ?: "")
                )
            )
        }
        if (!config.has("session.driver")) {
            config.load(
                "session",
                mapOf(
                    "driver" to (env.get("SESSION_DRIVER") ?: "array"),
                    "cookie" to "kotlimo_session",
                    "lifetime" to 120,
                    "path" to "/"
                )
            )
        }
        if (!config.has("mail.driver")) {
            config.load("mail", mapOf("driver" to (env.get("MAIL_DRIVER") ?: "log"), "from" to "hello@example.com"))
        }
        if (!config.has("queue.driver")) {
            config.load("queue", mapOf("driver" to (env.get("QUEUE_DRIVER") ?: "sync")))
        }
        if (!config.has("filesystems.default")) {
            config.load(
                "filesystems",
                mapOf(
                    "default" to "local",
                    "disks" to mapOf("local" to mapOf("driver" to "local", "root" to "storage/app"))
                )
            )
        }
        if (!config.has("hash.driver")) {
            config.load("hash", mapOf("driver" to Hash.BCRYPT))
        }
    }

    private fun overlayEnvironment(config: ConfigRepository, env: Environment) {
        val file = env.all()
        file["APP_NAME"]?.let { config.set("app.name", it) }
        file["APP_ENV"]?.let { config.set("app.env", it) }
        if (file.containsKey("APP_DEBUG")) config.set("app.debug", env.boolean("APP_DEBUG"))
        file["APP_URL"]?.let { config.set("app.url", it) }
        file["APP_KEY"]?.let { config.set("app.key", it) }
        file["DB_URL"]?.let { config.set("database.url", it) }
        file["DB_USERNAME"]?.let { config.set("database.username", it) }
        file["DB_PASSWORD"]?.let { config.set("database.password", it) }
        file["SESSION_DRIVER"]?.let { config.set("session.driver", it) }
        file["MAIL_DRIVER"]?.let { config.set("mail.driver", it) }
        file["QUEUE_DRIVER"]?.let { config.set("queue.driver", it) }
        file["HASH_DRIVER"]?.let { config.set("hash.driver", it) }
    }
}
