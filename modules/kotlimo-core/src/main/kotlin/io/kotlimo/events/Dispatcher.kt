package io.kotlimo.events

import kotlin.reflect.KClass

class Dispatcher {
    private val listeners = mutableMapOf<String, MutableList<(Any) -> Unit>>()

    fun <T : Any> listen(event: KClass<T>, listener: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        listeners.getOrPut(key(event)) { mutableListOf() }.add(listener as (Any) -> Unit)
    }

    fun listen(event: String, listener: (Any) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(listener)
    }

    fun <T : Any> dispatch(event: T): T {
        val named = event as? Event
        if (named != null) {
            listeners[named.name]?.toList()?.forEach { it(event) }
        }
        listeners[key(event::class)]?.toList()?.forEach { it(event) }
        listeners["*"]?.toList()?.forEach { it(event) }
        return event
    }

    fun forget(event: String) {
        listeners.remove(event)
    }

    fun hasListeners(event: String): Boolean = listeners[event]?.isNotEmpty() == true

    private fun key(type: KClass<*>): String = type.qualifiedName ?: type.simpleName ?: type.toString()
}

interface Event {
    val name: String
        get() = this::class.qualifiedName ?: this::class.simpleName ?: "event"
}
