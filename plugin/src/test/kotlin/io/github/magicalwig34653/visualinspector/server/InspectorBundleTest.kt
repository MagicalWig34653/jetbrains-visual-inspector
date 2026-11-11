package io.github.magicalwig34653.visualinspector.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorBundleTest {
    private val marker = "/*BUNDLE-MARKER*/window.startInspector=function(){};"
    private val script = InspectorBundle.bootstrap(marker)

    @Test fun embedsTheBundleExactlyOnceBeforeItIsStarted() {
        assertEquals(1, script.split(marker).size - 1)
        assertTrue(script.indexOf(marker) < script.indexOf("window.startInspector({"))
    }

    @Test fun isGuardedAgainstRunningTwice() {
        assertTrue(script.trimStart().startsWith("(function(){\nif(window.__viStarted)return;"))
        assertTrue(script.indexOf("if(window.__viStarted)return;") < script.indexOf(marker))
    }

    @Test fun startsOnlyWhenTheBundleDefinedStartInspector() {
        assertTrue(script.indexOf("typeof window.startInspector!=='function'") < script.indexOf("window.__viStarted=true"))
    }

    @Test fun routesThroughTheHostQueryAndExposesNoNewGlobalsBeyondTheDocumentedOnes() {
        assertTrue(script.contains("window.__viSend(m)"))
        assertTrue(script.contains("window.__viSend.mode(o?'1':'0')"))
        val globals = Regex("""window\.(__?\w+)\s*=""").findAll(script).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("__viStarted", "__viReceive"), globals) // startInspector comes from the bundle itself
    }

    @Test fun bundleTextIsNotInterpretedAsATemplate() {
        val tricky = "var s='\$bundle \${x} \"\"\"';"
        assertTrue(InspectorBundle.bootstrap(tricky).contains(tricky))
    }
}
