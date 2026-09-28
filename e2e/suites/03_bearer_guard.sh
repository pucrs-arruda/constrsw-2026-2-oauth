# Bearer guard + administrator guard across every protected route.
suite "guards: authentication (401) and administrator role (403)"

UUID_X="$(uuid_v4)"
# method path — every protected route in the service.
PROTECTED=(
  "GET /users" "POST /users" "GET /users/$UUID_X" "PUT /users/$UUID_X"
  "PATCH /users/$UUID_X" "DELETE /users/$UUID_X"
  "GET /roles" "POST /roles" "GET /roles/$UUID_X" "PUT /roles/$UUID_X"
  "PATCH /roles/$UUID_X" "DELETE /roles/$UUID_X"
  "POST /users/$UUID_X/roles" "DELETE /users/$UUID_X/roles/$UUID_X"
  "POST /authz/validate"
)

for route in "${PROTECTED[@]}"; do
  method="${route%% *}"; path="${route#* }"
  api "$method" "$path"
  assert_error 401 "*" "$route without Authorization"
done

api GET /users -H 'Authorization: Basic dXNlcjpwYXNz'
assert_error 401 "*" "non-Bearer scheme is rejected"

api GET /users -H 'Authorization: Bearer'
assert_error 401 "*" "Bearer with no token is rejected"

api GET /users -H 'Authorization: Bearer not.a.jwt'
assert_error 401 "*" "garbage Bearer token is rejected"

# Non-administrators are authenticated but must be refused on admin routes.
for who in STUDENT PROFESSOR COORDINATOR; do
  token_var="${who}_TOKEN"; token="${!token_var}"
  for route in "GET /users" "GET /roles" "POST /roles" "POST /users/$UUID_X/roles"; do
    method="${route%% *}"; path="${route#* }"
    jreq "$method" "$path" "$token" '{}'
    assert_error 403 "*" "$who → $route"
  done
done
