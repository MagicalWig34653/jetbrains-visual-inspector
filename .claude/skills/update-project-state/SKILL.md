---
name: update-project-state
description: Update docs/current-state.md, the backlog and related documents after finishing a task or before ending a session, so a new session can continue without information loss.
---

Compare `git status`, the recent diff and the documents, then update only what changed:

- `docs/current-state.md`: implemented functionality, verification results with date and exact command, known problems, and the `## Next task` section. Keep the headings `## Implemented`, `## Verification status` and `## Next task`, which tooling relies on.
- `docs/backlog.md`: remove finished items, add discovered work, keep the priority order.
- `docs/roadmap.md`: only when a phase changes.
- `docs/architecture.md` and `docs/decisions/`: for design changes. A significant decision gets an ADR.
- `docs/development.md`: for changed commands or toolchain versions.
- `docs/skills.md`: for added or changed skills.
- `README.md`: when the public feature status changes.

Rules: record only verified facts. Write "not run" for anything not run. Keep the documents short: replace outdated text instead of appending a history, because git holds the history.

Then run `npm run check:repo`.
