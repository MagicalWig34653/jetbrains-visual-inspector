package io.github.magicalwig34653.visualinspector.server

import io.netty.handler.codec.http.QueryStringDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides whether a built-in server request comes from an Inspector preview (ADR 0009): the preview URL
 * carries a random per-preview token, either as query parameter [PARAM] or, for follow-up requests such as
 * links to other pages, as cookie [PARAM] that the handler sets on the first authorized response.
 * Everything else (other browser tabs, other tools) gets the unmodified file.
 */
object PreviewGate {
    const val PARAM = "vi_preview"
    private val tokens = ConcurrentHashMap.newKeySet<String>()
    private val random = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(24).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).also(tokens::add)
    }

    fun revoke(token: String) { tokens.remove(token) }

    fun isValid(token: String?): Boolean {
        if (token == null || token.length > 128) return false
        val candidate = token.toByteArray()
        // constant-time compare against every live token
        var ok = false
        for (t in tokens) ok = MessageDigest.isEqual(t.toByteArray(), candidate) || ok
        return ok
    }

    /** The valid token presented by the request, or null. [uri] is the raw request URI. */
    fun tokenOf(uri: String, cookieHeader: String?): String? {
        val fromQuery = QueryStringDecoder(uri).parameters()[PARAM]?.firstOrNull()
        if (isValid(fromQuery)) return fromQuery
        val fromCookie = cookieHeader?.split(';')?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$PARAM=") }?.substringAfter('=')
        return if (isValid(fromCookie)) fromCookie else null
    }

    fun setCookieHeader(token: String): String = "$PARAM=$token; Path=/; HttpOnly; SameSite=Strict"
}
