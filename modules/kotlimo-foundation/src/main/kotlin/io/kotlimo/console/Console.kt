package io.kotlimo.console

import io.kotlimo.foundation.Application
import io.kotlimo.http.HttpKernel
import io.kotlimo.http.JettyServer
import java.nio.file.Files

abstract class Command {
    abstract val signature: String
    abstract val description: String
    lateinit var app: Application
    var arguments: Map<String, String> = emptyMap()

    abstract fun handle(): Int

    fun argument(name: String, default: String? = null): String? = arguments[name] ?: default

    fun option(name: String, default: String? = null): String? = arguments["--$name"] ?: default

    fun line(message: String) = println(message)

    fun info(message: String) = println(message)

    fun error(message: String) = System.err.println(message)
}

class ConsoleKernel(private val app: Application) {
    private val commands = linkedMapOf<String, Command>()

    fun register(command: Command) {
        command.app = app
        commands[command.signature.substringBefore(' ')] = command
    }

    fun commands(): Map<String, Command> = commands.toMap()

    fun handle(args: Array<String>): Int {
        if (args.isEmpty() || args[0] in setOf("list", "help", "--help", "-h")) {
            return HelpCommand(commands).also { it.app = app }.handle()
        }
        val name = args[0]
        val command = commands[name] ?: run {
            System.err.println("Command [$name] is not defined.")
            return 1
        }
        command.arguments = parseArgs(command.signature, args.drop(1))
        return command.handle()
    }

    private fun parseArgs(signature: String, args: List<String>): Map<String, String> {
        val values = mutableMapOf<String, String>()
        val positional = mutableListOf<String>()
        args.forEach { arg ->
            if (arg.startsWith("--")) {
                val body = arg.removePrefix("--")
                val key = body.substringBefore('=')
                val value = if (body.contains('=')) body.substringAfter('=') else "true"
                values["--$key"] = value
            } else {
                positional += arg
            }
        }
        Regex("\\{([^}]+)\\}").findAll(signature).forEachIndexed { index, match ->
            val raw = match.groupValues[1]
            if (raw.startsWith("--")) {
                val name = raw.removePrefix("--").substringBefore('=')
                val default = if (raw.contains('=')) raw.substringAfter('=') else null
                if ("--$name" !in values && default != null) values["--$name"] = default
            } else {
                val name = raw.removeSuffix("?")
                if (index < positional.size) values[name] = positional[index]
            }
        }
        return values
    }
}

class HelpCommand(private val commands: Map<String, Command>) : Command() {
    override val signature = "list"
    override val description = "List all available commands"

    override fun handle(): Int {
        line("Kotlimo Framework 0.1.0")
        line("")
        line("Available commands:")
        commands.forEach { (name, command) ->
            line("  ${name.padEnd(22)} ${command.description}")
        }
        return 0
    }
}

class ServeCommand : Command() {
    override val signature = "serve {--host=127.0.0.1} {--port=8000}"
    override val description = "Serve the application on the Kotlin development server"

    override fun handle(): Int {
        val host = option("host", "127.0.0.1") ?: "127.0.0.1"
        val port = option("port", "8000")?.toIntOrNull() ?: 8000
        line("Kotlimo development server started: http://$host:$port")
        val kernel = app.make(HttpKernel::class)
        JettyServer(kernel, host, port).start(block = true)
        return 0
    }
}

class RouteListCommand : Command() {
    override val signature = "route:list"
    override val description = "List all registered routes"

    override fun handle(): Int {
        val router = app.make(io.kotlimo.routing.Router::class)
        line(String.format("%-8s %-32s %-24s %s", "METHOD", "URI", "NAME", "ACTION"))
        router.routes().forEach { route ->
            val action = when (val item = route.action) {
                is io.kotlimo.routing.RouteAction.Closure -> "Closure"
                is io.kotlimo.routing.RouteAction.ControllerMethod -> "${item.controller.simpleName}@${item.method}"
            }
            line(String.format("%-8s %-32s %-24s %s", route.methods.joinToString("|"), route.uri, route.name ?: "", action))
        }
        return 0
    }
}

class MakeControllerCommand : Command() {
    override val signature = "make:controller {name}"
    override val description = "Create a new controller class"

    override fun handle(): Int {
        val name = argument("name") ?: run {
            error("Controller name is required. Run make:controller {name}.")
            return 1
        }
        val modern = usesAppLayout()
        val file = if (modern) {
            kotlinFile("app", "http", "controllers", "$name.kt")
        } else {
            kotlinFile("controllers", "$name.kt")
        }
        if (Files.exists(file)) {
            error("Controller already exists: $file")
            return 1
        }
        val pkg = if (modern) "package app.http.controllers\n\n" else ""
        val content = """
            ${pkg}class $name {
                fun index(request: io.kotlimo.http.Request): io.kotlimo.http.Response {
                    return io.kotlimo.http.Response.json(mapOf("message" to "$name"))
                }
            }
        """.trimIndent()
        Files.writeString(file, content)
        info("Controller created: $file")
        if (modern) {
            val test = writeFeatureTest(name)
            info("Feature test created: $test")
        }
        return 0
    }
}

class MakeModelCommand : Command() {
    override val signature = "make:model {name}"
    override val description = "Create a new model class"

    override fun handle(): Int {
        val name = argument("name") ?: run {
            error("Model name is required. Run make:model {name}.")
            return 1
        }
        val modern = usesAppLayout()
        val file = if (modern) kotlinFile("app", "models", "$name.kt") else kotlinFile("models", "$name.kt")
        if (Files.exists(file)) {
            error("Model already exists: $file")
            return 1
        }
        val table = io.kotlimo.support.Inflector.tableize(name)
        val pkg = if (modern) "package app.models\n\n" else ""
        val content = """
            ${pkg}import io.kotlimo.database.Model
            import io.kotlimo.database.ModelCompanion

            class $name : Model() {
                override val table = "$table"
                override val fillable = listOf<String>()

                companion object : ModelCompanion<$name>($name::class)
            }
        """.trimIndent()
        Files.writeString(file, content)
        info("Model created: $file")
        return 0
    }
}

class MakeRequestCommand : Command() {
    override val signature = "make:request {name}"
    override val description = "Create a form request rules class"

    override fun handle(): Int {
        val name = argument("name") ?: run {
            error("Request name is required. Run make:request {name}.")
            return 1
        }
        val modern = usesAppLayout()
        val file = if (modern) kotlinFile("app", "http", "requests", "$name.kt") else kotlinFile("requests", "$name.kt")
        if (Files.exists(file)) {
            error("Request already exists: $file")
            return 1
        }
        val pkg = if (modern) "package app.http.requests\n\n" else ""
        val content = """
            ${pkg}class $name {
                fun rules(): Map<String, String> = mapOf(
                    "title" to "required|string|max:255"
                )
            }
        """.trimIndent()
        Files.writeString(file, content)
        info("Request created: $file")
        return 0
    }
}

class MakePolicyCommand : Command() {
    override val signature = "make:policy {name}"
    override val description = "Create a policy class"

    override fun handle(): Int {
        val name = argument("name") ?: run {
            error("Policy name is required. Run make:policy {name}.")
            return 1
        }
        val modern = usesAppLayout()
        val file = if (modern) kotlinFile("app", "policies", "$name.kt") else kotlinFile("policies", "$name.kt")
        if (Files.exists(file)) {
            error("Policy already exists: $file")
            return 1
        }
        val pkg = if (modern) "package app.policies\n\n" else ""
        val content = """
            ${pkg}import io.kotlimo.auth.Authenticatable
            import io.kotlimo.auth.Policy

            class $name : Policy {
                override fun allows(user: Authenticatable?, ability: String, model: Any?): Boolean {
                    return user != null
                }
            }
        """.trimIndent()
        Files.writeString(file, content)
        info("Policy created: $file")
        return 0
    }
}

private fun Command.usesAppLayout(): Boolean =
    Files.exists(app.basePath.resolve("routes").resolve("web.kt"))

private fun Command.kotlinFile(vararg parts: String): java.nio.file.Path {
    val file = parts.fold(app.path("src", "main", "kotlin")) { path, part -> path.resolve(part) }
    Files.createDirectories(file.parent)
    return file
}

private fun Command.writeFeatureTest(controller: String): java.nio.file.Path {
    val file = app.path("tests").resolve("${controller}Test.kt")
    Files.createDirectories(file.parent)
    val content = """
        package app

        import io.kotlimo.testing.HttpTestCase
        import org.junit.jupiter.api.BeforeEach
        import org.junit.jupiter.api.Test

        class ${controller}Test : HttpTestCase() {
            @BeforeEach
            fun setUp() {
                app = createApp()
            }

            @Test
            fun `home responds`() {
                get("/health").assertOk()
            }
        }
    """.trimIndent() + "\n"
    Files.writeString(file, content)
    return file
}

fun consoleKernel(app: Application): ConsoleKernel = ConsoleKernel(app).apply {
    register(HelpCommand(emptyMap()))
    register(ServeCommand())
    register(RouteListCommand())
    register(MakeControllerCommand())
    register(MakeModelCommand())
    register(MakeRequestCommand())
    register(MakePolicyCommand())
}
