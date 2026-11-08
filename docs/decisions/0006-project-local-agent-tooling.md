# 0006: Project-local agents, skills and hooks

Status: Accepted (2026-11-08)

## Context

The tooling must work for anyone who clones the repository and must not affect other projects.

## Decision

Agents, skills, hooks and settings live in `.claude/` and are versioned. Nothing is installed
globally. Third-party skills are copied in only after review, with source, commit and license
recorded in `docs/skills.md`. Hooks are dependency-free Node scripts with tests, because Node is
already a project prerequisite and this avoids relying on `jq` or a specific shell.

## Consequences

Vendored skills do not update automatically and must be re-reviewed when refreshed. A developer's
personal global plugins may still load in their own sessions, but the project never depends on
them.
