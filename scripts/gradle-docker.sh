#!/bin/sh
# Runs Gradle for plugin/ inside a Temurin 25 container, for machines without JDK 25 where the
# Foojay download fails (see docs/development.md). Usage: scripts/gradle-docker.sh build
# Uses the Gradle distribution already cached by the wrapper on the host (mounted read-only) and a
# named Docker volume as Gradle home, so ~/.gradle is not written to.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
dist=$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-9.8.1-bin/*/gradle-9.8.1 2>/dev/null | head -n 1 || true)
[ -n "$dist" ] || { echo "Run plugin/gradlew once on the host to cache Gradle 9.8.1" >&2; exit 1; }
exec docker run --rm \
  -v "$root":/work \
  -v "$dist":/opt/gradle:ro \
  -v vi-gradle-home:/gradle-home \
  -e GRADLE_USER_HOME=/gradle-home \
  -w /work/plugin \
  eclipse-temurin:25-jdk /opt/gradle/bin/gradle --console=plain "$@"
