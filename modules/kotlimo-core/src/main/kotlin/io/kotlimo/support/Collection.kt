package io.kotlimo.support

class Collection<T>(private val items: List<T> = emptyList()) : Iterable<T> {
    override fun iterator(): Iterator<T> = items.iterator()

    fun all(): List<T> = items

    fun toList(): List<T> = items

    fun count(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    fun isNotEmpty(): Boolean = items.isNotEmpty()

    fun first(): T? = items.firstOrNull()

    fun first(predicate: (T) -> Boolean): T? = items.firstOrNull(predicate)

    fun last(): T? = items.lastOrNull()

    fun <R> map(transform: (T) -> R): Collection<R> = Collection(items.map(transform))

    fun filter(predicate: (T) -> Boolean): Collection<T> = Collection(items.filter(predicate))

    fun reject(predicate: (T) -> Boolean): Collection<T> = Collection(items.filterNot(predicate))

    fun each(action: (T) -> Unit): Collection<T> {
        items.forEach(action)
        return this
    }

    fun contains(item: T): Boolean = items.contains(item)

    fun unique(): Collection<T> = Collection(items.distinct())

    fun <K : Comparable<K>> sortBy(selector: (T) -> K): Collection<T> =
        Collection(items.sortedBy(selector))

    fun <K : Comparable<K>> sortByDesc(selector: (T) -> K): Collection<T> =
        Collection(items.sortedByDescending(selector))

    fun take(limit: Int): Collection<T> = Collection(items.take(limit))

    fun skip(count: Int): Collection<T> = Collection(items.drop(count))

    fun chunk(size: Int): Collection<Collection<T>> =
        Collection(items.chunked(size).map { Collection(it) })

    fun <K> groupBy(keySelector: (T) -> K): Map<K, Collection<T>> =
        items.groupBy(keySelector).mapValues { Collection(it.value) }

    fun implode(separator: String, transform: (T) -> CharSequence = { it.toString() }): String =
        items.joinToString(separator, transform = transform)

    fun reverse(): Collection<T> = Collection(items.reversed())

    fun values(): Collection<T> = this

    fun merge(other: Collection<T>): Collection<T> = Collection(items + other.items)

    fun toSet(): Set<T> = items.toSet()

    override fun equals(other: Any?): Boolean = other is Collection<*> && other.items == items

    override fun hashCode(): Int = items.hashCode()

    override fun toString(): String = items.toString()

    companion object {
        fun <T> make(items: Iterable<T>): Collection<T> = Collection(items.toList())

        fun <T> wrap(value: T?): Collection<T> = when (value) {
            null -> Collection()
            is Collection<*> -> @Suppress("UNCHECKED_CAST") (value as Collection<T>)
            is Iterable<*> -> @Suppress("UNCHECKED_CAST") Collection((value as Iterable<T>).toList())
            else -> Collection(listOf(value))
        }
    }
}

fun <T> collect(vararg items: T): Collection<T> = Collection(items.toList())

fun <T> collect(items: Iterable<T>): Collection<T> = Collection(items.toList())
