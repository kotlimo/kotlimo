package io.kotlimo.http

import io.kotlimo.session.Session
import io.kotlimo.session.SessionManager

class StartSession(
    private val manager: SessionManager,
    private val onStart: (Session) -> Unit = {}
) : Middleware {
    override fun handle(request: Request, next: (Request) -> Response): Response {
        val session = manager.start(request.cookie(manager.cookieName))
        request.sessionStore = session
        onStart(session)
        val response = next(request)
        manager.save(session)
        response.cookie(
            Cookie(
                name = manager.cookieName,
                value = manager.cookieValue(session),
                maxAge = manager.lifetimeMinutes * 60,
                path = manager.cookiePath,
                httpOnly = manager.httpOnly,
                secure = manager.secure
            )
        )
        return response
    }
}

class VerifyCsrfToken(
    private val except: List<String> = emptyList()
) : Middleware {
    override fun handle(request: Request, next: (Request) -> Response): Response {
        if (isReading(request) || inExcept(request) || tokensMatch(request)) {
            return next(request)
        }
        throw HttpException(419, "CSRF token mismatch.")
    }

    private fun isReading(request: Request): Boolean =
        request.method() in setOf("GET", "HEAD", "OPTIONS")

    private fun inExcept(request: Request): Boolean =
        except.any { pattern -> request.path == pattern || request.path.startsWith(pattern.trimEnd('*')) }

    private fun tokensMatch(request: Request): Boolean {
        val session: Session = request.sessionStore ?: return true
        val expected = session.token()
        val provided = request.input("_token")?.toString()
            ?: request.header("X-CSRF-TOKEN")
            ?: request.header("X-XSRF-TOKEN")
            ?: return false
        return java.security.MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            provided.toByteArray(Charsets.UTF_8)
        )
    }
}

class AuthenticateSession(
    private val resolveUser: (Request) -> Any?
) : Middleware {
    override fun handle(request: Request, next: (Request) -> Response): Response {
        val user = resolveUser(request)
        if (user is io.kotlimo.auth.Authenticatable) {
            request.user = user
        }
        return next(request)
    }
}
