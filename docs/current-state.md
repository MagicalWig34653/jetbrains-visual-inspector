# Current state

Last updated: 2026-11-08, after the phase 1 feasibility analysis and the owner's answers (ADR 0009).

Repository: <https://github.com/MagicalWig34653/jetbrains-visual-inspector>

Phase: 0 (project setup) finished; phase 1 (architecture and feasibility) not started. The owner's
product decisions are recorded in ADR 0008 (raw answers: `docs/research/owner-answers.json`).

## Implemented

- Plugin scaffold in `plugin/`: loads in the IDE and registers a placeholder tool window.
- TypeScript toolchain in `browser-extension/`: a protocol version constant and a smoke test.
- Project tooling: agents, skills, hooks, verification scripts, CI.

**Not implemented:** JCEF preview, DOM inspector, highlighting, navigation in either direction,
bridge protocol, browser extensions, framework adapters. The design in `docs/architecture.md` is
preliminary; ADR 0004 and 0005 are `Proposed`.

## Verification status

Results of 2026-11-08 on macOS (arm64):

| Check | Command | Result |
| --- | --- | --- |
| Repository check, secret scan | `npm run check:repo`, `npm run check:secrets` | Passed |
| Hook tests | `npm run test:hooks` | Passed, 57 of 57 |
| TypeScript typecheck | `npm run typecheck` | Passed |
| TypeScript tests | `npm test` | Passed, 1 of 1 (toolchain smoke test only) |
| Plugin build and platform tests | `cd plugin && ./gradlew clean build verifyPlugin` (Gradle 9.8.1 wrapper) | Passed, 2 of 2 |
| Plugin Verifier | same run | Compatible with PhpStorm 2026.2.3 (262.10968.76) and WebStorm 2026.3 (263.7307.202) |
| All local checks | `JDK25_HOME=<jdk> ./scripts/verify.sh --full` | Passed |
| CI on GitHub | `.github/workflows/ci.yml`, run 42470457728 for commit `393209b` | Passed: both jobs, including Plugin Verifier (Foojay provisioning worked on CI) |

**Limitation:** Foojay could not download JDK 25 locally because GitHub release downloads returned
HTTP 504 at that point. The run used a checksum-verified Azul Zulu JDK 25.0.5, passed in through
`-Porg.gradle.java.installations.paths=<jdk-home>`.

## Known problems

- The Plugin Verifier reports 4 deprecated and 6 experimental API usages, all in
  `ToolWindowFactory` default methods (`isApplicable`, `isDoNotActivateOnStart`, `manage`,
  `getAnchor`, `getIcon`), which Kotlin compiles into the implementing class. The plugin source
  does not call them. Check this when the real tool window is built (for example with the
  `-Xjvm-default` compiler setting).

## Phase 1 progress

The feasibility analysis is written (`docs/research/feasibility.md`; ADR 0004 and 0005 carry its
findings and stay `Proposed`). It was produced by the `architect` agent from documentation and
platform sources; no code or tests were run for it. The owner answered all eight follow-up
questions (ADR 0009); ADR 0004 and 0005 carry the decisions and stay `Proposed`.

## Spike 2 result (2026-11-08)

Feasible with caveats, recorded in ADR 0004. A `webServerFileHandler` (order first) injects location
attributes in memory, from the unsaved editor buffer, without writing the file. Fallback not needed.
Verified in this session with `scripts/gradle-docker.sh test verifyPlugin`: 12 of 12 tests passed
(`LocationInjectorTest` 7, `PsiOffsetTest` 1, `BuiltInServerInjectionTest` 2, `PluginSmokeTest` 2);
Plugin Verifier: Compatible with PhpStorm 2026.2.3 and WebStorm 2026.3, no new warnings. Caveats:
undocumented Netty-typed API; handler not yet gated to inspector previews; untrusted-project and
real-browser behavior unverified.

Local builds: no JDK 25 on this machine and downloads fail, so the checks above ran in Docker
(`scripts/gradle-docker.sh`, see `docs/development.md`). `./scripts/verify.sh` was not re-run.

## Spike 3 result (2026-11-08)

Coded and compiled, details in ADR 0004: product guard (WebStorm, PhpStorm), JCEF preview panel
limited to localhost URLs, `JBCefJSQuery` ping and a closed message validator, dependency on
`com.intellij.modules.jcef`. Verified in this session with `scripts/gradle-docker.sh test verifyPlugin`:
27 of 27 tests passed; Plugin Verifier Compatible with PhpStorm 2026.2.3 and WebStorm 2026.3, still
4 deprecated and 6 experimental usages from the old `ToolWindowFactory` scaffold.

**Not verified:** real JCEF rendering, the bridge round trip, the navigation veto and behavior in
PhpStorm. No test builds `PreviewPanel`; this needs `runIde` by hand (backlog item 3).

## Bridge protocol v1 (2026-11-08)

Specified in `docs/protocol.md`; implemented in `browser-extension/src/protocol.ts` and
`plugin/.../bridge/BridgeMessage.kt`, both tested against the shared `test-fixtures/protocol/vectors.json`
(202 vectors). `ProjectPathResolver` confines paths to the project, including symlink escapes.
Verified in this session: `npm test` 214 of 214, `npm run typecheck` clean,
`scripts/gradle-docker.sh test verifyPlugin` 34 of 34 tests, Plugin Verifier Compatible with both
products. ADR 0005 is now `Accepted`. Known difference: Kotlin rejects `1.0` and duplicate keys, TS
accepts `1.0` (no vector covers it).

## Phase 2 MVP wiring (2026-11-08)

Coded: in-page overlay (`browser-extension/src/inspector/`), gated injection (`PreviewGate`), click to
source (`PageMessageHandler`, `ProjectPathResolver`, `OpenFileDescriptor`), editor actions
"Highlight in Visual Inspector Preview", "Next Instance ..." and "Open Current HTML File in Visual
Inspector" (no default shortcut, no caret listener, ADR 0009). Verified in this session:
`npm test` 224 of 224, `npm run typecheck` clean, `scripts/gradle-docker.sh test verifyPlugin`
41 of 41 Kotlin tests, Plugin Verifier Compatible with PhpStorm 2026.2.3 and WebStorm 2026.3.

**Not verified (needs `runIde` by hand):** real JCEF rendering, bootstrap JS and query transport,
Alt+click navigation, highlight and next-instance on the page, the unmappable balloon, cookie-based
link following, the Open action, the missing-bundle message. The overlay DOM layer has no test.
The Phase 2 exit criterion (automated tests of both directions on the fixture) is therefore not met.

## PHP spike (2026-11-08)

Verdict feasible with caveats (ADR 0004, "Spike 5 result"). `php-support/visual-inspector-prepend.php`
rewrites static markup of included `.php` files in memory. Verified in this session with
`sh php-support/tests/check.sh` (Docker `php:8.5-cli`, opcache off and on): PASS, 23 tags and 31
attributes match independently computed offsets; HTML echoed from PHP strings gets no location. Limits:
offsets come from the file on disk, not the unsaved buffer; a UTF-8 BOM may shift offsets; opcache keeps
rewritten code, so toggling needs a restart. Not yet integrated into the plugin. `./scripts/verify.sh`
was not re-run.

## PHP in the preview (2026-11-08)

Coded (ADR 0010, `docs/php.md`): external localhost origins run the overlay only after the user
confirms that exact origin once ("Trust this origin", stored per project, revocable); the bridge also
answers `denied` for untrusted origins; highlight works on `.php` files via the HTML PSI view.
Verified in this session: `scripts/gradle-docker.sh test verifyPlugin`, 51 of 51 tests, Verifier
Compatible with both products. **Not verified:** the whole flow in a real IDE, PhpStorm, PHP PSI
view. Test server (from the repo root):
`docker run --rm -p 127.0.0.1:8000:8000 -v "$PWD":/app:ro -w /app/test-fixtures/php-site -e VISUAL_INSPECTOR_DEV=1 -e VISUAL_INSPECTOR_ROOT=/app php:8.5-cli php -d auto_prepend_file=/app/php-support/visual-inspector-prepend.php -S 0.0.0.0:8000`
then open `http://localhost:8000/index.php` in the preview and open the repo root as the project.

## Manual test results (owner, 2026-11-08, `runIde`)

Confirmed by hand: preview renders with styling (after fixing a cookie bug that made the built-in
server answer asset requests with 404; test `testSubresourcesOfPreviewedPageLoadWithCookiesOnly`),
Alt+click toggles inspect mode, hover frame appears, click moves the editor caret to the source.
After the later changes the owner also confirmed (WebStorm, `test-fixtures/demo-site`): highlight and
"Next Instance" from the editor, the unmappable notification and the Inspect toggle. Both directions
work for static HTML. Not yet confirmed: PhpStorm.

Changes after that feedback (verified: `npm test` 224 of 224, `scripts/gradle-docker.sh test verifyPlugin`
43 of 43, Verifier Compatible with both products): inspect mode starts on with a toolbar toggle,
shortcuts Ctrl+Alt+Shift+Period (highlight) and Ctrl+Alt+Shift+Comma (next instance) in the default
and both Mac keymaps (not checked for clashes), status line and notifications for each step. Manual
test project: `test-fixtures/demo-site` (open as its own project).

## Next task

Owner: try PHP in PhpStorm (steps in `docs/php.md`). Then: Phase 2 exit criteria (automated
end-to-end coverage), Vue adapter.
