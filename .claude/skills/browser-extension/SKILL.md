---
name: browser-extension
description: Rules and official documentation pointers for browser extensions, the DOM inspector, the browser-IDE bridge and framework adapters. Use when changing TypeScript in browser-extension/ or framework-adapters/.
user-invocable: false
paths: browser-extension/**, framework-adapters/**
---

Documentation:

- Chrome extensions (Manifest V3): <https://developer.chrome.com/docs/extensions>
- Firefox WebExtensions: <https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions>

Read the current page before using an extension API; Chrome and Firefox differ, notably in background script handling.

Rules:

- Page content, DOM attributes and framework metadata are untrusted input.
- The inspector is read-only toward the page, apart from its own overlay. Keep the overlay isolated from page styles.
- No continuous full DOM scans. React to pointer events and throttle them.
- Request the narrowest possible extension permissions and host permissions.
- Messages use the versioned bridge protocol. Validate on receipt on both sides.
- Never send a source path to the IDE as an instruction to open a file without IDE-side validation; the IDE decides.
- When no reliable source location exists, report that. Never guess from a CSS selector.
- Adapters contain only the mapping for their technology. Shared behavior belongs in the inspector.
- Tests run with `node --test`; TypeScript must stay within erasable syntax (no enums, no namespaces, no parameter properties).
