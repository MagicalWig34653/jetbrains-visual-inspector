package io.github.magicalwig34653.visualinspector.server

import com.intellij.openapi.editor.Document
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.ide.BuiltInServerManager
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class PsiOffsetTest : BasePlatformTestCase() {
    private val fixture: String = Files.readString(
        Path.of(System.getProperty("user.dir")).resolveSibling("test-fixtures/static-html/index.html"))

    private val attr = Regex("""<(\w+) data-vi-src="([^":]+):(\d+)"""")

    /** Injected offsets must equal PSI tag offsets, also for the fixture's parser-normalized table. */
    fun testInjectedOffsetsEqualPsiTagOffsets() {
        val psi = myFixture.configureByText("index.html", fixture)
        val tags = PsiTreeUtil.findChildrenOfType(psi, XmlTag::class.java)
        val injected = attr.findAll(LocationInjector.inject(psi.text, "index.html")).map { it.groupValues[1] to it.groupValues[3].toInt() }.toList()
        val expected = tags.map { it.name to it.textRange.startOffset }
        println("SPIKE2 psi tags=${expected.size} injected=${injected.size}")
        assertEquals(expected.sortedBy { it.second }, injected)
        assertTrue(tags.any { it.name == "table" } && tags.none { it.name == "tbody" })
    }
}
