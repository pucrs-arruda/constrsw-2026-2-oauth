# POST /login and POST /refresh.
suite "auth: POST /login"

login_form "$STUDENT_USER" "$E2E_PASSWORD"
assert_status 200 "login (urlencoded) with valid credentials"
assert_json '.token_type' 'Bearer' "login token_type is Bearer"
assert_json_true '(.access_token|type=="string") and (.access_token|length>0)' "login returns access_token"
assert_json_true '(.refresh_token|type=="string") and (.refresh_token|length>0)' "login returns refresh_token"
assert_json_true '(.expires_in|type=="number") and .expires_in>0' "login returns expires_in"
assert_json_true '(.referesh_expires_in|type=="number") and .referesh_expires_in>0' "login returns referesh_expires_in (brief's spelling)"
assert_json_true 'has("refresh_expires_in")|not' "login does not leak Keycloak's refresh_expires_in"
LOGIN_ACCESS=$(jq -r .access_token <<<"$BODY")
LOGIN_REFRESH=$(jq -r .refresh_token <<<"$BODY")

claims=$(jwt_payload "$LOGIN_ACCESS")
if [ "$(jq -r '.preferred_username' <<<"$claims")" = "$STUDENT_USER" ]; then
  pass "access_token belongs to the logged-in user"
else
  fail "access_token preferred_username mismatch" "claims: ${claims:0:200}"
fi
if jq -e '.scope | split(" ") | index("openid")' <<<"$claims" >/dev/null 2>&1; then
  pass "access_token carries the openid scope"
else
  fail "access_token is missing the openid scope"
fi

api POST /login -F "username=$STUDENT_USER" -F "password=$E2E_PASSWORD"
assert_status 200 "login (multipart/form-data)"

api POST /login --data-urlencode "username=$STUDENT_USER" --data-urlencode "password=$E2E_PASSWORD" \
  --data-urlencode "client_id=evil" --data-urlencode "grant_type=client_credentials"
assert_status 200 "login ignores caller-supplied client_id / grant_type"

api POST /login --data-urlencode "username=$STUDENT_USER" --data-urlencode "password=wrong-password"
assert_error 401 "invalid_grant" "login with a wrong password"

api POST /login --data-urlencode "username=nobody-$(uniq_suffix)@pucrs.br" --data-urlencode "password=x"
assert_error 401 "invalid_grant" "login with an unknown user"

api POST /login --data-urlencode "password=$E2E_PASSWORD"
assert_error 400 "OA-400" "login without username"

api POST /login --data-urlencode "username=$STUDENT_USER"
assert_error 400 "OA-400" "login without password"

api POST /login --data-urlencode "username=   " --data-urlencode "password=$E2E_PASSWORD"
assert_error 400 "OA-400" "login with a blank username"

api POST /login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$STUDENT_USER\",\"password\":\"$E2E_PASSWORD\"}"
assert_error 400 "OA-400" "login rejects application/json"

api POST /login
assert_error 400 "OA-400" "login with no body"

suite "auth: POST /refresh"

refresh_form "$LOGIN_REFRESH"
assert_status 200 "refresh with a valid refresh_token"
assert_json '.token_type' 'Bearer' "refresh token_type is Bearer"
assert_json_true '(.access_token|length)>0 and (.refresh_token|length)>0' "refresh returns a new token pair"
assert_json_true '.referesh_expires_in>0' "refresh returns referesh_expires_in"
NEW_ACCESS=$(jq -r .access_token <<<"$BODY")
NEW_REFRESH=$(jq -r .refresh_token <<<"$BODY")

jreq GET /users "$NEW_ACCESS"
assert_status 403 "refreshed access_token is accepted by the API (student → 403, not 401)"

api POST /refresh -F "refresh_token=$NEW_REFRESH"
assert_status 200 "refresh (multipart/form-data)"

refresh_form "not-a-real-refresh-token"
assert_error 401 "invalid_grant" "refresh with a garbage token"

api POST /refresh
assert_error 400 "OA-400" "refresh without refresh_token"

api POST /refresh --data-urlencode "refresh_token=  "
assert_error 400 "OA-400" "refresh with a blank refresh_token"

api POST /refresh -H 'Content-Type: application/json' -d '{"refresh_token":"x"}'
assert_error 400 "OA-400" "refresh rejects application/json"
