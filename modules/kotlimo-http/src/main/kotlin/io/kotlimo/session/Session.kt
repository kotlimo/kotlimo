package io.kotlimo.session

import io.kotlimo.support.Json
import io.kotlimo.support.Str
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Session(
    var id: String,
    private val attributes: MutableMap<String, Any?> = mutableMapOf()
) {
    var regenerateId: Boolean = false
        private set

    var invalidated: Boolean = false
        private set

    fun get(key: String, default: Any? = null): Any? = attributes[key] ?: default

    fun string(key: String, default: String = ""): String = get(key, default)?.toString() ?: default

    fun put(key: String, value: Any?): Session {
        attributes[key] = value
        return this
    }

    fun forget(key: String): Session {
        attributes.remove(key)
        return this
    }

    fun has(key: String): Boolean = attributes.containsKey(key) && attributes[key] != null

    fun all(): Map<String, Any?> = attributes.toMap()

    fun flush() {
        attributes.clear()
    }

    fun regenerate(destroy: Boolean = false): Session {
        if (destroy) attributes.clear()
        regenerateId = true
        return this
    }

    fun invalidate(): Session {
        attributes.clear()
        invalidated = true
        regenerateId = true
        return this
    }

    fun token(): String {
        val existing = string("_token")
        if (existing.isNotBlank()) return existing
        val token = Str.random(40)
        put("_token", token)
        return token
    }

    fun regenerateToken(): String {
        val token = Str.random(40)
        put("_token", token)
        return token
    }
}

interface SessionStore {
    fun read(id: String): MutableMap<String, Any?>
    fun write(id: String, data: Map<String, Any?>)
    fun destroy(id: String)
}

class ArraySessionStore : SessionStore {
    private val items = ConcurrentHashMap<String, Map<String, Any?>>()

    override fun read(id: String): MutableMap<String, Any?> =
        items[id]?.toMutableMap() ?: mutableMapOf()

    override fun write(id: String, data: Map<String, Any?>) {
        items[id] = data.toMap()
    }

    override fun destroy(id: String) {
        items.remove(id)
    }

    fun flush() = items.clear()
}

class FileSessionStore(private val directory: Path) : SessionStore {
    init {
        Files.createDirectories(directory)
    }

    override fun read(id: String): MutableMap<String, Any?> {
        val file = fileFor(id) ?: return mutableMapOf()
        if (!Files.isRegularFile(file)) return mutableMapOf()
        val decoded = Json.decode(Files.readString(file))
        @Suppress("UNCHECKED_CAST")
        return ((decoded as? Map<String, Any?>) ?: emptyMap()).toMutableMap()
    }

    override fun write(id: String, data: Map<String, Any?>) {
        val file = fileFor(id) ?: return
        Files.writeString(file, Json.encode(data))
    }

    override fun destroy(id: String) {
        fileFor(id)?.let { Files.deleteIfExists(it) }
    }

    private fun fileFor(id: String): Path? {
        if (!id.matches(Regex("[A-Za-z0-9]+"))) return null
        return directory.resolve(id)
    }
}

class SessionManager(
    private val store: SessionStore,
    val cookieName: String = "kotlimo_session",
    private val key: String = "",
    val lifetimeMinutes: Long = 120,
    val cookiePath: String = "/",
    val httpOnly: Boolean = true,
    val secure: Boolean = false
) {
    fun start(cookieValue: String?): Session {
        val id = cookieValue?.let { verify(it) }
        return if (id != null) {
            Session(id, store.read(id)).also { it.token() }
        } else {
            Session(newId()).also { it.token() }
        }
    }

    fun save(session: Session) {
        if (session.regenerateId) {
            store.destroy(session.id)
            session.id = newId()
        }
        if (session.invalidated) {
            store.destroy(session.id)
            store.write(session.id, emptyMap())
            return
        }
        store.write(session.id, session.all())
    }

    fun cookieValue(session: Session): String = sign(session.id)

    fun readToken(cookieValue: String): String? {
        val id = verify(cookieValue) ?: return null
        val data = store.read(id)
        return data["_token"]?.toString()
    }

    private fun newId(): String = Str.random(40)

    private fun sign(id: String): String {
        if (key.isBlank()) return id
        return "$id.${hmac(id)}"
    }

    private fun verify(payload: String): String? {
        if (payload.isBlank()) return null
        if (key.isBlank()) {
            return payload.takeIf { it.matches(Regex("[A-Za-z0-9]+")) }
        }
        val id = payload.substringBeforeLast('.')
        val signature = payload.substringAfterLast('.', missingDelimiterValue = "")
        if (id.isBlank() || signature.isBlank() || !id.matches(Regex("[A-Za-z0-9]+"))) return null
        val expected = hmac(id)
        return if (MessageDigest.isEqual(signature.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))) {
            id
        } else {
            null
        }
    }

    private fun hmac(id: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(id.toByteArray(Charsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }
    }
}
