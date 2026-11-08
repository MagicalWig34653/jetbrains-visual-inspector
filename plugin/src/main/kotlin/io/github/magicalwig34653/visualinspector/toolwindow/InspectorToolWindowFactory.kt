package io.github.magicalwig34653.visualinspector.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import javax.swing.SwingConstants

/** Placeholder tool window. The JCEF preview replaces this label in roadmap phase 2. */
class InspectorToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val placeholder = JBLabel("Visual Inspector is not implemented yet.", SwingConstants.CENTER)
        val content = ContentFactory.getInstance().createContent(placeholder, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
