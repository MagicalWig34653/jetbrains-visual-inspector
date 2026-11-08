package io.github.magicalwig34653.visualinspector.server

/**
 * Adds a compact source location attribute to the start tag of every HTML element, in memory.
 *
 * The attribute value is `<path>:<offset>` where `offset` is the UTF-16 index of the `<` of the start tag in
 * the input text, which equals the IDE `Document` / PSI offset when the input is the document text. The input
 * is never parsed into a tree, so parser normalization (implicit `tbody`, closed `p`) does not move offsets;
 * elements the browser creates implicitly simply have no attribute.
 *
 * Comments, doctype, processing instructions, CDATA, end tags and the raw text of `script`, `style`,
 * `textarea` and `title` are left untouched.
 */
object LocationInjector {
    const val ATTRIBUTE = "data-vi-src"

    private val RAW_TEXT = setOf("script", "style", "textarea", "title")

    fun inject(html: String, path: String): String {
        val safePath = escapeAttribute(path)
        val out = StringBuilder(html.length + html.length / 4)
        var copied = 0
        var i = 0
        while (i < html.length) {
            val lt = html.indexOf('<', i)
            if (lt < 0) break
            i = when {
                html.startsWith("<!--", lt) -> skipTo(html, "-->", lt + 4)
                html.startsWith("<![CDATA[", lt) -> skipTo(html, "]]>", lt + 9)
                html.startsWith("<!", lt) || html.startsWith("<?", lt) || html.startsWith("</", lt) ->
                    skipTo(html, ">", lt + 2)
                lt + 1 < html.length && html[lt + 1].isAsciiLetter() -> {
                    var nameEnd = lt + 1
                    while (nameEnd < html.length && !html[nameEnd].isWhitespace() && html[nameEnd] != '/' && html[nameEnd] != '>') nameEnd++
                    out.append(html, copied, nameEnd).append(' ').append(ATTRIBUTE).append("=\"")
                        .append(safePath).append(':').append(lt).append('"')
                    copied = nameEnd
                    val tagEnd = findTagEnd(html, nameEnd)
                    val name = html.substring(lt + 1, nameEnd).lowercase()
                    if (name in RAW_TEXT && tagEnd < html.length && html[tagEnd - 1] != '/') {
                        rawTextEnd(html, name, tagEnd)
                    } else {
                        tagEnd
                    }
                }
                else -> lt + 1
            }
        }
        return out.append(html, copied, html.length).toString()
    }

    fun escapeAttribute(value: String): String =
        value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

    /** Index after [terminator], or the text length when it is missing. */
    private fun skipTo(html: String, terminator: String, from: Int): Int {
        val end = html.indexOf(terminator, from)
        return if (end < 0) html.length else end + terminator.length
    }

    /** Index after the `>` that ends a start tag; quotes count only where an attribute value starts. */
    private fun findTagEnd(html: String, from: Int): Int {
        var i = from
        while (i < html.length) {
            val c = html[i]
            if (c == '>') return i + 1
            if (c == '=') {
                var j = i + 1
                while (j < html.length && html[j].isWhitespace()) j++
                if (j < html.length && (html[j] == '"' || html[j] == '\'')) {
                    val close = html.indexOf(html[j], j + 1)
                    if (close < 0) return html.length
                    i = close + 1
                    continue
                }
            }
            i++
        }
        return html.length
    }

    private fun rawTextEnd(html: String, name: String, from: Int): Int {
        val m = Regex("</" + Regex.escape(name) + "(?=[\\s/>])", RegexOption.IGNORE_CASE).find(html, from)
        return m?.range?.first ?: html.length
    }
}
