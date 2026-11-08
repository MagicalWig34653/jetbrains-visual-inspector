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

    private fun handler(base: Path?) = PageMessageHandler({ base }, { f, o -> opened += f to o }, { t, l, a -> notices += Triple(t, l, a) })

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
}
