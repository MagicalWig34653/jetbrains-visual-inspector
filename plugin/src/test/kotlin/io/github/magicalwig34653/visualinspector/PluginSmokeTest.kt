package io.github.magicalwig34653.visualinspector

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PluginSmokeTest : BasePlatformTestCase() {

    fun testPluginDescriptorIsLoaded() {
        val id = PluginId.getId("io.github.magicalwig34653.visualinspector")
        assertNotNull("plugin.xml was not loaded by the test IDE", PluginManagerCore.getPlugin(id))
    }

    /** Roadmap phase 2 relies on HTML PSI exposing tags and their source offsets. */
    fun testHtmlPsiExposesTagsWithSourceOffsets() {
        val html = """<main id="app"><p>Hello</p></main>"""
        val file = myFixture.configureByText("index.html", html)

        val paragraph = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).single { it.name == "p" }

        assertEquals(html.indexOf("<p>"), paragraph.textRange.startOffset)
    }
}
