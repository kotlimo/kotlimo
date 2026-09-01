package io.kotlimo.http

import java.nio.file.Files
import java.nio.file.Path

class ServePublicAssets(private val publicPath: Path) : Middleware {
    override fun handle(request: Request, next: (Request) -> Response): Response {
        if (request.method() != "GET" && request.method() != "HEAD") {
            return next(request)
        }
        val relative = request.path.removePrefix("/").ifEmpty { return next(request) }
        val resolved = publicPath.resolve(relative).normalize()
        if (!resolved.startsWith(publicPath.normalize())) {
            return next(request)
        }
        if (Files.isRegularFile(resolved)) {
            return Response.file(resolved)
        }
        return next(request)
    }
}

class TrimTrailingSlash : Middleware {
    override fun handle(request: Request, next: (Request) -> Response): Response {
        val path = request.path
        if (path.length > 1 && path.endsWith('/')) {
            val trimmed = path.dropLast(1)
            val query = request.uri.substringAfter('?', missingDelimiterValue = "")
            val location = if (query.isEmpty()) trimmed else "$trimmed?$query"
            return Response.redirect(location, 301)
        }
        return next(request)
    }
}

class AddSecurityHeaders : Middleware {
    override fun handle(request: Request, next: (Request) -> Response): Response {
        val response = next(request)
        response.headers.putIfAbsent("X-Content-Type-Options", "nosniff")
        response.headers.putIfAbsent("X-Frame-Options", "SAMEORIGIN")
        response.headers.putIfAbsent("Referrer-Policy", "strict-origin-when-cross-origin")
        return response
    }
}
