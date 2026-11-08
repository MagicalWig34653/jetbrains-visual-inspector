package io.github.magicalwig34653.visualinspector.action

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class HighlightTargetTest : BasePlatformTestCase() {
    private val html = "<html><body><div class=\"a\"><span>hi</span> text</div></body></html>"

    fun testCaretMapsToEnclosingTagOffset() {
        val psi = myFixture.configureByText("index.html", html)
        assertEquals(html.indexOf("<span"), HighlightTarget.tagOffsetAt(psi, html.indexOf("hi<")))
        assertEquals(html.indexOf("<div"), HighlightTarget.tagOffsetAt(psi, html.indexOf(" text")))
        assertEquals(html.indexOf("<div"), HighlightTarget.tagOffsetAt(psi, html.indexOf("class")))
        assertEquals(html.indexOf("<span"), HighlightTarget.tagOffsetAt(psi, html.indexOf("</span")))
        assertEquals(html.indexOf("<body"), HighlightTarget.tagOffsetAt(psi, html.indexOf("</body")))
    }

    fun testNextIndexCycles() {
        assertEquals(0, HighlightTarget.nextIndex(null, 3))
        assertEquals(2, HighlightTarget.nextIndex(1, 3))
        assertEquals(0, HighlightTarget.nextIndex(2, 3))
        assertEquals(1, HighlightTarget.nextIndex(0, null))
    }
}
