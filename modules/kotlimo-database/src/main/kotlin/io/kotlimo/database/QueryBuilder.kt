package io.kotlimo.database

import kotlin.reflect.KClass

data class WhereClause(
    val boolean: String,
    val sql: String,
    val bindings: List<Any?>
)

class QueryBuilder(
    val connection: Connection,
    val table: String,
    var modelType: KClass<out Model>? = null
) {
    private val columns = mutableListOf<String>()
    private val wheres = mutableListOf<WhereClause>()
    private val orders = mutableListOf<Pair<String, String>>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null
    private val groups = mutableListOf<String>()

    fun select(vararg columns: String): QueryBuilder {
        this.columns.clear()
        this.columns.addAll(if (columns.isEmpty()) listOf("*") else columns.toList())
        return this
    }

    fun where(column: String, operator: String, value: Any?): QueryBuilder {
        addWhere("and", "$column $operator ?", listOf(value))
        return this
    }

    fun where(column: String, value: Any?): QueryBuilder = where(column, "=", value)

    fun orWhere(column: String, operator: String, value: Any?): QueryBuilder {
        addWhere("or", "$column $operator ?", listOf(value))
        return this
    }

    fun orWhere(column: String, value: Any?): QueryBuilder = orWhere(column, "=", value)

    fun whereIn(column: String, values: Collection<Any?>): QueryBuilder {
        if (values.isEmpty()) {
            addWhere("and", "1 = 0", emptyList())
            return this
        }
        val placeholders = values.joinToString(", ") { "?" }
        addWhere("and", "$column IN ($placeholders)", values.toList())
        return this
    }

    fun whereNull(column: String): QueryBuilder {
        addWhere("and", "$column IS NULL", emptyList())
        return this
    }

    fun whereNotNull(column: String): QueryBuilder {
        addWhere("and", "$column IS NOT NULL", emptyList())
        return this
    }

    fun orderBy(column: String, direction: String = "asc"): QueryBuilder {
        orders += column to direction.uppercase()
        return this
    }

    fun latest(column: String = "id"): QueryBuilder = orderBy(column, "desc")

    fun oldest(column: String = "id"): QueryBuilder = orderBy(column, "asc")

    fun limit(value: Int): QueryBuilder {
        limitValue = value
        return this
    }

    fun offset(value: Int): QueryBuilder {
        offsetValue = value
        return this
    }

    fun take(value: Int): QueryBuilder = limit(value)

    fun skip(value: Int): QueryBuilder = offset(value)

    fun groupBy(vararg columns: String): QueryBuilder {
        groups.addAll(columns)
        return this
    }

    fun toSql(): String {
        val select = (if (columns.isEmpty()) listOf("*") else columns).joinToString(", ")
        val builder = StringBuilder("SELECT $select FROM $table")
        if (wheres.isNotEmpty()) {
            builder.append(" WHERE ")
            wheres.forEachIndexed { index, clause ->
                if (index > 0) builder.append(" ${clause.boolean.uppercase()} ")
                builder.append(clause.sql)
            }
        }
        if (groups.isNotEmpty()) builder.append(" GROUP BY ${groups.joinToString(", ")}")
        if (orders.isNotEmpty()) {
            builder.append(" ORDER BY " + orders.joinToString(", ") { "${it.first} ${it.second}" })
        }
        limitValue?.let { builder.append(" LIMIT $it") }
        offsetValue?.let { builder.append(" OFFSET $it") }
        return builder.toString()
    }

    fun getBindings(): List<Any?> = wheres.flatMap { it.bindings }

    fun get(): List<Map<String, Any?>> = connection.select(toSql(), getBindings())

    fun models(): List<Model> {
        val type = modelType ?: throw IllegalStateException("No model type bound to query")
        return get().map { hydrate(type, it) }
    }

    fun first(): Map<String, Any?>? = limit(1).get().firstOrNull()

    fun firstModel(): Model? = limit(1).models().firstOrNull()

    fun find(id: Any, key: String = "id"): Map<String, Any?>? = where(key, id).first()

    fun value(column: String): Any? {
        select(column)
        return first()?.values?.firstOrNull()
    }

    fun count(): Long {
        val previous = columns.toList()
        columns.clear()
        columns += "COUNT(*) as aggregate"
        val result = first()?.get("aggregate") ?: first()?.values?.firstOrNull()
        columns.clear()
        columns.addAll(previous)
        return (result as? Number)?.toLong() ?: 0L
    }

    fun exists(): Boolean = count() > 0

    fun insert(values: Map<String, Any?>): Long {
        val columns = values.keys.joinToString(", ")
        val placeholders = values.keys.joinToString(", ") { "?" }
        val sql = "INSERT INTO $table ($columns) VALUES ($placeholders)"
        return connection.insert(sql, values.values.toList())
    }

    fun insertGetId(values: Map<String, Any?>, key: String = "id"): Long = insert(values)

    fun update(values: Map<String, Any?>): Int {
        val assignments = values.keys.joinToString(", ") { "$it = ?" }
        val sql = "UPDATE $table SET $assignments" + whereSql()
        return connection.affectingStatement(sql, values.values.toList() + getBindings())
    }

    fun delete(): Int {
        val sql = "DELETE FROM $table" + whereSql()
        return connection.affectingStatement(sql, getBindings())
    }

    fun increment(column: String, amount: Int = 1): Int {
        val sql = "UPDATE $table SET $column = $column + ?" + whereSql()
        return connection.affectingStatement(sql, listOf(amount) + getBindings())
    }

    private fun whereSql(): String {
        if (wheres.isEmpty()) return ""
        val builder = StringBuilder(" WHERE ")
        wheres.forEachIndexed { index, clause ->
            if (index > 0) builder.append(" ${clause.boolean.uppercase()} ")
            builder.append(clause.sql)
        }
        return builder.toString()
    }

    private fun addWhere(boolean: String, sql: String, bindings: List<Any?>) {
        wheres += WhereClause(boolean, sql, bindings)
    }

    private fun hydrate(type: KClass<out Model>, attributes: Map<String, Any?>): Model {
        val model = type.java.getDeclaredConstructor().newInstance()
        model.exists = true
        model.syncOriginal(attributes)
        return model
    }
}
