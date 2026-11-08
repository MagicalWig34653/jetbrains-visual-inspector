---
name: intellij-platform
description: Rules and official documentation pointers for IntelliJ Platform plugin code - PSI, JCEF, threading, disposal, tool windows, compatibility. Use when changing files in plugin/.
user-invocable: false
paths: plugin/**
---

Base URL: <https://plugins.jetbrains.com/docs/intellij/>. Append `.md` to a page URL for Markdown. Read the page before using an API; the platform changes between branches.

| Topic | Page |
| --- | --- |
| JCEF browser and JS bridge | `embedded-browser-jcef.html` |
| PSI | `psi.html` |
| Navigation | `navigation.html` |
| Threading, read and write actions | `threading-model.html` |
| Disposal | `disposers.html` |
| Tool windows | `tool-windows.html` |
| `plugin.xml` | `plugin-configuration-file.html` |
| Compatibility and module dependencies | `plugin-compatibility.html` |
| Plugin Verifier | `verifying-plugin-compatibility.html` |
| Tests | `testing-plugins.html` |
| Platform and Java versions | `build-number-ranges.html` |

Rules:

- Never block the EDT. PSI reads need a read action; do slow work in the background.
- Check `JBCefApp.isSupported()` before creating a browser, and show a usable message otherwise.
- Register every browser, client and `JBCefJSQuery` with a `Disposable` parent.
- Treat each JS bridge payload as untrusted. Validate before any IDE action.
- Avoid `@ApiStatus.Internal` APIs; `verifyPlugin` reports them.
- Depend on `com.intellij.modules.platform` unless a feature needs more. WebStorm's JavaScript plugin requires `bundledPlugin("JavaScript")` and a `plugin.xml` dependency.
- Prefer `BasePlatformTestCase` for code touching PSI or the editor.

Versions are recorded in `docs/development.md`.
