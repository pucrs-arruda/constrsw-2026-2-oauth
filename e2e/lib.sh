#!/usr/bin/env bash
# Shared helpers for the oauth E2E suite. Sourced by run.sh; never executed.
# Deliberately no `set -e`: an assertion failure must not abort the run.

PASS=0
FAIL=0
FAILURES=()
CURRENT_SUITE=""
BODY_FILE="$(mktemp)"
CREATED_USER_IDS=()
STATUS=""
BODY=""

if [ -t 1 ]; then
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_DIM=$'\033[2m'; C_BOLD=$'\033[1m'; C_OFF=$'\033[0m'
else
  C_RED=""; C_GREEN=""; C_DIM=""; C_BOLD=""; C_OFF=""
fi

suite() {
  CURRENT_SUITE="$1"
  printf '\n%s== %s ==%s\n' "$C_BOLD" "$1" "$C_OFF"
}

pass() {
  PASS=$((PASS + 1))
  printf '  %s✓%s %s\n' "$C_GREEN" "$C_OFF" "$1"
}

fail() {
  FAIL=$((FAIL + 1))
  FAILURES+=("[$CURRENT_SUITE] $1")
  printf '  %s✗ %s%s\n' "$C_RED" "$1" "$C_OFF"
  [ -n "${2:-}" ] && printf '    %s%s%s\n' "$C_DIM" "$2" "$C_OFF"
}

# api METHOD PATH [curl args...]  →  sets STATUS and BODY.
# Extra curl args carry headers/bodies, e.g. -H "Authorization: Bearer $T" -d '{}'.
api() {
  local method="$1" path="$2"
  shift 2
  STATUS=$(curl -s -o "$BODY_FILE" -w '%{http_code}' --max-time "${E2E_TIMEOUT:-30}" \
    -X "$method" "${BASE_URL}${path}" "$@")
  [ -z "$STATUS" ] && STATUS="000"
  BODY=$(cat "$BODY_FILE")
}

auth_h() { printf 'Authorization: Bearer %s' "$1"; }

# JSON request with a Bearer token: jreq METHOD PATH TOKEN [JSON_BODY]
jreq() {
  local method="$1" path="$2" token="$3" data="${4:-}"
  if [ -n "$data" ]; then
    api "$method" "$path" -H "$(auth_h "$token")" -H 'Content-Type: application/json' -d "$data"
  else
    api "$method" "$path" -H "$(auth_h "$token")"
  fi
}

# Form login helpers. They only set STATUS/BODY.
login_form() { api POST /login --data-urlencode "username=$1" --data-urlencode "password=$2"; }
refresh_form() { api POST /refresh --data-urlencode "refresh_token=$1"; }

# token_for USER PASSWORD → prints access_token (empty on failure)
token_for() {
  login_form "$1" "$2"
  [ "$STATUS" = "200" ] && jq -r '.access_token // empty' <<<"$BODY"
}

# Decodes a JWT payload to JSON.
jwt_payload() {
  jq -R 'split(".")[1] | gsub("-";"+") | gsub("_";"/") | @base64d | fromjson' <<<"$1" 2>/dev/null
}

uuid_v4() {
  if command -v uuidgen >/dev/null 2>&1; then uuidgen | tr 'A-Z' 'a-z'; else cat /proc/sys/kernel/random/uuid; fi
}

uniq_suffix() { printf '%s%s' "$(date +%s)" "$RANDOM"; }

# ---- assertions ------------------------------------------------------------

assert_status() { # expected label
  if [ "$STATUS" = "$1" ]; then pass "$2 → $1"; else fail "$2: expected HTTP $1, got $STATUS" "body: ${BODY:0:300}"; fi
}

assert_json() { # jq-filter expected label  (compares the raw jq output)
  local actual
  actual=$(jq -r "$1" <<<"$BODY" 2>/dev/null)
  if [ "$actual" = "$2" ]; then pass "$3"; else fail "$3: $1 expected '$2', got '$actual'" "body: ${BODY:0:300}"; fi
}

assert_json_true() { # jq-boolean-expression label
  if [ "$(jq -r "$1" <<<"$BODY" 2>/dev/null)" = "true" ]; then pass "$2"; else fail "$2: '$1' is not true" "body: ${BODY:0:300}"; fi
}

assert_empty_body() {
  if [ -z "$BODY" ]; then pass "$1"; else fail "$1: expected empty body" "body: ${BODY:0:200}"; fi
}

# Uniform OA error envelope. assert_error STATUS CODE label; CODE may be "*" (any).
assert_error() {
  local status="$1" code="$2" label="$3"
  if [ "$STATUS" != "$status" ]; then
    fail "$label: expected HTTP $status, got $STATUS" "body: ${BODY:0:300}"
    return
  fi
  if ! jq -e '(.error_source == "OAuthAPI") and (.error_stack | type == "array")
              and (.error_stack | all(type == "object"))
              and (.error_code | type == "string") and (.error_description | type == "string")' \
      <<<"$BODY" >/dev/null 2>&1; then
    fail "$label: body is not the OA error envelope" "body: ${BODY:0:300}"
    return
  fi
  if [ "$code" != "*" ] && [ "$(jq -r .error_code <<<"$BODY")" != "$code" ]; then
    fail "$label: expected error_code $code, got $(jq -r .error_code <<<"$BODY")"
    return
  fi
  pass "$label → $status $(jq -r .error_code <<<"$BODY")"
}

# ---- fixtures --------------------------------------------------------------

E2E_USER_PASSWORD="${E2E_USER_PASSWORD:-E2e-Passw0rd!x1}"

# create_user_fixture [first] [last] → sets NEW_USER_ID / NEW_USER_EMAIL (exit 1 on failure).
create_user_fixture() {
  NEW_USER_EMAIL="e2e-$(uniq_suffix)@pucrs.br"
  jreq POST /users "$ADMIN_TOKEN" \
    "$(jq -nc --arg u "$NEW_USER_EMAIL" --arg p "$E2E_USER_PASSWORD" --arg f "${1:-E2E}" --arg l "${2:-Fixture}" \
       '{username:$u,password:$p,"first-name":$f,"last-name":$l}')"
  NEW_USER_ID=$(jq -r '.id // empty' <<<"$BODY")
  if [ "$STATUS" != "201" ] || [ -z "$NEW_USER_ID" ]; then
    fail "fixture: could not create a test user (HTTP $STATUS)" "body: ${BODY:0:300}"
    return 1
  fi
  CREATED_USER_IDS+=("$NEW_USER_ID")
}

# Users cannot be hard-deleted through the API (DELETE only disables), so the
# best cleanup available is to disable whatever the run created.
cleanup() {
  local id
  for id in "${CREATED_USER_IDS[@]:-}"; do
    [ -n "$id" ] && curl -s -o /dev/null --max-time 15 -X DELETE "${BASE_URL}/users/${id}" \
      -H "$(auth_h "${ADMIN_TOKEN:-}")"
  done
  rm -f "$BODY_FILE"
}

summary() {
  printf '\n%s== Summary ==%s\n' "$C_BOLD" "$C_OFF"
  printf '  passed: %s%d%s  failed: %s%d%s\n' "$C_GREEN" "$PASS" "$C_OFF" "$C_RED" "$FAIL" "$C_OFF"
  if [ "$FAIL" -gt 0 ]; then
    printf '\nFailures:\n'
    printf '  - %s\n' "${FAILURES[@]}"
    return 1
  fi
}
