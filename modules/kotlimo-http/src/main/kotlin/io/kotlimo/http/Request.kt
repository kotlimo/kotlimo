package io.kotlimo.http

data class UploadedFile(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is UploadedFile && other.filename == filename && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = filename.hashCode() * 31 + bytes.contentHashCode()
}

class Request(
    val method: String,
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val body: Map<String, Any?> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val files: Map<String, UploadedFile> = emptyMap(),
    val rawBody: String = "",
    routeParameters: Map<String, String> = emptyMap(),
    val serverPort: Int = 8000,
    val remoteAddress: String = "127.0.0.1"
) {
    private val bag = routeParameters.toMutableMap()
    var route: io.kotlimo.routing.Route? = null

    val path: String
        get() {
            val withoutQuery = uri.substringBefore('?')
            return if (withoutQuery.isEmpty()) "/" else withoutQuery
        }

    val fullUrl: String
        get() = uri

    fun method(): String = method.uppercase()

    fun isMethod(name: String): Boolean = method() == name.uppercase()

    fun header(key: String, default: String? = null): String? =
        headers.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: default

    fun bearerToken(): String? {
        val value = header("Authorization") ?: return null
        return if (value.startsWith("Bearer ", ignoreCase = true)) value.substring(7).trim() else null
    }

    fun wantsJson(): Boolean {
        val accept = header("Accept").orEmpty()
        val contentType = header("Content-Type").orEmpty()
        return accept.contains("application/json") || contentType.contains("application/json") || path.startsWith("/api")
    }

    fun ajax(): Boolean = header("X-Requested-With")?.equals("XMLHttpRequest", ignoreCase = true) == true

    fun ip(): String = remoteAddress

    fun input(key: String, default: Any? = null): Any? =
        body[key] ?: query[key] ?: bag[key] ?: default

    fun string(key: String, default: String = ""): String = input(key, default)?.toString() ?: default

    fun int(key: String, default: Int = 0): Int = string(key).toIntOrNull() ?: default

    fun boolean(key: String, default: Boolean = false): Boolean {
        val value = input(key) ?: return default
        return when (value) {
            is Boolean -> value
            is String -> value.lowercase() in setOf("1", "true", "on", "yes")
            is Number -> value.toInt() != 0
            else -> default
        }
    }

    fun all(): Map<String, Any?> = (query + body + bag)

    fun only(vararg keys: String): Map<String, Any?> = all().filterKeys { it in keys }

    fun except(vararg keys: String): Map<String, Any?> = all().filterKeys { it !in keys }

    fun has(key: String): Boolean = all().containsKey(key) && all()[key] != null

    fun filled(key: String): Boolean = io.kotlimo.support.filled(input(key))

    fun cookie(key: String, default: String? = null): String? = cookies[key] ?: default

    fun file(key: String): UploadedFile? = files[key]

    fun route(key: String): String? = bag[key]

    fun setRouteParameter(key: String, value: String) {
        bag[key] = value
    }

    fun routeParameters(): Map<String, String> = bag.toMap()

    fun isSecure(): Boolean = header("X-Forwarded-Proto") == "https"

    companion object {
        fun get(uri: String, query: Map<String, String> = emptyMap()): Request =
            Request(method = "GET", uri = uri, query = query)

        fun post(uri: String, body: Map<String, Any?> = emptyMap()): Request =
            Request(method = "POST", uri = uri, body = body)

        fun json(method: String, uri: String, body: Map<String, Any?> = emptyMap()): Request =
            Request(
                method = method,
                uri = uri,
                body = body,
                headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                rawBody = io.kotlimo.support.Json.encode(body)
            )
    }
}
