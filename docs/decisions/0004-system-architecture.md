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
