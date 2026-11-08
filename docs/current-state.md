# Current state

Last updated: 2026-11-08, after project initialization.

Repository: <https://github.com/MagicalWig34653/jetbrains-visual-inspector>

Phase: 0 (project setup) finished; phase 1 (architecture and feasibility) not started.

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

## Next task

Backlog item 1: feasibility analysis and architecture discussion from section 10 of
`projektauftrag.md`. It ends with targeted questions to the project owner; do not begin
implementing features before that discussion.
