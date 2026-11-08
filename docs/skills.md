# Skills

All skills live in `.claude/skills/` and exist only in this repository. Evaluated 2026-11-08 with
Claude Code 2.1.319.

## Project skills (written for this project)

| Skill | Invocation | Purpose |
| --- | --- | --- |
| `resume-project` | `/resume-project` | Rebuild project state from versioned files |
| `plan-feature` | `/plan-feature` | Break a feature into testable tasks |
| `implement-feature` | `/implement-feature` (manual only) | Implement a planned feature |
| `verify-project` | `/verify-project` | Run builds, tests and quality checks |
| `review-changes` | `/review-changes` | Review changes before a commit |
| `update-project-state` | `/update-project-state` | Update status and backlog |
| `intellij-platform` | automatic for `plugin/` | Verified platform rules and documentation pointers |
| `browser-extension` | automatic for TypeScript packages | Browser extension and bridge rules |

The last two are based on the official documentation linked inside them.

## Adopted third-party skills

Source: <https://github.com/obra/superpowers>, version 7.0.2, commit
`22f0b463e7a5e579904dd6211a520321035526f7`, MIT License, Copyright (c) 2025 Jesse Vincent. The
license text is in each skill directory.

| Skill | Files taken | Local changes |
| --- | --- | --- |
| `systematic-debugging` | `SKILL.md`, `root-cause-tracing.md`, `defense-in-depth.md`, `condition-based-waiting.md` | Cross-references point to local skill names; references to the two omitted files removed |
| `test-driven-development` | `SKILL.md`, `writing-good-tests.md` | One plugin cross-reference removed |
| `verification-before-completion` | `SKILL.md` | None |

Omitted on purpose: `find-polluter.sh` (npm-specific shell script) and
`condition-based-waiting-example.ts` (example code). The adopted skills therefore contain no
executable files.

Review result: reputable and widely used source; permissive license; last commit 2026-11-03;
standard `SKILL.md` format; every adopted file was read in full and contains no network calls,
install steps or instructions affecting anything outside the project; directly useful for the
required TDD and debugging workflow.

## Evaluated and not adopted

| Candidate | Reason |
| --- | --- |
| superpowers `brainstorming`, `writing-plans`, `executing-plans` | Covered by the smaller `/plan-feature` and `/implement-feature` |
| superpowers `subagent-driven-development`, `dispatching-parallel-agents` | Covered by `docs/agent-workflow.md` |
| superpowers `using-git-worktrees` | Covered by the built-in `isolation: worktree` |
| superpowers `requesting-code-review`, `receiving-code-review` | Covered by `/review-changes` |
| Official `kotlin-lsp`, `typescript-lsp` | Plugins that need a language server installed globally; conflicts with the project-local rule. Can be reconsidered by each developer |
| Official `security-guidance` | 74 files and its own hooks; the project hooks and `security-review` agent are smaller and specific |
| Official `code-review`, `claude-md-management`, `hookify` | Covered by bundled `/code-review`, `/security-review` or not needed |
| IntelliJ Platform or browser extension skills | None found in the preferred sources (commit `0412be76f50ab4f4f8abff3753efa9269fbbca5b` of `anthropics/claude-plugins-official` searched); own skills written |

## Adding a skill

Check source reputation, license, freshness, Claude Code compatibility, security-relevant
instructions and scripts, and real project benefit. Record the result here.
`scripts/check-repo.mjs` fails if a skill is not listed in this file.
