# Test report: closing test gaps (2026-11-11)

All numbers below come from runs in this session (macOS arm64, Node 26.12.1; Gradle in Docker via
`scripts/gradle-docker.sh`, Temurin 25). Nothing was committed.

## Baseline (before any change)

| Check | Command | Result |
| --- | --- | --- |
| TypeScript typecheck and tests | `npm run typecheck && npm test` | green, 224 of 224 |
| Hook tests | `npm run test:hooks` | green, 57 of 57 |
| Plugin tests and Plugin Verifier | `scripts/gradle-docker.sh test verifyPlugin` | green, 51 of 51; Compatible with PhpStorm 2026.2.3 and WebStorm 2026.3 (4 deprecated, 6 experimental usages, unchanged) |
| PHP support check | `sh php-support/tests/check.sh` | PASS |

Nothing was red, so nothing had to be repaired.

## After

| Check | Result |
| --- | --- |
| `npm run typecheck && npm test` | green, 258 of 258 (+34) |
| `npm run test:hooks` | green, 57 of 57 |
| `scripts/gradle-docker.sh test verifyPlugin --rerun-tasks` | green, 85 tests, 0 failed, 0 skipped (+34); verifier unchanged |
| `sh php-support/tests/check.sh` | PASS |

## Gaps found

- `inspector.ts` (DOM layer, 197 lines): no test at all.
- TS and Kotlin protocol validators differed: TS accepted `1.0`, `1e0` and `-0` as integers
  (`JSON.parse` normalizes them); Kotlin and `docs/protocol.md` reject them. No shared vector covered it.
- `InspectorBundle.bootstrap`, `PreviewAvailability`: no tests.
- `LocationInjectingFileHandler`: only GET happy path, gating and subresources were tested.
- `ProjectPathResolver`, `TrustedOrigins`/`PagePaths`, `PageMessageHandler`: happy paths and a few rejects only.
- Not testable headlessly (see below): `PreviewPanel`, `InspectorActions`, `IdeBridgeSupport`, `PreviewHost`,
  `InspectorToolWindowFactory` rendering.

## Bugs found

1. **Protocol validator mismatch (fixed, TS side).** Decision: the stricter Kotlin behavior is normative
   (docs/protocol.md already says so). `browser-extension/src/protocol.ts` now uses a `JSON.parse` reviver
   that reads the number's source text (`context.source`) and turns anything that is not a plain integer
   literal into `null`. Engines without source text access keep the old lenient behavior (Kotlin remains the
   security boundary). Five vectors added to `test-fixtures/protocol/vectors.json` (202 to 207); both sides
   consume them. Test failed first (`{"v":1.0,...}` was accepted), then passed.
2. **BOM shifts offsets by one (fixed, production).** For a UTF-8 file with a BOM that is not open in an
   editor, `LocationInjectingFileHandler` decoded the disk bytes with the BOM as a character, so every
   `data-vi-src` offset was +1 compared with the editor Document (which has no BOM). Navigation would land one
   character late. Fix: `.removePrefix("﻿")` in the disk fallback only. Test
   `testFileWithByteOrderMarkIsServedAndMapped` failed first (`(html,1)` instead of `(html,0)`).

## Tests added

TypeScript (+34):
- `browser-extension/src/protocol.test.ts`: 2 tests (integer literals; plain integers stay valid) and 5 shared vectors.
- `browser-extension/src/inspector/inspector.test.ts`: 27 tests with a small hand-written DOM stub (no jsdom,
  no new dependency): closed shadow root, hello, inert page elements, throwing transport, click pass-through when
  off, navigate on click, repeated component index/count, unmappable with ancestor, tampered attribute,
  untrusted (`isTrusted=false`) click and alt+click, overlay-host clicks, alt+click toggle, idempotent mode,
  rAF throttling to one repaint, hover labels, untrusted moves, removed element before frame, mode-off cancels
  frame, highlight with emphasis and scroll, no index/out-of-range index, replace/clear, unknown location,
  200-box cap, invalid IDE messages, subscribe wiring, dynamic DOM added later, dispose cleanup, dispose with
  pending frame. Mutation checks (each made one test fail): removing the `isTrusted` check in click and in
  move, the `disposed` guard, removing the click listener or the unsubscribe in `dispose`, the box cap, and
  `closed` mode. The move `isTrusted` mutation initially survived, so the test was sharpened and re-checked.

Kotlin (+34):
- `ProjectPathResolverTest` +6: shared-prefix sibling directory, encoded/Windows/UNC/dot-segment paths, NUL and
  control characters, missing base dir, Unicode and spaces, symlink to parent.
- `TrustedOriginsTest` +4: host/port spelling tricks, non-canonical origin spellings, duplicate trust and unknown
  revoke, `PagePaths` cap/dedupe/nesting.
- `PageMessageHandlerTest` +6: malformed input codes, hello, directory and symlink escape with request id,
  explicit ancestor navigation confinement, base dir read per message, unmappable without ancestor.
- `BuiltInServerInjectionTest` +10 (real built-in server, HTTP): HEAD, cache and cookie flags, multibyte
  Content-Length, CRLF offsets, BOM, `.htm` and upper-case extension, non-HTML fall-through, missing file,
  20,000-element file, edit after first request.
- `InspectorBundleTest` +5 (bootstrap text: single embed, run-once guard, start guard, documented globals only,
  no template interpretation of bundle text).
- `PreviewAvailabilityTest` +3.
- `BridgeMessageValidatorTest`: vector count assertion 202 to 207.

Unchanged-by-design: ProjectPathResolver and TrustedOrigins edge cases found no further bugs.

## Not testable headlessly (not faked)

`PreviewPanel` (needs real JCEF), `JBCefJSQuery` round trip, navigation veto, `InspectorActions`
(needs a live editor, tool window and panel), `IdeEditorOpener`/`IdeUnmappableNotifier` (EDT, notifications),
`PreviewHost` (plain holder), real-browser layout of the overlay (the stub has no layout, `getBoundingClientRect`
is canned), `InspectorBundle.source` resource packaging (depends on `npm run build:inspector` before Gradle),
shadow-root isolation in a real engine, behavior in PhpStorm.

## Manual runIde checklist

1. `npm run build:inspector -w browser-extension`, then `cd plugin && ./gradlew runIde` (WebStorm, then PhpStorm).
2. Open `test-fixtures/demo-site`; run "Open Current HTML File in Visual Inspector" on `index.html`: page renders with CSS and JS.
3. Hover shows the overlay and tip; click an element: editor opens at the tag; repeated card: index/count correct.
4. Click a generated or unmapped element: balloon with the "nearest ancestor" action; no automatic navigation.
5. Alt+click toggles inspect mode and the toolbar follows; with it off, links work normally.
6. In the editor: place the caret in a tag, Ctrl+Alt+Shift+. highlights all instances; Ctrl+Alt+Shift+, cycles the emphasized one.
7. Edit the file without saving, reload the preview: the unsaved text and offsets are used; disk file unchanged.
8. Open a BOM UTF-8 file and a CRLF file that is not open in an editor: click lands on the right tag.
9. PHP: start the PHP server, type its localhost URL, confirm the origin; the bridge works; a non-localhost URL is refused; an unconfirmed origin gets `denied`.
10. In a normal browser tab open the same built-in-server URL without the preview token: no `data-vi-src` in the source.
11. Other IDE (IntelliJ IDEA) loads without touching JCEF (guard).

## Commands

```
npm run typecheck && npm test
npm run test:hooks
scripts/gradle-docker.sh test verifyPlugin --rerun-tasks
sh php-support/tests/check.sh
```
`./scripts/verify.sh` was not run (no local JDK 25); CI on GitHub was not run.

## Remaining gaps

No DOM test in a real browser engine; no test that the Kotlin and TS builders agree on label/tag edge cases
beyond the shared vectors; Windows path behavior (backslash, drive letters) only tested as syntax rejects, and
symlink tests skip where symlinks are unsupported; no test for HTTP keep-alive/`Connection: close` handling.
