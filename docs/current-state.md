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

## Next task

Backlog item 3: JCEF availability and bridge round trip, with the product guard.
