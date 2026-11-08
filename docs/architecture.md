# Architecture

**Status: preliminary.** Roadmap phase 1 validates this design and `projektauftrag.md` requires a
joint architecture discussion before implementation. Final so far: repository layout and toolchain
(ADR 0002, 0003) and the owner's product decisions (ADR 0008), which this design must follow. ADR
0004 and 0005 are `Proposed`.

Evidence labels: **[V]** confirmed in official documentation or by a test in this repository,
**[P]** technically plausible but unconfirmed, **[E]** must be proven by a prototype.

## Goal and non-goals

Click a rendered element to reach its source; select source to highlight the rendered element.

Not goals: drag-and-drop or WYSIWYG editing, a website builder, a CSS editor, replacing DevTools,
cloud backends, user accounts, proprietary AI dependencies. The plugin never changes user code.

## Components

```text
JetBrains IDE (plugin/, Kotlin)                 Page (TypeScript)
+-----------------------------------+           +------------------------------+
| Preview tool window (JCEF)        |  bridge   | DOM inspector                |
| Bridge endpoint + validation      |<--------->|   hover highlight, click     |
| Source resolver (PSI, navigation) |           | Framework adapter            |
| Editor selection listener         |           |   DOM element -> source loc  |
+-----------------------------------+           +------------------------------+
                ^
                | later: authenticated local channel
                v
        Chrome / Firefox extension (browser-extension/)
```

| Component | Location | Responsibility |
| --- | --- | --- |
| Preview | `plugin/` | Hosts the page in JCEF, owns browser lifecycle |
| Bridge | `plugin/` + `browser-extension/` | Versioned, validated messages between page and IDE |
| Source resolver | `plugin/` | Turns a source location into a validated editor position; PSI lookups |
| Inspector | `browser-extension/` | Highlight, tooltip, click capture; shared by JCEF and extensions |
| Adapters | `framework-adapters/<name>/` | Only technology-specific DOM-to-source mapping |

## Platform facts

- **[V]** JCEF: `JBCefApp.isSupported()`, `JBCefBrowser.createBuilder()`, `JBCefJSQuery.create(...)`
  and `executeJavaScript` are documented. `JBCefBrowser`, `JBCefClient` and `JBCefJSQuery` are
  `Disposable` and must be disposed. Source: <https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html>
- **[V]** HTML PSI exposes tags with source offsets in WebStorm (`PluginSmokeTest`).
- **[V]** Platform 2026.2 and 2026.3 need Java 25.
  Source: <https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html>
- **[E]** Whether `CefRequestHandler` based in-memory response rewriting covers local dev servers.
- **[E]** Which IDE-side endpoint is suitable for external browsers without internal APIs.

## DOM-to-source mapping

A CSS selector cannot identify a source location: parsers normalize markup, scripts mutate the DOM
and components render repeatedly. When no reliable mapping exists, the plugin must say so and must
not navigate to a guess.

| Technology | Candidate source of truth | Evidence |
| --- | --- | --- |
| Static HTML (decided, ADR 0008) | Inject location attributes into the served response in memory, never on disk; served by the IDE's built-in web server, otherwise by the plugin | [E] |
| Static HTML fallback | Structural match against HTML PSI, with explicit ambiguity handling | [P] |
| Vanilla PHP (prioritized, ADR 0008) | To be researched: mapping server-rendered output back to `.php` files | [E] |
| Vue | Dev-mode component file metadata, Vite inspector plugins | [P] |
| Svelte | Dev-mode element location metadata | [P] |
| React | Dev-mode fiber debug information; changed across React versions | [E] |
| Twig, Blade | Optional dev-only template instrumentation | [E] |

## Security model (proposed, ADR 0005)

Page content is untrusted, including content served from localhost.

- The bridge accepts a closed set of versioned message types; unknown input is dropped.
- Pages can never trigger OS commands, arbitrary file reads or file writes.
- A source location is honored only if it canonicalizes to a file inside the open project, with
  symlinks resolved and traversal rejected.
- Privileged functions are limited to pages the user explicitly opened for the project.
- External browser channels bind to loopback only and require a per-session token and origin check.
- Source code is never sent to external services.

## Performance constraints

No full DOM scans on a timer, no blocking of the UI thread, no unnecessary browser re-creation,
editor-to-preview sync is configurable and debounced, resources are released when a preview closes.
