package io.kotlimo.http

import io.kotlimo.container.Container
import io.kotlimo.routing.ControllerDispatcher
import io.kotlimo.routing.Router
import io.kotlimo.validation.ValidationException
import kotlin.reflect.KClass

class HttpKernel(
    private val container: Container,
    private val router: Router,
    private val globalMiddleware: List<Middleware> = emptyList()
) {
    private val dispatcher = ControllerDispatcher { type: KClass<*> ->
        if (container.bound(type)) container.make(type) else type.java.getDeclaredConstructor().newInstance()
    }

    fun handle(request: Request): Response {
        container.instance(Request::class, request)
        return try {
            MiddlewarePipeline(globalMiddleware).send(request) { incoming ->
                dispatchToRouter(incoming)
            }
        } catch (e: HttpException) {
            e.toResponse(request)
        } catch (e: ValidationException) {
            renderValidation(request, e)
        } catch (e: Exception) {
            renderException(request, e)
        }
    }

    private fun dispatchToRouter(request: Request): Response {
        val route = router.match(request)
        if (route == null) {
            val byUri = router.matchUri(request.path)
            if (byUri.isNotEmpty()) {
                throw HttpException(405, "Method not allowed")
            }
            throw HttpException(404, "Not Found")
        }
        request.route = route
        route.extractParameters(request.path).forEach { (key, value) ->
            request.setRouteParameter(key, value)
        }
        return MiddlewarePipeline(route.middleware).send(request) { incoming ->
            dispatcher.dispatch(route, incoming)
        }
    }

    private fun renderValidation(request: Request, error: ValidationException): Response {
        if (request.wantsJson() || request.ajax()) {
            return Response.json(mapOf("message" to error.message, "errors" to error.errors), 422)
        }
        return Response.html(
            "<h1>422</h1><p>${io.kotlimo.support.Str.escapeHtml(error.message ?: "The given data was invalid.")}</p>",
            422
        )
    }

    private fun renderException(request: Request, error: Exception): Response {
        if (request.wantsJson()) {
            return Response.json(
                mapOf(
                    "message" to (error.message ?: "Server Error"),
                    "exception" to error::class.qualifiedName
                ),
                500
            )
        }
        val message = error.message ?: error::class.simpleName ?: "Server Error"
        return Response.html("<h1>Server Error</h1><p>${io.kotlimo.support.Str.escapeHtml(message)}</p>", 500)
    }
}

class HttpException(val status: Int, message: String) : RuntimeException(message) {
    fun toResponse(request: Request): Response {
        if (request.wantsJson()) {
            return Response.json(mapOf("message" to message), status)
        }
        return Response.html("<h1>$status</h1><p>${io.kotlimo.support.Str.escapeHtml(message ?: "")}</p>", status)
    }
}

fun abort(status: Int, message: String = "Error"): Nothing = throw HttpException(status, message)

fun abortIf(condition: Boolean, status: Int, message: String = "Error") {
    if (condition) abort(status, message)
}
