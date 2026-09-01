package io.kotlimo.http

data class Cookie(
    val name: String,
    val value: String,
    val maxAge: Long? = null,
    val path: String = "/",
    val domain: String? = null,
    val httpOnly: Boolean = true,
    val secure: Boolean = false,
    val sameSite: String = "Lax"
) {
    fun headerValue(): String = buildString {
        append("$name=$value")
        maxAge?.let { append("; Max-Age=$it") }
        append("; Path=$path")
        domain?.let { append("; Domain=$it") }
        if (httpOnly) append("; HttpOnly")
        if (secure) append("; Secure")
        append("; SameSite=$sameSite")
    }
}
