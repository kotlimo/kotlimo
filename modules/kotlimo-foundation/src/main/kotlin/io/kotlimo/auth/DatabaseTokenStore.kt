package io.kotlimo.auth

import io.kotlimo.database.Connection
import io.kotlimo.support.Str
import java.time.LocalDateTime

class DatabaseTokenStore(
    private val connection: () -> Connection,
    private val users: UserProvider,
    private val table: String = "personal_access_tokens"
) : TokenStore {
    override fun create(user: Authenticatable, name: String): String {
        val plain = Str.random(40)
        connection().table(table).insert(
            mapOf(
                "tokenable_id" to user.getAuthIdentifier().toString(),
                "name" to name,
                "token" to SessionGuard.rememberHash(plain),
                "created_at" to LocalDateTime.now().toString()
            )
        )
        return plain
    }

    override fun find(plainToken: String): Authenticatable? {
        val row = connection().table(table).where("token", SessionGuard.rememberHash(plainToken)).first() ?: return null
        val id = row["tokenable_id"] ?: return null
        return users.retrieveById(id)
    }

    override fun revoke(plainToken: String): Boolean =
        connection().table(table).where("token", SessionGuard.rememberHash(plainToken)).delete() > 0
}
