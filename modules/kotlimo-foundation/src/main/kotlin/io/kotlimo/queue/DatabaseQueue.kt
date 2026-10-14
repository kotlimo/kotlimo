package io.kotlimo.queue

import io.kotlimo.database.Connection
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class DatabaseQueue(
    private val connection: () -> Connection,
    private val table: String = "jobs"
) : QueueDriver {
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
        connection().table(table).insert(
            mapOf(
                "name" to named.name,
                "payload" to named.payload,
                "available_at" to Instant.now().epochSecond
            )
        )
    }

    override fun later(seconds: Long, job: Job) {
        val named = job as? NamedJob ?: return push(job)
        connection().table(table).insert(
            mapOf(
                "name" to named.name,
                "payload" to named.payload,
                "available_at" to Instant.now().plusSeconds(seconds).epochSecond
            )
        )
    }

    override fun pop(): Job? {
        val now = Instant.now().epochSecond
        val row = connection().table(table).where("available_at", "<=", now).orderBy("id").first() ?: return null
        connection().table(table).where("id", row["id"]).delete()
        val name = row["name"]?.toString().orEmpty()
        val payload = row["payload"]?.toString().orEmpty()
        return Job {
            val listener = listeners[name]
                ?: throw IllegalStateException("No queue listener for [$name]. Call DatabaseQueue.listen(\"$name\") { }.")
            listener(payload)
        }
    }

    override fun size(): Int = connection().table(table).count().toInt()
}
