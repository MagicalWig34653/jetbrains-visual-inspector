#!/usr/bin/env bash
# Runs every check that exists today and fails on the first error.
#   ./scripts/verify.sh           repository, hooks, TypeScript, plugin build and tests
#   ./scripts/verify.sh --full    additionally runs the JetBrains Plugin Verifier (large download)
# Set JDK25_HOME to use a specific JDK 25 instead of auto-detection or Foojay download.
set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE_ARGS=(--no-daemon)
if [[ -n "${JDK25_HOME:-}" ]]; then
  GRADLE_ARGS+=("-Porg.gradle.java.installations.paths=${JDK25_HOME}")
fi

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

step "Inspector bundle (packaged into the plugin)"
npm run --silent build:inspector -w browser-extension

step "Plugin build and tests"
(cd plugin && ./gradlew "${GRADLE_ARGS[@]}" build)

if [[ "${1:-}" == "--full" ]]; then
  step "JetBrains Plugin Verifier"
  (cd plugin && ./gradlew "${GRADLE_ARGS[@]}" verifyPlugin)
fi

printf '\nAll checks passed.\n'
