package io.github.magicalwig34653.visualinspector.preview

enum class PreviewAvailability {
    AVAILABLE, UNSUPPORTED_IDE, JCEF_UNSUPPORTED;

    companion object {
        /** The product guard wins: in other IDEs the plugin never touches JCEF. */
        fun decide(supportedIde: Boolean, jcefSupported: () -> Boolean): PreviewAvailability = when {
            !supportedIde -> UNSUPPORTED_IDE
            !jcefSupported() -> JCEF_UNSUPPORTED
            else -> AVAILABLE
        }
    }
}
