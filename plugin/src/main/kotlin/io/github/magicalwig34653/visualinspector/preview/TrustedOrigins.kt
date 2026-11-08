package io.github.magicalwig34653.visualinspector.preview

import com.google.gson.JsonParser
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import io.github.magicalwig34653.visualinspector.bridge.BridgeMessageValidator
import java.net.URI

/**
 * Which origins may run the inspector overlay and bridge (ADR 0010). Pure logic, no IDE types.
 * An origin is exactly `http://localhost:PORT` or `http://127.0.0.1:PORT`; scheme, host and port all count.
 */
object TrustedOrigins {
    /** Normalized origin of an allowed preview URL (see [PreviewUrlPolicy]), or null. A missing port means 80. */
    fun originOf(url: String?): String? {
        if (!PreviewUrlPolicy.isAllowedTarget(url)) return null
        val uri = URI(url!!)
        return "http://${uri.host}:${if (uri.port == -1) 80 else uri.port}"
    }

    /** [origin] itself when it is a canonical origin string (no path, no default-port shorthand), else null. */
    fun canonical(origin: String?): String? = originOf(origin)?.takeIf { it == origin }

    /** The built-in server origin counts only for pages opened with the preview token; the caller passes it then. */
    fun isTrusted(origin: String?, builtIn: String?, confirmed: Collection<String>): Boolean {
        val o = canonical(origin) ?: return false
        return o == canonical(builtIn) || o in confirmed
    }
}

/** Paths of the files whose elements are in the loaded page, reported by the host script (untrusted data). */
object PagePaths {
    private const val MAX_RAW = 64 * 1024
    private const val MAX_PATHS = 200

    fun parse(raw: String?): Set<String> {
        if (raw == null || raw.length > MAX_RAW) return emptySet()
        return try {
            val arr = JsonParser.parseString(raw).takeIf { it.isJsonArray }?.asJsonArray ?: return emptySet()
            arr.asSequence().take(MAX_PATHS)
                .mapNotNull { e -> if (e.isJsonPrimitive && e.asJsonPrimitive.isString) e.asString else null }
                .filter(BridgeMessageValidator::isValidPath).toSet()
        } catch (e: RuntimeException) { emptySet() }
    }
}

/**
 * Origins the user confirmed for this project, kept in the per-user workspace file (not shared through VCS).
 * Entries are re-validated on every read, so edited state cannot widen the allowed host set.
 */
@Service(Service.Level.PROJECT)
@State(name = "VisualInspectorTrustedOrigins", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class TrustedOriginsService : PersistentStateComponent<TrustedOriginsService.OriginsState> {
    class OriginsState { var origins: MutableList<String> = mutableListOf() }

    private var state = OriginsState()

    @Synchronized override fun getState(): OriginsState = state
    @Synchronized override fun loadState(s: OriginsState) { state = s }

    @Synchronized fun confirmed(): Set<String> = state.origins.mapNotNull(TrustedOrigins::canonical).toSet()
    @Synchronized fun trust(origin: String): Boolean {
        val o = TrustedOrigins.canonical(origin) ?: return false
        if (o !in state.origins) state.origins.add(o)
        return true
    }
    @Synchronized fun revoke(origin: String) { state.origins.removeAll { it == origin } }
}
