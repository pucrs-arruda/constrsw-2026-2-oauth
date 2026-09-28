# POST /authz/validate — the B.2 permission matrix, decided by Keycloak.
suite "authz: POST /authz/validate"

RESOURCES=(classes courses lessons professors reservations resources rooms students)

# allowed_for ROLE → space-separated resources (README "Story 6.5" oracle).
allowed_for() {
  case "$1" in
    ADMIN)       echo "classes courses lessons professors reservations resources rooms students" ;;
    COORDINATOR) echo "classes courses lessons reservations" ;;
    PROFESSOR)   echo "lessons reservations" ;;
    STUDENT)     echo "" ;;
  esac
}

# The seeded coordinator/professor users only hold *realm* roles, while the
# realm's Authorization policies bind the *client* roles oauth/coordinator and
# oauth/professor — so the seeds are denied everything. To test the documented
# matrix, give fixture users the client role through the API (which also
# exercises role assignment end to end). admin and student are used as seeded.
# role_fixture_token ROLE VAR → sets VAR to an access token of a fresh user holding that client role.
role_fixture_token() {
  create_user_fixture "E2E" "$1" || return 1
  jreq POST "/users/$NEW_USER_ID/roles" "$ADMIN_TOKEN" "{\"roleName\":\"$1\"}"
  [ "$STATUS" = "204" ] || { fail "fixture: could not assign client role $1 (HTTP $STATUS)"; return 1; }
  printf -v "$2" '%s' "$(token_for "$NEW_USER_EMAIL" "$E2E_USER_PASSWORD")"
}
role_fixture_token coordinator COORDINATOR_TOKEN
role_fixture_token professor PROFESSOR_TOKEN

for who in ADMIN COORDINATOR PROFESSOR STUDENT; do
  token_var="${who}_TOKEN"; token="${!token_var}"
  allowed=" $(allowed_for "$who") "
  for res in "${RESOURCES[@]}"; do
    jreq POST /authz/validate "$token" "{\"resource\":\"$res\"}"
    if [[ "$allowed" == *" $res "* ]]; then
      assert_status 200 "$who may access $res"
    else
      assert_error 403 "OA-403" "$who is denied $res"
    fi
  done
done

jreq POST /authz/validate "$ADMIN_TOKEN" '{"resource":"classes"}'
assert_json_true '. == {}' "a permitted answer has an empty JSON body"

jreq POST /authz/validate "$ADMIN_TOKEN" '{"resource":"not-a-resource"}'
assert_error 400 "OA-400" "unknown resource name"

jreq POST /authz/validate "$ADMIN_TOKEN" '{}'
assert_error 400 "OA-400" "missing resource"

jreq POST /authz/validate "$ADMIN_TOKEN" '{"resource":""}'
assert_error 400 "OA-400" "blank resource"

jreq POST /authz/validate "$ADMIN_TOKEN" '{"resource":123}'
assert_error 400 "OA-400" "non-string resource"

api POST /authz/validate -H "$(auth_h "$ADMIN_TOKEN")"
assert_error 400 "OA-400" "no body at all"

api POST /authz/validate -H 'Content-Type: application/json' -d '{"resource":"classes"}'
assert_error 401 "*" "no token"

jreq POST /authz/validate "not.a.token" '{"resource":"classes"}'
assert_error 401 "*" "garbage token"
