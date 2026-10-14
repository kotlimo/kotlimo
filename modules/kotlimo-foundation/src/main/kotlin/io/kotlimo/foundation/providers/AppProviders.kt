package io.kotlimo.foundation.providers

import io.kotlimo.auth.AuthManager
import io.kotlimo.auth.DatabaseTokenStore
import io.kotlimo.auth.DatabaseUserProvider
import io.kotlimo.auth.Gate
import io.kotlimo.auth.PasswordBroker
import io.kotlimo.config.ConfigRepository
import io.kotlimo.config.Environment
import io.kotlimo.database.DatabaseManager
import io.kotlimo.filesystem.FilesystemManager
import io.kotlimo.filesystem.LocalFilesystem
import io.kotlimo.filesystem.S3Filesystem
import io.kotlimo.foundation.Application
import io.kotlimo.foundation.ServiceProvider
import io.kotlimo.logging.Logger
import io.kotlimo.mail.ArrayMailer
import io.kotlimo.mail.JakartaSmtpTransport
import io.kotlimo.mail.LogMailer
import io.kotlimo.mail.Mailer
import io.kotlimo.mail.SmtpMailer
import io.kotlimo.queue.ArrayQueue
import io.kotlimo.queue.DatabaseQueue
import io.kotlimo.queue.QueueManager
import io.kotlimo.queue.RedisQueue
import io.kotlimo.queue.SyncQueue
import io.kotlimo.cache.JedisKeyValue
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
        app.singleton(Gate::class) { Gate() }
        app.singleton(AuthManager::class) { container ->
            val users = DatabaseUserProvider(
                connection = { container.make(DatabaseManager::class).connection() },
                table = container.make(ConfigRepository::class).string("auth.table", "users")
            )
            AuthManager(users, DatabaseTokenStore({ container.make(DatabaseManager::class).connection() }, users))
        }
        app.singleton(PasswordBroker::class) { container ->
            PasswordBroker(
                connection = { container.make(DatabaseManager::class).connection() },
                mailer = container.make(Mailer::class)
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
            val key = container.make(Environment::class).get("AWS_ACCESS_KEY_ID") ?: config.string("filesystems.disks.s3.key")
            val secret = container.make(Environment::class).get("AWS_SECRET_ACCESS_KEY") ?: config.string("filesystems.disks.s3.secret")
            val bucket = config.string("filesystems.disks.s3.bucket")
            if (key.isNotBlank() && secret.isNotBlank() && bucket.isNotBlank()) {
                val client = S3Filesystem.client(
                    region = config.string("filesystems.disks.s3.region", "us-east-1"),
                    key = key,
                    secret = secret,
                    endpoint = config.string("filesystems.disks.s3.endpoint").ifBlank { null }
                )
                manager.add("s3", S3Filesystem(bucket, client))
            }
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
            val database = DatabaseQueue({ container.make(io.kotlimo.database.DatabaseManager::class).connection() })
            manager.add("sync", SyncQueue())
            manager.add("array", container.make(ArrayQueue::class))
            manager.add("database", database)
            val redisUrl = env.get("REDIS_URL") ?: config.string("queue.redis", "redis://127.0.0.1:6379")
            if (driver == "redis") {
                manager.add("redis", RedisQueue(JedisKeyValue(redisUrl)))
            }
            manager.default = when (driver) {
                "array", "database", "redis" -> driver
                else -> "sync"
            }
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
                "smtp" -> SmtpMailer(
                    JakartaSmtpTransport(
                        host = env.get("MAIL_HOST") ?: config.string("mail.host", "127.0.0.1"),
                        port = (env.get("MAIL_PORT") ?: config.string("mail.port", "587")).toInt(),
                        username = env.get("MAIL_USERNAME") ?: config.string("mail.username"),
                        password = env.get("MAIL_PASSWORD") ?: config.string("mail.password"),
                        from = env.get("MAIL_FROM") ?: config.string("mail.from", "hello@example.com")
                    )
                )
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
