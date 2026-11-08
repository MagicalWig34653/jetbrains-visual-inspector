# 0005: Browser-IDE bridge security model

Status: Proposed (2026-11-08). Owner has accepted the worst case (ADR 0009); to be finalized with bridge protocol v1.

## Context

The preview executes arbitrary page JavaScript, and even localhost pages can be hostile through
dependencies or injected content. The bridge is the boundary between that code and the IDE.

## Proposal

Treat all page input as untrusted. The rules are listed under "Security model" in
`docs/architecture.md`: closed message set, no OS commands or arbitrary file access, canonicalized
project-confined paths, privileges only for user-opened pages, loopback plus token and origin
checks for external channels, no source code sent to external services.

## Phase 1 findings

Details: `docs/research/feasibility.md` sections 1 and 6.

- [V] The `JBCefJSQuery` handler is `Function<String, Response>`: it receives only the message string, not
  the frame or origin (platform source `JBCefJSQuery.java`, 262). The generated `window.cefQuery_*`
  function is callable by any script in any frame. Token secrecy inside the page therefore gives no
  protection against hostile page scripts.
- Added rules [P]: the preview may show only user-opened origins (veto other navigations and popups in the
  request and life span handlers); the bridge accepts project-relative paths only, with size limits;
  every location is canonicalized into the project before navigation. The accepted worst case is "a page
  can open a project file in the editor"; no commands, reads outside the project or writes.
- [V] If the IDE's built-in server hosts an external channel, `HttpRequestHandler.isAccessible` rejects
  non-local hosts and write requests without `Origin` (platform source comments); token and origin checks
  stay our responsibility.
- Not a precedent: `launch-editor` style endpoints start processes; this project never does.

## Owner decision (ADR 0009)

The worst case is accepted: a page in the preview may cause the IDE to open any file inside the open
project. Files outside the project, commands and writes stay impossible. The preview stays restricted
to origins the user opened.

## Consequences

Every bridge change needs validation tests and a `security-review` pass. Some convenience features
may be rejected to keep the boundary small. Status stays Proposed until bridge protocol v1 (backlog item
4) exists with its validation tests.
