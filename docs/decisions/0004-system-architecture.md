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

## Spike 3 result: JCEF preview, bridge round trip, product guard (2026-11-08)

Code: `plugin/src/main/kotlin/.../{product,bridge,preview,toolwindow}`.

Verified (Docker, `scripts/gradle-docker.sh test verifyPlugin`):

- `<depends>com.intellij.modules.jcef</depends>` plus `bundledPlugin("com.intellij.modules.jcef")` in
  `build.gradle.kts` compiles, loads in the WebStorm 2026.2.3 test IDE, and `verifyPlugin` reports
  Compatible for PhpStorm 262.10968.76 and WebStorm 263.7307.202. In 2026.2 the module is the bundled plugin
  `com.intellij.modules.jcef` (`plugins/jcef-plugin`).
- Public API used: `JBCefBrowser.createBuilder().setUrl().build()`, `JBCefJSQuery.create(JBCefBrowserBase)`,
  `addHandler`, `inject`, `JBCefClient.addRequestHandler/addLifeSpanHandler/addLoadHandler`
  (`CefRequestHandlerAdapter.onBeforeBrowse`, `CefLifeSpanHandlerAdapter.onBeforePopup`),
  `ApplicationInfo.build.productCode`. `JBCefJSQuery.create(JBCefBrowser)` and `Disposer.isDisposed` are
  deprecated, so they are avoided. `verifyPlugin` usages are unchanged from the scaffold: 4 deprecated and 6
  experimental, all in `ToolWindowFactory` overrides. No internal API and no new usages.
- Unit tests, 27 passing in total, 20 new: `ProductGuardTest` (2), `BridgeMessageValidatorTest` (6),
  `PreviewUrlPolicyTest` (3), `InspectorToolWindowFactoryTest` (4: availability decision, unsupported-IDE and
  JCEF-unsupported messages, the test IDE is WebStorm).
- Product guard: product code `WS` or `PS` only; JCEF is never queried in other IDEs.
- URL policy: only `http` on `localhost` / `127.0.0.1`, no userinfo, no whitespace, at most 2048 chars; the
  browser may also navigate to `about:blank`. Popups are always vetoed.

NOT verified (needs `runIde` by hand):

- Real JCEF rendering, `JBCefApp.isSupported()` outcome in a real IDE, and the `PreviewPanel` path with a real
  browser (tests never construct it).
- The bridge round trip: the load handler injects `window.__viSend`, pings once with `self-check`, and the
  status line should show "received Ping"; the reply arrives as a `vi-response` DOM event. Not run.
- Navigation and popup veto (`onBeforeBrowse` also fires for redirects; `window.open` handling in a real page).
- "Built-in server" button (adds the `_ijt` auth token); behavior in PhpStorm; JBR without JCEF.
- Registration order: the query is created right after `build()` while the native browser is created lazily,
  which should avoid the "cefQuery undefined" warning, but this is unproven.

Known limits: the validator is a round-trip proof (`ping`, `hello`), not protocol v1. The guard does not
block installation elsewhere (option D), it only makes the tool window inert.

Verdict: the approach is feasible and compiles and verifies cleanly against both products; the JCEF part is
proven only up to compile, load and verifier level until the owner runs `runIde`.

## Spike 5 result (2026-11-08): vanilla PHP mapping

Goal: only HTML elements need a source location (ADR 0009). Two mechanisms were compared. Code:
`php-support/visual-inspector-prepend.php` (about 310 lines, no dependencies), fixture
`test-fixtures/php-site/`, check `php-support/tests/check.sh` + `assert.mjs`, wired into
`scripts/verify.sh` (skips with exit 0 when neither the Docker image nor a host php 8+ exists).

### A) Prepend script with stream wrapper (built and run)

Mechanism: `auto_prepend_file` registers a user-space `file://` wrapper. For includes of project `.php`
files, `token_get_all` finds `T_INLINE_HTML`, a small scanner (same rules as `LocationInjector`: skips
comments, doctype, end tags, raw text of `script`/`style`/`textarea`/`title`) adds
`data-vi-src="rel/path.php:OFFSET"` after the tag name, and the rewritten source is compiled from memory.
Guards: off unless `VISUAL_INSPECTOR_DEV=1` (or ini `visual_inspector.dev=1`); only `.php` files whose
realpath is inside `VISUAL_INSPECTOR_ROOT` (default: document root), never under `vendor/` or
`node_modules/`; files over 2 MB and any rewrite error fall back to the original source; the script has no
write calls (its other wrapper methods only forward the application's own file calls to the native wrapper).

Verified (run in `php:8.5-cli`, `php -S`, fixture mounted read-only, curl; PASS):

- Disabled without the env value: no attribute anywhere. Enabled: page with attributes minus the attributes is
  byte-identical to the disabled page (nothing else changed); line numbers unchanged.
- 23 distinct start tags checked, 31 attributes in the page, exact total (no extras). Offsets equal an
  independent computation (JS `indexOf` of the literal tag in the file, UTF-16 units, LF-normalized) for
  `index.php` and the partials `header.php`, `footer.php`, `card.php`, including a tag after a line with
  non-ASCII text and a surrogate pair (emoji).
- Loops: the inline `foreach` renders `li`/`span` 3 times with the identical location each time; a partial
  included 3 times in a loop gives the identical `partials/card.php:0` three times.
- Not mapped, no attribute (as designed): `echo "<li ...>"` (3x), `<?= '<i id=..>' ?>` string output,
  `vendor/lib.php`, a file outside the project root (the include itself still ran), a tag inside an HTML
  comment, text inside `<title>` that spans a PHP block.
- Tag split across PHP blocks (`<div id="split" <?php ... ?>>`), quote spanning a PHP block with `>` in a
  value, mixed case (`<DIV ID=..>`), `<?= ?>` short echo: mapped correctly.
- `opcache.enable_cli=1`, three requests: opcache reported enabled, all project files cached (including the
  prepend script) and hits rising (13); pages identical to the non-opcache run. Files in the fixture
  unchanged (hash before/after, plus read-only mount).
- Ad hoc (not in the check): CRLF files give offsets as if LF (matches the IDE `Document`); a file with a PHP
  syntax error still tokenizes; 920 KB template with 40000 tags rewrites in 58 ms; `index.php` about 0.04 ms.
  With opcache the cost is paid once per file change.

Failures and surprises found on the way:

- `STREAM_OPEN_FOR_INCLUDE` is documented but not defined in userland PHP 8.5 (fatal "undefined constant");
  the script uses its source value `0x80`. This depends on an internal flag value; the check would catch a
  change.
- Docker VMs may not share the host temp dir (colima shares only the home tree); the check keeps its temp
  files under `php-support/tests/` for that reason.

Limits (honest user statement: only static markup in `.php` files is mappable):

- Markup produced by `echo`, heredocs, `printf` or template engines (Twig, Blade, any compiled or cached
  template) gets no attribute. A tag whose name is split by PHP gets none.
- Offsets refer to the file on disk. Unsaved editor text and a file edited after the page was rendered give
  stale offsets until reload; the plugin cannot serve PHP from the editor buffer. A UTF-8 BOM probably shifts
  offsets by one (not verified against the IDE).
- Opcache keeps the rewritten code: toggling the env value or editing the script needs an opcache reset or a
  server restart. With `validate_timestamps=0` edited files also stay stale (normal opcache behavior).
- Rewriting changes the response: pages that hash or validate their own HTML, or output JSON/XML from `.php`
  files, can break (the scanner only touches tags in inline HTML, so JSON echoed from PHP code is safe, but an
  inline-HTML fragment returned to an AJAX caller gets attributes too).
- User setup needed: one `auto_prepend_file` value and one env value, in dev only. The root default is the
  document root; a project root above it (where `vendor/` lives) needs `VISUAL_INSPECTOR_ROOT`. Paths in
  Docker or remote setups map only if the container sees the same relative layout (relative paths are used,
  so this mostly works; the IDE side must resolve them against the project root).
- Not tested: php-fpm/Apache, PHP below 8.5, Windows paths, frameworks that include via custom autoloaders
  (works the same, they use `include`), `eval`, files with `declare(strict_types)` first-line rules (the
  rewrite does not touch PHP tokens).

### B) Plugin-side proxy / structural matching (paper only, not built)

It would need: a reverse proxy in the plugin in front of the user's PHP server (new port, forwarding of
cookies, redirects, WebSocket, streaming; this is more surface than the built-in server hook), HTML parsing
of every response, and per element a match against the HTML PSI of the `.php` files (tag, id, classes,
text, sibling order). Dynamic text and includes make matches ambiguous; loops make one location many
elements (already handled by "highlight all"), but includes and conditionals make the candidate file
unknown, so it would guess among several files. ADR 0009 forbids silent guesses, so it could only offer
"candidates" lists. It cannot tell which of several identical `<li>` partials rendered an element. Cost is
high (proxy plus matcher plus ambiguity UI) and the result is strictly less exact than A. Measured: nothing;
no proxy was built.

### Verdict and recommendation

Feasible with caveats for A; B is not worth building for vanilla PHP. Recommend A as the exact mechanism,
shipped as an optional, documented dev-only script (copy or Composer-free include), with the plugin treating
elements without an attribute as unmappable and offering the nearest mapped ancestor (ADR 0009). B stays a
possible later fallback only for users who cannot add a prepend value. Follow-ups for phase 3: IDE-side
resolution of PHP relative paths and PhpStorm `PsiFile` offsets (BOM, CRLF), UX text for "mapped from disk
version", an opcache/restart hint in the docs, and a test in PhpStorm of the real round trip.
