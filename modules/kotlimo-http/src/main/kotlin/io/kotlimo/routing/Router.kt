package io.kotlimo.routing

import io.kotlimo.http.Middleware
import io.kotlimo.http.Request
import io.kotlimo.http.Response
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions

sealed class RouteAction {
    data class Closure(val handler: (Request) -> Response) : RouteAction()
    data class ControllerMethod(val controller: KClass<*>, val method: String) : RouteAction()
}

class Route(
    val methods: Set<String>,
    val uri: String,
    val action: RouteAction,
    var name: String? = null,
    val middleware: MutableList<Middleware> = mutableListOf(),
    val defaults: MutableMap<String, String> = mutableMapOf()
) {
    private val parameterNames: List<String>
    private val regex: Regex

    init {
        val names = mutableListOf<String>()
        val pattern = uri.trimEnd('/').ifEmpty { "/" }.replace(Regex("\\{([^}]+)\\}")) { match ->
            val raw = match.groupValues[1]
            val optional = raw.endsWith('?')
            val name = raw.removeSuffix("?")
            names += name
            if (optional) "([^/]*)" else "([^/]+)"
        }
        parameterNames = names
        regex = Regex("^" + pattern.replace("/", "\\/") + "/?$")
    }

    fun named(name: String): Route {
        this.name = (this.name ?: "") + name
        return this
    }

    fun middleware(vararg middleware: Middleware): Route {
        this.middleware.addAll(middleware)
        return this
    }

    fun matches(method: String, path: String): Boolean =
        method.uppercase() in methods && regex.matches(normalize(path))

    fun matchesUri(path: String): Boolean = regex.matches(normalize(path))

    fun extractParameters(path: String): Map<String, String> {
        val match = regex.matchEntire(normalize(path)) ?: return defaults.toMap()
        val values = mutableMapOf<String, String>()
        parameterNames.forEachIndexed { index, name ->
            val value = match.groupValues.getOrNull(index + 1).orEmpty()
            if (value.isNotEmpty()) {
                values[name] = value
            } else {
                defaults[name]?.let { values[name] = it }
            }
        }
        return values
    }

    fun compiledUri(parameters: Map<String, Any?> = emptyMap()): String {
        var compiled = uri
        Regex("\\{([^}]+)\\}").findAll(uri).forEach { match ->
            val name = match.groupValues[1].removeSuffix("?")
            val value = parameters[name]?.toString() ?: defaults[name] ?: ""
            compiled = compiled.replace(match.value, value)
        }
        return compiled.replace(Regex("/{2,}"), "/").ifEmpty { "/" }
    }

    private fun normalize(path: String): String {
        val clean = path.substringBefore('?').ifEmpty { "/" }
        return if (clean.length > 1) clean.trimEnd('/') else clean
    }
}

typealias RouteHandler = (Request) -> Response

class Router {
    private val routes = mutableListOf<Route>()
    private val groupStack = ArrayDeque<RouteGroup>()

    data class RouteGroup(
        val prefix: String = "",
        val middleware: List<Middleware> = emptyList(),
        val name: String = ""
    )

    fun routes(): List<Route> = routes.toList()

    fun get(uri: String, handler: RouteHandler): Route = addRoute(setOf("GET", "HEAD"), uri, RouteAction.Closure(handler))

    fun post(uri: String, handler: RouteHandler): Route = addRoute(setOf("POST"), uri, RouteAction.Closure(handler))

    fun put(uri: String, handler: RouteAction): Route = addRoute(setOf("PUT"), uri, handler)

    fun put(uri: String, handler: RouteHandler): Route = put(uri, RouteAction.Closure(handler))

    fun patch(uri: String, handler: RouteHandler): Route = addRoute(setOf("PATCH"), uri, RouteAction.Closure(handler))

    fun delete(uri: String, handler: RouteHandler): Route = addRoute(setOf("DELETE"), uri, RouteAction.Closure(handler))

    fun match(methods: Collection<String>, uri: String, handler: RouteHandler): Route =
        addRoute(methods.map { it.uppercase() }.toSet(), uri, RouteAction.Closure(handler))

    fun any(uri: String, handler: RouteHandler): Route =
        addRoute(setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"), uri, RouteAction.Closure(handler))

    fun get(uri: String, controller: KClass<*>, method: String): Route =
        addRoute(setOf("GET", "HEAD"), uri, RouteAction.ControllerMethod(controller, method))

    fun post(uri: String, controller: KClass<*>, method: String): Route =
        addRoute(setOf("POST"), uri, RouteAction.ControllerMethod(controller, method))

    fun add(methods: Set<String>, uri: String, action: RouteAction): Route = addRoute(methods, uri, action)

    fun group(
        prefix: String = "",
        middleware: List<Middleware> = emptyList(),
        name: String = "",
        block: Router.() -> Unit
    ) {
        val parent = groupStack.lastOrNull()
        val combined = RouteGroup(
            prefix = joinPaths(parent?.prefix.orEmpty(), prefix),
            middleware = (parent?.middleware ?: emptyList()) + middleware,
            name = (parent?.name.orEmpty()) + name
        )
        groupStack.addLast(combined)
        try {
            block()
        } finally {
            groupStack.removeLast()
        }
    }

    fun findByName(name: String): Route? = routes.firstOrNull { it.name == name }

    fun url(name: String, parameters: Map<String, Any?> = emptyMap()): String {
        val route = findByName(name) ?: throw IllegalArgumentException("Named route [$name] is not defined")
        return route.compiledUri(parameters)
    }

    fun match(request: Request): Route? =
        routes.firstOrNull { it.matches(request.method(), request.path) }

    fun matchUri(path: String): List<Route> = routes.filter { it.matchesUri(path) }

    private fun addRoute(methods: Set<String>, uri: String, action: RouteAction): Route {
        val group = groupStack.lastOrNull()
        val fullUri = joinPaths(group?.prefix.orEmpty(), uri)
        val route = Route(
            methods = methods,
            uri = fullUri,
            action = action,
            name = group?.name?.takeIf { it.isNotEmpty() }
        )
        group?.middleware?.let { route.middleware.addAll(it) }
        routes += route
        return route
    }

    private fun joinPaths(prefix: String, uri: String): String {
        val left = prefix.trim('/')
        val right = uri.trim('/')
        val joined = listOf(left, right).filter { it.isNotEmpty() }.joinToString("/")
        return "/" + joined
    }
}

class ControllerDispatcher(private val resolver: (KClass<*>) -> Any) {
    fun dispatch(route: Route, request: Request): Response {
        return when (val action = route.action) {
            is RouteAction.Closure -> action.handler(request)
            is RouteAction.ControllerMethod -> invokeController(action, request)
        }
    }

    private fun invokeController(action: RouteAction.ControllerMethod, request: Request): Response {
        val instance = resolver(action.controller)
        val function = action.controller.memberFunctions.firstOrNull { it.name == action.method }
            ?: throw IllegalStateException("Controller method [${action.controller.simpleName}@${action.method}] not found")
        val args = function.parameters.drop(1).map { parameter ->
            val name = parameter.name
            val type = parameter.type.classifier
            when {
                type == Request::class -> request
                name != null && request.route(name) != null -> coerce(request.route(name)!!, parameter)
                name != null && request.has(name) -> coerce(request.input(name), parameter)
                parameter.type.isMarkedNullable -> null
                else -> throw IllegalArgumentException("Unable to resolve controller argument [$name]")
            }
        }
        val result = try {
            function.call(instance, *args.toTypedArray())
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        }
        return toResponse(result)
    }

    private fun coerce(value: Any?, parameter: kotlin.reflect.KParameter): Any? {
        val type = parameter.type.classifier
        val raw = value?.toString() ?: return null
        return when (type) {
            String::class -> raw
            Int::class -> raw.toInt()
            Long::class -> raw.toLong()
            Boolean::class -> raw.lowercase() in setOf("1", "true", "yes")
            Double::class -> raw.toDouble()
            else -> value
        }
    }

    fun toResponse(result: Any?): Response = when (result) {
        is Response -> result
        is String -> Response.html(result)
        null -> Response.noContent()
        else -> Response.json(result)
    }
}
