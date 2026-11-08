package io.github.magicalwig34653.visualinspector.preview

import com.google.gson.GsonBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.guessProjectDir
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
import io.github.magicalwig34653.visualinspector.action.PreviewHost
import io.github.magicalwig34653.visualinspector.bridge.BridgeMessage
import io.github.magicalwig34653.visualinspector.bridge.BridgeMessageValidator
import io.github.magicalwig34653.visualinspector.bridge.PageMessageHandler
import io.github.magicalwig34653.visualinspector.server.InspectorBundle
import io.github.magicalwig34653.visualinspector.server.PreviewGate
import io.github.magicalwig34653.visualinspector.bridge.ValidationResult
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import org.jetbrains.ide.BuiltInServerManager
import java.net.URI
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
    private val token = PreviewGate.newToken()
    private val host = project.service<PreviewHost>()
    private val messageHandler: PageMessageHandler = PageMessageHandler(
        { project.guessProjectDir()?.toNioPath() },
        IdeEditorOpener(project),
        IdeUnmappableNotifier(project) { path, offset -> messageHandler.navigate(path, offset, null) },
    )
    /** Origin of the last token-bearing URL; later same-origin pages carry the token cookie instead. */
    @Volatile private var previewOrigin: String? = null
    @Volatile private var disposed = false
    val component: JComponent = JPanel(BorderLayout())

    init {
        Disposer.register(parent, this)
        host.panel = this
        Disposer.register(this, browser)
        Disposer.register(this, query)

        query.addHandler { raw ->
            val reply = messageHandler.handle(raw)
            BridgeMessageValidator.validate(raw).let { r ->
                if (r is ValidationResult.Valid) {
                    val m = r.message
                    if (m is BridgeMessage.Navigate) host.lastNavigate = m
                    setStatus("Bridge: received ${m::class.simpleName}")
                } else if (r is ValidationResult.Rejected) setStatus("Bridge: dropped a message (${r.code.wire})")
            }
            JBCefJSQuery.Response(reply)
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
                // Only previews opened with our token get the inspector (the same token gates attribute injection).
                if (originOf(frame.url) != previewOrigin) return
                val bundle = InspectorBundle.source
                if (bundle == null) { setStatus(InspectorBundle.MISSING_MESSAGE); return }
                val send = query.inject(
                    "msg",
                    "function(r){}",
                    "function(c,m){}",
                )
                b?.executeJavaScript(
                    "window.__viSend=function(msg){$send};" + InspectorBundle.bootstrap(bundle),
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
            val url = previewUrl(null)
            ApplicationManager.getApplication().invokeLater({
                if (!disposed) {
                    address.text = url
                    previewOrigin = originOf(url)
                    load(url)
                }
            }, ModalityState.any())
        }
    }

    /** Built-in server URL (with IDE auth token and preview token) of a project-relative file, or the project root. */
    private fun previewUrl(relativePath: String?): String {
        val manager = BuiltInServerManager.getInstance()
        val path = "/" + project.name + "/" + (relativePath ?: "")
        val base = "http://127.0.0.1:${manager.waitForStart().port}" + URI(null, null, path, null).rawPath
        // addAuthToken replaces any query, so the preview token is appended after it.
        val authed = Urls.parseEncoded(base)?.let { manager.addAuthToken(it).toExternalForm() } ?: base
        return authed + (if ('?' in authed) "&" else "?") + "${PreviewGate.PARAM}=$token"
    }

    /** Opens a project HTML file in the preview. Safe to call from the EDT. */
    fun openFile(relativePath: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val url = previewUrl(relativePath)
            ApplicationManager.getApplication().invokeLater({
                if (!disposed) {
                    address.text = url
                    previewOrigin = originOf(url)
                    load(url)
                }
            }, ModalityState.any())
        }
    }

    /** IDE to page message (already built by the validated serializer). */
    fun sendToPage(message: BridgeMessage) {
        val json = BridgeMessageValidator.serialize(message)
        browser.cefBrowser.executeJavaScript(
            "if(window.__viReceive)window.__viReceive(${GsonBuilder().create().toJson(json)});", PreviewUrlPolicy.BLANK, 0)
    }

    private fun originOf(url: String?): String? =
        try { URI(url ?: return null).let { "${it.scheme}://${it.host}:${it.port}" } } catch (e: java.net.URISyntaxException) { null }

    private fun setStatus(text: String) {
        ApplicationManager.getApplication().invokeLater({ if (!disposed) status.text = text }, ModalityState.any())
    }

    override fun dispose() {
        disposed = true
        PreviewGate.revoke(token)
        if (host.panel === this) host.panel = null
    }
}
