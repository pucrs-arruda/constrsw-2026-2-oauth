# /users CRUD lifecycle (administrator).
suite "users"

U_EMAIL="e2e-$(uniq_suffix)@pucrs.br"
U_PASS="$E2E_USER_PASSWORD"

# ---- create ----
jreq POST /users "$ADMIN_TOKEN" \
  "$(jq -nc --arg u "$U_EMAIL" --arg p "$U_PASS" '{username:$u,password:$p,"first-name":"Ana","last-name":"Silva"}')"
assert_status 201 "POST /users creates a user"
U_ID=$(jq -r '.id // empty' <<<"$BODY")
[ -n "$U_ID" ] && CREATED_USER_IDS+=("$U_ID")
assert_json '.username' "$U_EMAIL" "created user echoes username"
assert_json '.["first-name"]' 'Ana' "created user echoes first-name"
assert_json '.["last-name"]' 'Silva' "created user echoes last-name"
assert_json '.enabled' 'true' "created user is enabled"
assert_json_true 'has("password")|not' "response never contains the password"

if [ -z "$U_ID" ]; then
  fail "users: creation failed, skipping the dependent checks"
else
  # ---- create validation ----
  jreq POST /users "$ADMIN_TOKEN" "$(jq -nc --arg u "$U_EMAIL" --arg p "$U_PASS" '{username:$u,password:$p}')"
  assert_error 409 "*" "duplicate username → conflict"

  jreq POST /users "$ADMIN_TOKEN" "$(jq -nc --arg p "$U_PASS" '{username:"not-an-email",password:$p}')"
  assert_error 400 "OA-400" "non-email username"

  jreq POST /users "$ADMIN_TOKEN" '{"username":"someone@pucrs.br"}'
  assert_error 400 "OA-400" "missing password"

  jreq POST /users "$ADMIN_TOKEN" "$(jq -nc --arg p "$U_PASS" '{password:$p}')"
  assert_error 400 "OA-400" "missing username"

  jreq POST /users "$ADMIN_TOKEN" "$(jq -nc --arg u "e2e-$(uniq_suffix)@pucrs.br" --arg p "$U_PASS" '{username:$u,password:$p,"first-name":42}')"
  assert_error 400 "OA-400" "non-string first-name"

  # ---- read ----
  jreq GET "/users/$U_ID" "$ADMIN_TOKEN"
  assert_status 200 "GET /users/:id"
  assert_json '.id' "$U_ID" "GET /users/:id returns the same id"
  assert_json '.username' "$U_EMAIL" "GET /users/:id returns the username"

  jreq GET /users "$ADMIN_TOKEN"
  assert_status 200 "GET /users (default: enabled only)"
  assert_json_true "type==\"array\" and all(.[]; .enabled==true)" "default list contains only enabled users"
  assert_json_true "any(.[]; .id==\"$U_ID\")" "default list includes the new user"
  assert_json_true 'all(.[]; has("id") and has("username") and has("enabled"))' "list items follow the brief's shape"

  jreq GET "/users?enabled=true" "$ADMIN_TOKEN"
  assert_status 200 "GET /users?enabled=true"

  jreq GET "/users?enabled=false" "$ADMIN_TOKEN"
  assert_status 200 "GET /users?enabled=false"
  assert_json_true 'type=="array" and all(.[]; .enabled==false)' "enabled=false list has only disabled users"
  assert_json_true "any(.[]; .id==\"$U_ID\")|not" "enabled=false list excludes the active user"

  jreq GET "/users?enabled=maybe" "$ADMIN_TOKEN"
  assert_error 400 "OA-400" "invalid enabled filter"

  jreq GET "/users/not-a-uuid" "$ADMIN_TOKEN"
  assert_error 400 "OA-400" "GET /users/:id with a malformed id"

  jreq GET "/users/$(uuid_v4)" "$ADMIN_TOKEN"
  assert_error 404 "*" "GET /users/:id for an unknown id"

  # ---- update (PUT) ----
  jreq PUT "/users/$U_ID" "$ADMIN_TOKEN" '{"first-name":"Beatriz","last-name":"Souza"}'
  assert_status 200 "PUT /users/:id updates names"
  jreq GET "/users/$U_ID" "$ADMIN_TOKEN"
  assert_json '.["first-name"]' 'Beatriz' "first-name was persisted"
  assert_json '.["last-name"]' 'Souza' "last-name was persisted"
  assert_json '.username' "$U_EMAIL" "username is unchanged"

  jreq PUT "/users/$U_ID" "$ADMIN_TOKEN" '{"first-name":"Carla"}'
  assert_status 200 "PUT with a single field"
  jreq GET "/users/$U_ID" "$ADMIN_TOKEN"
  assert_json '.["first-name"]' 'Carla' "partial update changed first-name"
  assert_json '.["last-name"]' 'Souza' "partial update kept last-name"

  jreq PUT "/users/$U_ID" "$ADMIN_TOKEN" '{"username":"other@pucrs.br"}'
  assert_error 400 "OA-400" "PUT cannot change username"

  jreq PUT "/users/$U_ID" "$ADMIN_TOKEN" '{}'
  assert_error 400 "OA-400" "PUT with an empty body"

  jreq PUT "/users/$U_ID" "$ADMIN_TOKEN" '{"enabled":"yes"}'
  assert_error 400 "OA-400" "PUT with a non-boolean enabled"

  jreq PUT "/users/not-a-uuid" "$ADMIN_TOKEN" '{"first-name":"X"}'
  assert_error 400 "OA-400" "PUT with a malformed id"

  jreq PUT "/users/$(uuid_v4)" "$ADMIN_TOKEN" '{"first-name":"X"}'
  assert_error 404 "*" "PUT for an unknown id"

  # ---- password change (PATCH) ----
  NEW_PASS="E2e-Chang3d!y2"
  jreq PATCH "/users/$U_ID" "$ADMIN_TOKEN" "$(jq -nc --arg p "$NEW_PASS" '{password:$p}')"
  assert_status 200 "PATCH /users/:id changes the password"
  login_form "$U_EMAIL" "$NEW_PASS"
  assert_status 200 "login works with the new password"
  login_form "$U_EMAIL" "$U_PASS"
  assert_error 401 "invalid_grant" "old password no longer works"

  jreq PATCH "/users/$U_ID" "$ADMIN_TOKEN" '{}'
  assert_error 400 "OA-400" "PATCH without a password"

  jreq PATCH "/users/not-a-uuid" "$ADMIN_TOKEN" '{"password":"x"}'
  assert_error 400 "OA-400" "PATCH with a malformed id"

  jreq PATCH "/users/$(uuid_v4)" "$ADMIN_TOKEN" "$(jq -nc --arg p "$NEW_PASS" '{password:$p}')"
  assert_error 404 "*" "PATCH for an unknown id"

  # ---- logical delete ----
  jreq DELETE "/users/$U_ID" "$ADMIN_TOKEN"
  assert_status 204 "DELETE /users/:id (logical)"
  assert_empty_body "DELETE returns no body"

  jreq GET "/users/$U_ID" "$ADMIN_TOKEN"
  assert_status 200 "disabled user is still retrievable"
  assert_json '.enabled' 'false' "DELETE only disabled the account"

  jreq GET /users "$ADMIN_TOKEN"
  assert_json_true "any(.[]; .id==\"$U_ID\")|not" "disabled user left the default list"

  login_form "$U_EMAIL" "$NEW_PASS"
  assert_error 401 "*" "disabled user cannot log in"

  jreq DELETE "/users/$U_ID" "$ADMIN_TOKEN"
  assert_status 204 "DELETE is idempotent"

  jreq PUT "/users/$U_ID" "$ADMIN_TOKEN" '{"enabled":true}'
  assert_status 200 "PUT enabled=true re-enables the user"
  login_form "$U_EMAIL" "$NEW_PASS"
  assert_status 200 "re-enabled user can log in again"

  jreq DELETE "/users/not-a-uuid" "$ADMIN_TOKEN"
  assert_error 400 "OA-400" "DELETE with a malformed id"

  jreq DELETE "/users/$(uuid_v4)" "$ADMIN_TOKEN"
  assert_error 404 "*" "DELETE for an unknown id"
fi
