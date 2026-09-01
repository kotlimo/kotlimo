package io.kotlimo.queue

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

fun interface Job {
    fun handle()
}

data class QueueItem(
    val job: Job,
    val availableAt: Instant = Instant.now()
)

interface QueueDriver {
    fun push(job: Job)
    fun later(seconds: Long, job: Job)
    fun pop(): Job?
    fun size(): Int
}

class SyncQueue : QueueDriver {
    override fun push(job: Job) = job.handle()

    override fun later(seconds: Long, job: Job) = job.handle()

    override fun pop(): Job? = null

    override fun size(): Int = 0
}

class ArrayQueue : QueueDriver {
    private val items = ConcurrentLinkedQueue<QueueItem>()

    override fun push(job: Job) {
        items.add(QueueItem(job))
    }

    override fun later(seconds: Long, job: Job) {
        items.add(QueueItem(job, Instant.now().plusSeconds(seconds)))
    }

    override fun pop(): Job? {
        val now = Instant.now()
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (!item.availableAt.isAfter(now)) {
                iterator.remove()
                return item.job
            }
        }
        return null
    }

    override fun size(): Int = items.size

    fun pending(): List<QueueItem> = items.toList()

    fun flush() = items.clear()
}

class QueueManager(private val drivers: MutableMap<String, QueueDriver> = mutableMapOf()) {
    var default: String = "sync"

    fun add(name: String, driver: QueueDriver) {
        drivers[name] = driver
        if (drivers.size == 1) default = name
    }

    fun driver(name: String? = null): QueueDriver =
        drivers[name ?: default] ?: throw IllegalStateException("Queue driver [${name ?: default}] is not configured")

    fun push(job: Job) = driver().push(job)

    fun later(seconds: Long, job: Job) = driver().later(seconds, job)

    fun pop(): Job? = driver().pop()
}
