package io.github.magicalwig34653.visualinspector.preview

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.magicalwig34653.visualinspector.bridge.Ancestor
import io.github.magicalwig34653.visualinspector.bridge.EditorOpener
import io.github.magicalwig34653.visualinspector.bridge.UnmappableNotifier
import java.nio.file.Path

/** Opens the file in the editor on the EDT with the public [OpenFileDescriptor] API. */
class IdeEditorOpener(private val project: Project) : EditorOpener {
    override fun open(file: Path, offset: Int) {
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: return@invokeLater
            val length = FileDocumentManager.getInstance().getDocument(vf)?.textLength ?: Int.MAX_VALUE
            OpenFileDescriptor(project, vf, offset.coerceIn(0, length)).navigate(true)
        }, ModalityState.nonModal())
    }
}

/** Non-modal balloon; an ancestor is offered only as an explicit, labeled action (ADR 0009). */
class IdeUnmappableNotifier(
    private val project: Project,
    private val goToAncestor: (path: String, offset: Int) -> Unit,
) : UnmappableNotifier {
    override fun notify(tag: String, label: String?, ancestor: Ancestor?) {
        val what = StringUtil.escapeXmlEntities(label ?: "<$tag>")
        val n = NotificationGroupManager.getInstance().getNotificationGroup("Visual Inspector")
            .createNotification("Visual Inspector", "$what has no source location (generated at runtime).", NotificationType.INFORMATION)
        if (ancestor != null) {
            val name = StringUtil.escapeXmlEntities(ancestor.label ?: ancestor.path)
            n.addAction(NotificationAction.createSimple("Go to nearest ancestor with a location: $name") {
                goToAncestor(ancestor.path, ancestor.offset)
            })
        }
        n.notify(project)
    }
}
