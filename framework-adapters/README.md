# Framework adapters

Each adapter will be one npm workspace at `framework-adapters/<name>/` and will only answer
"which source location produced this DOM element?". Bridge, UI, highlighting and editor navigation
stay shared. See [`docs/architecture.md`](../docs/architecture.md).

**Status:** no adapters exist yet. React, Vue and Svelte are planned for roadmap phase 3, PHP
templates for phase 4.
