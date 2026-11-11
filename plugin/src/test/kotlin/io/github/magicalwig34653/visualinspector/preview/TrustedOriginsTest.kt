package io.github.magicalwig34653.visualinspector.preview

import org.junit.Assert.*
import org.junit.Test

class TrustedOriginsTest {
    @Test fun originIncludesSchemeHostAndPort() {
        assertEquals("http://localhost:8000", TrustedOrigins.originOf("http://localhost:8000/a/b.php?x=1"))
        assertEquals("http://127.0.0.1:80", TrustedOrigins.originOf("http://127.0.0.1/"))
        assertNull(TrustedOrigins.originOf("https://localhost:8000/"))
        assertNull(TrustedOrigins.originOf("http://evil.example:8000/"))
        assertNull(TrustedOrigins.originOf("http://localhost.evil.example/"))
        assertNull(TrustedOrigins.originOf("http://user@localhost:8000/"))
        assertNull(TrustedOrigins.originOf(null))
    }

    @Test fun externalOriginNeedsConfirmationAndMatchesExactly() {
        val ok = setOf("http://localhost:8000")
        assertFalse(TrustedOrigins.isTrusted("http://localhost:8000", null, emptySet()))
        assertTrue(TrustedOrigins.isTrusted("http://localhost:8000", null, ok))
        assertFalse(TrustedOrigins.isTrusted("http://localhost:8001", null, ok))
        assertFalse(TrustedOrigins.isTrusted("http://127.0.0.1:8000", null, ok))
        assertFalse(TrustedOrigins.isTrusted("http://localhost:8000/x", null, ok)) // not an origin string
        assertFalse(TrustedOrigins.isTrusted(null, null, ok))
    }

    @Test fun builtInOriginIsTrustedOnlyWhenPassed() {
        assertTrue(TrustedOrigins.isTrusted("http://127.0.0.1:63342", "http://127.0.0.1:63342", emptySet()))
        assertFalse(TrustedOrigins.isTrusted("http://127.0.0.1:63342", null, emptySet()))
        assertFalse(TrustedOrigins.isTrusted("http://localhost:63342", "http://127.0.0.1:63342", emptySet()))
    }

    @Test fun serviceStoresOnlyCanonicalOriginsAndRevokes() {
        val s = TrustedOriginsService()
        assertFalse(s.trust("http://evil.example:80"))
        assertFalse(s.trust("http://localhost:8000/path"))
        assertFalse(s.trust("http://localhost"))
        assertTrue(s.trust("http://localhost:8000"))
        assertEquals(setOf("http://localhost:8000"), s.confirmed())
        s.revoke("http://localhost:8000")
        assertTrue(s.confirmed().isEmpty())
    }

    @Test fun tamperedStateIsIgnoredOnRead() {
        val s = TrustedOriginsService()
        s.loadState(TrustedOriginsService.OriginsState().apply { origins = mutableListOf("http://evil.example:80", "http://localhost:9000") })
        assertEquals(setOf("http://localhost:9000"), s.confirmed())
    }

    @Test fun pagePathsKeepOnlyValidProjectPaths() {
        assertEquals(setOf("index.php", "partials/card.php"), PagePaths.parse("""["index.php","partials/card.php"]"""))
        assertEquals(setOf("a.php"), PagePaths.parse("""["a.php","../x","/etc/passwd",5,null,"C:x"]"""))
        assertTrue(PagePaths.parse("not json").isEmpty())
        assertTrue(PagePaths.parse("""{"a":1}""").isEmpty())
        assertTrue(PagePaths.parse(null).isEmpty())
        assertTrue(PagePaths.parse("[\"" + "a".repeat(70000) + "\"]").isEmpty())
    }

    @Test fun hostAndPortSpellingTricksAreNotTrusted() {
        for (u in listOf("http://LOCALHOST:8000/", "http://localhost:0/", "http://localhost:65536/", "http://[::1]:8000/",
                         "http://0.0.0.0:8000/", "http://127.1:8000/", "http://localhost:8000@evil.example/", "http://localhost%2eevil:80/")) {
            assertNull(u, TrustedOrigins.originOf(u))
        }
        assertEquals("http://localhost:65535", TrustedOrigins.originOf("http://localhost:65535/"))
    }

    @Test fun onlyTheCanonicalOriginSpellingCanBeTrusted() {
        val ok = setOf("http://localhost:8000")
        for (o in listOf("http://localhost:08000", "http://localhost:8000/", "http://localhost:8000?x", "http://localhost:8000#", "HTTP://localhost:8000", " http://localhost:8000")) {
            assertFalse(o, TrustedOrigins.isTrusted(o, null, ok))
            assertNull(o, TrustedOrigins.canonical(o))
        }
        assertEquals("http://localhost:80", TrustedOrigins.canonical("http://localhost:80"))
        assertNull("default-port shorthand is not canonical", TrustedOrigins.canonical("http://localhost"))
    }

    @Test fun trustingTwiceKeepsOneEntryAndRevokingAnUnknownOriginIsHarmless() {
        val s = TrustedOriginsService()
        assertTrue(s.trust("http://localhost:8000"))
        assertTrue(s.trust("http://localhost:8000"))
        assertEquals(1, s.state.origins.size)
        s.revoke("http://localhost:9999")
        assertEquals(setOf("http://localhost:8000"), s.confirmed())
    }

    @Test fun pagePathsAreCappedDeduplicatedAndRejectNesting() {
        val many = (1..300).joinToString(",", "[", "]") { "\"p$it.php\"" }
        assertEquals(200, PagePaths.parse(many).size)
        assertEquals(setOf("a.php"), PagePaths.parse("""["a.php","a.php"]"""))
        assertEquals(setOf("a.php"), PagePaths.parse("""["a.php",["b.php"],{"p":"c.php"}]"""))
        assertTrue(PagePaths.parse("[").isEmpty())
        assertTrue(PagePaths.parse("").isEmpty())
        assertTrue(PagePaths.parse("null").isEmpty())
        assertTrue(PagePaths.parse("\"a.php\"").isEmpty())
    }
}
