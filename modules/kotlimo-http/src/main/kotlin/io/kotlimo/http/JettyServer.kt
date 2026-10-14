package io.kotlimo.http

import jakarta.servlet.MultipartConfigElement
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.net.InetSocketAddress

class JettyServer(
    private val kernel: HttpKernel,
    private val host: String = "127.0.0.1",
    private val port: Int = 8000
) {
    private var server: Server? = null

    fun start(block: Boolean = false) {
        val jetty = Server()
        val connector = ServerConnector(jetty)
        connector.host = host
        connector.port = port
        jetty.addConnector(connector)
        val context = ServletContextHandler(ServletContextHandler.NO_SESSIONS)
        context.contextPath = "/"
        val holder = ServletHolder(KotlimoServlet(kernel))
        holder.registration.setMultipartConfig(MultipartConfigElement(System.getProperty("java.io.tmpdir")))
        context.addServlet(holder, "/*")
        jetty.handler = context
        jetty.start()
        server = jetty
        if (block) jetty.join()
    }

    fun stop() {
        server?.stop()
        server = null
    }

    fun port(): Int = (server?.connectors?.firstOrNull() as? ServerConnector)?.localPort ?: port

    fun address(): InetSocketAddress = InetSocketAddress(host, port())
}

private class KotlimoServlet(private val kernel: HttpKernel) : HttpServlet() {
    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        val kotlimo = RequestFactory.fromServlet(request)
        val result = try {
            kernel.handle(kotlimo)
        } catch (e: Exception) {
            Response.html("<h1>Server Error</h1><p>${io.kotlimo.support.Str.escapeHtml(e.message ?: "Server Error")}</p>", 500)
        }
        result.headers.forEach { (key, value) -> response.setHeader(key, value) }
        result.cookies.forEach { response.addHeader("Set-Cookie", it.headerValue()) }
        response.status = result.status
        val bytes = result.contentBytes ?: result.content.toByteArray(Charsets.UTF_8)
        if (result.status != 204 && result.status != 304) {
            response.outputStream.use { it.write(bytes) }
        }
    }
}
