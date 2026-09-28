# /roles CRUD and /users/:id/roles assignment (administrator).
suite "roles"

R_NAME="e2e-role-$(uniq_suffix)"

jreq POST /roles "$ADMIN_TOKEN" "$(jq -nc --arg n "$R_NAME" '{name:$n,description:"created by e2e",attributes:{team:["e2e"]}}')"
assert_status 201 "POST /roles creates a client role"
R_ID=$(jq -r '.id // empty' <<<"$BODY")
assert_json '.name' "$R_NAME" "created role echoes name"
assert_json '.description' 'created by e2e' "created role echoes description"
assert_json '.clientRole' 'true' "created role is a client role"

if [ -z "$R_ID" ]; then
  fail "roles: creation failed, skipping the dependent checks"
else
  # ---- create validation ----
  jreq POST /roles "$ADMIN_TOKEN" "$(jq -nc --arg n "$R_NAME" '{name:$n}')"
  assert_error 409 "*" "duplicate role name → conflict"

  jreq POST /roles "$ADMIN_TOKEN" '{"description":"no name"}'
  assert_error 400 "OA-400" "missing role name"

  jreq POST /roles "$ADMIN_TOKEN" '{"name":"   "}'
  assert_error 400 "OA-400" "blank role name"

  jreq POST /roles "$ADMIN_TOKEN" "{\"name\":\"e2e-x-$(uniq_suffix)\",\"bogus\":1}"
  assert_error 400 "OA-400" "unsupported field"

  jreq POST /roles "$ADMIN_TOKEN" "{\"name\":\"e2e-x-$(uniq_suffix)\",\"attributes\":[1]}"
  assert_error 400 "OA-400" "attributes must be an object"

  jreq POST /roles "$ADMIN_TOKEN" "{\"name\":\"e2e-x-$(uniq_suffix)\",\"description\":5}"
  assert_error 400 "OA-400" "description must be a string"

  # ---- read ----
  jreq GET /roles "$ADMIN_TOKEN"
  assert_status 200 "GET /roles"
  assert_json_true 'type=="array"' "list is an array"
  assert_json_true "any(.[]; .id==\"$R_ID\")" "list includes the new role"
  assert_json_true '[.[].name] | contains(["administrator","coordinator","professor","student"])' "list includes the four B.2 roles"

  jreq GET "/roles/$R_ID" "$ADMIN_TOKEN"
  assert_status 200 "GET /roles/:id"
  assert_json '.id' "$R_ID" "GET returns the same id"
  assert_json '.name' "$R_NAME" "GET returns the name"

  jreq GET "/roles/not-a-uuid" "$ADMIN_TOKEN"
  assert_error 400 "OA-400" "GET with a malformed id"

  jreq GET "/roles/$(uuid_v4)" "$ADMIN_TOKEN"
  assert_error 404 "*" "GET for an unknown id"

  # ---- replace (PUT) ----
  jreq PUT "/roles/$R_ID" "$ADMIN_TOKEN" "$(jq -nc --arg n "$R_NAME" '{name:$n,description:"replaced"}')"
  assert_status 200 "PUT /roles/:id"
  jreq GET "/roles/$R_ID" "$ADMIN_TOKEN"
  assert_json '.description' 'replaced' "PUT persisted the description"

  jreq PUT "/roles/$R_ID" "$ADMIN_TOKEN" '{"description":"no name"}'
  assert_error 400 "OA-400" "PUT requires a name"

  jreq PUT "/roles/$(uuid_v4)" "$ADMIN_TOKEN" "$(jq -nc --arg n "$R_NAME" '{name:$n}')"
  assert_error 404 "*" "PUT for an unknown id"

  jreq PUT "/roles/not-a-uuid" "$ADMIN_TOKEN" "$(jq -nc --arg n "$R_NAME" '{name:$n}')"
  assert_error 400 "OA-400" "PUT with a malformed id"

  # ---- partial update (PATCH) ----
  jreq PATCH "/roles/$R_ID" "$ADMIN_TOKEN" '{"description":"patched"}'
  assert_status 200 "PATCH /roles/:id"
  jreq GET "/roles/$R_ID" "$ADMIN_TOKEN"
  assert_json '.description' 'patched' "PATCH persisted the description"
  assert_json '.name' "$R_NAME" "PATCH kept the name"

  jreq PATCH "/roles/$R_ID" "$ADMIN_TOKEN" '{}'
  assert_error 400 "OA-400" "PATCH with an empty body"

  jreq PATCH "/roles/$R_ID" "$ADMIN_TOKEN" '{"bogus":true}'
  assert_error 400 "OA-400" "PATCH with an unsupported field"

  jreq PATCH "/roles/$(uuid_v4)" "$ADMIN_TOKEN" '{"description":"x"}'
  assert_error 404 "*" "PATCH for an unknown id"

  # ---- assignment ----
  if create_user_fixture; then
    A_USER="$NEW_USER_ID"; A_EMAIL="$NEW_USER_EMAIL"

    jreq POST "/users/$A_USER/roles" "$ADMIN_TOKEN" "$(jq -nc --arg n "$R_NAME" '{roleName:$n}')"
    assert_status 204 "POST /users/:id/roles by roleName"
    assert_empty_body "assign returns no body"

    tok=$(token_for "$A_EMAIL" "$E2E_USER_PASSWORD")
    if jq -e --arg n "$R_NAME" '.resource_access.oauth.roles // [] | index($n)' <<<"$(jwt_payload "$tok")" >/dev/null 2>&1; then
      pass "assigned role appears in the user's next access token"
    else
      fail "assigned role is missing from resource_access.oauth.roles"
    fi

    jreq POST "/users/$A_USER/roles" "$ADMIN_TOKEN" "$(jq -nc --arg n "$R_NAME" '{roleName:$n}')"
    assert_status 204 "assigning the same role twice is idempotent"

    jreq DELETE "/users/$A_USER/roles/$R_ID" "$ADMIN_TOKEN"
    assert_status 204 "DELETE /users/:id/roles/:roleId"
    tok=$(token_for "$A_EMAIL" "$E2E_USER_PASSWORD")
    if jq -e --arg n "$R_NAME" '.resource_access.oauth.roles // [] | index($n) | not' <<<"$(jwt_payload "$tok")" >/dev/null 2>&1; then
      pass "unassigned role is gone from the user's next access token"
    else
      fail "unassigned role is still present in the access token"
    fi

    jreq DELETE "/users/$A_USER/roles/$R_ID" "$ADMIN_TOKEN"
    assert_status 204 "unassigning a role the user lacks is a no-op"

    jreq POST "/users/$A_USER/roles" "$ADMIN_TOKEN" "$(jq -nc --arg i "$R_ID" '{roleId:$i}')"
    assert_status 204 "POST /users/:id/roles by roleId"
    jreq DELETE "/users/$A_USER/roles/$R_ID" "$ADMIN_TOKEN"
    assert_status 204 "unassign after roleId assignment"

    jreq POST "/users/$A_USER/roles" "$ADMIN_TOKEN" '{}'
    assert_error 400 "OA-400" "assign without roleId/roleName"

    jreq POST "/users/$A_USER/roles" "$ADMIN_TOKEN" '{"roleId":"not-a-uuid"}'
    assert_error 400 "OA-400" "assign with a malformed roleId"

    jreq POST "/users/$A_USER/roles" "$ADMIN_TOKEN" '{"roleName":"e2e-no-such-role"}'
    assert_error 404 "*" "assign an unknown role name"

    jreq POST "/users/$A_USER/roles" "$ADMIN_TOKEN" "$(jq -nc --arg i "$(uuid_v4)" '{roleId:$i}')"
    assert_error 404 "*" "assign an unknown roleId"

    jreq POST "/users/not-a-uuid/roles" "$ADMIN_TOKEN" "$(jq -nc --arg n "$R_NAME" '{roleName:$n}')"
    assert_error 400 "OA-400" "assign to a malformed user id"

    jreq POST "/users/$(uuid_v4)/roles" "$ADMIN_TOKEN" "$(jq -nc --arg n "$R_NAME" '{roleName:$n}')"
    assert_error 404 "*" "assign to an unknown user"

    jreq DELETE "/users/$(uuid_v4)/roles/$R_ID" "$ADMIN_TOKEN"
    assert_error 404 "*" "unassign from an unknown user"

    jreq DELETE "/users/$A_USER/roles/not-a-uuid" "$ADMIN_TOKEN"
    assert_error 400 "OA-400" "unassign with a malformed roleId"
  fi

  # ---- logical delete ----
  jreq DELETE "/roles/$R_ID" "$ADMIN_TOKEN"
  assert_status 204 "DELETE /roles/:id (logical)"
  assert_empty_body "DELETE returns no body"

  jreq GET "/roles/$R_ID" "$ADMIN_TOKEN"
  assert_status 200 "logically deleted role is still retrievable"
  assert_json '.attributes.inactive[0]' 'true' "DELETE marked the role inactive"
  assert_json '.attributes.team[0]' 'e2e' "DELETE preserved existing attributes"

  jreq DELETE "/roles/not-a-uuid" "$ADMIN_TOKEN"
  assert_error 400 "OA-400" "DELETE with a malformed id"

  jreq DELETE "/roles/$(uuid_v4)" "$ADMIN_TOKEN"
  assert_error 404 "*" "DELETE for an unknown id"
fi
