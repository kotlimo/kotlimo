package io.kotlimo.config

import java.nio.file.Files
import java.nio.file.Path

class Environment(private val values: MutableMap<String, String> = mutableMapOf()) {
    fun get(key: String, default: String? = null): String? =
        values[key] ?: System.getenv(key) ?: default

    fun require(key: String): String =
        get(key) ?: throw IllegalStateException("Environment variable [$key] is not defined")

    fun boolean(key: String, default: Boolean = false): Boolean {
        val raw = get(key) ?: return default
        return raw.lowercase() in setOf("1", "true", "yes", "on")
    }

    fun int(key: String, default: Int = 0): Int = get(key)?.toIntOrNull() ?: default

    fun set(key: String, value: String) {
        values[key] = value
    }

    fun has(key: String): Boolean = values.containsKey(key) || System.getenv(key) != null

    fun all(): Map<String, String> = values.toMap()

    companion object {
        fun load(path: Path): Environment {
            val env = Environment()
            if (!Files.exists(path)) return env
            Files.readAllLines(path).forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val cleaned = line.removePrefix("export ").trim()
                val idx = cleaned.indexOf('=')
                if (idx <= 0) return@forEach
                val key = cleaned.substring(0, idx).trim()
                var value = cleaned.substring(idx + 1).trim()
                if (
                    (value.startsWith('"') && value.endsWith('"')) ||
                    (value.startsWith('\'') && value.endsWith('\''))
                ) {
                    value = value.substring(1, value.length - 1)
                }
                env.set(key, value)
            }
            return env
        }
    }
}
