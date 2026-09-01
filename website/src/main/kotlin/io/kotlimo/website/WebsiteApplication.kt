package io.kotlimo.website

import io.kotlimo.foundation.Application
import io.kotlimo.foundation.run
import io.kotlimo.foundation.withConsole
import io.kotlimo.foundation.withDefaultProviders
import io.kotlimo.website.console.SiteExportCommand
import io.kotlimo.website.http.registerWebRoutes
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val app = createWebsite()
    val command = if (args.isEmpty()) arrayOf("serve") else args
    exitProcess(app.run(command))
}

fun createWebsite(basePath: String = detectBasePath()): Application {
    val app = Application.create(basePath).withDefaultProviders()
    app.boot()
    registerWebRoutes(app)
    app.withConsole().register(SiteExportCommand())
    return app
}

fun detectBasePath(): String {
    val userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
    val candidates = listOf(
        userDir,
        userDir.resolve("website"),
        userDir.parent.resolve("website")
    )
    return candidates.firstOrNull { Files.isDirectory(it.resolve("resources").resolve("views")) }?.toString()
        ?: userDir.toString()
}
