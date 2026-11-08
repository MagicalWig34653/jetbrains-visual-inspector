// Derived from JetBrains/intellij-platform-plugin-template (Apache-2.0).
// See THIRD-PARTY-NOTICES.md for the exact upstream commit and the changes made.
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    // IntelliJ Platform 2026.2+ requires Java 25 (docs: build-number-ranges).
    jvmToolchain(25)
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        webstorm("2026.2.3")
        bundledPlugin("com.intellij.modules.jcef")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
        }
    }

    pluginVerification {
        ides {
            // One entry per target product, covering the oldest and newest supported branch.
            create(IntelliJPlatformType.PhpStorm, "2026.2.3")
            create(IntelliJPlatformType.WebStorm, "2026.3")
        }
    }
}

tasks.test {
    // Shared protocol vectors live outside plugin/ (repo root: test-fixtures/protocol/vectors.json).
    systemProperty("visualinspector.protocolVectors", rootProject.projectDir.resolve("../test-fixtures/protocol/vectors.json").canonicalPath)
}

// The inspector bundle is built by npm (browser-extension/dist, git-ignored) and packaged when present, so the
// Gradle build itself never needs node. The plugin reports a missing bundle at runtime.
val inspectorDist = rootProject.projectDir.resolve("../browser-extension/dist")
tasks.processResources {
    from(inspectorDist) {
        include("inspector.js")
        into("visualinspector")
    }
    val bundleFile = inspectorDist.resolve("inspector.js")
    doLast {
        if (!bundleFile.exists()) {
            logger.warn("WARNING: browser-extension/dist/inspector.js missing; run 'npm run build:inspector -w browser-extension'. The preview will not inspect.")
        }
    }
}
