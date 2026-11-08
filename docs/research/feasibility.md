# Phase 1 feasibility analysis

Date: 2026-11-08. Scope: `projektauftrag.md` section 10, points 1 to 8, plus the open questions of
`docs/backlog.md` item 1. Fixed inputs: ADR 0008 (WebStorm and PhpStorm only, platform 2026.2 / build 262,
in-memory attribute injection, MIT).

Labels: **[V]** proven by official documentation, platform source or a check run in this session (source
named); **[P]** plausible, reasoned but not tested; **[E]** needs a prototype. Platform source references
are to tag/branch `262.11879` of <https://github.com/JetBrains/intellij-community> unless stated. "Local
IDE" means the WebStorm 2026.2.3 (262.10968.77) and PhpStorm 2026.2.3 (262.10968.76) distributions that
Gradle already downloaded for this project; I read their `product-info.json` and jars.

Nothing here required changing `plugin/` or `browser-extension/`.

## Summary

1. The JCEF preview, bridge, editor navigation and HTML PSI are all supported public API [V]. The plugin
   alone can deliver the static HTML MVP.
2. The built-in web server can be extended to rewrite responses in memory through the public, dynamic
   extension point `org.jetbrains.webServerFileHandler`, but it is not in the SDK docs and exposes Netty
   types [P]. Plugin-served content via `CefResourceHandler` is the documented fallback and is equally
   simple [P]. Spike 2 decides; I recommend the fallback as the primary path (section 2.2).
3. Java JCEF has no response filter in the bundled build [V], so rewriting a third-party server's response
   inside JCEF is not possible without re-fetching it. This is not needed: framework adapters read
   dev-mode metadata from the live DOM and the inspector is injected after load [P].
4. `plugin.xml` cannot express "WebStorm or PhpStorm". Aliases `com.intellij.modules.webstorm` and
   `com.intellij.modules.phpstorm` exist [V, local product-info], but `depends` elements are combined with
   AND [P]. Recommended: runtime product guard, keep Plugin Verifier on both (section 2.3).
5. Vanilla PHP can be mapped by an optional, user-installed `auto_prepend_file` script that rewrites
   included PHP files in memory so that static HTML segments carry location attributes [P/E]. HTML that PHP
   builds from strings stays unmappable. Details and limits in section 2.4.
6. The `JBCefJSQuery` handler receives only the message string, no frame or origin [V]. ADR 0005 must treat
   the bridge as reachable by any script in the browser.

## 1. Task 1: platform APIs (JCEF, navigation, PSI)

| Topic | Finding | Label | Source |
| --- | --- | --- | --- |
| Support check | `JBCefApp.isSupported()` is false when the JBR lacks JCEF or has an incompatible version; there is no need to initialize `JBCefApp` explicitly. Offer a non-browser fallback message. | V | <https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html> |
| Module dependency | From 2026.2 JCEF is a bundled plugin; plugins must declare `<depends>com.intellij.modules.jcef</depends>` (alias since 2025.3.1). The alias is listed in the local WebStorm and PhpStorm 2026.2.3 `product-info.json`. The scaffold `plugin.xml` lacks it and must add it. | V | same page; local product-info |
| Browser | `JBCefBrowser` / `JBCefBrowser.createBuilder()`, `getComponent()`, `load*()` callable from any thread, `JBCefBrowser.openDevtools()`. All JBCef objects are `Disposable`. | V | same page |
| JS to IDE | `JBCefJSQuery.create(browser)`, `addHandler(Function<String, Response>)`, `inject("js-expr")`. Create queries before the browser is created or set `JBCefClient.Properties.JS_QUERY_POOL_SIZE`; otherwise `window.cefQuery_...` is undefined in JS (source warns about this). | V | docs page; `platform/ui.jcef/jcef/JBCefJSQuery.java` |
| Handler sees no origin | The handler is `Function<String, Response>`; the underlying `CefFrame` and browser are not passed on. The plugin cannot distinguish main frame, iframe or origin inside the handler. | V | `JBCefJSQuery.addHandler` source |
| IDE to JS | `browser.getCefBrowser().executeJavaScript(code, url, line)`. No direct DOM access from the plugin. | V | docs page |
| Handlers | `JBCefClient.addRequestHandler`, `addLoadHandler`, `addLifeSpanHandler` etc. are public and not `@ApiStatus.Internal`. The docs "do not recommend" using them but show `CefRequestHandler` plus `CefResourceRequestHandler` for serving plugin resources (`JCefImageViewer` as reference). | V | docs page; `JBCefClient.java` |
| Scheme handlers | `JBCefApp.addCefCustomSchemeHandlerFactory` is `@ApiStatus.Internal`. Avoid custom schemes. | V | `JBCefApp.java` |
| Response filter | The bundled JCEF 144.0.15 has `CefResourceHandler` and `CefResourceRequestHandler` but no `CefResponseFilter` class (listed the jar `intellij.libraries.jcef.jar` of WebStorm 2026.2.3). Rewriting a response means answering the request with a `CefResourceHandler`. | V | local jar listing |
| Inspector injection | `JBCefClient.addLoadHandler(...).onLoadEnd` plus `executeJavaScript` injects the shared TS inspector into any page, including dev servers. Early scripts are missed; acceptable for an interactive inspector. | P | `JBCefClient.java` |
| Rendering mode | `JBCefBrowserBuilder.setOffScreenRendering(boolean)` exists; windowed vs OSR behavior inside tool windows on each OS is untested. | E | `JBCefBrowserBuilder.java` |
| Editor navigation | `OpenFileDescriptor(project, file, offset)` and `(project, file, logicalLine, logicalColumn)` with `navigate(requestFocus)` are public (the `IntSupplier` constructor is Internal). `FileEditorManager.openTextEditor(descriptor, focus)` returns the `Editor`, on which the plugin sets the selection for the element range. Use offsets, not line/column, internally. | V | `OpenFileDescriptor.java`; <https://plugins.jetbrains.com/docs/intellij/navigation.html> |
| HTML PSI | `XmlTag` with `textRange.startOffset` for tags in `.html` is proven by `PluginSmokeTest` (WebStorm 2026.2.3). Same test in PhpStorm is not run yet. | V (WebStorm), E (PhpStorm) | `PluginSmokeTest.kt` |
| PHP PSI | PHP classes come from `com.jetbrains.php`, present in PhpStorm only [V local]. Code using them goes into an optional `<depends optional="true" config-file="...">com.jetbrains.php</depends>` so WebStorm still loads the plugin. Optional dependency mechanism: <https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html>. | V (mechanism), P (PHP PSI use) | |
| Threading | PSI reads need read actions, JCEF calls arrive on non-EDT threads; hop to a background read action, then to the EDT for navigation. | V | `threading-model.html` |

Consequence for the bridge handler: it runs for messages from any page the browser shows. Section 6
describes the mitigation.

## 2. Task 2 and the three explicit questions

### 2.1 Existing tools (what they teach, licenses)

Licenses read from the GitHub or npm metadata on 2026-11-08 [V].

| Tool | License | Mechanism | Lesson for us |
| --- | --- | --- | --- |
| `launch-editor` / `launch-editor-middleware` (<https://github.com/yyx990803/launch-editor>) | MIT | Dev server endpoint opens `file:line:col` in an editor by spawning a process. | The standard "open in editor" model. We must not copy its trust model: it lets an HTTP request start a process (ADR 0005 forbids this). We navigate in-process. |
| Vue DevTools / `vite-plugin-vue-devtools` (<https://github.com/vuejs/devtools>), `vite-plugin-vue-inspector` (npm) | MIT | Vite plugin adds source location attributes to template elements in dev; click calls launch-editor. | Optional dev dependency pattern; matches ADR 0008. Read the location attribute instead of launching an editor. Exact attribute names [E]. |
| Svelte (<https://github.com/sveltejs/svelte>, `vite-plugin-svelte`) | MIT | In dev mode compiled output sets `element.__svelte_meta = { loc: { file, line, column } }` (source `packages/svelte/src/internal/client/dev/elements.js`, main branch). | Location metadata is built in; no extra dev dependency for the common case. Version stability [E]. |
| `react-dev-inspector` (<https://github.com/zthxxx/react-dev-inspector>), `click-to-component`, LocatorJS (<https://github.com/infi-pc/locatorjs>) | MIT | Read `fiber._debugSource` from the React fiber (JSX source transform) or add a compile-time attribute. | React 19 removed `_debugSource`; community tools patch the JSX dev runtime or inject attributes at build time (changelog of `vite-plugin-react-click-to-component`, React issue 29092 "not planned"). React stays the highest-risk adapter, as ADR 0008 ordered. |
| `vite-plugin-inspect` (<https://github.com/antfu-collective/vite-plugin-inspect>) | MIT | Shows Vite's transform pipeline. | A debugging aid for adapter work, not a runtime dependency. |
| JetBrains WebStorm Live Edit / JS debugger, `JetBrains IDE Support` browser extension | Proprietary bundled plugin (`com.intellij.plugins.html.instantEditing` is bundled, [V local]) / extension license not checked | Reload on save, debugger attach; no DOM-to-source click navigation known to me. | Nothing to reuse in code. Check in the first spike whether existing WebStorm "Open in browser" and built-in server usage conflict with ours [E]. Claims about missing features are [P]. |
| Dreamweaver Live View | Proprietary | Design model only. | |

All reusable code is MIT, compatible with this MIT project. We reuse ideas and dev-mode metadata formats,
not source, until a specific file is chosen and attributed in `THIRD-PARTY-NOTICES.md`.

### 2.2 Question (a): can the built-in web server rewrite responses in memory

Facts from platform source (branch 262.11879):

- `org.jetbrains.ide.HttpRequestHandler` (extension point `com.intellij.httpRequestHandler`, dynamic) is a
  public abstract class; handlers get a Netty `FullHttpRequest` and `ChannelHandlerContext` and respond
  themselves. [V source]
- The built-in server (`BuiltInWebServer`, registered `order="last"`) maps `/<project>/<path>`, requires a
  trusted project, validates a token (`_ijt`) and then asks the dynamic extension points
  `org.jetbrains.webServerPathHandler` and `org.jetbrains.webServerFileHandler`. [V source]
- `WebServerFileHandler` is a public abstract class with
  `process(pathInfo, canonicalPath, project, request, channel, projectNameIfNotCustomHost, extraHeaders): Boolean`
  and `pageFileExtensions`. The default `StaticFileHandler` (internal class, `order="last"`) handles
  `html htm shtml stm shtm`, and the live-reload feature already appends a script suffix to HTML responses,
  so serving a modified body is an intended pattern. [V source]
- The module descriptor is `visibility="internal"` and these types are absent from the SDK docs
  (<https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html> does not list them); no
  `@ApiStatus.Internal` annotation is on `WebServerFileHandler`, `HttpRequestHandler` or `PathInfo`. The
  `BuiltInServerManager` abstract class is `@ApiStatus.NonExtendable`, public, with `port`, `waitForStart()`,
  `isOnBuiltInWebServer(url)`, `addAuthToken(url)`. [V source]

Verdict: a custom `WebServerFileHandler` registered with `order="first"` could read the file or the
unsaved `Document`, inject attributes and the inspector script, and answer. That is rewriting in memory
through a public extension point, but it is undocumented, Netty-typed and its module visibility is
`internal`: **[P]**, whether the Plugin Verifier and a runtime load accept it is **[E]** (spike 2). It also
only helps static files inside the project; dev servers and PHP are served by other processes.

Alternative, documented path: the plugin answers the request itself in JCEF. `CefRequestHandler.getResourceRequestHandler`
returns a `CefResourceRequestHandler` whose `getResourceHandler` returns a `CefResourceHandler` that serves
bytes the plugin builds from project files (the pattern the docs describe for plugin-served content). Use a
real `http://` URL on a reserved host name, not a custom scheme (custom scheme registration is Internal).
Advantages: no Netty types, no dependency on server settings, trust and token model, or port. Costs: the plugin must
serve linked assets (CSS, JS, images) from the project too, with the same path confinement; relative links
work, absolute `/x` links resolve against the preview origin, which must map to the project root. **[P]**,
testable in spike 2.

Recommendation: spike both for ten minutes of code each, measure, and prefer plugin-served content unless
the built-in server variant proves clearly simpler and passes `verifyPlugin`. ADR 0008 says "built-in server
first"; the spike result is the "if not possible" criterion, so this does not contradict it. I list this as
owner question 1.

Not possible by documented means: rewriting responses of external dev servers inside JCEF (no
`CefResponseFilter`). Alternatives if ever needed: fetch upstream in the plugin and serve the modified copy
(cookie, HMR websocket and CORS handling make this heavy [P]); not recommended. Adapters avoid the need.

### 2.3 Question (b): restricting installation to WebStorm and PhpStorm

- Docs: a plugin declaring only all-product modules loads in every IDE; product restriction comes from
  product-specific module or plugin dependencies (PhpStorm example `com.jetbrains.php`);
  `<incompatible-with>` excludes by ID; `since-build`/`until-build` restrict by build only. Marketplace
  compatibility is derived from the declared dependencies. [V]
  <https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html>,
  <https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html>
- Product aliases exist in the 2026.2.3 distributions: `com.intellij.modules.webstorm` in WebStorm,
  `com.intellij.modules.phpstorm` and `com.intellij.modules.php-capable` in PhpStorm, each absent from the
  other. [V local product-info.json; not listed in the docs, so their stability is **[P]**]
- Documented IDs `JavaScript` (WebStorm, PhpStorm both bundle it) and `com.intellij.modules.ultimate` are not
  unique to the two products (other Ultimate IDEs have them) [P].
- `<depends>` elements are conjunctive (all required), so one cannot write "webstorm OR phpstorm". The
  docs page does not state the operator explicitly [P, standard platform behavior].

Options:

| Option | Effect | Cost |
| --- | --- | --- |
| A. Hard `depends` on one alias | Plugin only installs in that product; breaks PhpStorm. | Rejected. |
| B. Two builds, one per alias | Exact restriction, two Marketplace listings and artifacts. | Extra build logic; rejected unless the owner insists. |
| C. `<incompatible-with>` per other IDE | Excludes named IDEs only; unknown future IDEs slip through; IDs [E]. | Fragile. |
| D. Depend on `platform` + `jcef` only, guard at runtime: tool window factory `isApplicableAsync` (public, `ToolWindowFactory.kt`) returns true only if `ApplicationInfo.getInstance().build.productCode` is `WS` or `PS` (`BuildNumber.getProductCode` is public) | Installable elsewhere but inert; no extra files. | Not a hard install restriction. |
| E. Optional `<depends optional="true" config-file=...>` on each alias, shared registrations via `xi:include` | Inert elsewhere without code checks; docs describe optional dependencies and `xi:include`. | More XML; **[E]** to test. |

Recommendation: D now, state the supported products in the plugin description, keep the Plugin Verifier
matrix at PhpStorm 2026.2.3 and WebStorm 2026.3. Do not use `PlatformUtils.isWebStorm()` (class is
`@ApiStatus.Internal`). Test consequence: with D, `BasePlatformTestCase` tests run in the WebStorm
sandbox as today; PhpStorm gets verifier runs plus a second `runIde`/test configuration for the PHP spike.
With a hard alias dependency (A) the plugin would fail to load in the other product's test IDE.
If the owner wants a true install restriction, B is the only exact solution known to me; E may be a middle
way after an experiment. Owner question 2.

### 2.4 Question (c): vanilla PHP output to source

The browser only sees HTML text; the DOM carries no hint of the `.php` file. Candidates:

| Approach | How | Limits | Label |
| --- | --- | --- | --- |
| Structural matching | Match DOM tag, id, classes, text, sibling order against the PHP file's HTML PSI. | Ambiguous for loops, includes and dynamic text; navigation could go to a wrong place. Must show ambiguity, never guess. | P |
| Output-side, markers per output chunk | `auto_prepend_file` script calls `ob_start` with a callback. | The callback sees only the final string; location is lost. Insufficient alone. | P |
| Include-time source rewriting (recommended to test) | An optional prepend script registers a user-space stream wrapper for `file://` that, for `.php` files inside the project, runs `token_get_all`, finds `T_INLINE_HTML` tokens (the static markup outside `<?php ?>`), and injects `data-vi-src="relative/path.php:line:col"` into the start tags it contains; the rewritten code is compiled in memory, disk is untouched. Same attribute and browser inspector as static HTML. | Misses HTML echoed from PHP strings (`echo "<li>$x</li>"`), tags split across `<?php ?>` blocks, templates loaded by frameworks that compile or cache (Twig, Blade, opcache and file caches), file paths in Docker or remote setups, performance and correctness of tokenizing every included file. PHP manual: `stream_wrapper_register`, `auto_prepend_file`, `token_get_all`. | E |
| Debugger based | Xdebug tracing per element. | Requires extension, slow, ADR 0008 asks for an Xdebug-free start. | Rejected |
| Template engine line maps | Twig and Blade compile to PHP with line information. | Out of scope for vanilla PHP; later. | P |

Constraints: the user adds the prepend script (a documented dev dependency, ADR 0008; the plugin never
edits `php.ini`, `.user.ini` or project files). Attributes use project-relative paths and are validated by
the IDE like any other location (ADR 0005). Honest limit statement for users: only static markup in `.php`
files is mappable; dynamic strings give "no source mapping". Spike 5 (new) proves or kills this on a
sample project with `php -S`, includes in loops, and opcache enabled.

## 3. Task 3: how DOM-to-source mapping works and where it ends

Mapping sources, from most to least reliable:

1. **Injected location attribute** (static HTML, vanilla PHP static markup, Vue via inspector plugin): exact
   tag start offset, survives DOM mutation by scripts for nodes that already exist. [P], static HTML [E] in
   spike 2.
2. **Framework dev metadata** (Svelte `__svelte_meta` [V source], Vue dev `__file`/inspector plugin [P],
   React fiber or build attribute [E]): component or element level, version dependent.
3. **Structural PSI matching**: fallback only; flagged as ambiguous when more than one candidate exists.

Where it ends: nodes created by scripts or the HTML parser (implied `tbody`, `html`, `head`), text-only
nodes, repeated rendering (many DOM nodes, one source location; handled by "highlight all, cycle", ADR
0008), minified or SSR-hydrated output, content in iframes or shadow DOM, templates compiled elsewhere.
Rule kept from the architecture: unmapped means feedback, never a guessed navigation. For parser-created
elements without an attribute the inspector may offer the nearest mapped ancestor only if labeled as such
[P].

Reverse direction (code to preview): the caret's `XmlTag` offset maps to the attribute value; the preview
runs `document.querySelectorAll('[data-vi-src="..."]')` and highlights all matches [P]. Manual trigger by
default (ADR 0008). Unsaved editor text differs from the served file: serving from the `Document` (plugin
served content) keeps offsets consistent; the built-in server variant needs the same care [E].

## 4. Task 4: plugin-only versus plugin plus browser or framework adapter

| Criterion | Plugin only (JCEF, injected inspector, plugin-side rewriting) | Plugin + browser extension / adapters |
| --- | --- | --- |
| Static HTML MVP | Complete. | Not needed. |
| Vue, Svelte, React | Needs dev metadata from the page; read it from the DOM by injected TS adapter code (no extension needed). | Same adapter code; extension only adds external browsers. |
| Existing logins and devtools in Chrome/Firefox | Not available (JCEF session). | Available; needs authenticated local channel (loopback, token, origin check; ADR 0005) and a server endpoint in the IDE ([E], built-in server `HttpRequestHandler` is a candidate, same API-status concern as 2.2). |
| Attack surface | Bridge only. | Adds a network endpoint; larger security work. |
| Cost | Low. | High; phase 3, after JCEF MVP (ADR 0008). |

Conclusion (supports ADR 0004): one TypeScript inspector shared by JCEF and later extensions, thin adapters
as TS modules, no separate service. Adapters are code that runs in the page, not extra processes.
Status of ADR 0004 stays Proposed because the injected-attribute path and the PHP path are untested.

## 5. Task 5: framework effort

Effort = adapter plus tests relative to the static HTML MVP (1). Estimates are judgment [P].

| Target | Effort | Reason | Main risk |
| --- | --- | --- | --- |
| Static HTML | 1 | PSI offsets proven; in-memory injection to prove. | Parser-normalized nodes. |
| Vanilla PHP | 2 to 3 | Reuses HTML mechanism plus PHP-side rewrite script. | Dynamic HTML strings, opcache, environment variety. |
| Vue | 2 | Established MIT inspector plugin; attribute read. | Dependence on user-added plugin and its attribute format. |
| Svelte | 2 | Built-in `__svelte_meta` in dev. | Metadata format changes between majors. |
| React | 4 | `_debugSource` removed in 19; needs build-time attribute or runtime patch. | Fragmented toolchains. |
| Twig, Blade | 4 | Compile step, caches, dev instrumentation in the engine. | Template compilation caches, framework variety. |

Order from ADR 0008: static HTML, PHP, Vue, Svelte, React, later Twig/Blade.

## 6. Security consequences for ADR 0005

- The `cefQuery` function is a window global inside the browser; any script in any frame can call it, and the
  Kotlin handler cannot see the frame [V source]. A per-session token in the page does not help against
  scripts running in the same page. Therefore: the browser may only show user-opened origins (block
  `CefRequestHandler.onBeforeBrowse` navigations elsewhere and `onBeforePopup`), the closed message set
  carries only a source location and element metadata, size limits apply, and every location is
  canonicalized into the project before any navigation. The worst a hostile page can do is open a project
  file in the editor; make that explicit [P].
- Never accept absolute paths from the page; accept relative project paths only [P].
- External channel (phase 3): loopback only, token, origin check [from ADR 0005]; if the built-in server is
  used, `HttpRequestHandler.isAccessible` already rejects non-local hosts and blocks writes without an
  `Origin` header (source comment) [V], but the token layer is still ours.

## 7. Top risks and the prototypes that test them

| # | Risk | Prototype (backlog) | Pass criterion |
| --- | --- | --- | --- |
| R1 | Attribute injection fails or shifts offsets (parser normalization, unsaved files, encodings) | Spike 2: inject into three sample HTML files via both the plugin-served handler and a `WebServerFileHandler`; click element, assert file and offset | 100% of elements with attribute resolve to the exact tag; unmapped ones report it |
| R2 | JCEF unsupported or flaky per OS/product | Spike 3: `isSupported()`, `JBCefJSQuery` round trip in WebStorm and PhpStorm, behavior with a JBR without JCEF | Round trip OK; unsupported case shows message, no exception |
| R3 | Built-in server extension unsupported by verifier or unstable | Part of spike 2 plus `verifyPlugin` | No Internal-API findings; loads in both products |
| R4 | Bridge abuse by hostile pages | Protocol v1 tests (backlog 4) with malformed, oversized, traversal and symlink inputs | All rejected |
| R5 | PHP include-time rewrite is unreliable | New spike 5 (below) | Static markup mapped correctly in a sample with includes and loops; opcache behavior known |
| R6 | Product restriction (D) accepted by Marketplace and tests | Check in `verifyPlugin` and Marketplace listing before any release (no publishing now) | Owner decision |
| R7 | React and Vue/Svelte metadata drift | Phase 3 adapter spikes per framework version | Version matrix documented |
| R8 | Unsaved-edit mismatch between editor and served content | In spike 2: edit without saving, reload preview | Preview reflects the buffer or says it is stale |

## 8. Prioritized work plan (consistent with `docs/backlog.md`)

Phase 1 (now):
1. This analysis, ADR 0004/0005 updates, owner answers (below).
2. Spike: in-memory injection for static HTML (backlog 2), covering R1, R3, R8 and the choice between
   built-in server and plugin-served content.
3. Spike: JCEF availability and bridge round trip (backlog 3), including adding `com.intellij.modules.jcef`
   to `plugin.xml` and the product guard (D).
4. Bridge protocol v1 with validation tests on both sides (backlog 4), including the frame-blindness finding.

Phase 2 (backlog 5 to 9 unchanged): tool window preview, project-confined location validator,
inspector overlay, click-to-source for static HTML, editor-to-preview highlight (manual trigger, highlight
all instances, cycling).

Phase 3, in this order (ADR 0008, pending owner question 3): adapter interface and vanilla PHP
(spike 5 first), then Vue, Svelte, React; Chrome/Firefox extension with authenticated channel; Twig/Blade
evaluation. Suggested new backlog entry: "Spike: vanilla PHP include-time rewriting" placed before the
PHP adapter.

## Suggested changes to files I was told not to edit

- `docs/backlog.md`: add to item 3 "declare `com.intellij.modules.jcef` and the runtime product guard";
  add a spike "vanilla PHP include-time rewriting" at the head of Phase 3 / Later; reword the "Restrict
  `plugin.xml`" Later item to "runtime guard (option D), see feasibility 2.3".
- `docs/current-state.md`: phase 1 analysis delivered, awaiting owner answers; ADR 0004/0005 still Proposed.

## Questions for the project owner

1. **Static HTML serving path.** Spike both the built-in server extension point and plugin-served JCEF
   content, then choose? Recommended default: yes; prefer plugin-served content if the built-in route is
   undocumented-only, fails `verifyPlugin`, or is not clearly simpler. (Your ADR 0008 priority stays the
   first candidate.)
2. **Installation restriction.** Accept a runtime guard (option D: loads everywhere, active only in
   WebStorm and PhpStorm) instead of an exact install restriction (which would need two builds)?
   Recommended default: yes, D.
3. **PHP placement.** Confirm ADR 0008's interpretation: vanilla PHP is the first item of phase 3, after the
   static HTML MVP and before Vue. Recommended default: confirm, with spike 5 run at the end of phase 2.
4. **Dev dependency for PHP.** May vanilla PHP support require the user to add one documented
   `auto_prepend_file` script (copied or via Composer `require-dev`) to their dev environment, never
   production? Recommended default: yes, as ADR 0008 allows for frameworks.
5. **Unmappable and ambiguous elements.** Show "no source mapping" and offer nothing, or offer the nearest
   mapped ancestor labeled as such? Recommended default: nearest ancestor only when clearly labeled; never
   silent guessing.
6. **Unsaved editor content.** Should the preview reflect unsaved editor text (serve from the `Document`)
   or the file on disk? Recommended default: editor buffer for plugin-served content, so offsets match what
   the user sees.
7. **Hostile-page worst case.** Is "a page in the preview can make the IDE open any file inside the open
   project" acceptable, as long as nothing outside the project, no commands and no writes are possible?
   Recommended default: yes (ADR 0005 scope), and the preview stays restricted to user-opened origins.
8. **Supported IDE versions beyond 2026.2.** Keep `since-build` 262 and verify against WebStorm 2026.3 and
   PhpStorm 2026.2.3 only until the first release? Recommended default: yes.
