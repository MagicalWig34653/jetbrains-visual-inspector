package io.github.magicalwig34653.visualinspector.bridge

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

class PageMessageHandlerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val opened = mutableListOf<Pair<Path, Int>>()
    private val notices = mutableListOf<Triple<String, String?, Ancestor?>>()

    private fun handler(base: Path?, trusted: Boolean = true) =
        PageMessageHandler({ base }, { f, o -> opened += f to o }, { t, l, a -> notices += Triple(t, l, a) }, { trusted })

    @Test fun navigateOpensResolvedFileAndAcks() {
        val dir = tmp.newFolder().toPath()
        val f = dir.resolve("index.html").also { java.nio.file.Files.writeString(it, "<p>") }
        val reply = handler(dir).handle("""{"v":1,"type":"navigate","path":"index.html","offset":3,"id":"a1"}""")
        assertEquals("""{"v":1,"type":"ack","ref":"a1"}""", reply)
        assertEquals(listOf(f.toRealPath() to 3), opened)
    }

    @Test fun navigateFailuresOpenNothing() {
        val dir = tmp.newFolder().toPath()
        val h = handler(dir)
        assertTrue(h.handle("""{"v":1,"type":"navigate","path":"missing.html","offset":0,"id":"b"}""").contains(""""code":"not_found""""))
        assertTrue(h.handle("""{"v":1,"type":"navigate","path":"../x","offset":0}""").contains(""""code":"bad_path""""))
        assertTrue(h.handle("""{"v":1,"type":"navigate","path":"a","offset":0,"extra":1}""").contains(""""code":"unknown_field""""))
        assertTrue(handler(null).handle("""{"v":1,"type":"navigate","path":"a","offset":0}""").contains("not_found"))
        assertTrue(opened.isEmpty())
    }

    @Test fun unmappableNotifiesAndNeverNavigates() {
        val dir = tmp.newFolder().toPath()
        val reply = handler(dir).handle("""{"v":1,"type":"unmappable","tag":"div","ancestor":{"path":"a.html","offset":5,"label":"ul"}}""")
        assertTrue(reply.contains(""""type":"ack""""))
        assertEquals(listOf(Triple("div", null, Ancestor("a.html", 5, "ul"))), notices)
        assertTrue(opened.isEmpty())
    }

    @Test fun untrustedOriginIsDeniedForEveryMessage() {
        val dir = tmp.newFolder().toPath()
        java.nio.file.Files.writeString(dir.resolve("index.html"), "<p>")
        val h = handler(dir, trusted = false)
        val denied = """{"v":1,"type":"error","code":"denied"}"""
        assertEquals(denied, h.handle("""{"v":1,"type":"navigate","path":"index.html","offset":0}"""))
        assertEquals(denied, h.handle("""{"v":1,"type":"unmappable","tag":"div"}"""))
        assertEquals(denied, h.handle("""{"v":1,"type":"hello"}"""))
        assertTrue(opened.isEmpty() && notices.isEmpty())
    }

    @Test fun trustIsEvaluatedPerMessage() {
        var trusted = true
        val h = PageMessageHandler({ null }, { _, _ -> }, { _, _, _ -> }, { trusted })
        assertTrue(h.handle("""{"v":1,"type":"hello"}""").contains("ack"))
        trusted = false
        assertTrue(h.handle("""{"v":1,"type":"hello"}""").contains("denied"))
    }

    private fun code(reply: String) = Regex(""""code":"(\w+)"""").find(reply)?.groupValues?.get(1)

    @Test fun malformedInputIsAnErrorReplyAndNeverOpensAnything() {
        val dir = tmp.newFolder().toPath()
        val h = handler(dir)
        assertEquals("bad_json", code(h.handle(null)))
        assertEquals("bad_json", code(h.handle("")))
        assertEquals("bad_json", code(h.handle("[]")))
        assertEquals("too_large", code(h.handle("x".repeat(5000))))
        assertEquals("bad_version", code(h.handle("""{"v":2,"type":"hello"}""")))
        assertEquals("unknown_type", code(h.handle("""{"v":1,"type":"eval","code":"x"}""")))
        assertEquals("unknown_type", code(h.handle("""{"v":1,"type":"highlight","path":"a.html","offset":1}"""))) // IDE type from the page
        assertEquals("bad_json", code(h.handle("""{"v":1,"type":"navigate","path":"a.html","path":"b.html","offset":1}""")))
        assertTrue(opened.isEmpty() && notices.isEmpty())
    }

    @Test fun helloAcksWithItsIdAndTouchesNothing() {
        val reply = handler(tmp.newFolder().toPath()).handle("""{"v":1,"type":"hello","id":"h-1"}""")
        assertEquals("""{"v":1,"type":"ack","ref":"h-1"}""", reply)
        assertTrue(opened.isEmpty() && notices.isEmpty())
    }

    @Test fun navigateToDirectoryOrSymlinkEscapeIsAnErrorWithTheRequestId() {
        val dir = tmp.newFolder().toPath()
        java.nio.file.Files.createDirectories(dir.resolve("sub"))
        val outside = tmp.newFolder().toPath().resolve("secret.html").also { java.nio.file.Files.writeString(it, "s") }
        try { java.nio.file.Files.createSymbolicLink(dir.resolve("leak.html"), outside) } catch (e: UnsupportedOperationException) { return }
        val h = handler(dir)
        assertEquals("""{"v":1,"type":"error","code":"not_found","ref":"d1"}""", h.handle("""{"v":1,"type":"navigate","path":"sub","offset":0,"id":"d1"}"""))
        assertEquals("""{"v":1,"type":"error","code":"denied","ref":"d2"}""", h.handle("""{"v":1,"type":"navigate","path":"leak.html","offset":0,"id":"d2"}"""))
        assertTrue(opened.isEmpty())
    }

    @Test fun explicitAncestorNavigationIsConfinedLikeAPageNavigation() {
        val dir = tmp.newFolder().toPath()
        val f = dir.resolve("a.html").also { java.nio.file.Files.writeString(it, "<p>") }
        val h = handler(dir)
        assertEquals("bad_path", code(h.navigate("../a.html", 0, "x")))
        assertEquals("not_found", code(h.navigate("none.html", 0, null)))
        assertTrue(opened.isEmpty())
        assertEquals("""{"v":1,"type":"ack","ref":"x"}""", h.navigate("a.html", 1, "x"))
        assertEquals(listOf(f.toRealPath() to 1), opened)
    }

    @Test fun projectBaseDirIsReadPerMessage() {
        val dir = tmp.newFolder().toPath()
        java.nio.file.Files.writeString(dir.resolve("a.html"), "<p>")
        var base: Path? = null
        val h = PageMessageHandler({ base }, { f, o -> opened += f to o }, { _, _, _ -> }, { true })
        assertEquals("not_found", code(h.handle("""{"v":1,"type":"navigate","path":"a.html","offset":0}""")))
        base = dir
        assertTrue(h.handle("""{"v":1,"type":"navigate","path":"a.html","offset":0}""").contains("ack"))
    }

    @Test fun unmappableWithoutAncestorStillNotifiesOnceWithLabel() {
        handler(tmp.newFolder().toPath()).handle("""{"v":1,"type":"unmappable","tag":"canvas","label":"canvas.chart","id":"u"}""")
        assertEquals(listOf(Triple("canvas", "canvas.chart" as String?, null as Ancestor?)), notices)
    }
}
