package io.kotlimo.support

object Str {
    fun camel(value: String): String {
        val studly = studly(value)
        return studly.replaceFirstChar { it.lowercase() }
    }

    fun studly(value: String): String =
        value.split(Regex("[\\s_-]+"))
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }

    fun snake(value: String, delimiter: Char = '_'): String {
        val withoutAcronyms = value.replace(Regex("([a-z0-9])([A-Z])"), "$1$delimiter$2")
        return withoutAcronyms.replace(Regex("[\\s-]+"), delimiter.toString()).lowercase()
    }

    fun kebab(value: String): String = snake(value, '-')

    fun slug(value: String, separator: String = "-"): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), separator)
            .trim(separator.first())

    fun contains(haystack: String, needle: String, ignoreCase: Boolean = false): Boolean =
        haystack.contains(needle, ignoreCase)

    fun startsWith(value: String, prefix: String): Boolean = value.startsWith(prefix)

    fun endsWith(value: String, suffix: String): Boolean = value.endsWith(suffix)

    fun limit(value: String, limit: Int, end: String = "..."): String =
        if (value.length <= limit) value else value.take(limit) + end

    fun random(length: Int = 16): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { alphabet.random() }.joinToString("")
    }

    fun after(subject: String, search: String): String {
        val index = subject.indexOf(search)
        return if (index == -1) subject else subject.substring(index + search.length)
    }

    fun before(subject: String, search: String): String {
        val index = subject.indexOf(search)
        return if (index == -1) subject else subject.substring(0, index)
    }

    fun finish(value: String, cap: String): String =
        if (value.endsWith(cap)) value else value + cap

    fun headline(value: String): String =
        snake(value).split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    fun isEmpty(value: String?): Boolean = value.isNullOrEmpty()

    fun uuid(): String = java.util.UUID.randomUUID().toString()

    fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
}
