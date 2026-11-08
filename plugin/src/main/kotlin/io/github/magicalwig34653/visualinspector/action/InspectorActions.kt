package io.github.magicalwig34653.visualinspector.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import io.github.magicalwig34653.visualinspector.bridge.BridgeMessage
import io.github.magicalwig34653.visualinspector.product.ProductGuard

private const val TOOL_WINDOW_ID = "Visual Inspector"

private fun VirtualFile.isHtml() = extension?.lowercase() in setOf("html", "htm")
private fun VirtualFile.isMarkup() = isHtml() || extension?.lowercase() == "php"
private fun Project.relativePathOf(file: VirtualFile): String? =
    guessProjectDir()?.let { VfsUtilCore.getRelativePath(file, it) }

abstract class InspectorAction : AnAction(), DumbAware {
    protected open fun appliesTo(file: VirtualFile) = file.isHtml()
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = ProductGuard.isSupportedIde() && e.project != null && file != null && appliesTo(file)
    }
}

/** Opens the current HTML file in the preview through the built-in server, with the preview token. */
class OpenInInspectorAction : InspectorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val rel = project.relativePathOf(file) ?: return
        val tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        tw.show { project.service<PreviewHost>().panel?.openFile(rel) }
    }
}

/** Focuses the preview's address bar so the user can type the URL of a PHP page. Starts no process. */
class OpenPhpPageAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ProductGuard.isSupportedIde() && e.project != null
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        tw.show { project.service<PreviewHost>().panel?.focusAddressBar() }
    }
}

/** Sends `highlight` for the tag around the caret. Manual only; there is no automatic caret listener. */
class HighlightInPreviewAction : InspectorAction() {
    override fun appliesTo(file: VirtualFile) = file.isMarkup()
    override fun actionPerformed(e: AnActionEvent) = highlight(e, next = false)
}

/** Re-sends `highlight` for the same tag with the next instance emphasized. */
class NextInstanceAction : InspectorAction() {
    override fun appliesTo(file: VirtualFile) = file.isMarkup()
    override fun actionPerformed(e: AnActionEvent) = highlight(e, next = true)
}

private fun warn(project: Project, text: String) {
    NotificationGroupManager.getInstance().getNotificationGroup("Visual Inspector")
        .createNotification("Visual Inspector", StringUtil.escapeXmlEntities(text), NotificationType.WARNING)
        .notify(project)
}

private fun highlight(e: AnActionEvent, next: Boolean) {
    val project = e.project ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val host = project.service<PreviewHost>()
    val panel = host.panel ?: return warn(project, HighlightMessages.NO_PREVIEW)
    val rel = project.relativePathOf(file) ?: return
    if (!panel.showsFile(rel)) return warn(
        project,
        if (panel.loadedRelativePath == null) HighlightMessages.notInPage(rel) else HighlightMessages.notServed(rel, panel.loadedRelativePath),
    )
    val psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    val offset = psi?.let { HighlightTarget.tagOffsetAt(it, editor.caretModel.offset) }
        ?: return warn(project, HighlightMessages.NO_TAG)
    val last = host.lastHighlight
    val same = last != null && last.path == rel && last.offset == offset
    val count = host.lastNavigate?.takeIf { it.path == rel && it.offset == offset }?.count
    val index = if (next) HighlightTarget.nextIndex(if (same) last?.index else null, count) else null
    val msg = BridgeMessage.Highlight(rel, offset, index, null)
    host.lastHighlight = msg
    panel.sendToPage(msg)
    panel.showStatus("Highlight sent: $rel:$offset" + (index?.let { " (instance ${it + 1})" } ?: ""))
}
