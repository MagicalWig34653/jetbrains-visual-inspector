package io.github.magicalwig34653.visualinspector.server

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationInjectorTest {
    private fun inject(html: String) = LocationInjector.inject(html, "a/b.html")

    @Test fun injectsOffsetOfEveryStartTag() {
        assertEquals(
            """<div data-vi-src="a/b.html:0"><p data-vi-src="a/b.html:5">x</p></div>""",
            inject("<div><p>x</p></div>"),
        )
    }

    @Test fun keepsExistingAttributesAndSelfClosing() {
        assertEquals(
            """<img data-vi-src="a/b.html:0" src="x>y" alt='a>b'/><br data-vi-src="a/b.html:26"/>""",
            inject("""<img src="x>y" alt='a>b'/><br/>"""),
        )
    }

    @Test fun skipsCommentsDoctypeEndTagsAndCdata() {
        val html = "<!doctype html><!-- <b> --><?php <i> ?><![CDATA[<u>]]></x>"
        assertEquals(html, inject(html))
    }

    @Test fun skipsRawTextOfScriptStyleTitleTextarea() {
        assertEquals(
            """<script data-vi-src="a/b.html:0">if (a<b) {"<p>"}</script><p data-vi-src="a/b.html:33">""",
            inject("""<script>if (a<b) {"<p>"}</script><p>"""),
        )
        assertEquals(
            """<title data-vi-src="a/b.html:0"><b></TITLE ><i data-vi-src="a/b.html:19">""",
            inject("<title><b></TITLE ><i>"),
        )
    }

    @Test fun offsetsCountUtf16UnitsOfTheInput() {
        val html = "<p>ä😀</p><b>"
        val out = inject(html)
        assertEquals(html.indexOf("<b>"), Regex("<b data-vi-src=\"a/b.html:(\\d+)\"").find(out)!!.groupValues[1].toInt())
    }

    @Test fun escapesPathInAttribute() {
        assertEquals(
            """<p data-vi-src="a&quot;&amp;&lt;.html:0">""",
            LocationInjector.inject("<p>", "a\"&<.html"),
        )
    }

    @Test fun toleratesTruncatedAndStrayMarkup() {
        assertEquals("a < b <", inject("a < b <"))
        assertEquals("""<p data-vi-src="a/b.html:0" class="x""", inject("<p class=\"x"))
        assertEquals("<!-- open", inject("<!-- open"))
    }
}
