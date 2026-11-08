#!/usr/bin/env bash
# Runs every check that exists today and fails on the first error.
#   ./scripts/verify.sh           repository, hooks, TypeScript, plugin build and tests
#   ./scripts/verify.sh --full    additionally runs the JetBrains Plugin Verifier (large download)
set -euo pipefail
cd "$(dirname "$0")/.."

step() { printf '\n==> %s\n' "$1"; }

step "Repository and agent setup"
node scripts/check-repo.mjs

step "Secret scan (tracked files)"
node .claude/hooks/secret-scan.mjs --all

step "Hook tests"
npm run --silent test:hooks

step "TypeScript typecheck"
npm run --silent typecheck

step "TypeScript tests"
npm --silent test

step "Plugin build and tests"
(cd plugin && ./gradlew --no-daemon build)

if [[ "${1:-}" == "--full" ]]; then
  step "JetBrains Plugin Verifier"
  (cd plugin && ./gradlew --no-daemon verifyPlugin)
fi

printf '\nAll checks passed.\n'
