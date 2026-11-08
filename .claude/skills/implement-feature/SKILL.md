---
name: implement-feature
description: Implement a planned feature task by task with tests, review and state updates.
argument-hint: "[plan file or backlog item]"
disable-model-invocation: true
---

Implement: $ARGUMENTS

Without a plan for a non-trivial feature, run `/plan-feature` first.

1. Use a feature branch for larger changes.
2. For each task:
   - Use the `test-driven-development` skill: failing test first.
   - Delegate to the suggested agent when useful, stating context, files, expected result and acceptance criteria. Parallelize only independent tasks; never two writers on one file. Use `isolation: worktree` for larger parallel work.
   - Check each delegated result against the actual diff and fresh test output.
   - On failure, use the `systematic-debugging` skill.
3. Run `/verify-project`.
4. Run `/review-changes`. Delegate to `security-review` if the bridge, paths or hooks changed.
5. Run `/update-project-state`.
6. Commit with a message explaining why.

Done means the Definition of Done in `CLAUDE.md` is met. Report anything unmet as open.
