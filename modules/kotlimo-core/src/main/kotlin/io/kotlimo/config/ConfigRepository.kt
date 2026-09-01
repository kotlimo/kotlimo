package io.kotlimo.config

import io.kotlimo.support.Arr
import java.nio.file.Files
import java.nio.file.Path

class ConfigRepository(private val items: MutableMap<String, Any?> = mutableMapOf()) {
    fun get(key: String, default: Any? = null): Any? = Arr.get(items, key, default)

    fun set(key: String, value: Any?) = Arr.set(items, key, value)

    fun has(key: String): Boolean = Arr.has(items, key)

    fun all(): Map<String, Any?> = items.toMap()

    fun string(key: String, default: String = ""): String = get(key, default)?.toString() ?: default

    fun int(key: String, default: Int = 0): Int = when (val value = get(key, default)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }

    fun boolean(key: String, default: Boolean = false): Boolean = when (val value = get(key, default)) {
        is Boolean -> value
        is String -> value.lowercase() in setOf("1", "true", "yes", "on")
        is Number -> value.toInt() != 0
        else -> default
    }

    fun long(key: String, default: Long = 0): Long = when (val value = get(key, default)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: default
        else -> default
    }

    fun map(key: String): Map<String, Any?> {
        val value = get(key)
        @Suppress("UNCHECKED_CAST")
        return (value as? Map<String, Any?>) ?: emptyMap()
    }

    fun load(name: String, values: Map<String, Any?>) {
        items[name] = values.toMutableMap()
    }

    companion object {
        fun fromMaps(documents: Map<String, Map<String, Any?>>): ConfigRepository {
            val repo = ConfigRepository()
            documents.forEach { (name, values) -> repo.load(name, values) }
            return repo
        }

        fun loadDirectory(directory: Path): ConfigRepository {
            val repo = ConfigRepository()
            if (!Files.isDirectory(directory)) return repo
            Files.list(directory).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    val name = file.fileName.toString().substringBeforeLast('.')
                    val ext = file.fileName.toString().substringAfterLast('.', "")
                    val content = Files.readString(file)
                    when (ext) {
                        "json" -> {
                            @Suppress("UNCHECKED_CAST")
                            val decoded = io.kotlimo.support.Json.decode(content) as? Map<String, Any?> ?: emptyMap()
                            repo.load(name, decoded)
                        }
                        "properties" -> {
                            val props = java.util.Properties()
                            props.load(content.reader())
                            val map = props.entries.associate { it.key.toString() to it.value }
                            repo.load(name, map)
                        }
                    }
                }
            }
            return repo
        }
    }
}
