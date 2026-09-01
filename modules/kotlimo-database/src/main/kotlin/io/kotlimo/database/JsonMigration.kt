package io.kotlimo.database

import io.kotlimo.support.Json
import java.nio.file.Files
import java.nio.file.Path

class JsonMigration(private val file: Path) : Migration() {
    private val document: Map<String, Any?>

    init {
        @Suppress("UNCHECKED_CAST")
        document = (Json.decode(Files.readString(file)) as? Map<String, Any?>) ?: emptyMap()
    }

    override val name: String
        get() = document["name"]?.toString() ?: file.fileName.toString().substringBeforeLast('.')

    override fun up(schema: SchemaBuilder) = applyOperation(schema, document["up"])

    override fun down(schema: SchemaBuilder) = applyOperation(schema, document["down"])

    @Suppress("UNCHECKED_CAST")
    private fun applyOperation(schema: SchemaBuilder, raw: Any?) {
        val operation = raw as? Map<String, Any?> ?: return
        val create = operation["create"]?.toString()
        if (create != null) {
            val columns = operation["columns"] as? List<*> ?: emptyList<Any?>()
            schema.create(create) {
                columns.forEach { column ->
                    val spec = column as? Map<String, Any?> ?: return@forEach
                    applyColumn(this, spec)
                }
            }
            return
        }
        val drop = operation["drop"]?.toString() ?: operation["dropIfExists"]?.toString()
        if (drop != null) {
            schema.dropIfExists(drop)
        }
    }

    private fun applyColumn(blueprint: Blueprint, spec: Map<String, Any?>) {
        val name = spec["name"]?.toString() ?: return
        val type = spec["type"]?.toString()?.lowercase() ?: "string"
        if (type == "timestamps") {
            blueprint.timestamps()
            return
        }
        val column = when (type) {
            "id" -> blueprint.id(name)
            "text" -> blueprint.text(name)
            "integer", "int" -> blueprint.integer(name)
            "bigint", "biginteger" -> blueprint.bigInteger(name)
            "boolean", "bool" -> blueprint.boolean(name)
            "timestamp" -> blueprint.timestamp(name)
            "foreignid", "foreign_id" -> blueprint.foreignId(name)
            else -> {
                val length = (spec["length"] as? Number)?.toInt() ?: 255
                blueprint.string(name, length)
            }
        }
        if (spec["nullable"] == true) column.nullable()
        if (spec["unique"] == true) column.unique()
        if (spec.containsKey("default")) column.default(spec["default"])
    }
}

object MigrationLoader {
    fun fromDirectory(directory: Path): List<Migration> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .sorted(Comparator.comparing { it.fileName.toString() })
                .map { JsonMigration(it) as Migration }
                .toList()
        }
    }
}
