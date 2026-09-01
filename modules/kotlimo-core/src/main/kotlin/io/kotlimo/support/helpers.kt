package io.kotlimo.support

inline fun <T> tap(value: T, block: (T) -> Unit): T {
    block(value)
    return value
}

inline fun <T, R> withValue(receiver: T, block: T.() -> R): R = receiver.block()

fun value(value: Any?): Any? = if (value is Function0<*>) value.invoke() else value

fun blank(value: Any?): Boolean = when (value) {
    null -> true
    is String -> value.isBlank()
    is Iterable<*> -> !value.iterator().hasNext()
    is Map<*, *> -> value.isEmpty()
    else -> false
}

fun filled(value: Any?): Boolean = !blank(value)

fun optional(value: Any?): Any? = value
