package io.github.magicalwig34653.visualinspector.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import io.github.magicalwig34653.visualinspector.preview.PreviewAvailability
import io.github.magicalwig34653.visualinspector.preview.PreviewPanel
import io.github.magicalwig34653.visualinspector.product.ProductGuard
import javax.swing.JComponent
import javax.swing.SwingConstants

/** Tool window with the JCEF preview, or a message when the IDE or its JCEF support is missing. */
class InspectorToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val component = createComponent(
            project, toolWindow.disposable,
            PreviewAvailability.decide(ProductGuard.isSupportedIde(), JBCefApp::isSupported),
        )
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(component, null, false))
    }

    companion object {
        const val UNSUPPORTED_IDE_MESSAGE =
            "Visual Inspector only works in WebStorm and PhpStorm. It is inactive in this IDE."
        const val JCEF_UNSUPPORTED_MESSAGE =
            "The embedded browser (JCEF) is not supported by this IDE runtime, so the preview is unavailable."

        fun createComponent(project: Project, parent: Disposable, availability: PreviewAvailability): JComponent =
            when (availability) {
                PreviewAvailability.UNSUPPORTED_IDE -> JBLabel(UNSUPPORTED_IDE_MESSAGE, SwingConstants.CENTER)
                PreviewAvailability.JCEF_UNSUPPORTED -> JBLabel(JCEF_UNSUPPORTED_MESSAGE, SwingConstants.CENTER)
                PreviewAvailability.AVAILABLE -> PreviewPanel(project, parent).component
            }
    }
}
