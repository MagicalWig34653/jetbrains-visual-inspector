#!/usr/bin/env bash
# Spike 5 check: php -S with the prepend script, fetched with curl. Uses Docker (php:8.5-cli) or a host php 8+.
# (Temp files go under php-support/tests/ because Docker VMs such as colima only share the home tree.)
# Skips with exit 0 when neither is usable. Usage: php-support/tests/check.sh
set -uo pipefail
cd "$(dirname "$0")/../.."
ROOT="$PWD"
FIX="$ROOT/test-fixtures/php-site"
IMAGE="${VI_PHP_IMAGE:-php:8.5-cli}"
PORT="${VI_PHP_PORT:-18085}"

if command -v docker >/dev/null 2>&1 && docker image inspect "$IMAGE" >/dev/null 2>&1; then
  MODE=docker
elif command -v php >/dev/null 2>&1 && php -r 'exit(PHP_VERSION_ID >= 80000 ? 0 : 1);' 2>/dev/null; then
  MODE=host
else
  echo "SKIP php-support check: no usable Docker image $IMAGE and no host php 8+"
  exit 0
fi
command -v curl >/dev/null 2>&1 && command -v node >/dev/null 2>&1 || { echo "SKIP php-support check: curl or node missing"; exit 0; }

WORK="$(mktemp -d "$ROOT/php-support/tests/.work.XXXXXX")"; CID=""; PID=""
cleanup() { [[ -n "$CID" ]] && docker rm -f "$CID" >/dev/null 2>&1; [[ -n "$PID" ]] && kill "$PID" 2>/dev/null; rm -rf "$WORK"; }
trap cleanup EXIT
mkdir "$WORK/outside"; echo '<section id="outside-block">outside root</section>' > "$WORK/outside/outside.php"
hash_tree() { (cd "$1" && find . -type f -print0 | sort -z | xargs -0 shasum | shasum); }
BEFORE="$(hash_tree "$FIX")"
FAIL=0
fail() { echo "FAIL $*"; FAIL=1; }

# start NAME PHP_ARGS... (environment: DEV=1|0)
start() {
  local name="$1"; shift
  stop
  if [[ "$MODE" == docker ]]; then
    CID="$(docker run -d --rm -p "127.0.0.1::8080" \
      -v "$FIX:/app:ro" -v "$ROOT/php-support:/vi:ro" -v "$WORK/outside:/outside:ro" \
      -e VISUAL_INSPECTOR_DEV="$DEV" -e VISUAL_INSPECTOR_ROOT=/app -e VI_OUTSIDE_FILE=/outside/outside.php \
      "$IMAGE" php -d auto_prepend_file=/vi/visual-inspector-prepend.php "$@" -S 0.0.0.0:8080 -t /app)"
    URL="http://127.0.0.1:$(docker port "$CID" 8080/tcp | head -1 | sed 's/.*://')"
  else
    VISUAL_INSPECTOR_DEV="$DEV" VISUAL_INSPECTOR_ROOT="$FIX" VI_OUTSIDE_FILE="$WORK/outside/outside.php" \
      php -d auto_prepend_file="$ROOT/php-support/visual-inspector-prepend.php" "$@" -S "127.0.0.1:$PORT" -t "$FIX" \
      >/dev/null 2>&1 & PID=$!
    URL="http://127.0.0.1:$PORT"
  fi
  for _ in $(seq 1 50); do curl -fs "$URL/status.php" >/dev/null 2>&1 && return 0; sleep 0.2; done
  fail "$name: server did not start"; return 1
}
stop() {
  [[ -n "$CID" ]] && { docker rm -f "$CID" >/dev/null 2>&1; CID=""; }
  [[ -n "$PID" ]] && { kill "$PID" 2>/dev/null; wait "$PID" 2>/dev/null; PID=""; }
  return 0
}
fetch() { curl -fs "$URL/$1" -o "$2" || fail "fetch $1"; }
as() { node php-support/tests/assert.mjs "$@" || fail "assertion: $*"; }

echo "mode: $MODE"
echo "1. disabled (no env value)";      DEV=0 start off && { fetch index.php "$WORK/off.html"; as plain "$WORK/off.html"; }
echo "2. enabled, opcache off";         DEV=1 start on  && {
  fetch index.php "$WORK/on1.html"; fetch index.php "$WORK/on2.html"
  as check "$FIX" "$WORK/on1.html" "$WORK/outside/outside.php"; as same "$WORK/on1.html" "$WORK/on2.html"
  as stripped "$WORK/on1.html" "$WORK/off.html"; }
echo "3. enabled, opcache.enable_cli=1"; DEV=1 start oc -d opcache.enable_cli=1 -d opcache.validate_timestamps=1 && {
  fetch index.php "$WORK/oc1.html"; fetch index.php "$WORK/oc2.html"; fetch status.php "$WORK/status.json"
  echo "  opcache status: $(cat "$WORK/status.json")"
  as check "$FIX" "$WORK/oc1.html" "$WORK/outside/outside.php"; as same "$WORK/oc1.html" "$WORK/oc2.html"
  as same "$WORK/oc1.html" "$WORK/on1.html"; }
stop
[[ "$(hash_tree "$FIX")" == "$BEFORE" ]] || fail "fixture files changed"
if [[ $FAIL -eq 0 ]]; then echo "PASS php-support check"; else echo "FAILED php-support check"; exit 1; fi
