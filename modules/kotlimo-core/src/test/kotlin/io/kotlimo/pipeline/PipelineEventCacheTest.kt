package io.kotlimo.pipeline

import io.kotlimo.cache.ArrayStore
import io.kotlimo.cache.CacheRepository
import io.kotlimo.events.Dispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PipelineTest {
    @Test
    fun `pipes wrap the destination in registration order`() {
        val log = mutableListOf<String>()
        val result = Pipeline<String, String>()
            .send("payload")
            .through(
                listOf(
                    { passable, next ->
                        log += "one"
                        next(passable)
                    },
                    { passable, next ->
                        log += "two"
                        next(passable).uppercase()
                    }
                )
            )
            .then { it + "!" }
        assertEquals("PAYLOAD!", result)
        assertEquals(listOf("one", "two"), log)
    }
}

class DispatcherTest {
    data class UserRegistered(val email: String)

    @Test
    fun `dispatches typed listeners`() {
        val dispatcher = Dispatcher()
        val received = mutableListOf<String>()
        dispatcher.listen(UserRegistered::class) { received += it.email }
        dispatcher.dispatch(UserRegistered("ada@example.com"))
        assertEquals(listOf("ada@example.com"), received)
    }

    @Test
    fun `wildcard listeners receive every event`() {
        val dispatcher = Dispatcher()
        var count = 0
        dispatcher.listen("*") { count++ }
        dispatcher.dispatch("first")
        dispatcher.dispatch(1)
        assertEquals(2, count)
    }
}

class CacheTest {
    @Test
    fun `remember stores computed values`() {
        val cache = CacheRepository(ArrayStore())
        var hits = 0
        val first = cache.remember("answer", 60) {
            hits++
            42
        }
        val second = cache.remember("answer", 60) {
            hits++
            99
        }
        assertEquals(42, first)
        assertEquals(42, second)
        assertEquals(1, hits)
    }

    @Test
    fun `expired items are forgotten`() {
        val store = ArrayStore()
        store.put("temp", "value", seconds = 0)
        Thread.sleep(5)
        assertNull(store.get("temp"))
    }

    @Test
    fun `increment mutates numeric keys`() {
        val cache = CacheRepository(ArrayStore())
        assertEquals(1, cache.increment("visits"))
        assertEquals(3, cache.increment("visits", 2))
        assertEquals(2, cache.decrement("visits"))
    }
}
