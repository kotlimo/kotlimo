package io.kotlimo.auth

import io.kotlimo.database.Connection
import io.kotlimo.hash.Hash
import io.kotlimo.mail.MailMessage
import io.kotlimo.mail.Mailer
import io.kotlimo.support.Str
import java.time.LocalDateTime

class PasswordBroker(
    private val connection: () -> Connection,
    private val mailer: Mailer,
    private val users: String = "users",
    private val table: String = "password_resets"
) {
    fun sendResetLink(email: String): String? {
        if (!connection().table(users).where("email", email).exists()) return null
        val plain = Str.random(40)
        connection().affectingStatement("DELETE FROM $table WHERE email = ?", listOf(email))
        connection().table(table).insert(
            mapOf(
                "email" to email,
                "token" to SessionGuard.rememberHash(plain),
                "created_at" to LocalDateTime.now().toString()
            )
        )
        mailer.send(
            MailMessage()
                .to(email)
                .subject("Reset your password")
                .text("Reset token: $plain")
        )
        return plain
    }

    fun reset(email: String, token: String, password: String): Boolean {
        val row = connection().table(table).where("email", email).first() ?: return false
        val stored = row["token"]?.toString() ?: return false
        if (stored != SessionGuard.rememberHash(token)) return false
        val updated = connection().table(users).where("email", email).update(mapOf("password" to Hash.make(password)))
        connection().affectingStatement("DELETE FROM $table WHERE email = ?", listOf(email))
        return updated > 0
    }
}
