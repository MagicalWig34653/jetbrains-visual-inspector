---
name: resume-project
description: Reconstruct the project state from versioned files at the start of a new session or after losing context. Use before starting substantial work.
allowed-tools: Read Grep Glob Bash(git status) Bash(git status *) Bash(git log *) Bash(git branch --show-current)
---

Use only repository files and git. Do not rely on earlier conversations or agent memory.

1. Run `git status --short`, `git branch --show-current` and `git log --oneline -10`.
2. Read `CLAUDE.md`, `docs/current-state.md` and `docs/backlog.md`.
3. Read `docs/architecture.md` and the table in `docs/decisions/README.md`. Open individual ADRs only when relevant.
4. Read `docs/roadmap.md` for the current phase.
5. Consult `projektauftrag.md` when product requirements matter.

Report briefly:

1. Project goal.
2. Decided architecture, and what is still only proposed.
3. Implemented functionality.
4. Open tasks.
5. Which tests work, according to the recorded status and its date. These are recorded results, not fresh ones; offer `/verify-project`.
6. The sensible next task, with reasons.

Flag any contradiction between the documents, git and the code instead of silently choosing one. Do not begin implementation until the task is confirmed.
