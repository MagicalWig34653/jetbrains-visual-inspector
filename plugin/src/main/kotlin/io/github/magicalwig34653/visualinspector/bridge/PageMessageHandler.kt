package io.github.magicalwig34653.visualinspector.bridge

import java.nio.file.Path

/** Opens [file] at [offset] in the editor (asynchronously on the EDT in the real implementation). */
fun interface EditorOpener { fun open(file: Path, offset: Int) }

/** Shows the non-modal notice for an element without a location. */
fun interface UnmappableNotifier { fun notify(tag: String, label: String?, ancestor: Ancestor?) }

/**
 * Handles validated page messages. `navigate` is resolved against the project, then opened; every failure is
 * an `error` reply and nothing is opened. Returns the serialized reply.
 */
class PageMessageHandler(
    private val projectBaseDir: () -> Path?,
    private val opener: EditorOpener,
    private val notifier: UnmappableNotifier,
) {
    fun handle(raw: String?): String = when (val r = BridgeMessageValidator.validate(raw)) {
        is ValidationResult.Rejected -> BridgeMessageValidator.errorReply(r.code)
        is ValidationResult.Valid -> when (val m = r.message) {
            is BridgeMessage.Navigate -> navigate(m.path, m.offset, m.id)
            is BridgeMessage.Unmappable -> {
                notifier.notify(m.tag, m.label, m.ancestor)
                BridgeMessageValidator.ackFor(m)
            }
            else -> BridgeMessageValidator.ackFor(m)
        }
    }

    /** Also used for the explicit "go to ancestor" notification action. */
    fun navigate(path: String, offset: Int, id: String?): String {
        val base = projectBaseDir() ?: return err(ErrorCode.NOT_FOUND, id)
        return when (val res = ProjectPathResolver.resolve(path, base)) {
            is PathResolution.Failed -> err(res.code, id)
            is PathResolution.Resolved -> {
                opener.open(res.file, offset)
                BridgeMessageValidator.serialize(BridgeMessage.Ack(id))
            }
        }
    }

    private fun err(code: ErrorCode, id: String?) = BridgeMessageValidator.serialize(BridgeMessage.Error(code, id))
}
