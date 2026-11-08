# 0003: Toolchain and platform versions

Status: Accepted (2026-11-08)

## Context

Kotlin, Gradle, the IntelliJ Platform Gradle Plugin and the target platform must be compatible.
The official documentation lists Java 25 for platform branches 262 and 263. The official template
at commit `c7c9f997a7e67920efc534202e7ec29684d3b161` pins a known-good combination around platform
2026.2.3. WebStorm and PhpStorm 2026.3 were released on 2026-11-05.

## Decision

Use the template's combination: Gradle 9.8.1, Kotlin 2.4.21, IntelliJ Platform Gradle Plugin 2.19.0.
Compile with a Java 25 toolchain. Build against WebStorm 2026.2.3 with `since-build` 262 and no
`until-build`. Verify PhpStorm 2026.2.3 and WebStorm 2026.3, so that both products and the oldest
and newest supported branches are covered. The plugin depends only on
`com.intellij.modules.platform` for now.

Node 24 or newer runs TypeScript directly for tests, avoiding a test framework dependency.

## Consequences

Gradle 9.9.0 exists but the template has not adopted it; stay with the tested combination until the
template or Dependabot moves. Verifying only two combinations is a deliberate trade-off against
download size; the full matrix is in the backlog.
