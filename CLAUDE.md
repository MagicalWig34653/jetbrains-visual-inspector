# JetBrains Visual Inspector

Native WebStorm and PhpStorm plugin for bidirectional navigation between rendered web pages and
their source code, modeled on Dreamweaver's Live View / Inspect Mode. Navigation only: no visual
editing, and the plugin never modifies user code.

## Core rule: sessions are disposable

Any session must be replaceable by a new one at any moment without losing information. Durable
truth lives only in versioned files and git. Do not rely on conversation context, agent memory or
private session files. New session: run `/resume-project`.

## Technical rules

- Kotlin + IntelliJ Platform SDK in `plugin/`; TypeScript in `browser-extension/` and `framework-adapters/`.
- Keep it simple: established technology, no extra services, no unnecessary dependencies.
- Check official documentation before using an API. Never invent APIs.
- Treat every web page as untrusted. Browser-to-IDE input is validated; see `docs/architecture.md`.
- Never claim a test or build passed without having run it in this session.
- Do not change anything outside this repository without explicit approval.
- Skills, agents and hooks stay project-local, never global. No Marketplace publishing or releases.

## Where things are

| Need | File |
| --- | --- |
| Current status, test status, next task | `docs/current-state.md` |
| Prioritized work | `docs/backlog.md` |
| Phases | `docs/roadmap.md` |
| System design | `docs/architecture.md` |
| Decisions and reasons | `docs/decisions/` |
| Setup, toolchain versions | `docs/development.md` |
| Agent and session rules | `docs/agent-workflow.md` |
| Skills, sources, licenses | `docs/skills.md` |
| Original product brief (German) | `projektauftrag.md` |

## Commands

```bash
npm ci                       # once after cloning
./scripts/verify.sh          # all checks: repo, secrets, hooks, TypeScript, plugin build and tests
./scripts/verify.sh --full   # additionally runs the JetBrains Plugin Verifier
npm run typecheck && npm test
cd plugin && ./gradlew build # also: test, verifyPlugin, runIde
```

## Agents and skills

The main agent coordinates, integrates and verifies. Delegate bounded subtasks with stated results
and acceptance criteria to `architect`, `kotlin-plugin`, `browser-integration`, `testing` or
`security-review`. Parallelize only independent work, never two writers on one file. Full rules:
`docs/agent-workflow.md`.

Skills: `/resume-project`, `/plan-feature`, `/implement-feature`, `/verify-project`,
`/review-changes`, `/update-project-state`. Process skills adopted from superpowers:
`test-driven-development`, `systematic-debugging`, `verification-before-completion`. List and
sources: `docs/skills.md`.

## Git

`main` is the main branch; use feature branches for larger changes. Never force-push `main`. Commit
messages explain why. Never commit build output, IDE settings, secrets, transcripts or agent state.

## Definition of Done

Acceptance criteria met; relevant tests passed and were actually run; no known critical bugs;
changes documented; `docs/current-state.md` updated; traceable git commit exists.

## Keeping status current

After every completed task run `/update-project-state`. Record only what was verified. Significant
decisions get an ADR in `docs/decisions/`.
