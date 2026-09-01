package io.kotlimo.view

import io.kotlimo.support.Str
import java.nio.file.Files
import java.nio.file.Path

sealed class Node {
    data class Text(val value: String) : Node()
    data class Echo(val expression: String, val escape: Boolean) : Node()
    data class If(val expression: String, val thenBody: List<Node>, val elseBody: List<Node>) : Node()
    data class ForEach(val collection: String, val item: String, val body: List<Node>) : Node()
    data class Yield(val name: String) : Node()
    data class Include(val name: String) : Node()
    data class Extends(val name: String) : Node()
    data class Section(val name: String, val body: List<Node>) : Node()
}

class ViewEngine(private val paths: MutableList<Path> = mutableListOf()) {
    private val cache = mutableMapOf<String, List<Node>>()
    var extension: String = ".kote"

    fun addLocation(path: Path) {
        paths.add(path)
    }

    fun exists(name: String): Boolean = find(name) != null

    fun render(name: String, data: Map<String, Any?> = emptyMap()): String {
        val source = load(name)
        val ast = cache.getOrPut(name) { Parser(source).parse() }
        val sections = mutableMapOf<String, List<Node>>()
        val layout = ast.filterIsInstance<Node.Extends>().firstOrNull()
        ast.filterIsInstance<Node.Section>().forEach { sections[it.name] = it.body }
        val tree = if (layout != null) {
            val parent = cache.getOrPut(layout.name) { Parser(load(layout.name)).parse() }
            parent
        } else {
            ast.filterNot { it is Node.Extends || it is Node.Section }
        }
        return Renderer(this, data, sections).render(tree)
    }

    fun load(name: String): String {
        val file = find(name) ?: throw ViewNotFoundException(name)
        return Files.readString(file)
    }

    fun find(name: String): Path? {
        val relative = name.replace('.', '/') + extension
        return paths.asSequence()
            .map { it.resolve(relative) }
            .firstOrNull { Files.isRegularFile(it) }
    }

    fun clearCache() = cache.clear()
}

class ViewNotFoundException(name: String) : RuntimeException("View [$name] not found.")

internal class Parser(source: String) {
    private val tokens = tokenize(source)
    private var index = 0

    fun parse(): List<Node> = parseUntil()

    private fun parseUntil(vararg stoppers: String): List<Node> {
        val nodes = mutableListOf<Node>()
        while (index < tokens.size) {
            val token = tokens[index]
            if (token is Token.Directive && token.name in stoppers) break
            index++
            nodes += when (token) {
                is Token.Text -> Node.Text(token.value)
                is Token.Echo -> Node.Echo(token.expression, token.escape)
                is Token.Directive -> parseDirective(token)
            }
        }
        return nodes
    }

    private fun parseDirective(token: Token.Directive): Node {
        return when (token.name) {
            "if" -> {
                val thenBody = parseUntil("elseif", "else", "endif")
                val elseBody = mutableListOf<Node>()
                while (index < tokens.size && tokens[index] is Token.Directive) {
                    val current = tokens[index] as Token.Directive
                    if (current.name == "endif") {
                        index++
                        break
                    }
                    if (current.name == "else") {
                        index++
                        elseBody += parseUntil("endif")
                        if (index < tokens.size && (tokens[index] as? Token.Directive)?.name == "endif") index++
                        break
                    }
                    if (current.name == "elseif") {
                        index++
                        val nested = parseDirective(current)
                        elseBody += nested
                        break
                    }
                    break
                }
                Node.If(token.expression, thenBody, elseBody)
            }
            "foreach" -> {
                val (collection, item) = parseForeach(token.expression)
                val body = parseUntil("endforeach")
                if (index < tokens.size && (tokens[index] as? Token.Directive)?.name == "endforeach") index++
                Node.ForEach(collection, item, body)
            }
            "section" -> {
                val body = parseUntil("endsection")
                if (index < tokens.size && (tokens[index] as? Token.Directive)?.name == "endsection") index++
                Node.Section(unquote(token.expression), body)
            }
            "yield" -> Node.Yield(unquote(token.expression))
            "include" -> Node.Include(unquote(token.expression))
            "extends" -> Node.Extends(unquote(token.expression))
            else -> Node.Text("")
        }
    }

    private fun parseForeach(expression: String): Pair<String, String> {
        val parts = expression.split(Regex("\\s+as\\s+"), limit = 2)
        require(parts.size == 2) { "Invalid @foreach expression: $expression" }
        return parts[0].trim() to parts[1].trim()
    }

    private fun unquote(value: String): String = value.trim().removeSurrounding("'").removeSurrounding("\"")
}

private sealed class Token {
    data class Text(val value: String) : Token()
    data class Echo(val expression: String, val escape: Boolean) : Token()
    data class Directive(val name: String, val expression: String) : Token()
}

private fun tokenize(source: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var remaining = source
    while (remaining.isNotEmpty()) {
        val start = remaining.indexOf("@verbatim")
        if (start == -1) {
            tokens += tokenizeDirectives(remaining)
            break
        }
        if (start > 0) {
            tokens += tokenizeDirectives(remaining.substring(0, start))
        }
        val after = remaining.substring(start + "@verbatim".length).removePrefix("\n")
        val end = after.indexOf("@endverbatim")
        if (end == -1) {
            tokens += Token.Text(remaining.substring(start))
            break
        }
        tokens += Token.Text(after.substring(0, end))
        remaining = after.substring(end + "@endverbatim".length).removePrefix("\n")
    }
    return tokens
}

private fun tokenizeDirectives(source: String): List<Token> {
    val tokens = mutableListOf<Token>()
    val regex = Regex("\\{\\{!?(.*?)}}|\\{!!(.*?)!!}|@(extends|section|endsection|yield|include|if|elseif|else|endif|foreach|endforeach)(?:\\((.*?)\\))?", RegexOption.DOT_MATCHES_ALL)
    var last = 0
    regex.findAll(source).forEach { match ->
        if (match.range.first > last) {
            tokens += Token.Text(source.substring(last, match.range.first))
        }
        when {
            match.value.startsWith("{!!") -> tokens += Token.Echo(match.groupValues[2].trim(), escape = false)
            match.value.startsWith("{{") -> {
                val expr = match.groupValues[1].trim().removePrefix("!")
                tokens += Token.Echo(expr.trim(), escape = !match.value.startsWith("{{!"))
            }
            else -> tokens += Token.Directive(match.groupValues[3], match.groupValues[4].orEmpty().trim())
        }
        last = match.range.last + 1
    }
    if (last < source.length) tokens += Token.Text(source.substring(last))
    return tokens
}

internal class Renderer(
    private val engine: ViewEngine,
    private val data: Map<String, Any?>,
    private val sections: Map<String, List<Node>>
) {
    fun render(nodes: List<Node>): String = buildString {
        nodes.forEach { node -> append(renderNode(node)) }
    }

    private fun renderNode(node: Node): String = when (node) {
        is Node.Text -> node.value
        is Node.Echo -> {
            val value = evaluate(node.expression)
            val text = value?.toString().orEmpty()
            if (node.escape) Str.escapeHtml(text) else text
        }
        is Node.If -> {
            if (truthy(evaluate(node.expression))) render(node.thenBody) else render(node.elseBody)
        }
        is Node.ForEach -> {
            val collection = iterate(evaluate(node.collection))
            buildString {
                collection.forEachIndexed { index, item ->
                    val nested = data.toMutableMap()
                    nested[node.item] = item
                    nested["loop"] = mapOf("index" to index, "first" to (index == 0), "last" to (index == collection.size - 1))
                    append(Renderer(engine, nested, sections).render(node.body))
                }
            }
        }
        is Node.Yield -> sections[node.name]?.let { render(it) }.orEmpty()
        is Node.Include -> engine.render(node.name, data)
        is Node.Extends -> ""
        is Node.Section -> ""
    }

    private fun evaluate(expression: String): Any? {
        val trimmed = expression.trim()
        return when {
            trimmed.equals("true", true) -> true
            trimmed.equals("false", true) -> false
            trimmed.equals("null", true) -> null
            trimmed.toIntOrNull() != null -> trimmed.toInt()
            trimmed.startsWith("\"") && trimmed.endsWith("\"") -> trimmed.removeSurrounding("\"")
            trimmed.startsWith("'") && trimmed.endsWith("'") -> trimmed.removeSurrounding("'")
            trimmed.contains("==") -> {
                val (left, right) = trimmed.split("==", limit = 2).map { evaluate(it.trim()) }
                left?.toString() == right?.toString()
            }
            trimmed.contains("!=") -> {
                val (left, right) = trimmed.split("!=", limit = 2).map { evaluate(it.trim()) }
                left?.toString() != right?.toString()
            }
            else -> resolvePath(data, trimmed)
        }
    }

    private fun truthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is String -> value.isNotBlank()
        is Number -> value.toDouble() != 0.0
        is Collection<*> -> value.isNotEmpty()
        else -> true
    }

    private fun resolvePath(data: Map<String, Any?>, path: String): Any? {
        var current: Any? = data
        for (segment in path.split('.')) {
            current = when (current) {
                is Map<*, *> -> current[segment]
                is io.kotlimo.support.Collection<*> -> {
                    segment.toIntOrNull()?.let { current.toList().getOrNull(it) }
                }
                null -> null
                else -> {
                    val getter = current.javaClass.methods.firstOrNull {
                        it.parameterCount == 1 && it.name == "get" && it.parameterTypes[0] == String::class.java
                    }
                    getter?.invoke(current, segment) ?: run {
                        val bean = current.javaClass.methods.firstOrNull {
                            it.parameterCount == 0 && it.name.equals("get${segment.replaceFirstChar { c -> c.uppercase() }}", ignoreCase = true)
                        }
                        bean?.invoke(current)
                    }
                }
            }
        }
        return current
    }

    private fun iterate(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is Collection<*> -> value.toList()
        is Array<*> -> value.toList()
        is Map<*, *> -> value.entries.toList()
        is io.kotlimo.support.Collection<*> -> value.toList()
        else -> listOf(value)
    }
}
