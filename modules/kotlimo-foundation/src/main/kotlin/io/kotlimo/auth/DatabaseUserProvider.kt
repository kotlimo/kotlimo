package io.kotlimo.auth

import io.kotlimo.database.Connection

class DatabaseUserProvider(
    private val connection: () -> Connection,
    private val table: String = "users",
    private val identifier: String = "id"
) : UserProvider {
    override fun retrieveById(id: Any): Authenticatable? {
        val row = connection().table(table).where(identifier, id).first() ?: return null
        return GenericUser(row)
    }

    override fun retrieveByCredentials(credentials: Map<String, Any?>): Authenticatable? {
        val lookup = credentials.filterKeys { it != "password" }
        if (lookup.isEmpty()) return null
        var query = connection().table(table)
        lookup.forEach { (column, value) ->
            query = query.where(column, value)
        }
        val row = query.first() ?: return null
        return GenericUser(row)
    }
}
