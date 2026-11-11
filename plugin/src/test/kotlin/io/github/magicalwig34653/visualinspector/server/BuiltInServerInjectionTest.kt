package io.github.magicalwig34653.visualinspector.server

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.Urls
import org.jetbrains.ide.BuiltInServerManager
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** End-to-end: real HTTP requests to the IDE built-in web server, handled by [LocationInjectingFileHandler]. */
class BuiltInServerInjectionTest : HeavyPlatformTestCase() {
    private val fixture: String = Files.readString(
        Path.of(System.getProperty("user.dir")).resolveSibling("test-fixtures/static-html/index.html"))

    private val attr = Regex("""<(\w+) data-vi-src="([^":]+):(\d+)"""")

    private fun url(withToken: Boolean, preview: String? = null): String {
        val base = "http://127.0.0.1:${BuiltInServerManager.getInstance().waitForStart().port}/${project.name}/index.html"
        val authed = if (withToken) BuiltInServerManager.getInstance().addAuthToken(Urls.parseEncoded(base)!!).toExternalForm() else base
        // addAuthToken does not keep a query, so the preview token is appended afterwards (as PreviewPanel does)
        return if (preview == null) authed else authed + (if ('?' in authed) "&" else "?") + "${PreviewGate.PARAM}=$preview"
    }

    private fun get(withToken: Boolean, preview: String? = null, cookie: String? = null): Pair<Int, String> {
        val c = URI(url(withToken, preview)).toURL().openConnection() as HttpURLConnection
        if (cookie != null) c.setRequestProperty("Cookie", cookie)
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.toString(Charsets.UTF_8) ?: "")
    }

    private fun createIndex(): com.intellij.openapi.vfs.VirtualFile = WriteCommandAction.writeCommandAction(project).compute<com.intellij.openapi.vfs.VirtualFile, Exception> {
        val dir = VfsUtil.createDirectoryIfMissing(project.basePath!!)!!
        com.intellij.testFramework.PsiTestUtil.addContentRoot(module, dir)
        val f = dir.createChildData(this, "index.html")
        VfsUtil.saveText(f, fixture)
        f
    }

    fun testServesInjectedHtmlFromUnsavedBufferWithPsiOffsets() {
        val file = createIndex()
        val diskBefore = String(file.contentsToByteArray())
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "<!-- unsaved -->\n") }
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))

        val token = PreviewGate.newToken()
        val (status, body) = get(true, token)
        println("SPIKE2 project=${project.name} base=${project.basePath} status=$status\n$body")
        assertEquals(200, status)
        assertTrue("unsaved buffer text expected", body.contains("<!-- unsaved -->"))
        val psi = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
        val expected = PsiTreeUtil.findChildrenOfType(psi, XmlTag::class.java).map { it.name to it.textRange.startOffset }.sortedBy { it.second }
        assertEquals(expected, attr.findAll(body).map { it.groupValues[1] to it.groupValues[3].toInt() }.toList())
        assertEquals("index.html", attr.find(body)!!.groupValues[2])
        assertEquals("file on disk untouched", diskBefore, String(file.contentsToByteArray()))
        assertEquals("no injected attribute in the document", -1, document.text.indexOf("data-vi-src"))
    }

    fun testRequestWithoutTokenGetsNoHtml() {
        createIndex()
        val (status, body) = get(false)
        println("SPIKE2 no-token status=$status")
        assertTrue(status >= 400)
        assertFalse(body.contains("data-vi-src"))
    }

    fun testGatingOnlyPreviewTokenGetsInjection() {
        val file = createIndex()
        val token = PreviewGate.newToken()
        try {
            val (s1, plain) = get(true)
            assertEquals(200, s1)
            assertFalse("no preview token: unmodified file", plain.contains("data-vi-src"))
            assertEquals(fixture, plain)
            val (_, wrong) = get(true, "not-a-token")
            assertFalse(wrong.contains("data-vi-src"))
            assertTrue(get(true, token).second.contains("data-vi-src"))
            assertTrue("cookie carries the preview token", get(true, cookie = "other=1; ${PreviewGate.PARAM}=$token").second.contains("data-vi-src"))
            PreviewGate.revoke(token)
            assertFalse("revoked token", get(true, token).second.contains("data-vi-src"))
        } finally {
            PreviewGate.revoke(token)
        }
        assertEquals(fixture, String(file.contentsToByteArray()))
    }

    private val demo: Path = Path.of(System.getProperty("user.dir")).resolveSibling("test-fixtures/demo-site")

    private fun copyDemo() = WriteCommandAction.writeCommandAction(project).compute<Unit, Exception> {
        val dir = VfsUtil.createDirectoryIfMissing(project.basePath!!)!!
        com.intellij.testFramework.PsiTestUtil.addContentRoot(module, dir)
        for (rel in listOf("index.html", "css/style.css", "js/app.js", "pages/about.html")) {
            val target = VfsUtil.createDirectoryIfMissing(dir, rel.substringBeforeLast('/', ""))!!
            VfsUtil.saveText(target.createChildData(this, rel.substringAfterLast('/')), Files.readString(demo.resolve(rel)))
        }
    }

    private class Resp(val code: Int, val body: String, val type: String?, val cookies: List<String>)

    /** Like a browser: only the given cookies and Referer, no query token. */
    private fun raw(url: String, cookie: String? = null, referer: String? = null): Resp {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        if (cookie != null) c.setRequestProperty("Cookie", cookie)
        if (referer != null) c.setRequestProperty("Referer", referer)
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return Resp(code, body, c.contentType, c.headerFields.filterKeys { it.equals("Set-Cookie", true) }.values.flatten())
    }

    private fun site(path: String) = "http://127.0.0.1:${BuiltInServerManager.getInstance().waitForStart().port}/${project.name}/$path"

    private fun cookieJar(vararg r: Resp) = r.flatMap { it.cookies }.joinToString("; ") { it.substringBefore(';') }

    fun testSubresourcesOfPreviewedPageLoadWithCookiesOnly() {
        copyDemo()
        val token = PreviewGate.newToken()
        try {
            val first = raw(url(true, token).replace("index.html", "index.html"))
            println("DEMO first status=${first.code} cookies=${first.cookies}")
            assertEquals(200, first.code)
            val jar = cookieJar(first)
            val ref = site("index.html")
            val css = raw(site("css/style.css"), jar, ref)
            val js = raw(site("js/app.js"), jar, ref)
            println("DEMO css=${css.code} ${css.type} js=${js.code} ${js.type} jar=$jar")
            assertEquals(200, css.code)
            assertTrue(css.type!!.startsWith("text/css"))
            assertTrue(css.body.contains("site-header"))
            assertEquals(200, js.code)
            assertEquals("css served unmodified", Files.readString(demo.resolve("css/style.css")), css.body)
            assertEquals("js served unmodified", Files.readString(demo.resolve("js/app.js")), js.body)
        } finally { PreviewGate.revoke(token) }
    }

    fun testAboutPageRelativeStylesheetResolves() {
        copyDemo()
        val token = PreviewGate.newToken()
        try {
            val first = raw(url(true, token).replace("index.html", "pages/about.html"))
            assertEquals(200, first.code)
            val href = Regex("""href="([^"]*style\.css)"""").find(first.body)!!.groupValues[1]
            val resolved = URI(site("pages/about.html")).resolve(href).toString()
            assertEquals(site("css/style.css"), resolved)
            val css = raw(resolved, cookieJar(first), site("pages/about.html"))
            assertEquals(200, css.code)
            assertTrue(css.type!!.startsWith("text/css"))
        } finally { PreviewGate.revoke(token) }
    }

    // --- handler edge cases -------------------------------------------------------------------------------

    private fun createFile(name: String, bytes: ByteArray): com.intellij.openapi.vfs.VirtualFile =
        WriteCommandAction.writeCommandAction(project).compute<com.intellij.openapi.vfs.VirtualFile, Exception> {
            val dir = VfsUtil.createDirectoryIfMissing(project.basePath!!)!!
            if (com.intellij.openapi.roots.ModuleRootManager.getInstance(module).contentRoots.none { it == dir }) {
                com.intellij.testFramework.PsiTestUtil.addContentRoot(module, dir)
            }
            dir.createChildData(this, name).also { it.setBinaryContent(bytes) }
        }

    private fun fileUrl(name: String) = site(name)

    /** Authed + preview-token URL for another file name. */
    private fun previewUrl(name: String, token: String) = url(true, token).replace("index.html", name)

    private fun request(url: String, method: String): Pair<HttpURLConnection, ByteArray> {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = method
        val code = c.responseCode
        return c to ((if (code < 400) c.inputStream else c.errorStream)?.readBytes() ?: ByteArray(0))
    }

    fun testHeadRequestHasHeadersAndNoBody() {
        createFile("index.html", fixture.toByteArray())
        val token = PreviewGate.newToken()
        try {
            val (get, getBody) = request(url(true, token), "GET")
            val (head, headBody) = request(url(true, token), "HEAD")
            assertEquals(200, head.responseCode)
            assertEquals(0, headBody.size)
            assertEquals("same Content-Length as GET", getBody.size, head.contentLength)
            assertEquals(get.contentLength, head.contentLength)
            assertTrue(head.contentType.startsWith("text/html"))
        } finally { PreviewGate.revoke(token) }
    }

    fun testResponseIsNotCachedAndSetsTheGateCookieHttpOnly() {
        createFile("index.html", fixture.toByteArray())
        val token = PreviewGate.newToken()
        try {
            val (c, _) = request(url(true, token), "GET")
            assertEquals("no-cache, no-store", c.getHeaderField("Cache-Control"))
            val cookies = c.headerFields.filterKeys { it.equals("Set-Cookie", true) }.values.flatten()
            val gate = cookies.single { it.startsWith("${PreviewGate.PARAM}=") }
            assertTrue(gate, gate.contains("HttpOnly") && gate.contains("SameSite=Strict"))
            assertTrue("the platform auth cookie must survive next to the gate cookie", cookies.size >= 2)
        } finally { PreviewGate.revoke(token) }
    }

    fun testContentLengthMatchesBodyForMultibyteContent() {
        val text = "<html><body><p>ü ö 日本語 \uD83D\uDE00</p></body></html>"
        createFile("index.html", text.toByteArray())
        val token = PreviewGate.newToken()
        try {
            val (c, body) = request(url(true, token), "GET")
            assertEquals(body.size, c.contentLength)
            assertTrue(String(body).contains("<p data-vi-src=\"index.html:") && String(body).contains("ü ö 日本語 \uD83D\uDE00"))
        } finally { PreviewGate.revoke(token) }
    }

    fun testCrlfFileGetsOffsetsOfTheNormalizedDocumentText() {
        val crlf = "<html>\r\n<body>\r\n<p>x</p>\r\n</body>\r\n</html>\r\n"
        val file = createFile("index.html", crlf.toByteArray())
        val token = PreviewGate.newToken()
        try {
            val (_, body) = request(url(true, token), "GET")
            val doc = FileDocumentManager.getInstance().getDocument(file)!!
            val psi = PsiDocumentManager.getInstance(project).getPsiFile(doc)!!
            val expected = PsiTreeUtil.findChildrenOfType(psi, XmlTag::class.java).map { it.name to it.textRange.startOffset }.sortedBy { it.second }
            val actual = attr.findAll(String(body)).map { it.groupValues[1] to it.groupValues[3].toInt() }.toList()
            assertEquals("offsets must be the editor's (LF-normalized) offsets, not raw byte offsets", expected, actual)
            assertTrue("CRLF is normalized in the served text", !String(body).contains("\r"))
        } finally { PreviewGate.revoke(token) }
    }

    fun testFileWithByteOrderMarkIsServedAndMapped() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "<html><body><p>x</p></body></html>".toByteArray()
        val file = createFile("index.html", bom)
        val token = PreviewGate.newToken()
        try {
            val (status, body) = request(url(true, token), "GET").let { it.first.responseCode to String(it.second) }
            assertEquals(200, status)
            val doc = FileDocumentManager.getInstance().getDocument(file)!!
            val psi = PsiDocumentManager.getInstance(project).getPsiFile(doc)!!
            val expected = PsiTreeUtil.findChildrenOfType(psi, XmlTag::class.java).map { it.name to it.textRange.startOffset }.sortedBy { it.second }
            assertEquals(expected, attr.findAll(body).map { it.groupValues[1] to it.groupValues[3].toInt() }.toList())
            assertEquals("<p>", doc.text.substring(expected.first { it.first == "p" }.second).take(3))
        } finally { PreviewGate.revoke(token) }
    }

    fun testHtmExtensionAndUppercaseExtensionAreInjected() {
        createFile("page.htm", "<p>a</p>".toByteArray())
        createFile("UP.HTML", "<p>b</p>".toByteArray())
        val token = PreviewGate.newToken()
        try {
            assertTrue(String(request(previewUrl("page.htm", token), "GET").second).contains("data-vi-src=\"page.htm:0\""))
            assertTrue(String(request(previewUrl("UP.HTML", token), "GET").second).contains("data-vi-src=\"UP.HTML:0\""))
        } finally { PreviewGate.revoke(token) }
    }

    fun testNonHtmlFileFallsThroughUnmodifiedEvenWithToken() {
        val js = "var x = '<p>not html</p>';\n"
        createFile("app.js", js.toByteArray())
        createFile("data.json", "{\"a\":\"<p>\"}".toByteArray())
        val token = PreviewGate.newToken()
        try {
            assertEquals(js, String(request(previewUrl("app.js", token), "GET").second))
            assertEquals("{\"a\":\"<p>\"}", String(request(previewUrl("data.json", token), "GET").second))
        } finally { PreviewGate.revoke(token) }
    }

    fun testMissingFileIsNotFoundAndGetsNoInjectionOrGateCookie() {
        createFile("index.html", fixture.toByteArray())
        val token = PreviewGate.newToken()
        try {
            val (c, body) = request(previewUrl("nope.html", token), "GET")
            assertEquals(404, c.responseCode)
            assertFalse(String(body).contains("data-vi-src"))
            assertTrue(c.headerFields.filterKeys { it.equals("Set-Cookie", true) }.values.flatten().none { it.startsWith("${PreviewGate.PARAM}=") })
        } finally { PreviewGate.revoke(token) }
    }

    fun testLargeHtmlFileIsServedCompleteWithCorrectLength() {
        val sb = StringBuilder("<html><body>")
        repeat(20_000) { sb.append("<p>line ").append(it).append("</p>\n") }
        sb.append("</body></html>")
        createFile("index.html", sb.toString().toByteArray())
        val token = PreviewGate.newToken()
        try {
            val (c, body) = request(url(true, token), "GET")
            assertEquals(200, c.responseCode)
            assertEquals(body.size, c.contentLength)
            assertEquals(20_000, Regex("data-vi-src=\"index.html:").findAll(String(body)).count() - 2) // + html and body
        } finally { PreviewGate.revoke(token) }
    }

    fun testEditAfterFirstRequestIsReflectedOnTheNext() {
        val file = createFile("index.html", "<p>one</p>".toByteArray())
        val token = PreviewGate.newToken()
        try {
            assertTrue(String(request(url(true, token), "GET").second).contains("one"))
            val doc = FileDocumentManager.getInstance().getDocument(file)!!
            WriteCommandAction.runWriteCommandAction(project) { doc.setText("<div><p>two</p></div>") }
            val second = String(request(url(true, token), "GET").second)
            assertTrue(second.contains("two") && !second.contains("one"))
            assertTrue("offset of the inner <p> follows the unsaved edit", second.contains("<p data-vi-src=\"index.html:5\""))
        } finally { PreviewGate.revoke(token) }
    }
}
