# 0008: Product decisions by the project owner

Status: Accepted (2026-11-08)

## Context

Before the phase 1 feasibility analysis, the project owner answered 14 product and architecture
questions through a form. The raw answers, including the question texts, are in
`docs/research/owner-answers.json`.

## Decision

| Topic | Decision |
| --- | --- |
| Target products | WebStorm and PhpStorm only |
| Platform baseline | 2026.2 (build 262) |
| Static HTML mapping | Inject location attributes into the served response, in memory only |
| Static file source | The IDE's built-in web server first; if injection is not possible there, the plugin serves project files to JCEF itself |
| Preview placement | Tool window |
| Chrome and Firefox | Phase 3, after the JCEF MVP |
| Framework order | Vue, then Svelte, then React |
| Dev instrumentation | Allowed as an optional, documented dev dependency the user adds; the plugin never adds it |
| Code-to-preview sync | Off by default; triggered by an action or shortcut |
| Repeated elements | Highlight all instances; allow cycling through them |
| Navigation trust scope | Only project files and localhost URLs the user opened |
| PHP | Prioritized earlier than planned, starting with vanilla PHP only; Twig and Blade stay later |
| Next session | Feasibility report first, then follow-up questions, then spikes |
| Name and license | Unchanged: repository `jetbrains-visual-inspector`, plugin "Visual Inspector", MIT |

Interpretation to confirm in phase 1: "earlier" for PHP is placed as the first item of phase 3,
after the static HTML MVP and before Vue.

## Consequences

- The phase 1 analysis must check whether the built-in web server allows in-memory response
  rewriting through a supported API, and how `plugin.xml` can restrict installation to WebStorm
  and PhpStorm.
- ADR 0004 and 0005 stay `Proposed` until the analysis; they must follow these decisions.
