package io.kotlimo.support

object Arr {
    fun get(array: Map<String, Any?>, key: String, default: Any? = null): Any? {
        if (!key.contains('.')) {
            return if (array.containsKey(key)) array[key] else default
        }
        var current: Any? = array
        for (segment in key.split('.')) {
            current = when (current) {
                is Map<*, *> -> current[segment]
                else -> return default
            }
        }
        return current ?: default
    }

    @Suppress("UNCHECKED_CAST")
    fun set(array: MutableMap<String, Any?>, key: String, value: Any?) {
        if (!key.contains('.')) {
            array[key] = value
            return
        }
        val segments = key.split('.')
        var current: MutableMap<String, Any?> = array
        for (i in 0 until segments.lastIndex) {
            val segment = segments[i]
            val next = current[segment]
            if (next !is MutableMap<*, *>) {
                val nested = mutableMapOf<String, Any?>()
                current[segment] = nested
                current = nested
            } else {
                current = next as MutableMap<String, Any?>
            }
        }
        current[segments.last()] = value
    }

    fun has(array: Map<String, Any?>, key: String): Boolean {
        if (!key.contains('.')) return array.containsKey(key)
        var current: Any? = array
        for (segment in key.split('.')) {
            if (current !is Map<*, *> || !current.containsKey(segment)) return false
            current = current[segment]
        }
        return true
    }

    fun except(array: Map<String, Any?>, keys: Iterable<String>): Map<String, Any?> =
        array.filterKeys { it !in keys }

    fun only(array: Map<String, Any?>, keys: Iterable<String>): Map<String, Any?> =
        array.filterKeys { it in keys }

    fun wrap(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is List<*> -> value
        is Array<*> -> value.toList()
        else -> listOf(value)
    }

    fun flatten(array: Iterable<Any?>, depth: Int = Int.MAX_VALUE): List<Any?> {
        if (depth <= 0) return array.toList()
        val result = mutableListOf<Any?>()
        for (item in array) {
            if (item is Iterable<*> && item !is String) {
                @Suppress("UNCHECKED_CAST")
                result.addAll(flatten(item as Iterable<Any?>, depth - 1))
            } else {
                result.add(item)
            }
        }
        return result
    }

    fun first(array: Iterable<Any?>, predicate: ((Any?) -> Boolean)? = null): Any? =
        if (predicate == null) array.firstOrNull() else array.firstOrNull(predicate)

    fun last(array: Iterable<Any?>): Any? = array.lastOrNull()
}
