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

    @Test fun siblingDirectoryWithSharedNamePrefixIsDenied() {
        val (project, _) = layout()
        val evil = Files.createDirectories(project.resolveSibling("project-evil"))
        Files.writeString(evil.resolve("secret.txt"), "secret")
        symlink(project.resolve("link"), evil)
        assertEquals(ErrorCode.DENIED, failed(ProjectPathResolver.resolve("link/secret.txt", project)))
    }

    @Test fun encodedOrWindowsStyleTraversalIsBadPath() {
        val (project, _) = layout()
        for (p in listOf("%2e%2e/outside/secret.txt", "src/%2e%2e/x", "C:/Windows/win.ini", "src/index.html:stream",
                         "src//index.html", "src/", "./src/index.html", "src/./index.html", "~/x", "\\\\host\\share\\x")) {
            assertEquals(p, ErrorCode.BAD_PATH, failed(ProjectPathResolver.resolve(p, project)))
        }
    }

    @Test fun nulAndOtherControlCharactersAreBadPathNotACrash() {
        val (project, _) = layout()
        for (p in listOf("src/index.html\u0000.txt", "src/\u0007.html", "src/in\u202edex.html")) {
            assertEquals(ErrorCode.BAD_PATH, failed(ProjectPathResolver.resolve(p, project)))
        }
    }

    @Test fun missingProjectBaseDirIsNotFound() {
        val (project, _) = layout()
        assertEquals(ErrorCode.NOT_FOUND, failed(ProjectPathResolver.resolve("src/index.html", project.resolve("gone"))))
    }

    @Test fun unicodeAndSpacedNamesInsideProjectResolve() {
        val (project, _) = layout()
        val f = project.resolve("seiten/über uns.html")
        Files.createDirectories(f.parent)
        Files.writeString(f, "<p>")
        assertEquals(PathResolution.Resolved(f.toRealPath()), ProjectPathResolver.resolve("seiten/über uns.html", project))
    }

    @Test fun parentLinkBackIntoProjectViaSymlinkDoesNotEscapeCheck() {
        val (project, _) = layout()
        symlink(project.resolve("up"), project.parent) // link to the parent of the project
        assertEquals(ErrorCode.DENIED, failed(ProjectPathResolver.resolve("up/outside/secret.txt", project)))
    }
}
