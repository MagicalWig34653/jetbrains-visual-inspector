# Agent and session workflow

## Disposable sessions

A session may end at any moment. Before a task is reported as finished, its results must be in
versioned files. Nothing required for continuation may exist only in a conversation, in subagent
state or in a private session file. Claude's auto memory and any plugin memory are never a required
information source. Agent definitions do not set the `memory` field; `scripts/check-repo.mjs`
enforces this.

To resume: run `/resume-project`. It uses only the repository and git.

## Standard workflow

1. Read the project state. 2. Identify the task. 3. Study relevant code and docs. 4. Decide the
approach (larger tasks: short plan via `/plan-feature`). 5. Delegate if useful. 6. Define tests.
7. Implement. 8. Run tests and build. 9. Review changes (`/review-changes`). 10. Update docs and
state (`/update-project-state`). 11. Commit.

## Orchestration

- One main agent coordinates, integrates and verifies. It checks subagent claims against the diff
  and fresh test output.
- Specialists get bounded subtasks. Each assignment states context, files in scope, expected
  result and acceptance criteria, because subagents start without this conversation.
- Parallel only for independent work. Never two concurrent writers on the same file. Use
  `isolation: worktree` for larger parallel changes.
- No agent hierarchies, agent-to-agent messaging systems or autonomous loops.

## Agents

| Agent | Writes | Purpose |
| --- | --- | --- |
| `architect` | docs only | Decisions, API research, module boundaries, risks |
| `kotlin-plugin` | `plugin/` | IntelliJ Platform, PSI, JCEF, Gradle |
| `browser-integration` | TypeScript packages | Extensions, DOM inspection, bridge, source mapping |
| `testing` | tests, fixtures | Strategy, regressions, reproducible failures, verification |
| `security-review` | read-only | Bridge, path validation, hooks, skills, secrets |

Built-in agents were checked first. `Explore` remains the choice for read-only code search and
`Plan` for plan mode. They are read-only and start without CLAUDE.md, so they do not cover roles
that must write files or follow project rules. The bundled `/security-review` reviews a diff; the
`security-review` agent adds the project's bridge and hook threat model.

## Hooks

Configured in `.claude/settings.json`, active only in this repository, implemented in dependency-free
Node with tests in `.claude/hooks/test/`.

| Event | Script | Behavior |
| --- | --- | --- |
| `SessionStart` | `session-context.mjs` | Adds branch, change count, last commit, next task |
| `PreToolUse` Bash | `guard-bash.mjs` | Denies dangerous or out-of-policy commands, runs the secret scan before `git commit`, asks before destructive git actions and writes outside the project |
| `PreToolUse` Read, Write, Edit, NotebookEdit | `guard-paths.mjs` | Denies credential files and global Claude configuration, asks before edits outside the project |

The guards are pattern based and catch accidents. They are not a security boundary. They fail
closed (`onFailure: block`). No hook runs after every file write: full validation belongs to
`./scripts/verify.sh`.

The permission allowlist contains only read-only git commands and the project's check commands.
Blanket shell permissions are forbidden and rejected by `scripts/check-repo.mjs`.

## Updating status

`docs/current-state.md` records facts verified by running something. If a check was not run, write
that it was not run. Backlog items are removed when done. Decisions go into ADRs.
