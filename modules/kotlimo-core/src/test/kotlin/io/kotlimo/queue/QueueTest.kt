package io.kotlimo.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QueueTest {
    class CounterJob(private val hits: MutableList<String>) : Job {
        override fun handle() {
            hits += "ran"
        }
    }

    @Test
    fun `sync queue runs immediately`() {
        val hits = mutableListOf<String>()
        SyncQueue().push(CounterJob(hits))
        assertEquals(listOf("ran"), hits)
    }

    @Test
    fun `array queue defers until pop`() {
        val hits = mutableListOf<String>()
        val queue = ArrayQueue()
        queue.push(CounterJob(hits))
        assertEquals(1, queue.size())
        assertEquals(emptyList<String>(), hits)
        queue.pop()?.handle()
        assertEquals(listOf("ran"), hits)
        assertNull(queue.pop())
    }

    @Test
    fun `queue manager dispatches through the default driver`() {
        val hits = mutableListOf<String>()
        val manager = QueueManager()
        manager.add("sync", SyncQueue())
        manager.push(CounterJob(hits))
        assertEquals(listOf("ran"), hits)
    }
}
