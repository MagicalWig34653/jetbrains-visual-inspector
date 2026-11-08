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
}
