package io.github.magicalwig34653.visualinspector.server

import org.junit.Assert.*
import org.junit.Test

class PreviewGateTest {
    @Test fun tokenFromQueryCookieAndRejects() {
        val t = PreviewGate.newToken()
        try {
            assertNotEquals(t, PreviewGate.newToken().also(PreviewGate::revoke))
            assertEquals(t, PreviewGate.tokenOf("/p/index.html?x=1&${PreviewGate.PARAM}=$t", null))
            assertEquals(t, PreviewGate.tokenOf("/p/index.html", "a=b; ${PreviewGate.PARAM}=$t"))
            assertNull(PreviewGate.tokenOf("/p/index.html", null))
            assertNull(PreviewGate.tokenOf("/p/index.html?${PreviewGate.PARAM}=nope", "${PreviewGate.PARAM}=nope2"))
            assertNull(PreviewGate.tokenOf("/p/index.html?${PreviewGate.PARAM}=", null))
            assertNull(PreviewGate.tokenOf("/p/index.html?${PreviewGate.PARAM}=${t}x", null))
            assertNull(PreviewGate.tokenOf("/p/index.html?${PreviewGate.PARAM}=${"a".repeat(500)}", null))
        } finally {
            PreviewGate.revoke(t)
        }
        assertNull(PreviewGate.tokenOf("/p/index.html?${PreviewGate.PARAM}=$t", null))
    }
}
