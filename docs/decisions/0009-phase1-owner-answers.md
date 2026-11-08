# 0009: Owner answers after the phase 1 feasibility analysis

Status: Accepted (2026-11-08)

## Context

`docs/research/feasibility.md` ended with eight questions for the project owner. The owner answered
all of them on 2026-11-08. This record extends ADR 0008 and does not
replace it.

## Decision

| Topic | Decision |
| --- | --- |
| Static HTML serving | Spike only the IDE's built-in web server. If it cannot inject in memory, ADR 0008's fallback (plugin-served content to JCEF) applies, and the owner is asked again before it is built. |
| Installation restriction | Runtime product guard: one build, active only in WebStorm and PhpStorm. No separate builds per product. |
| PHP placement | Confirmed: vanilla PHP is the first item of phase 3, after the static HTML MVP and before Vue. The PHP spike runs at the end of phase 2. |
| PHP scope | Only HTML elements need a source location. Text produced by PHP does not. Elements echoed from PHP strings stay unmappable. |
| PHP mechanism | The spike tests both an optional `auto_prepend_file` script and a plugin-side proxy that matches elements against the HTML structure of the `.php` file. The choice is made from the spike results. |
| Unmappable elements | Offer the nearest mappable ancestor, clearly labeled as such. Never navigate to a silent guess. |
| Unsaved editor text | Plugin-served content uses the editor buffer, so offsets match what the user sees. |
| IDE versions | Keep `since-build` 262 and verify only against WebStorm 2026.3 and PhpStorm 2026.2.3 until the first release. |
| Hostile-page worst case | Accepted: a page in the preview may cause the IDE to open any file inside the open project. Files outside the project, commands and writes stay impossible. The preview stays restricted to origins the user opened. |

## Consequences

- ADR 0004 and 0005 can be revised against these decisions; they stay `Proposed` until the spikes
  have results.
- Backlog item 3 gains the `com.intellij.modules.jcef` dependency and the runtime product guard.
  A PHP spike is added at the end of phase 2.
