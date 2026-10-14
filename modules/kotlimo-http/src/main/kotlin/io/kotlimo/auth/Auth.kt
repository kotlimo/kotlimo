package io.kotlimo.auth

import io.kotlimo.hash.Hash
import io.kotlimo.http.Cookie
import io.kotlimo.http.Request
import io.kotlimo.session.Session

interface Authenticatable {
    fun getAuthIdentifier(): Any
    fun getAuthPassword(): String
    fun getAuthIdentifierName(): String = "id"
}

data class GenericUser(val attributes: Map<String, Any?>) : Authenticatable {
    override fun getAuthIdentifier(): Any = attributes["id"] ?: error("User is missing an id")

    override fun getAuthPassword(): String = attributes["password"]?.toString().orEmpty()

    operator fun get(key: String): Any? = attributes[key]

    fun getAttribute(key: String): Any? = attributes[key]
}

interface UserProvider {
    fun retrieveById(id: Any): Authenticatable?
    fun retrieveByCredentials(credentials: Map<String, Any?>): Authenticatable?
    fun validateCredentials(user: Authenticatable, credentials: Map<String, Any?>): Boolean {
        val password = credentials["password"]?.toString() ?: return false
        return Hash.check(password, user.getAuthPassword())
    }
    fun updateRememberToken(user: Authenticatable, token: String?) {}
    fun retrieveByToken(id: Any, token: String): Authenticatable? = null
    fun updatePassword(user: Authenticatable, hashedPassword: String) {}
}

class SessionGuard(
    private val session: () -> Session,
    private val provider: UserProvider,
    private val request: Request? = null
) {
    private var resolved: Authenticatable? = null
    private var attempted = false

    fun attempt(credentials: Map<String, Any?>, remember: Boolean = false): Boolean {
        val user = provider.retrieveByCredentials(credentials) ?: return false
        if (!provider.validateCredentials(user, credentials)) return false
        login(user, remember)
        return true
    }

    fun login(user: Authenticatable, remember: Boolean = false) {
        session().put("login_id", user.getAuthIdentifier().toString())
        session().regenerate()
        if (remember) {
            val plain = io.kotlimo.support.Str.random(60)
            val hashed = rememberHash(plain)
            provider.updateRememberToken(user, hashed)
            request?.queueCookie(
                Cookie(
                    name = REMEMBER_COOKIE,
                    value = "${user.getAuthIdentifier()}|$plain",
                    maxAge = 60L * 60 * 24 * 30 * 5
                )
            )
        }
        resolved = user
        attempted = true
    }

    fun logout() {
        user()?.let { provider.updateRememberToken(it, null) }
        session().forget("login_id")
        session().invalidate()
        request?.queueCookie(Cookie(name = REMEMBER_COOKIE, value = "", maxAge = 0))
        resolved = null
        attempted = true
    }

    fun user(): Authenticatable? {
        if (attempted) return resolved
        val id = session().get("login_id")
        if (id != null) {
            resolved = provider.retrieveById(id)
            attempted = true
            return resolved
        }
        resolved = userFromRememberCookie()
        if (resolved != null) {
            session().put("login_id", resolved!!.getAuthIdentifier().toString())
        }
        attempted = true
        return resolved
    }

    private fun userFromRememberCookie(): Authenticatable? {
        val cookie = request?.cookie(REMEMBER_COOKIE) ?: return null
        val parts = cookie.split("|", limit = 2)
        if (parts.size != 2) return null
        return provider.retrieveByToken(parts[0], rememberHash(parts[1]))
    }

    fun check(): Boolean = user() != null

    fun guest(): Boolean = !check()

    fun id(): Any? = user()?.getAuthIdentifier()

    companion object {
        const val REMEMBER_COOKIE = "kotlimo_remember"

        fun rememberHash(plain: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(plain.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

interface TokenStore {
    fun create(user: Authenticatable, name: String = "default"): String
    fun find(plainToken: String): Authenticatable?
    fun revoke(plainToken: String): Boolean
}

class AuthManager(
    private val provider: UserProvider,
    private val tokens: TokenStore? = null
) {
    fun guard(request: Request): SessionGuard =
        SessionGuard({ request.session() }, provider, request)

    fun attempt(request: Request, credentials: Map<String, Any?>, remember: Boolean = false): Boolean =
        guard(request).attempt(credentials, remember)

    fun login(request: Request, user: Authenticatable, remember: Boolean = false) =
        guard(request).login(user, remember)

    fun logout(request: Request) = guard(request).logout()

    fun user(request: Request): Authenticatable? {
        request.bearerToken()?.let { plain ->
            tokens?.find(plain)?.let { return it }
        }
        if (!request.hasSession()) return null
        return guard(request).user()
    }

    fun check(request: Request): Boolean = user(request) != null

    fun guest(request: Request): Boolean = !check(request)

    fun id(request: Request): Any? = user(request)?.getAuthIdentifier()

    fun createToken(user: Authenticatable, name: String = "default"): String =
        tokens?.create(user, name)
            ?: throw IllegalStateException("Token store is not configured. Add database/migrations for personal_access_tokens and migrate.")

    fun revokeToken(plain: String): Boolean = tokens?.revoke(plain) ?: false
}
