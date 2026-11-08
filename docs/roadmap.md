# Roadmap

Each phase is independently testable. Source: section 8 of `projektauftrag.md`.

## Phase 0: Project setup (this is the initial commit)

Repository, documentation, agents, skills, hooks, CI and a buildable plugin scaffold.

## Phase 1: Architecture and feasibility

Check current JetBrains APIs, research existing inspector tools, evaluate source mapping limits,
define the security model, finalize architecture and interfaces with the project owner.

Exit: ADR 0004 and 0005 are `Accepted` or replaced; spikes for the biggest risks have results.

## Phase 2: MVP for static HTML

JCEF preview in a tool window, static HTML loading, hover highlighting, click navigation into HTML
source, basic editor-to-preview highlighting.

Exit: both directions work on `test-fixtures/static-html` with automated tests, including elements
that cannot be mapped.

## Phase 3: Frameworks

Adapter interface; React, Vue and Svelte step by step; dev servers and HMR; ambiguous mappings and
repeated component instances; Chrome and Firefox extension.

## Phase 4: Extended integration

Evaluate PHP and template support, polish the UI, optimize performance, test across IDE versions.

## Phase 5: Publication preparation

Complete automated tests, Plugin Verifier coverage, documentation, and preparation for a JetBrains
Marketplace submission. Publication itself is a separate, explicit decision.
