# 0001: Disposable sessions, repository as source of truth

Status: Accepted (2026-11-08)

## Context

Development happens in Claude Code sessions that can end at any time. Knowledge held only in a
conversation or in unversioned agent memory is lost or invisible to other contributors.

## Decision

Every session must be replaceable without information loss. Durable truth lives only in versioned
files and git. Agents are stateless and receive context through their assignment, versioned docs
and code. Agent definitions do not use persistent `memory`. `/resume-project` restores state using
only the repository.

## Consequences

Every completed task updates `docs/current-state.md` and related documents, which costs some effort
per task. `scripts/check-repo.mjs` enforces the required structure in CI.
