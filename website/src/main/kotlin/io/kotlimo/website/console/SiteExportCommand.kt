package io.kotlimo.website.console

import io.kotlimo.console.Command
import io.kotlimo.http.HttpKernel
import io.kotlimo.http.Request
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

class SiteExportCommand : Command() {
    override val signature = "site:export {--path=}"
    override val description = "Export the website as static HTML for GitHub Pages"

    private val docPages = listOf(
        "installation",
        "routing",
        "http",
        "container",
        "views",
        "validation",
        "database",
        "artisan"
    )

    override fun handle(): Int {
        val defaultPath = app.basePath.parent.resolve("docs")
        val raw = option("path").orEmpty()
        val out = Path.of(raw.ifBlank { defaultPath.toString() }).toAbsolutePath().normalize()
        Files.createDirectories(out)

        val kernel = app.make(HttpKernel::class)
        val pages = buildList {
            add("/" to "index.html")
            add("/docs" to "docs/index.html")
            add("/404.html" to "404.html")
            docPages.forEach { slug ->
                add("/docs/$slug" to "docs/$slug/index.html")
            }
        }

        pages.forEach { (uri, relative) ->
            val response = kernel.handle(Request.get(uri))
            val target = out.resolve(relative)
            Files.createDirectories(target.parent)
            Files.writeString(target, response.content)
            info("Wrote $relative (${response.status})")
        }

        copyPublicAssets(out)
        Files.writeString(out.resolve(".nojekyll"), "")
        Files.writeString(out.resolve("CNAME"), "kotlimo.github.io\n")
        info("Exported static site to $out")
        return 0
    }

    private fun copyPublicAssets(out: Path) {
        val publicPath = app.publicPath()
        if (!Files.isDirectory(publicPath)) return
        Files.walk(publicPath).use { stream ->
            stream.filter { it.isRegularFile() }.forEach { file ->
                val relative = file.relativeTo(publicPath)
                val target = out.resolve(relative.toString())
                Files.createDirectories(target.parent)
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
