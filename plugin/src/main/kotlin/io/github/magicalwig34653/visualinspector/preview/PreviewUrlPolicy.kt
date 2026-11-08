package io.github.magicalwig34653.visualinspector.preview

import java.net.URI
import java.net.URISyntaxException

/** Only loopback http origins may be shown in the preview (ADR 0005, ADR 0009). */
object PreviewUrlPolicy {
    const val BLANK = "about:blank"
    private val allowedHosts = setOf("localhost", "127.0.0.1")

    /** URL a user may type or the plugin may load. */
    fun isAllowedTarget(url: String?): Boolean {
        if (url == null || url.length > 2048 || url.any { it.isISOControl() || it.isWhitespace() }) return false
        val uri = try { URI(url) } catch (e: URISyntaxException) { return false }
        return uri.scheme == "http" && uri.rawUserInfo == null && uri.host in allowedHosts &&
            (uri.port == -1 || uri.port in 1..65535)
    }

    /** Navigation inside the browser: the targets above plus the initial blank page. */
    fun isAllowedNavigation(url: String?): Boolean = url == BLANK || isAllowedTarget(url)
}
