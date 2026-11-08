# 0002: Simple monorepo with a self-contained plugin project

Status: Accepted (2026-11-08)

## Context

The project combines a Gradle-built Kotlin plugin and TypeScript packages. JetBrains'
`intellij-platform-plugin-template` assumes the Gradle project is the repository root.

## Decision

`plugin/` is a complete, self-contained Gradle project containing the template's wrapper and build
structure. TypeScript packages use npm workspaces at the repository root. There is no combined
build system; `scripts/verify.sh` calls both toolchains.

Template parts not adopted: sample code, changelog plugin, Qodana and release workflows, and
template cleanup workflows. They are demo material or would enable publishing.

## Consequences

Upstream template changes map directly onto `plugin/`. CI and commands run from `plugin/` for
Gradle. Opening `plugin/` directly gives the best IDE experience for plugin work.
