package io.kotlimo.queue

import java.util.concurrent.ConcurrentHashMap

data class NamedJob(val name: String, val payload: String = "") : Job {
    override fun handle() = Unit
}

class RedisQueue(private val keys: io.kotlimo.cache.KeyValue, private val key: String = "kotlimo:queue") : QueueDriver {
    private val listeners = ConcurrentHashMap<String, (String) -> Unit>()

    fun listen(name: String, handler: (String) -> Unit) {
        listeners[name] = handler
    }

    override fun push(job: Job) {
        val named = job as? NamedJob
        if (named == null) {
            job.handle()
            return
        }
        keys.lpush(key, "${named.name}\n${named.payload}")
    }

    override fun later(seconds: Long, job: Job) = push(job)

    override fun pop(): Job? {
        val raw = keys.rpop(key) ?: return null
        val name = raw.substringBefore('\n')
        val payload = raw.substringAfter('\n', "")
        return Job {
            listeners[name]?.invoke(payload)
                ?: throw IllegalStateException("No queue listener for [$name]. Call RedisQueue.listen(\"$name\") { }.")
        }
    }

    override fun size(): Int = 0
}
