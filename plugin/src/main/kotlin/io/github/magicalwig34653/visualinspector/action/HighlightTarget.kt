package io.github.magicalwig34653.visualinspector.action

import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

/** Maps an editor caret to the `data-vi-src` offset of the enclosing HTML tag (the offset of its `<`). */
object HighlightTarget {
    fun tagOffsetAt(file: PsiFile, caretOffset: Int): Int? {
        // A .php file (PhpStorm) has several views; the HTML view is the markup with PHP blocks as foreign parts.
        // It spans the same text, so its offsets equal the file offsets. Other files use the file itself.
        val view = file.viewProvider.getPsi(HTMLLanguage.INSTANCE) ?: file
        val leaf = view.findElementAt(caretOffset) ?: view.findElementAt((caretOffset - 1).coerceAtLeast(0)) ?: return null
        return PsiTreeUtil.getParentOfType(leaf, XmlTag::class.java, false)?.textRange?.startOffset
    }

    /** Instance to emphasize after [current] among [count] (unknown count: just count up from 0). */
    fun nextIndex(current: Int?, count: Int?): Int {
        val next = (current ?: -1) + 1
        return if (count != null && count > 0) next % count else next.coerceAtMost(9999)
    }
}

/** Why a highlight could not be sent; the text is shown in a non-modal notification. */
object HighlightMessages {
    const val NO_PREVIEW = "No Visual Inspector preview is open. Open the tool window and use \"Open Current HTML File in Visual Inspector\"."
    const val NO_TAG = "The caret is not inside an HTML tag that has a source location."
    fun notInPage(rel: String) =
        "The page in the preview has no elements mapped to $rel. For PHP, open a page that includes this file " +
            "with the prepend script enabled (see docs/php.md)."
    fun untrusted() = "The page in the preview is not trusted yet. Use \"Trust this origin\" in the preview."
    fun notServed(rel: String, shown: String?) =
        "The preview is " + (if (shown == null) "not showing a page served by the IDE" else "showing $shown") +
            ", not $rel. Use \"Open Current HTML File in Visual Inspector\"."
}
