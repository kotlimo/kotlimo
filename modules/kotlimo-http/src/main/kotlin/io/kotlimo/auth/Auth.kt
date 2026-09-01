package io.kotlimo.auth

import io.kotlimo.hash.Hash
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
}

class SessionGuard(
    private val session: () -> Session,
    private val provider: UserProvider
) {
    private var resolved: Authenticatable? = null
    private var attempted = false

    fun attempt(credentials: Map<String, Any?>, remember: Boolean = false): Boolean {
        val user = provider.retrieveByCredentials(credentials) ?: return false
        if (!provider.validateCredentials(user, credentials)) return false
        login(user)
        return true
    }

    fun login(user: Authenticatable) {
        session().put("login_id", user.getAuthIdentifier().toString())
        session().regenerate()
        resolved = user
        attempted = true
    }

    fun logout() {
        session().forget("login_id")
        session().invalidate()
        resolved = null
        attempted = true
    }

    fun user(): Authenticatable? {
        if (attempted) return resolved
        val id = session().get("login_id") ?: run {
            attempted = true
            resolved = null
            return null
        }
        resolved = provider.retrieveById(id)
        attempted = true
        return resolved
    }

    fun check(): Boolean = user() != null

    fun guest(): Boolean = !check()

    fun id(): Any? = user()?.getAuthIdentifier()
}

class AuthManager(private val provider: UserProvider) {
    fun guard(request: Request): SessionGuard = SessionGuard({ request.session() }, provider)

    fun attempt(request: Request, credentials: Map<String, Any?>): Boolean =
        guard(request).attempt(credentials)

    fun login(request: Request, user: Authenticatable) = guard(request).login(user)

    fun logout(request: Request) = guard(request).logout()

    fun user(request: Request): Authenticatable? = guard(request).user()

    fun check(request: Request): Boolean = guard(request).check()

    fun guest(request: Request): Boolean = guard(request).guest()

    fun id(request: Request): Any? = guard(request).id()
}
