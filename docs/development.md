# Development

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK to run Gradle | 21 or newer | JDK 26 confirmed locally |
| Node.js | 24 or newer | Runs TypeScript tests through native type stripping; also runs the hooks |
| Git, GitHub CLI | current | `gh` only for repository operations |

Gradle needs no installation; use the wrapper. If no JDK 25 is installed, the Foojay resolver
downloads one. If that download fails, install a JDK 25 or pass one explicitly:
`./gradlew build -Porg.gradle.java.installations.paths=/path/to/jdk-25/home`, or
`JDK25_HOME=/path/to/jdk-25/home ./scripts/verify.sh`.

Gradle stores its distribution, the JDK toolchain and the downloaded IDEs in `~/.gradle` and
`plugin/.intellijPlatform`. Expect several gigabytes, more with the Plugin Verifier.

## Toolchain versions

Decided in ADR 0003, taken from the official plugin template (commit in `THIRD-PARTY-NOTICES.md`).

| Component | Version |
| --- | --- |
| IntelliJ Platform target | WebStorm 2026.2.3, `since-build` 262, no `until-build` |
| Plugin Verifier targets | PhpStorm 2026.2.3, WebStorm 2026.3 |
| Java (toolchain) | 25 |
| Kotlin | 2.4.21 |
| Gradle (wrapper) | 9.8.1 |
| IntelliJ Platform Gradle Plugin | 2.19.0 |
| TypeScript | 7.x |

## Commands

```bash
npm ci                        # install TypeScript dependencies
./scripts/verify.sh           # everything except the Plugin Verifier
./scripts/verify.sh --full    # including the Plugin Verifier

npm run typecheck
npm test
npm run test:hooks            # tests for the Claude Code hooks
npm run check:repo            # agent setup and required documents
npm run check:secrets         # secret scan over tracked files

cd plugin
./gradlew build               # compile, test, package to build/distributions/
./gradlew test
./gradlew verifyPlugin
./gradlew runIde              # start a sandbox WebStorm with the plugin
```

## Layout

| Path | Content |
| --- | --- |
| `plugin/` | Self-contained Gradle project for the IDE plugin |
| `browser-extension/` | npm workspace; extension and DOM inspector (scaffold) |
| `framework-adapters/` | One npm workspace per adapter (none yet) |
| `test-fixtures/` | Sample web projects for tests |
| `scripts/` | Verification scripts |
| `.claude/` | Project-local agents, skills, hooks, settings |
| `docs/` | Project knowledge |

## Continuous integration

`.github/workflows/ci.yml` runs on pushes to `main` and on pull requests:

- `repository`: repository check, secret scan, hook tests, TypeScript typecheck and tests.
- `plugin`: Gradle wrapper validation, build with tests, Plugin Verifier.

No release or Marketplace jobs exist, deliberately.
