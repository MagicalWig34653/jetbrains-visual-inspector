package io.github.magicalwig34653.visualinspector.bridge

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * IDE-side path confinement (docs/protocol.md "Paths", ADR 0005). Turns an already syntax-checked page path
 * into a canonical regular file inside the project, or a failure code. Symlinks are resolved first, so a
 * link that leaves the project is `denied`. Page input is never trusted: syntax is re-checked here.
 */
sealed interface PathResolution {
    data class Resolved(val file: Path) : PathResolution
    data class Failed(val code: ErrorCode) : PathResolution
}

object ProjectPathResolver {
    fun resolve(relativePath: String, projectBaseDir: Path): PathResolution {
        if (!BridgeMessageValidator.isValidPath(relativePath)) return PathResolution.Failed(ErrorCode.BAD_PATH)
        return try {
            val base = projectBaseDir.toRealPath()
            val real = base.resolve(relativePath).toRealPath() // follows symlinks, throws if missing
            when {
                !real.startsWith(base) -> PathResolution.Failed(ErrorCode.DENIED)
                !Files.isRegularFile(real) -> PathResolution.Failed(ErrorCode.NOT_FOUND)
                else -> PathResolution.Resolved(real)
            }
        } catch (e: IOException) {
            PathResolution.Failed(ErrorCode.NOT_FOUND)
        } catch (e: RuntimeException) { // InvalidPathException and similar
            PathResolution.Failed(ErrorCode.BAD_PATH)
        }
    }
}
