---
name: plan-feature
description: Break a feature into small, independently testable tasks with acceptance criteria. Use before implementing anything larger than a small fix.
argument-hint: "[feature]"
---

Plan: $ARGUMENTS

1. Read `docs/current-state.md`, the relevant parts of `docs/architecture.md`, related ADRs and existing code.
2. Check the official documentation for every API the plan depends on. Mark anything unconfirmed as a spike.
3. If the feature needs an architecture decision, delegate to `architect` or ask the project owner first.
4. Write the plan to `docs/plans/YYYY-MM-DD-<feature>.md`:
   - Goal and explicit non-goals.
   - Ordered tasks, each small enough for one commit, with: files, failing test to write first, acceptance criteria, suggested agent.
   - Which tasks are independent and may run in parallel.
   - Security impact for anything touching the bridge or file paths.
   - Risks and open questions.
5. Add or update the matching entries in `docs/backlog.md`.

Stop after the plan. Do not implement.
