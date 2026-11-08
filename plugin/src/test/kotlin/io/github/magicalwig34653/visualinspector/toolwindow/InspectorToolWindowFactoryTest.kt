package io.github.magicalwig34653.visualinspector.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import io.github.magicalwig34653.visualinspector.preview.PreviewAvailability
import io.github.magicalwig34653.visualinspector.product.ProductGuard

class InspectorToolWindowFactoryTest : BasePlatformTestCase() {
    fun testAvailabilityDecision() {
        var jcefQueried = false
        assertEquals(PreviewAvailability.UNSUPPORTED_IDE, PreviewAvailability.decide(false) { jcefQueried = true; true })
        assertFalse("JCEF must not be touched in an unsupported IDE", jcefQueried)
        assertEquals(PreviewAvailability.JCEF_UNSUPPORTED, PreviewAvailability.decide(true) { false })
        assertEquals(PreviewAvailability.AVAILABLE, PreviewAvailability.decide(true) { true })
    }

    fun testUnsupportedIdeShowsMessageOnly() {
        val c = InspectorToolWindowFactory.createComponent(project, testRootDisposable, PreviewAvailability.UNSUPPORTED_IDE)
        assertEquals(InspectorToolWindowFactory.UNSUPPORTED_IDE_MESSAGE, (c as JBLabel).text)
    }

    fun testJcefUnsupportedShowsMessage() {
        val c = InspectorToolWindowFactory.createComponent(project, testRootDisposable, PreviewAvailability.JCEF_UNSUPPORTED)
        assertEquals(InspectorToolWindowFactory.JCEF_UNSUPPORTED_MESSAGE, (c as JBLabel).text)
    }

    fun testTestIdeIsWebStorm() {
        assertTrue(ProductGuard.isSupportedIde())
    }
}
