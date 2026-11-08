# 0004: Plugin, shared inspector and thin adapters

Status: Proposed (2026-11-08). Requires the phase 1 analysis and the project owner's review.

## Context

`projektauftrag.md` asks for a simple, modular design and a comparison between a plugin-only
approach and a plugin with optional browser or framework adapters.

## Proposal

- A Kotlin plugin hosts the JCEF preview and does source resolution and navigation.
- One TypeScript DOM inspector is shared by JCEF and later browser extensions.
- Adapters only map a DOM element to a source location.
- The first milestone uses JCEF only. External browsers follow in phase 3.
- No separate daemon or service.

## Open questions for phase 1

- Can static HTML be mapped by in-memory attribute injection, or is structural PSI matching needed?
- What is the most stable source location signal per framework and version?
- Which IDE-side channel suits external browsers without internal APIs?

## Consequences

If accepted, the phase 2 tasks in `docs/backlog.md` apply as written. Spike results may change them.
