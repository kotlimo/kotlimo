package io.kotlimo.cache

interface CacheStore {
    fun get(key: String): Any?
    fun put(key: String, value: Any?, seconds: Long? = null)
    fun forever(key: String, value: Any?) = put(key, value, null)
    fun forget(key: String): Boolean
    fun flush()
    fun has(key: String): Boolean = get(key) != null
}

class ArrayStore : CacheStore {
    private data class Entry(val value: Any?, val expiresAt: Long?)

    private val items = mutableMapOf<String, Entry>()

    override fun get(key: String): Any? {
        val entry = items[key] ?: return null
        if (entry.expiresAt != null && entry.expiresAt < System.currentTimeMillis()) {
            items.remove(key)
            return null
        }
        return entry.value
    }

    override fun put(key: String, value: Any?, seconds: Long?) {
        val expiresAt = seconds?.let { System.currentTimeMillis() + it * 1000 }
        items[key] = Entry(value, expiresAt)
    }

    override fun forget(key: String): Boolean = items.remove(key) != null

    override fun flush() = items.clear()
}

class CacheRepository(private val store: CacheStore) {
    fun get(key: String, default: Any? = null): Any? = store.get(key) ?: default

    fun put(key: String, value: Any?, seconds: Long = 3600) = store.put(key, value, seconds)

    fun forever(key: String, value: Any?) = store.forever(key, value)

    fun forget(key: String): Boolean = store.forget(key)

    fun flush() = store.flush()

    fun has(key: String): Boolean = store.has(key)

    fun increment(key: String, amount: Long = 1): Long {
        val current = (store.get(key) as? Number)?.toLong() ?: 0L
        val next = current + amount
        store.forever(key, next)
        return next
    }

    fun decrement(key: String, amount: Long = 1): Long = increment(key, -amount)

    fun <T> remember(key: String, seconds: Long, callback: () -> T): T {
        val cached = store.get(key)
        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            return cached as T
        }
        val value = callback()
        store.put(key, value, seconds)
        return value
    }

    fun <T> rememberForever(key: String, callback: () -> T): T = remember(key, Long.MAX_VALUE / 1000, callback)
}
