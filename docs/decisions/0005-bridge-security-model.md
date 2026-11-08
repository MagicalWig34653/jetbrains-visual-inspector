# 0005: Browser-IDE bridge security model

Status: Proposed (2026-11-08). To be finalized with bridge protocol v1 in phase 1.

## Context

The preview executes arbitrary page JavaScript, and even localhost pages can be hostile through
dependencies or injected content. The bridge is the boundary between that code and the IDE.

## Proposal

Treat all page input as untrusted. The rules are listed under "Security model" in
`docs/architecture.md`: closed message set, no OS commands or arbitrary file access, canonicalized
project-confined paths, privileges only for user-opened pages, loopback plus token and origin
checks for external channels, no source code sent to external services.

## Consequences

Every bridge change needs validation tests and a `security-review` pass. Some convenience features
may be rejected to keep the boundary small.
