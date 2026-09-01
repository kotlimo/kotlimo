package io.kotlimo.database

import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

class Connection(
    val jdbc: java.sql.Connection,
    val name: String = "default",
    val driver: String = "h2"
) {
    fun select(sql: String, bindings: List<Any?> = emptyList()): List<Map<String, Any?>> {
        jdbc.prepareStatement(sql).use { statement ->
            bind(statement, bindings)
            statement.executeQuery().use { rs -> return hydrate(rs) }
        }
    }

    fun insert(sql: String, bindings: List<Any?> = emptyList()): Long {
        jdbc.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
            bind(statement, bindings)
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                return if (keys.next()) keys.getLong(1) else 0L
            }
        }
    }

    fun affectingStatement(sql: String, bindings: List<Any?> = emptyList()): Int {
        jdbc.prepareStatement(sql).use { statement ->
            bind(statement, bindings)
            return statement.executeUpdate()
        }
    }

    fun statement(sql: String) {
        jdbc.createStatement().use { it.execute(sql) }
    }

    fun transaction(block: Connection.() -> Unit) {
        val previous = jdbc.autoCommit
        jdbc.autoCommit = false
        try {
            block()
            jdbc.commit()
        } catch (e: Exception) {
            jdbc.rollback()
            throw e
        } finally {
            jdbc.autoCommit = previous
        }
    }

    fun table(name: String): QueryBuilder = QueryBuilder(this, name)

    fun close() = jdbc.close()

    private fun bind(statement: java.sql.PreparedStatement, bindings: List<Any?>) {
        bindings.forEachIndexed { index, value ->
            statement.setObject(index + 1, value)
        }
    }

    private fun hydrate(rs: ResultSet): List<Map<String, Any?>> {
        val meta = rs.metaData
        val columns = (1..meta.columnCount).map { meta.getColumnLabel(it) }
        val rows = mutableListOf<Map<String, Any?>>()
        while (rs.next()) {
            val row = linkedMapOf<String, Any?>()
            columns.forEachIndexed { index, column ->
                val value = rs.getObject(index + 1)
                row[column] = value
                row[column.lowercase()] = value
            }
            rows += row
        }
        return rows
    }

    companion object {
        fun connect(
            url: String,
            username: String = "",
            password: String = "",
            name: String = "default"
        ): Connection {
            val driver = when {
                url.startsWith("jdbc:h2") -> "h2"
                url.startsWith("jdbc:sqlite") -> "sqlite"
                url.startsWith("jdbc:mysql") -> "mysql"
                url.startsWith("jdbc:postgresql") -> "pgsql"
                else -> "generic"
            }
            return Connection(DriverManager.getConnection(url, username, password), name, driver)
        }
    }
}

class DatabaseManager(private val connections: MutableMap<String, Connection> = mutableMapOf()) {
    var default: String = "default"

    fun addConnection(name: String, connection: Connection) {
        connections[name] = connection
        if (connections.size == 1) default = name
    }

    fun connection(name: String? = null): Connection =
        connections[name ?: default]
            ?: throw IllegalStateException("Database connection [${name ?: default}] is not configured")

    fun table(name: String): QueryBuilder = connection().table(name)

    fun disconnect(name: String? = null) {
        val key = name ?: default
        connections.remove(key)?.close()
    }
}
