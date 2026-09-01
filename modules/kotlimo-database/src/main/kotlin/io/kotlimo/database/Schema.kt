package io.kotlimo.database

class ColumnDefinition(
    val name: String,
    val type: String,
    var nullable: Boolean = false,
    var unique: Boolean = false,
    var default: Any? = UNSET,
    var primary: Boolean = false,
    var autoIncrement: Boolean = false
) {
    fun nullable(): ColumnDefinition {
        nullable = true
        return this
    }

    fun unique(): ColumnDefinition {
        unique = true
        return this
    }

    fun default(value: Any?): ColumnDefinition {
        default = value
        return this
    }

    companion object {
        val UNSET = Any()
    }
}

class Blueprint(val table: String) {
    val columns = mutableListOf<ColumnDefinition>()
    val indexes = mutableListOf<List<String>>()

    fun id(name: String = "id"): ColumnDefinition =
        ColumnDefinition(name, "BIGINT", primary = true, autoIncrement = true, nullable = false).also { columns += it }

    fun string(name: String, length: Int = 255): ColumnDefinition =
        ColumnDefinition(name, "VARCHAR($length)").also { columns += it }

    fun text(name: String): ColumnDefinition =
        ColumnDefinition(name, "TEXT").also { columns += it }

    fun integer(name: String): ColumnDefinition =
        ColumnDefinition(name, "INTEGER").also { columns += it }

    fun bigInteger(name: String): ColumnDefinition =
        ColumnDefinition(name, "BIGINT").also { columns += it }

    fun boolean(name: String): ColumnDefinition =
        ColumnDefinition(name, "BOOLEAN").also { columns += it }

    fun timestamp(name: String): ColumnDefinition =
        ColumnDefinition(name, "TIMESTAMP").also { columns += it }

    fun timestamps() {
        timestamp("created_at").nullable()
        timestamp("updated_at").nullable()
    }

    fun foreignId(name: String): ColumnDefinition = bigInteger(name)

    fun index(vararg columns: String) {
        indexes += columns.toList()
    }
}

class SchemaBuilder(private val connection: Connection) {
    fun create(table: String, block: Blueprint.() -> Unit) {
        val blueprint = Blueprint(table).apply(block)
        val definitions = blueprint.columns.joinToString(", ") { columnSql(it) }
        val indexes = blueprint.indexes.mapIndexed { index, cols ->
            ", INDEX idx_${table}_$index (${cols.joinToString(", ")})"
        }.joinToString("")
        connection.statement("CREATE TABLE IF NOT EXISTS $table ($definitions$indexes)")
    }

    fun drop(table: String) {
        val names = listOf(table, table.uppercase(), table.lowercase()).distinct()
        names.forEach { name ->
            runCatching { connection.statement("DROP TABLE IF EXISTS $name CASCADE") }
            runCatching { connection.statement("DROP TABLE IF EXISTS \"$name\" CASCADE") }
        }
    }

    fun dropIfExists(table: String) = drop(table)

    fun hasTable(table: String): Boolean {
        return try {
            connection.select("SELECT 1 AS ok FROM $table WHERE 1 = 0")
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun columnSql(column: ColumnDefinition): String {
        val builder = StringBuilder("${column.name} ${column.type}")
        if (column.primary && column.autoIncrement) {
            builder.append(" PRIMARY KEY AUTO_INCREMENT")
        } else {
            if (!column.nullable) builder.append(" NOT NULL")
            if (column.unique) builder.append(" UNIQUE")
            if (column.default !== ColumnDefinition.UNSET) {
                val value = column.default
                val rendered = when (value) {
                    is String -> "'$value'"
                    is Boolean -> if (value) "TRUE" else "FALSE"
                    null -> "NULL"
                    else -> value.toString()
                }
                builder.append(" DEFAULT $rendered")
            }
        }
        return builder.toString()
    }
}

abstract class Migration {
    abstract fun up(schema: SchemaBuilder)
    open fun down(schema: SchemaBuilder) {}
    open val name: String get() = this::class.simpleName ?: "Migration"
}

object Schema {
    fun builder(): SchemaBuilder {
        val connection = Model.connection ?: throw IllegalStateException("No database connection is set")
        return SchemaBuilder(connection)
    }

    fun create(table: String, block: Blueprint.() -> Unit) = builder().create(table, block)

    fun drop(table: String) = builder().drop(table)

    fun dropIfExists(table: String) = builder().dropIfExists(table)

    fun hasTable(table: String): Boolean = builder().hasTable(table)
}

class Migrator(private val connection: Connection) {
    private val schema = SchemaBuilder(connection)

    fun migrate(migrations: List<Migration>): List<String> {
        ensureMigrationsTable()
        val ran = ran()
        val batch = lastBatch() + 1
        val executed = mutableListOf<String>()
        migrations.forEach { migration ->
            if (migration.name !in ran) {
                migration.up(schema)
                connection.table("migrations").insert(mapOf("migration" to migration.name, "batch" to batch))
                executed += migration.name
            }
        }
        return executed
    }

    fun rollback(migrations: List<Migration>, steps: Int = 1): List<String> {
        ensureMigrationsTable()
        val byName = migrations.associateBy { it.name }
        val rolled = mutableListOf<String>()
        repeat(steps.coerceAtLeast(1)) {
            val batch = lastBatch()
            if (batch == 0) return rolled
            val rows = connection.table("migrations").where("batch", batch).get()
            rows.asReversed().forEach { row ->
                val name = row["migration"]?.toString() ?: return@forEach
                byName[name]?.down(schema)
                connection.table("migrations").where("migration", name).delete()
                rolled += name
            }
        }
        return rolled
    }

    fun ran(): Set<String> {
        if (!schema.hasTable("migrations")) return emptySet()
        return connection.table("migrations").get().mapNotNull { it["migration"]?.toString() }.toSet()
    }

    fun lastBatch(): Int {
        if (!schema.hasTable("migrations")) return 0
        val value = connection.table("migrations").get().maxOfOrNull { (it["batch"] as? Number)?.toInt() ?: 0 } ?: 0
        return value
    }

    private fun ensureMigrationsTable() {
        schema.create("migrations") {
            id()
            string("migration")
            integer("batch")
        }
    }
}
