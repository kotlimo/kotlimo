package io.kotlimo.database

import io.kotlimo.support.Inflector
import kotlin.reflect.KClass

abstract class Model {
    open val table: String
        get() = Inflector.tableize(this::class.simpleName ?: "models")

    open val primaryKey: String = "id"
    open val fillable: List<String> = emptyList()
    open val guarded: List<String> = listOf("id")
    open val timestamps: Boolean = true

    var exists: Boolean = false
    val attributes: MutableMap<String, Any?> = mutableMapOf()
    private val original: MutableMap<String, Any?> = mutableMapOf()

    var connection: Connection?
        get() = Companion.connection
        set(value) { Companion.connection = value }

    operator fun get(key: String): Any? = attributes[key]
    operator fun set(key: String, value: Any?) {
        attributes[key] = value
    }

    fun getAttribute(key: String): Any? = attributes[key]
    fun setAttribute(key: String, value: Any?) {
        attributes[key] = value
    }

    val id: Any?
        get() = attributes[primaryKey]

    fun fill(values: Map<String, Any?>): Model {
        values.forEach { (key, value) ->
            if (isFillable(key)) attributes[key] = value
        }
        return this
    }

    fun forceFill(values: Map<String, Any?>): Model {
        attributes.putAll(values)
        return this
    }

    fun isFillable(key: String): Boolean {
        if (fillable.isNotEmpty()) return key in fillable
        return key !in guarded
    }

    fun isDirty(): Boolean = attributes != original

    fun syncOriginal(values: Map<String, Any?> = attributes) {
        attributes.clear()
        attributes.putAll(values)
        original.clear()
        original.putAll(values)
    }

    fun save(): Boolean {
        val conn = requireConnection()
        if (timestamps) {
            val now = java.time.LocalDateTime.now().toString()
            if (!exists) attributes.putIfAbsent("created_at", now)
            attributes["updated_at"] = now
        }
        return if (exists) {
            val dirty = attributes.filterKeys { it != primaryKey }
            conn.table(table).where(primaryKey, id).update(dirty)
            original.clear()
            original.putAll(attributes)
            true
        } else {
            val id = conn.table(table).insert(attributes)
            if (id > 0) attributes[primaryKey] = id
            exists = true
            original.clear()
            original.putAll(attributes)
            true
        }
    }

    fun delete(): Boolean {
        if (!exists) return false
        requireConnection().table(table).where(primaryKey, id).delete()
        exists = false
        return true
    }

    fun toMap(): Map<String, Any?> = attributes.toMap()

    fun fresh(): Model? {
        val key = id ?: return null
        val row = requireConnection().table(table).where(primaryKey, key).first() ?: return null
        syncOriginal(row)
        exists = true
        return this
    }

    fun hasMany(related: KClass<out Model>, foreignKey: String = Inflector.foreignKey(this::class.simpleName ?: "model")): List<Model> {
        val relatedTable = Inflector.tableize(related.simpleName ?: "models")
        return requireConnection().table(relatedTable).where(foreignKey, id).get().map { row ->
            related.java.getDeclaredConstructor().newInstance().apply {
                exists = true
                syncOriginal(row)
                connection = this@Model.requireConnection()
            }
        }
    }

    fun belongsTo(related: KClass<out Model>, foreignKey: String = Inflector.foreignKey(related.simpleName ?: "model")): Model? {
        val ownerId = attributes[foreignKey] ?: return null
        val relatedTable = Inflector.tableize(related.simpleName ?: "models")
        val row = requireConnection().table(relatedTable).where("id", ownerId).first() ?: return null
        return related.java.getDeclaredConstructor().newInstance().apply {
            exists = true
            syncOriginal(row)
            connection = this@Model.requireConnection()
        }
    }

    internal fun requireConnection(): Connection =
        connection ?: throw IllegalStateException("No database connection is set for ${this::class.simpleName}")

    companion object {
        var connection: Connection? = null

        fun query(table: String): QueryBuilder {
            val conn = connection ?: throw IllegalStateException("No database connection is set")
            return QueryBuilder(conn, table)
        }
    }
}

open class ModelCompanion<T : Model>(private val type: KClass<T>) {
    fun newInstance(): T = type.java.getDeclaredConstructor().newInstance()

    fun query(): QueryBuilder {
        val model = newInstance()
        val conn = Model.connection ?: throw IllegalStateException("No database connection is set")
        return QueryBuilder(conn, model.table, type)
    }

    fun find(id: Any): T? {
        val row = query().where(newInstance().primaryKey, id).first() ?: return null
        return hydrate(row)
    }

    fun findOrFail(id: Any): T = find(id) ?: throw ModelNotFoundException(type, id)

    fun all(): List<T> = query().get().map { hydrate(it) }

    fun where(column: String, value: Any?): QueryBuilder = query().where(column, value)

    fun where(column: String, operator: String, value: Any?): QueryBuilder = query().where(column, operator, value)

    fun create(attributes: Map<String, Any?>): T {
        val model = newInstance().apply { fill(attributes) }
        model.save()
        return model
    }

    fun first(): T? = query().first()?.let { hydrate(it) }

    fun count(): Long = query().count()

    fun hydrate(attributes: Map<String, Any?>): T = newInstance().apply {
        exists = true
        syncOriginal(attributes)
    }
}

class ModelNotFoundException(type: KClass<*>, id: Any) :
    RuntimeException("No query results for model [${type.simpleName}] $id")
