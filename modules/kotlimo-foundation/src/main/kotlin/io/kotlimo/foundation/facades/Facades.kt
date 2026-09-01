package io.kotlimo.foundation.facades

import io.kotlimo.auth.AuthManager
import io.kotlimo.auth.Authenticatable
import io.kotlimo.cache.CacheRepository
import io.kotlimo.config.ConfigRepository
import io.kotlimo.database.Connection
import io.kotlimo.database.DatabaseManager
import io.kotlimo.database.QueryBuilder
import io.kotlimo.events.Dispatcher
import io.kotlimo.filesystem.Filesystem
import io.kotlimo.filesystem.FilesystemManager
import io.kotlimo.foundation.Application
import io.kotlimo.foundation.Facade
import io.kotlimo.hash.Hash as HashHasher
import io.kotlimo.http.Request
import io.kotlimo.http.Response
import io.kotlimo.logging.Logger
import io.kotlimo.mail.MailMessage
import io.kotlimo.mail.Mailer
import io.kotlimo.queue.Job
import io.kotlimo.queue.QueueManager
import io.kotlimo.routing.RouteHandler
import io.kotlimo.routing.Router
import io.kotlimo.session.Session
import io.kotlimo.view.ViewEngine
import kotlin.reflect.KClass

object App : Facade() {
    fun get(): Application = app
    fun make(abstract: String): Any = app.make(abstract)
    inline fun <reified T : Any> make(): T = app.make()
    fun environment(): String = app.environment()
}

object Config : Facade() {
    private fun repo() = app.make(ConfigRepository::class)
    fun get(key: String, default: Any? = null): Any? = repo().get(key, default)
    fun string(key: String, default: String = ""): String = repo().string(key, default)
    fun boolean(key: String, default: Boolean = false): Boolean = repo().boolean(key, default)
    fun set(key: String, value: Any?) = repo().set(key, value)
}

object Route : Facade() {
    private fun router() = app.make(Router::class)
    fun get(uri: String, handler: RouteHandler): io.kotlimo.routing.Route = router().get(uri, handler)
    fun post(uri: String, handler: RouteHandler): io.kotlimo.routing.Route = router().post(uri, handler)
    fun put(uri: String, handler: RouteHandler): io.kotlimo.routing.Route = router().put(uri, handler)
    fun patch(uri: String, handler: RouteHandler): io.kotlimo.routing.Route = router().patch(uri, handler)
    fun delete(uri: String, handler: RouteHandler): io.kotlimo.routing.Route = router().delete(uri, handler)
    fun get(uri: String, controller: KClass<*>, method: String) = router().get(uri, controller, method)
    fun post(uri: String, controller: KClass<*>, method: String) = router().post(uri, controller, method)
    fun group(
        prefix: String = "",
        middleware: List<io.kotlimo.http.Middleware> = emptyList(),
        name: String = "",
        routes: Router.() -> Unit
    ) = router().group(prefix, middleware, name, routes)
    fun url(name: String, parameters: Map<String, Any?> = emptyMap()): String = router().url(name, parameters)
    fun list(): List<io.kotlimo.routing.Route> = router().routes()
}

object View : Facade() {
    fun make(name: String, data: Map<String, Any?> = emptyMap()): String =
        app.make(ViewEngine::class).render(name, data)
}

object DB : Facade() {
    fun connection(): Connection = app.make(DatabaseManager::class).connection()
    fun table(name: String): QueryBuilder = connection().table(name)
    fun select(sql: String, bindings: List<Any?> = emptyList()) = connection().select(sql, bindings)
}

object Schema : Facade() {
    fun create(table: String, block: io.kotlimo.database.Blueprint.() -> Unit) =
        io.kotlimo.database.Schema.create(table, block)

    fun drop(table: String) = io.kotlimo.database.Schema.drop(table)

    fun hasTable(table: String): Boolean = io.kotlimo.database.Schema.hasTable(table)
}

object Cache : Facade() {
    private fun repo() = app.make(CacheRepository::class)
    fun get(key: String, default: Any? = null): Any? = repo().get(key, default)
    fun put(key: String, value: Any?, seconds: Long = 3600) = repo().put(key, value, seconds)
    fun remember(key: String, seconds: Long, callback: () -> Any?): Any? = repo().remember(key, seconds, callback)
    fun forget(key: String) = repo().forget(key)
    fun flush() = repo().flush()
}

object Event : Facade() {
    fun dispatch(event: Any): Any = app.make(Dispatcher::class).dispatch(event)
    fun <T : Any> listen(type: KClass<T>, listener: (T) -> Unit) = app.make(Dispatcher::class).listen(type, listener)
}

object Log : Facade() {
    private fun logger() = app.make(Logger::class)
    fun info(message: String) = logger().info(message)
    fun error(message: String, throwable: Throwable? = null) = logger().error(message, throwable)
    fun debug(message: String) = logger().debug(message)
}

object Hash : Facade() {
    fun make(value: String, algorithm: String = HashHasher.driver): String = HashHasher.make(value, algorithm)
    fun check(value: String, hashed: String): Boolean = HashHasher.check(value, hashed)
}

object Auth : Facade() {
    private fun manager() = app.make(AuthManager::class)
    private fun request() = app.make(Request::class)
    fun attempt(credentials: Map<String, Any?>): Boolean = manager().attempt(request(), credentials)
    fun login(user: Authenticatable) = manager().login(request(), user)
    fun logout() = manager().logout(request())
    fun user(): Authenticatable? = if (app.bound(Request::class)) manager().user(request()) else null
    fun check(): Boolean = user() != null
    fun guest(): Boolean = user() == null
    fun id(): Any? = user()?.getAuthIdentifier()
}

object Mail : Facade() {
    private fun mailer() = app.make(Mailer::class)

    fun send(message: MailMessage) = mailer().send(message)

    fun send(to: String, subject: String, body: String) {
        send(
            MailMessage().to(to).subject(subject).html(body)
        )
    }

    fun send(block: MailMessage.() -> Unit) {
        send(MailMessage().apply(block))
    }
}

object Queue : Facade() {
    private fun manager() = app.make(QueueManager::class)
    fun push(job: Job) = manager().push(job)
    fun later(seconds: Long, job: Job) = manager().later(seconds, job)
    fun pop(): Job? = manager().pop()
}

object Storage : Facade() {
    private fun manager() = app.make(FilesystemManager::class)
    fun disk(name: String? = null): Filesystem = manager().disk(name)
    fun put(path: String, contents: String): Boolean = disk().put(path, contents)
    fun get(path: String): String = disk().get(path)
    fun exists(path: String): Boolean = disk().exists(path)
    fun delete(path: String): Boolean = disk().delete(path)
}

fun view(name: String, data: Map<String, Any?> = emptyMap()): Response =
    Response.html(View.make(name, data))

fun view(name: String, vararg pairs: Pair<String, Any?>): Response = view(name, pairs.toMap())

fun redirect(uri: String): Response = Response.redirect(uri)

fun abort(status: Int, message: String = "Error"): Nothing = io.kotlimo.http.abort(status, message)

fun request(): Request = Facade.app.make(Request::class)

fun session(): Session = request().session()

fun user(): Authenticatable? = Auth.user()

fun csrf(): String = request().csrf()

fun csrfField(): String = """<input type="hidden" name="_token" value="${csrf()}">"""

fun dispatch(job: Job) = Queue.push(job)
