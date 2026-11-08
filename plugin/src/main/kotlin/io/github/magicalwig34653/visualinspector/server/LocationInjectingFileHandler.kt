package io.github.magicalwig34653.visualinspector.server

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import org.jetbrains.builtInWebServer.PathInfo
import org.jetbrains.builtInWebServer.WebServerFileHandler

/**
 * Serves project HTML pages of the IDE built-in web server with [LocationInjector] applied, in memory.
 * Registered with `order="first"`, so it runs before the platform `StaticFileHandler`. The unsaved editor
 * `Document` wins over the file on disk; the user's file is never written.
 *
 * Gated: only requests carrying a live preview token (see [PreviewGate]) are handled; others return false.
 */
internal class LocationInjectingFileHandler : WebServerFileHandler() {
    override val pageFileExtensions = listOf("html", "htm")

    override fun process(
        pathInfo: PathInfo,
        canonicalPath: CharSequence,
        project: Project,
        request: FullHttpRequest,
        channel: Channel,
        projectNameIfNotCustomHost: String?,
        extraHeaders: HttpHeaders,
    ): Boolean {
        // Gate (ADR 0009): only Inspector previews get injection; all other requests fall through unmodified.
        val token = PreviewGate.tokenOf(request.uri(), request.headers().get(HttpHeaderNames.COOKIE)) ?: return false
        val file = pathInfo.getOrResolveVirtualFile() ?: return false
        if (file.isDirectory || file.extension?.lowercase() !in pageFileExtensions) return false

        val (text, relativePath) = runReadActionBlocking {
            val document = FileDocumentManager.getInstance().getCachedDocument(file)
            val raw = document?.text ?: StringUtil.convertLineSeparators(String(file.contentsToByteArray(), file.charset))
            val base = project.guessProjectDir()
            raw to (base?.let { VfsUtilCore.getRelativePath(file, it) } ?: file.name)
        }
        val body = LocationInjector.inject(text, FileUtil.toSystemIndependentName(relativePath)).toByteArray(file.charset)

        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
            if (request.method() == HttpMethod.HEAD) Unpooled.EMPTY_BUFFER else Unpooled.wrappedBuffer(body))
        response.headers().add(extraHeaders)
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=${file.charset.name()}")
        response.headers().set(HttpHeaderNames.SET_COOKIE, PreviewGate.setCookieHeader(token))
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store")
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.size)
        val keepAlive = HttpUtil.isKeepAlive(request)
        if (!keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, "close")
        val future = channel.writeAndFlush(response)
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE)
        return true
    }
}
