# 0004: Plugin, shared inspector and thin adapters

Status: Proposed (2026-11-08). Phase 1 analysis and owner answers (ADR 0009) are in; waits for spike 2.

## Context

`projektauftrag.md` asks for a simple, modular design and a comparison between a plugin-only
approach and a plugin with optional browser or framework adapters.

## Proposal

- A Kotlin plugin hosts the JCEF preview and does source resolution and navigation.
- One TypeScript DOM inspector is shared by JCEF and later browser extensions.
- Adapters only map a DOM element to a source location.
- The first milestone uses JCEF only. External browsers follow in phase 3.
- No separate daemon or service.

## Phase 1 findings

Details and sources: `docs/research/feasibility.md`. Labels: [V] proven, [P] plausible, [E] experiment.

- [V] JCEF preview, `JBCefJSQuery` bridge, `OpenFileDescriptor` navigation and HTML PSI are public API. A
  plugin-only design delivers the static HTML MVP. `plugin.xml` must add `com.intellij.modules.jcef`.
- [V] The bundled JCEF has `CefResourceHandler` but no response filter, so a third-party server's
  responses cannot be rewritten inside JCEF. Adapters read dev-mode metadata from the live DOM instead [P].
- [P] Static HTML source: either a custom `WebServerFileHandler` (extension point
  `org.jetbrains.webServerFileHandler`, public class, not in the SDK docs, Netty types) or content served
  to JCEF by the plugin through `CefRequestHandler` and `CefResourceHandler`. Spike 2 decides. ADR 0008
  stays the rule: built-in server first; the plugin serves itself if the spike finds it unsuitable.
- [P] Install restriction: no `plugin.xml` construct means "WebStorm or PhpStorm". Recommended: depend on
  the platform and JCEF modules and guard at runtime by product code (owner decides).
- [P/E] Vanilla PHP: optional user-added `auto_prepend_file` that injects location attributes into static
  markup of included files in memory. HTML built in PHP strings stays unmappable.
- [V] Existing tools (MIT: Vue devtools and inspector plugin, Svelte `__svelte_meta`, react-dev-inspector,
  LocatorJS, launch-editor) confirm the "dev metadata plus click" pattern. React 19 removed
  `_debugSource`, so React is the riskiest adapter.

## Owner decisions (ADR 0009)

- Static HTML: spike only the built-in web server; if it fails, ask the owner before building the
  plugin-served fallback.
- One build with a runtime product guard (WebStorm and PhpStorm only), plus `com.intellij.modules.jcef`.
- PHP: first item of phase 3; the spike tests the `auto_prepend_file` script and a plugin-side proxy.
  Only HTML elements need a location.
- Unmappable elements: nearest mappable ancestor, labeled; plugin-served content uses the editor buffer.
- Verify only against WebStorm 2026.3 and PhpStorm 2026.2.3 until the first release.

## Open questions

- Does in-memory attribute injection keep exact offsets for unsaved buffers and parser-normalized
  markup (spike 2)?
- Which serving path for static HTML passes `verifyPlugin` and loads in both products (spike 2)?
- Does the PHP include-time rewrite survive opcache and loops (new spike, phase 3 start)?
- Which IDE-side channel suits external browsers without internal APIs (phase 3; the built-in server
  `HttpRequestHandler` is a candidate with the same API-status caveat)?

## Consequences

If accepted, the phase 2 tasks in `docs/backlog.md` apply as written. Spike results may change them.
The owner's answers are recorded (ADR 0009). Remains Proposed until spike 2 has a result.

## Spike 2 result (2026-11-08): static HTML via the built-in web server

Verdict: **feasible with caveats**. Measured in this session against WebStorm 2026.2.3 (test IDE) and
`verifyPlugin` for PhpStorm 2026.2.3 and WebStorm 2026.3. Per ADR 0009 the plugin-served JCEF fallback was
not built.

Facts:

- Code: `LocationInjector.inject(html, path)` (pure function, no parsing tree) adds
  `data-vi-src="<project-relative path>:<UTF-16 offset of '<'>"` to every start tag.
  `LocationInjectingFileHandler : WebServerFileHandler` reads the unsaved `Document` if one is cached, else
  the file, and answers with the injected bytes. It never writes the user's file.
- Compile and load: compiles against WebStorm 2026.2.3 with no extra Gradle dependency (Netty and
  `org.jetbrains.builtInWebServer.*` are on the platform classpath). `plugin.xml` needs only the extension
  `<extensions defaultExtensionNs="org.jetbrains"><webServerFileHandler ... order="first"/>`; no extra
  `<depends>` was needed. The module descriptors `intellij.platform.builtInServer(.impl)` are
  `visibility="internal"`, yet the plugin loaded and the handler ran in the test IDE.
- `verifyPlugin`: PhpStorm 262.10968.76 and WebStorm 263.7307.202 both "Compatible". No internal API usage
  reported for the handler, no warning about `WebServerFileHandler`, `PathInfo` or Netty. The only reports
  are the scaffold's `ToolWindowFactory` ones (6 experimental, 4 deprecated: `manage`, `getAnchor`,
  `getIcon`, `isApplicable`, `isDoNotActivateOnStart`). A first run flagged `ReadAction.compute` as
  deprecated; replaced by `runReadActionBlocking`.
- End-to-end test (`BuiltInServerInjectionTest`, `HeavyPlatformTestCase`, real HTTP via
  `HttpURLConnection` to `BuiltInServerManager` port): with `BuiltInServerManager.addAuthToken` (`_ijt`) the
  response is 200 with the unsaved buffer text and injected attributes; without the token it is 404 and the
  body has no `data-vi-src`. The file on disk and the `Document` stay unmodified. Requirements found: a
  real-file-system project (`BasePlatformTestCase` uses `temp://`, where the server throws "Failed to map
  temp:///... into nio Path"), and the file must lie under a content root. Project trust was not exercised:
  the test project is trusted by default, so the untrusted-project path is **unverified**. Real browser
  (JCEF or external) loading is **unverified**.
- Offsets: for `test-fixtures/static-html/index.html` the injected offsets equal the HTML PSI
  `XmlTag.textRange.startOffset` for all 13 tags (`PsiOffsetTest`, and again on the HTTP response of the
  edited buffer). The fixture's table has no `tbody` in PSI, so the browser's implicit `tbody` is simply an
  element without attribute (nearest mappable ancestor rule, ADR 0008). Source-text injection cannot shift
  offsets. Unit tests (`LocationInjectorTest`, 7) cover quotes containing `>`, comments, doctype, CDATA,
  `script`/`style`/`title`/`textarea` raw text, surrogate pairs and truncated input.

Caveats:

- Undocumented, Netty-typed API (`WebServerFileHandler`, `PathInfo`), not in the SDK docs; may change
  between releases without a deprecation. Verifier does not guard it, only our tests do.
- `LineSeparators`: the handler uses `Document` text (always `\n`) or file text with line separators
  normalized to `\n`, so offsets match PSI/Document, not raw disk bytes for CRLF files.
- Spike scope applies the injection to every HTML page the built-in server serves, regardless of who asked.
  Production must restrict it to requests from the Visual Inspector preview.
- Dev servers, PHP and external browsers are not covered; only static files of the project.

Recommendation: proceed with this path for the static HTML MVP; no owner decision on the fallback is needed.
If a later platform release breaks it, the test `BuiltInServerInjectionTest` fails first and the owner would
then decide on the plugin-served `CefResourceHandler` fallback (ADR 0009).
