#!/usr/bin/env bash
# End-to-end suite for the oauth service. Hits a RUNNING stack over HTTP
# (docker compose up: keycloak + oauth) — nothing is mocked.
#
#   ./e2e/run.sh              run every suite
#   ./e2e/run.sh users roles  run only the named suites (file name minus NN_ and .sh)
#
# Environment (all optional):
#   BASE_URL              oauth API base URL           default http://localhost:8181
#   METRICS_URL           Prometheus scrape URL        default http://localhost:8381  (skipped if unreachable)
#   E2E_PASSWORD          password of the seeded users default a12345678
#   E2E_ADMIN_USER / E2E_COORDINATOR_USER / E2E_PROFESSOR_USER / E2E_STUDENT_USER
#   E2E_WAIT_SECONDS      how long to wait for /health default 120
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8181}"
BASE_URL="${BASE_URL%/}"
METRICS_URL="${METRICS_URL:-http://localhost:8381}"
E2E_PASSWORD="${E2E_PASSWORD:-a12345678}"
ADMIN_USER="${E2E_ADMIN_USER:-admin@pucrs.br}"
COORDINATOR_USER="${E2E_COORDINATOR_USER:-coordinator@pucrs.br}"
PROFESSOR_USER="${E2E_PROFESSOR_USER:-professor@pucrs.br}"
STUDENT_USER="${E2E_STUDENT_USER:-student@pucrs.br}"

for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "error: '$tool' is required" >&2; exit 2; }
done

# shellcheck source=lib.sh
. "$HERE/lib.sh"
trap cleanup EXIT

echo "oauth E2E against $BASE_URL"

# Wait for the stack (Keycloak import can take a while on first boot).
deadline=$(( $(date +%s) + ${E2E_WAIT_SECONDS:-120} ))
until [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BASE_URL/health")" = "200" ]; do
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "error: $BASE_URL/health did not return 200 within ${E2E_WAIT_SECONDS:-120}s — is the stack up?" >&2
    exit 2
  fi
  sleep 3
done

# Seeded-user tokens, fetched once and shared by every suite.
ADMIN_TOKEN=$(token_for "$ADMIN_USER" "$E2E_PASSWORD")
COORDINATOR_TOKEN=$(token_for "$COORDINATOR_USER" "$E2E_PASSWORD")
PROFESSOR_TOKEN=$(token_for "$PROFESSOR_USER" "$E2E_PASSWORD")
STUDENT_TOKEN=$(token_for "$STUDENT_USER" "$E2E_PASSWORD")
for var in ADMIN_TOKEN COORDINATOR_TOKEN PROFESSOR_TOKEN STUDENT_TOKEN; do
  if [ -z "${!var}" ]; then
    echo "error: could not log in the seeded user for $var (check realm import / E2E_PASSWORD)" >&2
    exit 2
  fi
done

selected=("$@")
for file in "$HERE"/suites/[0-9][0-9]_*.sh; do
  name="$(basename "$file" .sh)"; name="${name#*_}"
  if [ "${#selected[@]}" -gt 0 ]; then
    match=0; for s in "${selected[@]}"; do [ "$s" = "$name" ] && match=1; done
    [ "$match" = 1 ] || continue
  fi
  # shellcheck disable=SC1090
  . "$file"
done

summary
