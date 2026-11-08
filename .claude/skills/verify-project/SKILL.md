---
name: verify-project
description: Run the project's builds, tests and quality checks and report the actual results. Use before claiming work is complete or before a commit.
argument-hint: "[--full]"
allowed-tools: Bash(./scripts/verify.sh) Bash(./scripts/verify.sh *)
---

Run `./scripts/verify.sh $ARGUMENTS` from the repository root. Pass `--full` to include the JetBrains Plugin Verifier, which is required before merging plugin changes that use new platform APIs.

Report per step: passed, failed or not run, with test counts where available. On failure, report the exact error and use the `systematic-debugging` skill; do not rerun hoping for a different result, except for a demonstrated transient network failure.

Follow the `verification-before-completion` skill: claim only what this run showed. Skipped or unavailable checks are reported as not run.
