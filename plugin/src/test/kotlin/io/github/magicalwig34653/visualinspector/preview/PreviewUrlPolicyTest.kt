package io.github.magicalwig34653.visualinspector.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewUrlPolicyTest {
    @Test fun allowsLoopbackHttp() {
        for (u in listOf("http://localhost", "http://localhost:63342/proj/index.html?_ijt=abc", "http://127.0.0.1:5173/#x")) {
            assertTrue(u, PreviewUrlPolicy.isAllowedTarget(u))
        }
    }

    @Test fun rejectsEverythingElse() {
        val bad = listOf(
            null, "", "https://localhost/", "http://example.com/", "http://localhost.evil.com/", "http://127.0.0.2/",
            "http://user@localhost/", "http://localhost@evil.com/", "http://evil.com#@localhost/", "file:///etc/passwd",
            "javascript:alert(1)", "data:text/html,x", "about:blank", "http://localhost:99999/", "http://[::1]/",
            "http://localhost/ x", "http://localhost/\n", "localhost:8080",
        )
        for (u in bad) assertFalse("$u", PreviewUrlPolicy.isAllowedTarget(u))
    }

    @Test fun navigationAlsoAllowsInitialBlank() {
        assertTrue(PreviewUrlPolicy.isAllowedNavigation("about:blank"))
        assertFalse(PreviewUrlPolicy.isAllowedNavigation("about:srcdoc"))
        assertFalse(PreviewUrlPolicy.isAllowedNavigation("https://localhost/"))
    }
}
