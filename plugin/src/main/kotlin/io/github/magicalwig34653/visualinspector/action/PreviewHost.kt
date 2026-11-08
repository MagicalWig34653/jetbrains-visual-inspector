package io.github.magicalwig34653.visualinspector.action

import com.intellij.openapi.components.Service
import io.github.magicalwig34653.visualinspector.bridge.BridgeMessage
import io.github.magicalwig34653.visualinspector.preview.PreviewPanel

/** Per-project handle to the live preview panel plus the last selection, for the editor actions. */
@Service(Service.Level.PROJECT)
class PreviewHost {
    @Volatile var panel: PreviewPanel? = null
    @Volatile var lastNavigate: BridgeMessage.Navigate? = null
    @Volatile var lastHighlight: BridgeMessage.Highlight? = null
}
