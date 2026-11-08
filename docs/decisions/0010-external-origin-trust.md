# 0010: Trust for external localhost origins

Status: Accepted (2026-11-08, owner decision)

## Context

Phase 3 lets the preview show a server the user runs (`php -S`, Docker, MAMP). Until now the overlay and
bridge ran only on pages of the built-in server opened with the preview token. A page on an external
localhost port is not under the plugin's control, and any local process can listen there (ADR 0005).

## Decision

- The overlay and bridge run on an external origin only after the user confirms that exact scheme, host and
  port once (`http://localhost:8000` is not `http://127.0.0.1:8000` and not `http://localhost:8001`).
- Confirmation is explicit: a dialog started by the "Trust this origin" button in the preview. Never
  automatic; typing a URL in the address bar does not trust it.
- Confirmed origins are stored per project (`TrustedOriginsService`, workspace file, per user, not shared
  through VCS) and can be revoked with the same button ("Revoke trust"). Stored values are re-validated on
  every read.
- Still only `http` on `localhost` and `127.0.0.1` (`PreviewUrlPolicy`).
- The built-in server origin stays trusted, but only for pages opened with the preview token.
- Defense in depth: the bridge handler checks, for every message, that the browser's current main frame URL
  has a trusted origin and otherwise replies `error` `denied`. `JBCefJSQuery` gives no frame or origin
  (ADR 0005), so a hostile subframe of a trusted page, or a script running during a navigation, cannot be
  told apart. That limit stays. The accepted worst case is unchanged: a trusted page can make the IDE open
  files inside the project.

## Consequences

Trust is per origin, not per page: any app later served on that port inherits it, so users should revoke
trust or use another port when the app changes. Paths from the page stay confined by `ProjectPathResolver`.
