package io.kotlimo.http

import io.kotlimo.support.Json
import java.nio.file.Files
import java.nio.file.Path

class Response(
    var status: Int = 200,
    var content: String = "",
    val headers: MutableMap<String, String> = mutableMapOf(),
    var contentBytes: ByteArray? = null,
    val cookies: MutableList<Cookie> = mutableListOf()
) {
    fun header(key: String, value: String): Response {
        headers[key] = value
        return this
    }

    fun cookie(cookie: Cookie): Response {
        cookies += cookie
        return this
    }

    fun cookie(
        name: String,
        value: String,
        maxAge: Long? = null,
        path: String = "/",
        httpOnly: Boolean = true,
        secure: Boolean = false
    ): Response = cookie(Cookie(name, value, maxAge = maxAge, path = path, httpOnly = httpOnly, secure = secure))

    fun withHeaders(extra: Map<String, String>): Response {
        headers.putAll(extra)
        return this
    }

    val body: String
        get() = content

    fun isSuccessful(): Boolean = status in 200..299

    fun isRedirect(): Boolean = status in 300..399

    companion object {
        fun make(content: String = "", status: Int = 200, headers: Map<String, String> = emptyMap()): Response =
            Response(status, content, headers.toMutableMap())

        fun html(content: String, status: Int = 200): Response =
            make(content, status, mapOf("Content-Type" to "text/html; charset=UTF-8"))

        fun text(content: String, status: Int = 200): Response =
            make(content, status, mapOf("Content-Type" to "text/plain; charset=UTF-8"))

        fun json(data: Any?, status: Int = 200): Response =
            make(Json.encode(data), status, mapOf("Content-Type" to "application/json; charset=UTF-8"))

        fun redirect(uri: String, status: Int = 302): Response =
            make("", status, mapOf("Location" to uri))

        fun noContent(): Response = make("", 204)

        fun notFound(message: String = "Not Found"): Response = html(message, 404)

        fun file(path: Path, contentType: String? = null): Response {
            val bytes = Files.readAllBytes(path)
            val type = contentType ?: guessContentType(path.fileName.toString())
            val text = if (
                type.startsWith("text/") ||
                type.contains("json") ||
                type.contains("xml") ||
                type.contains("javascript") ||
                type.contains("svg")
            ) {
                String(bytes, Charsets.UTF_8)
            } else {
                ""
            }
            return Response(
                status = 200,
                content = text,
                headers = mutableMapOf("Content-Type" to type),
                contentBytes = bytes
            )
        }

        fun guessContentType(filename: String): String = when (filename.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html; charset=UTF-8"
            "css" -> "text/css; charset=UTF-8"
            "js" -> "application/javascript; charset=UTF-8"
            "json" -> "application/json; charset=UTF-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "txt" -> "text/plain; charset=UTF-8"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }
}

fun response(content: String = "", status: Int = 200): Response = Response.make(content, status)

fun json(data: Any?, status: Int = 200): Response = Response.json(data, status)

fun redirect(uri: String, status: Int = 302): Response = Response.redirect(uri, status)
