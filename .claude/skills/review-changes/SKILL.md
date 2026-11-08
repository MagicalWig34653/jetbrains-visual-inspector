---
name: review-changes
description: Review uncommitted or branch changes before a commit for correctness, scope, security and repository hygiene.
allowed-tools: Read Grep Glob Bash(git status) Bash(git status *) Bash(git diff) Bash(git diff *) Bash(git log *) Bash(npm run check:secrets)
---

1. Inspect `git status` and the full diff, staged and unstaged.
2. Check:
   - Correctness against the task's acceptance criteria.
   - Tests exist for changed behavior and would fail without the change.
   - Scope: no unrelated changes, unnecessary dependencies or invented APIs.
   - Hygiene: no build output, IDE settings, secrets, transcripts or debug leftovers.
   - The plugin never modifies user code.
3. Run `npm run check:secrets`. Note that it scans tracked files; the commit hook scans the index.
4. If the bridge, path handling, hooks, skills or CI changed, delegate to the `security-review` agent.
5. For a deeper correctness pass, the bundled `/code-review` and `/security-review` skills can be used.

Report concrete findings by severity with file and line, or state that none were found and what was covered. Do not commit.
