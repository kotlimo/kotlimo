package io.kotlimo.cache

import io.kotlimo.support.Json
import redis.clients.jedis.JedisPool

interface KeyValue {
    fun get(key: String): String?
    fun set(key: String, value: String, seconds: Long?)
    fun del(key: String): Boolean
    fun flush()
    fun lpush(key: String, value: String)
    fun rpop(key: String): String?
}

class MemoryKeyValue : KeyValue {
    private val values = mutableMapOf<String, String>()
    private val lists = mutableMapOf<String, ArrayDeque<String>>()

    override fun get(key: String): String? = values[key]

    override fun set(key: String, value: String, seconds: Long?) {
        values[key] = value
    }

    override fun del(key: String): Boolean = values.remove(key) != null

    override fun flush() {
        values.clear()
        lists.clear()
    }

    override fun lpush(key: String, value: String) {
        lists.getOrPut(key) { ArrayDeque() }.addFirst(value)
    }

    override fun rpop(key: String): String? = lists[key]?.removeLastOrNull()
}

class JedisKeyValue(uri: String) : KeyValue {
    private val pool = JedisPool(uri)

    override fun get(key: String): String? = pool.resource.use { it.get(key) }

    override fun set(key: String, value: String, seconds: Long?) {
        pool.resource.use { jedis ->
            if (seconds == null) jedis.set(key, value) else jedis.setex(key, seconds, value)
        }
    }

    override fun del(key: String): Boolean = pool.resource.use { it.del(key) > 0 }

    override fun flush() {
        pool.resource.use { it.flushDB() }
    }

    override fun lpush(key: String, value: String) {
        pool.resource.use { it.lpush(key, value) }
    }

    override fun rpop(key: String): String? = pool.resource.use { it.rpop(key) }
}

class RedisStore(private val keys: KeyValue, private val prefix: String = "kotlimo:") : CacheStore {
    override fun get(key: String): Any? {
        val raw = keys.get(prefix + key) ?: return null
        return Json.decode(raw)
    }

    override fun put(key: String, value: Any?, seconds: Long?) {
        keys.set(prefix + key, Json.encode(value), seconds)
    }

    override fun forget(key: String): Boolean = keys.del(prefix + key)

    override fun flush() = keys.flush()
}
