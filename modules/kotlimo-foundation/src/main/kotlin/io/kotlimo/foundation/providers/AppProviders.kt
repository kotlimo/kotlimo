package io.kotlimo.foundation.providers

import io.kotlimo.auth.AuthManager
import io.kotlimo.auth.DatabaseUserProvider
import io.kotlimo.config.ConfigRepository
import io.kotlimo.config.Environment
import io.kotlimo.database.DatabaseManager
import io.kotlimo.filesystem.FilesystemManager
import io.kotlimo.filesystem.LocalFilesystem
import io.kotlimo.foundation.Application
import io.kotlimo.foundation.ServiceProvider
import io.kotlimo.logging.Logger
import io.kotlimo.mail.ArrayMailer
import io.kotlimo.mail.LogMailer
import io.kotlimo.mail.Mailer
import io.kotlimo.queue.ArrayQueue
import io.kotlimo.queue.QueueManager
import io.kotlimo.queue.SyncQueue
import io.kotlimo.scheduling.Schedule
import io.kotlimo.session.ArraySessionStore
import io.kotlimo.session.FileSessionStore
import io.kotlimo.session.SessionManager

class SessionServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(SessionManager::class) { container ->
            val config = container.make(ConfigRepository::class)
            val env = container.make(Environment::class)
            val driver = env.get("SESSION_DRIVER") ?: config.string("session.driver", "array")
            val store = if (driver == "file") {
                FileSessionStore(app.storagePath().resolve("framework").resolve("sessions"))
            } else {
                ArraySessionStore()
            }
            SessionManager(
                store = store,
                cookieName = config.string("session.cookie", "kotlimo_session"),
                key = env.get("APP_KEY") ?: config.string("app.key"),
                lifetimeMinutes = config.long("session.lifetime", 120),
                cookiePath = config.string("session.path", "/"),
                secure = config.boolean("session.secure")
            )
        }
    }
}

class AuthServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(AuthManager::class) { container ->
            AuthManager(
                DatabaseUserProvider(
                    connection = { container.make(DatabaseManager::class).connection() },
                    table = container.make(ConfigRepository::class).string("auth.table", "users")
                )
            )
        }
    }
}

class FilesystemServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(FilesystemManager::class) { container ->
            val config = container.make(ConfigRepository::class)
            val manager = FilesystemManager()
            val root = config.string("filesystems.disks.local.root").ifBlank { "storage/app" }
            manager.add("local", LocalFilesystem(app.path(*root.split('/').toTypedArray())))
            manager.default = config.string("filesystems.default", "local")
            manager
        }
    }
}

class QueueServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(ArrayQueue::class) { ArrayQueue() }
        app.singleton(QueueManager::class) { container ->
            val config = container.make(ConfigRepository::class)
            val env = container.make(Environment::class)
            val driver = env.get("QUEUE_DRIVER") ?: config.string("queue.driver", "sync")
            val manager = QueueManager()
            manager.add("sync", SyncQueue())
            manager.add("array", container.make(ArrayQueue::class))
            manager.default = if (driver == "array") "array" else "sync"
            manager
        }
    }
}

class MailServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(ArrayMailer::class) { ArrayMailer() }
        app.bind(Mailer::class, shared = true) { container ->
            val config = container.make(ConfigRepository::class)
            val env = container.make(Environment::class)
            val driver = env.get("MAIL_DRIVER") ?: config.string("mail.driver", "log")
            when (driver) {
                "array" -> container.make(ArrayMailer::class)
                else -> LogMailer { container.make(Logger::class).info(it) }
            }
        }
    }
}

class SchedulingServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(Schedule::class) { Schedule() }
    }
}
