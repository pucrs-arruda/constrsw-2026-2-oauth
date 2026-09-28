#!/usr/bin/env bash
# Platform API smoke test — walks every implemented endpoint in dependency order.
# Contract: _bmad-output/specs/spec-api-smoke-test/SPEC.md + endpoint-catalog.md
set -u -o pipefail

API="${API:-http://localhost:8181}"
ADMIN_USER="${ADMIN_USER:-admin@pucrs.br}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-a12345678}"
SUFFIX="$(date +%s)-$$"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/smoke-test-$(date +%Y%m%d-%H%M%S).log"

# Some assert_status calls are captured via $(...) to grab a response body,
# which forks a subshell — plain counter variables incremented inside would
# not survive back to the parent. A results file does, since it's a real
# filesystem write visible from any subshell.
RESULTS_FILE="$(mktemp)"
trap 'rm -f "$RESULTS_FILE"' EXIT

# ---------------------------------------------------------------------------
# reporting
# ---------------------------------------------------------------------------

# Always writes to stderr, never stdout: callers capture response bodies from
# assert_status via command substitution, and a PASS/FAIL line on stdout would
# corrupt that capture (and silently vanish from the visible run output).
report() {
  local status="$1" method="$2" path="$3" expected="$4" actual="$5"
  echo "$status" >>"$RESULTS_FILE"
  case "$status" in
    PASS) printf 'PASS  %-6s %-45s expected=%s actual=%s\n' "$method" "$path" "$expected" "$actual" >&2 ;;
    FAIL) printf 'FAIL  %-6s %-45s expected=%s actual=%s\n' "$method" "$path" "$expected" "$actual" >&2 ;;
    SKIP) printf 'SKIP  %-6s %-45s (%s)\n' "$method" "$path" "$expected" >&2 ;;
  esac
}

# curl wrapper: prints "<status>\n<body>", auth header optional
call() {
  local method="$1" path="$2" auth_header="$3" body="${4:-}"
  local resp
  if [[ -n "$body" ]]; then
    resp=$(curl -sS -o /tmp/smoke-body.$$ -w '%{http_code}' -X "$method" "$API$path" \
      ${auth_header:+-H "$auth_header"} -H 'Content-Type: application/json' -d "$body")
  else
    resp=$(curl -sS -o /tmp/smoke-body.$$ -w '%{http_code}' -X "$method" "$API$path" \
      ${auth_header:+-H "$auth_header"})
  fi
  local code=$?
  if [[ $code -ne 0 ]]; then
    echo "000"
    echo ""
    return
  fi
  echo "$resp"
  cat /tmp/smoke-body.$$ 2>/dev/null
  rm -f /tmp/smoke-body.$$
}

# assert an expected status; body (if any) printed after the status line is returned via stdout for callers that need it
assert_status() {
  local method="$1" path="$2" expected="$3" auth_header="$4" body="${5:-}"
  local out actual resp_body
  out=$(call "$method" "$path" "$auth_header" "$body")
  actual=$(echo "$out" | head -n1)
  resp_body=$(echo "$out" | tail -n +2)
  if [[ "$actual" == "$expected" ]]; then
    report PASS "$method" "$path" "$expected" "$actual"
  else
    report FAIL "$method" "$path" "$expected" "$actual"
  fi
  echo "$resp_body"
}

# ---------------------------------------------------------------------------
# oauth — reachability check
# ---------------------------------------------------------------------------

oauth_reachable() {
  curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$API/health" 2>/dev/null | grep -q '^2'
}

# ---------------------------------------------------------------------------
# oauth — happy path (CAP-1, CAP-2, CAP-6)
# ---------------------------------------------------------------------------

run_oauth_happy_path() {
  echo "== oauth: happy path =="

  # health (no auth)
  local health_out health_code
  health_out=$(call GET /health "")
  health_code=$(echo "$health_out" | head -n1)
  [[ "$health_code" == 2* ]] && report PASS GET /health "2xx" "$health_code" || report FAIL GET /health "2xx" "$health_code"

  # 1. login (CAP-1)
  local login_out login_status login_body token
  login_out=$(curl -sS -o /tmp/smoke-login.$$ -w '%{http_code}' -X POST "$API/login" \
    -F "username=$ADMIN_USER" -F "password=$ADMIN_PASSWORD")
  login_status="$login_out"
  login_body=$(cat /tmp/smoke-login.$$ 2>/dev/null); rm -f /tmp/smoke-login.$$
  if [[ "$login_status" == "201" ]]; then
    report PASS POST /login 201 "$login_status"
  else
    report FAIL POST /login 201 "$login_status"
    echo "Login failed — aborting run (CAP-1)." >&2
    return 1
  fi
  token=$(echo "$login_body" | jq -r '.access_token // empty')
  if [[ -z "$token" ]]; then
    echo "Login succeeded but no access_token in response — aborting run." >&2
    return 1
  fi
  local AUTH="Authorization: Bearer $token"

  # 2. create user
  local user_email="smoke-user-$SUFFIX@pucrs.br"
  local user_body
  user_body=$(assert_status POST /users 201 "$AUTH" \
    "{\"username\":\"$user_email\",\"password\":\"a12345678\",\"first-name\":\"Smoke\",\"last-name\":\"Test\"}")
  local user_id
  user_id=$(echo "$user_body" | jq -r '.id // empty')

  # 3-6. read/update user
  assert_status GET /users 200 "$AUTH" >/dev/null
  if [[ -n "$user_id" ]]; then
    assert_status GET "/users/$user_id" 200 "$AUTH" >/dev/null
    assert_status PUT "/users/$user_id" 200 "$AUTH" '{"first-name":"SmokeUpdated"}' >/dev/null
    assert_status PATCH "/users/$user_id" 200 "$AUTH" '{"password":"a123456789"}' >/dev/null
  else
    echo "No user id returned from create — skipping dependent user steps." >&2
  fi

  # 7. create role
  local role_name="smoke-role-$SUFFIX"
  local role_body
  role_body=$(assert_status POST /roles 201 "$AUTH" "{\"name\":\"$role_name\"}")
  local role_id
  role_id=$(echo "$role_body" | jq -r '.id // empty')

  # 8-11. read/update role
  assert_status GET /roles 200 "$AUTH" >/dev/null
  if [[ -n "$role_id" ]]; then
    assert_status GET "/roles/$role_id" 200 "$AUTH" >/dev/null
    assert_status PUT "/roles/$role_id" 200 "$AUTH" "{\"name\":\"$role_name\"}" >/dev/null
    assert_status PATCH "/roles/$role_id" 200 "$AUTH" '{"description":"smoke"}' >/dev/null
  else
    echo "No role id returned from create — skipping dependent role steps." >&2
  fi

  # 12-13. assign/revoke
  if [[ -n "$user_id" && -n "$role_id" ]]; then
    assert_status POST "/users/$user_id/roles/$role_id" 204 "$AUTH" >/dev/null
    assert_status DELETE "/users/$user_id/roles/$role_id" 204 "$AUTH" >/dev/null
  else
    echo "Missing user_id or role_id — skipping assign/revoke." >&2
  fi

  # 14. delete role (real delete)
  if [[ -n "$role_id" ]]; then
    assert_status DELETE "/roles/$role_id" 204 "$AUTH" >/dev/null
  fi

  # 15. delete user (logical delete)
  if [[ -n "$user_id" ]]; then
    assert_status DELETE "/users/$user_id" 204 "$AUTH" >/dev/null
  fi

  export SMOKE_TOKEN="$token"
}

# ---------------------------------------------------------------------------
# oauth — negative paths (CAP-7)
# ---------------------------------------------------------------------------

run_oauth_negative_paths() {
  echo "== oauth: negative paths =="
  local token="${SMOKE_TOKEN:-}"
  local AUTH="Authorization: Bearer $token"
  local RANDOM_ID="00000000-0000-0000-0000-000000000000"

  # invalid credentials
  local bad_login
  bad_login=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/login" \
    -F "username=$ADMIN_USER" -F "password=wrong-password")
  [[ "$bad_login" == "401" ]] && report PASS POST /login 401 "$bad_login" || report FAIL POST /login 401 "$bad_login"

  # login missing fields
  local missing_login
  missing_login=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/login")
  [[ "$missing_login" == "400" ]] && report PASS POST /login 400 "$missing_login" || report FAIL POST /login 400 "$missing_login"

  # missing Authorization header
  assert_status GET /users 400 "" >/dev/null

  # malformed Authorization header
  assert_status GET /users 400 "Authorization: not-a-bearer-token" >/dev/null

  # invalid/expired token
  assert_status GET /users 401 "Authorization: Bearer invalid.token.value" >/dev/null

  if [[ -n "$token" ]]; then
    # user not found
    assert_status GET "/users/$RANDOM_ID" 404 "$AUTH" >/dev/null
    assert_status PUT "/users/$RANDOM_ID" 404 "$AUTH" '{"first-name":"x"}' >/dev/null
    assert_status PATCH "/users/$RANDOM_ID" 404 "$AUTH" '{"password":"a12345678"}' >/dev/null
    assert_status DELETE "/users/$RANDOM_ID" 404 "$AUTH" >/dev/null

    # role not found
    assert_status GET "/roles/$RANDOM_ID" 404 "$AUTH" >/dev/null
    assert_status PUT "/roles/$RANDOM_ID" 404 "$AUTH" '{"name":"x"}' >/dev/null
    assert_status PATCH "/roles/$RANDOM_ID" 404 "$AUTH" '{"description":"x"}' >/dev/null
    assert_status DELETE "/roles/$RANDOM_ID" 404 "$AUTH" >/dev/null

    # assign/revoke with missing ids
    assert_status POST "/users/$RANDOM_ID/roles/$RANDOM_ID" 404 "$AUTH" >/dev/null
    assert_status DELETE "/users/$RANDOM_ID/roles/$RANDOM_ID" 404 "$AUTH" >/dev/null

    # duplicate user
    local dup_email="smoke-dup-$SUFFIX@pucrs.br"
    local dup_body="{\"username\":\"$dup_email\",\"password\":\"a12345678\",\"first-name\":\"Dup\",\"last-name\":\"User\"}"
    local first_dup
    first_dup=$(assert_status POST /users 201 "$AUTH" "$dup_body")
    assert_status POST /users 409 "$AUTH" "$dup_body" >/dev/null
    local dup_id
    dup_id=$(echo "$first_dup" | jq -r '.id // empty')
    [[ -n "$dup_id" ]] && call DELETE "/users/$dup_id" "$AUTH" >/dev/null

    # duplicate role
    local dup_role_name="smoke-dup-role-$SUFFIX"
    local dup_role_body="{\"name\":\"$dup_role_name\"}"
    local first_role
    first_role=$(assert_status POST /roles 201 "$AUTH" "$dup_role_body")
    assert_status POST /roles 409 "$AUTH" "$dup_role_body" >/dev/null
    local dup_role_id
    dup_role_id=$(echo "$first_role" | jq -r '.id // empty')
    [[ -n "$dup_role_id" ]] && call DELETE "/roles/$dup_role_id" "$AUTH" >/dev/null
  else
    echo "No token from happy-path login — skipping authenticated negative-path checks." >&2
  fi
}

# ---------------------------------------------------------------------------
# reserved slots for other services (CAP-3, CAP-4)
# ---------------------------------------------------------------------------

run_service() {
  local name="$1"
  echo "== $name =="
  report SKIP "-" "$name" "not implemented yet" "-"
}

# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

main() {
  if oauth_reachable; then
    run_oauth_happy_path
    run_oauth_negative_paths
  else
    echo "oauth unreachable at $API — skipping its checks." >&2
    report SKIP "-" "oauth" "unreachable" "-"
  fi

  for svc in bff classes courses employees lessons professors reservations resources rooms students; do
    run_service "$svc"
  done

  local pass_count fail_count skip_count
  pass_count=$(grep -c '^PASS$' "$RESULTS_FILE")
  fail_count=$(grep -c '^FAIL$' "$RESULTS_FILE")
  skip_count=$(grep -c '^SKIP$' "$RESULTS_FILE")

  echo
  echo "==================== summary ===================="
  echo "PASS: $pass_count  FAIL: $fail_count  SKIP: $skip_count"
  echo "Log saved to: $LOG_FILE"

  [[ "$fail_count" -eq 0 ]]
}

main "$@" 2>&1 | tee "$LOG_FILE"
exit "${PIPESTATUS[0]}"
