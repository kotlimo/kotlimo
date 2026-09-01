package io.kotlimo.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class RequestFactory {
    companion object {
        fun from(exchange: HttpExchange): Request {
            val uri = exchange.requestURI.toString()
            val query = parseQuery(exchange.requestURI.rawQuery)
            val headers = exchange.requestHeaders.toMap().mapValues { it.value.lastOrNull().orEmpty() }
            val cookies = parseCookies(headers["Cookie"] ?: headers["cookie"])
            val raw = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value.orEmpty()
            val body = parseBody(raw, contentType)
            return Request(
                method = exchange.requestMethod.uppercase(),
                uri = uri,
                headers = headers,
                query = query,
                body = body,
                cookies = cookies,
                rawBody = raw,
                serverPort = exchange.localAddress.port,
                remoteAddress = exchange.remoteAddress.address.hostAddress
            )
        }

        fun parseQuery(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split('&').mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val parts = pair.split('=', limit = 2)
                val key = urlDecode(parts[0])
                val value = urlDecode(parts.getOrElse(1) { "" })
                key to value
            }.toMap()
        }

        fun parseBody(raw: String, contentType: String): Map<String, Any?> {
            if (raw.isBlank()) return emptyMap()
            return when {
                contentType.contains("application/json") -> {
                    val decoded = io.kotlimo.support.Json.decode(raw)
                    @Suppress("UNCHECKED_CAST")
                    (decoded as? Map<String, Any?>) ?: emptyMap()
                }
                contentType.contains("application/x-www-form-urlencoded") || !contentType.contains('/') ->
                    parseQuery(raw)
                else -> emptyMap()
            }
        }

        fun parseCookies(header: String?): Map<String, String> {
            if (header.isNullOrBlank()) return emptyMap()
            return header.split(';').mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val idx = trimmed.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                trimmed.substring(0, idx) to trimmed.substring(idx + 1)
            }.toMap()
        }

        private fun urlDecode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8)
    }
}

class EmbeddedServer(
    private val kernel: HttpKernel,
    private val host: String = "127.0.0.1",
    private val port: Int = 8000
) {
    private var server: HttpServer? = null

    fun start(block: Boolean = false) {
        val httpServer = HttpServer.create(InetSocketAddress(host, port), 0)
        httpServer.createContext("/") { exchange ->
            try {
                val request = RequestFactory.from(exchange)
                val response = kernel.handle(request)
                write(exchange, response)
            } catch (e: Exception) {
                val message = e.message ?: "Server Error"
                val fallback = Response.html("<h1>Server Error</h1><p>$message</p>", 500)
                write(exchange, fallback)
            }
        }
        httpServer.executor = Executors.newCachedThreadPool()
        httpServer.start()
        server = httpServer
        if (block) {
            Thread.currentThread().join()
        }
    }

    fun stop(delay: Int = 0) {
        server?.stop(delay)
        server = null
    }

    fun port(): Int = port

    private fun write(exchange: HttpExchange, response: Response) {
        response.headers.forEach { (key, value) ->
            exchange.responseHeaders.add(key, value)
        }
        response.cookies.forEach { cookie ->
            exchange.responseHeaders.add("Set-Cookie", cookie.headerValue())
        }
        val bytes = response.contentBytes ?: response.content.toByteArray(Charsets.UTF_8)
        val length = if (response.status == 204 || response.status == 304) -1 else bytes.size.toLong()
        exchange.sendResponseHeaders(response.status, length)
        if (length > 0) {
            exchange.responseBody.use { it.write(bytes) }
        } else {
            exchange.responseBody.close()
        }
    }
}
