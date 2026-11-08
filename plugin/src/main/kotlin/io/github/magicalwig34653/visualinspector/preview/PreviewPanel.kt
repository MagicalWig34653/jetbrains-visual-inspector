package io.github.magicalwig34653.visualinspector.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.Urls
import io.github.magicalwig34653.visualinspector.bridge.BridgeMessageValidator
import io.github.magicalwig34653.visualinspector.bridge.ValidationResult
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import org.jetbrains.ide.BuiltInServerManager
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Minimal JCEF preview: address field, navigation veto, JS bridge round trip.
 * Must only be created when `JBCefApp.isSupported()` is true. Everything is disposed with [parent].
 */
class PreviewPanel(private val project: Project, parent: Disposable) : Disposable {
    private val browser: JBCefBrowser = JBCefBrowser.createBuilder().setUrl(PreviewUrlPolicy.BLANK).build()
    private val query: JBCefJSQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val status = JBLabel("Bridge: no message yet")
    private val address = JBTextField("http://localhost:63342/")
    @Volatile private var disposed = false
    val component: JComponent = JPanel(BorderLayout())

    init {
        Disposer.register(parent, this)
        Disposer.register(this, browser)
        Disposer.register(this, query)

        query.addHandler { raw ->
            when (val r = BridgeMessageValidator.validate(raw)) {
                is ValidationResult.Valid -> {
                    setStatus("Bridge: received ${r.message::class.simpleName}")
                    JBCefJSQuery.Response(BridgeMessageValidator.reply(r.message))
                }
                is ValidationResult.Rejected -> {
                    setStatus("Bridge: dropped a message (${r.reason})")
                    JBCefJSQuery.Response(null, 400, "rejected")
                }
            }
        }

        val client = browser.jbCefClient
        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                b: CefBrowser?, frame: CefFrame?, request: CefRequest?, userGesture: Boolean, isRedirect: Boolean,
            ): Boolean = !PreviewUrlPolicy.isAllowedNavigation(request?.url) // true cancels
        }, browser.cefBrowser)
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(b: CefBrowser?, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean =
                true // never open popups
        }, browser.cefBrowser)
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true || !PreviewUrlPolicy.isAllowedTarget(frame.url)) return
                // Defines window.__viSend(jsonString); then the plugin pings itself once as the round-trip proof.
                val send = query.inject(
                    "msg",
                    "function(r){window.dispatchEvent(new CustomEvent('vi-response',{detail:r}));}",
                    "function(c,m){window.dispatchEvent(new CustomEvent('vi-error',{detail:c}));}",
                )
                b?.executeJavaScript(
                    "window.__viSend=function(msg){$send};window.__viSend('{\"v\":1,\"type\":\"ping\",\"id\":\"self-check\"}');",
                    frame.url, 0,
                )
            }
        }, browser.cefBrowser)

        val go = JButton("Go").apply { addActionListener { load(address.text.trim()) } }
        address.addActionListener { load(address.text.trim()) }
        val builtIn = JButton("Built-in server").apply { addActionListener { loadBuiltInServer() } }
        val bar = JPanel(BorderLayout()).apply {
            add(address, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply { add(go); add(builtIn) }, BorderLayout.EAST)
        }
        component.add(bar, BorderLayout.NORTH)
        component.add(browser.component, BorderLayout.CENTER)
        component.add(status, BorderLayout.SOUTH)
    }

    fun load(url: String) {
        if (!PreviewUrlPolicy.isAllowedTarget(url)) {
            Messages.showErrorDialog(project, "Only http://localhost and http://127.0.0.1 URLs can be previewed.", "Visual Inspector")
            return
        }
        browser.loadURL(url)
    }

    private fun loadBuiltInServer() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val manager = BuiltInServerManager.getInstance()
            val base = "http://127.0.0.1:${manager.waitForStart().port}/${project.name}/"
            val url = Urls.parseEncoded(base)?.let { manager.addAuthToken(it).toExternalForm() } ?: base
            ApplicationManager.getApplication().invokeLater({
                if (!disposed) {
                    address.text = url
                    load(url)
                }
            }, ModalityState.any())
        }
    }

    private fun setStatus(text: String) {
        ApplicationManager.getApplication().invokeLater({ if (!disposed) status.text = text }, ModalityState.any())
    }

    override fun dispose() { disposed = true }
}
