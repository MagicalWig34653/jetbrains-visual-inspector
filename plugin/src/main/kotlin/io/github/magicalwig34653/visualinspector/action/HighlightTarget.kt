package io.github.magicalwig34653.visualinspector.action

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

/** Maps an editor caret to the `data-vi-src` offset of the enclosing HTML tag (the offset of its `<`). */
object HighlightTarget {
    fun tagOffsetAt(file: PsiFile, caretOffset: Int): Int? {
        val leaf = file.findElementAt(caretOffset) ?: file.findElementAt((caretOffset - 1).coerceAtLeast(0)) ?: return null
        return PsiTreeUtil.getParentOfType(leaf, XmlTag::class.java, false)?.textRange?.startOffset
    }

    /** Instance to emphasize after [current] among [count] (unknown count: just count up from 0). */
    fun nextIndex(current: Int?, count: Int?): Int {
        val next = (current ?: -1) + 1
        return if (count != null && count > 0) next % count else next.coerceAtMost(9999)
    }
}
