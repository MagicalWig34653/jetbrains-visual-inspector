package io.github.magicalwig34653.visualinspector.bridge

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class ProjectPathResolverTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun failed(r: PathResolution) = (r as? PathResolution.Failed)?.code

    private fun layout(): Pair<Path, Path> {
        val root = tmp.root.toPath().toRealPath()
        val project = Files.createDirectories(root.resolve("project"))
        val outside = Files.createDirectories(root.resolve("outside"))
        Files.writeString(outside.resolve("secret.txt"), "secret")
        Files.createDirectories(project.resolve("src"))
        Files.writeString(project.resolve("src/index.html"), "<p>")
        return project to outside
    }

    private fun symlink(link: Path, target: Path) =
        try { Files.createSymbolicLink(link, target) } catch (e: UnsupportedOperationException) { assumeNoException(e); throw e }

    @Test fun resolvesFileInsideProject() {
        val (project, _) = layout()
        assertEquals(PathResolution.Resolved(project.resolve("src/index.html")), ProjectPathResolver.resolve("src/index.html", project))
    }

    @Test fun missingFileAndDirectoryAreNotFound() {
        val (project, _) = layout()
        assertEquals(ErrorCode.NOT_FOUND, failed(ProjectPathResolver.resolve("src/none.html", project)))
        assertEquals(ErrorCode.NOT_FOUND, failed(ProjectPathResolver.resolve("src", project)))
    }

    @Test fun traversalSyntaxIsRejected() {
        val (project, _) = layout()
        for (p in listOf("../outside/secret.txt", "/etc/passwd", "src/../../outside/secret.txt", "", "a\\b")) {
            assertEquals(p, ErrorCode.BAD_PATH, failed(ProjectPathResolver.resolve(p, project)))
        }
    }

    @Test fun symlinkedFileEscapingProjectIsDenied() {
        val (project, outside) = layout()
        symlink(project.resolve("leak.txt"), outside.resolve("secret.txt"))
        assertEquals(ErrorCode.DENIED, failed(ProjectPathResolver.resolve("leak.txt", project)))
    }

    @Test fun symlinkedDirectoryEscapingProjectIsDenied() {
        val (project, outside) = layout()
        symlink(project.resolve("linkdir"), outside)
        assertEquals(ErrorCode.DENIED, failed(ProjectPathResolver.resolve("linkdir/secret.txt", project)))
    }

    @Test fun symlinkInsideProjectIsAllowedAndCanonicalized() {
        val (project, _) = layout()
        symlink(project.resolve("alias.html"), project.resolve("src/index.html"))
        assertEquals(PathResolution.Resolved(project.resolve("src/index.html")), ProjectPathResolver.resolve("alias.html", project))
    }

    @Test fun danglingSymlinkIsNotFound() {
        val (project, outside) = layout()
        symlink(project.resolve("dangling.html"), outside.resolve("missing.txt"))
        assertEquals(ErrorCode.NOT_FOUND, failed(ProjectPathResolver.resolve("dangling.html", project)))
    }

    @Test fun symlinkedProjectBaseDirWorks() {
        val (project, _) = layout()
        val link = tmp.root.toPath().toRealPath().resolve("projectlink")
        symlink(link, project)
        assertEquals(PathResolution.Resolved(project.resolve("src/index.html")), ProjectPathResolver.resolve("src/index.html", link))
    }
}
