# oauth
Serviço oauth - constrsw 2026/2

------ Grupo 07 ------
Membros: Gabriel Hoppe, Juliano Chies, Leonardo Gemin, William Klein

---

## Stack

NestJS 11 + TypeScript, npm. The service is a REST gateway in front of Keycloak
(realm `constrsw`, client `oauth`).

## Architecture

**Layered architecture** inside an **identity API Gateway**, running as one
service of a **microservices** system.

Three levels, from the outside in.

### 1. System level — microservices

The `base` repository composes twelve independent services as git submodules,
each with its own repository, lifecycle and container, talking over HTTP/REST.
`oauth` is one of them.

```mermaid
flowchart LR
    Client([Client / Postman])
    subgraph compose["docker compose — network constrsw"]
        OAUTH["oauth<br/>identity gateway<br/>:3001"]
        KC[("Keycloak 26<br/>realm constrsw<br/>:8080")]
        PROM["Prometheus<br/>scrapes :9464"]
        OTHER["classes · courses · lessons<br/>professors · rooms · students<br/>reservations · resources"]
    end

    Client -->|"HTTP :8181"| OAUTH
    OAUTH -->|"OIDC + Admin REST"| KC
    PROM -.->|"pull /metrics"| OAUTH
    OTHER -.->|"validate tokens"| OAUTH
```

### 2. Service level — API Gateway

`oauth` owns **no database and no business domain**. It receives, validates and
translates calls to Keycloak, adapting Keycloak's contract to the one the T1
brief requires: form-data login, HTTP `200`, the `referesh_expires_in` spelling,
and the uniform OA error envelope.

Keycloak stays the single source of truth for identity and authorization.

### 3. Internal level — layers

Dependencies flow in **one direction only**: presentation → application →
infrastructure. A controller never opens an HTTP connection; an infrastructure
client never knows what an HTTP request looks like coming in.

```mermaid
flowchart TD
    subgraph P["Presentation — HTTP in"]
        C1["login.controller<br/>refresh.controller"]
        C2["users.controller"]
        C3["roles.controller"]
        C4["authz.controller"]
        C5["health.controller"]
    end

    subgraph A["Application — rules"]
        S1["KeycloakUsersService"]
        S2["KeycloakAdminService<br/>(client roles)"]
    end

    subgraph I["Infrastructure — HTTP out"]
        I1["KeycloakTokenClient"]
        I2["KeycloakAdminClient"]
        I3["KeycloakAuthorizationClient"]
        I4["KeycloakTokenVerifierService"]
    end

    subgraph X["Cross-cutting"]
        G["common/<br/>BearerAuthGuard<br/>AdministratorRoleGuard"]
        E["errors/<br/>OA envelope + mapper"]
        CF["config/<br/>settings + URL builder"]
        T["telemetry/<br/>OpenTelemetry"]
    end

    KC[("Keycloak")]

    C1 --> I1
    C2 --> S1
    C3 --> S2
    C4 --> I3
    S1 --> I2
    S2 --> I2
    I1 --> KC
    I2 --> KC
    I3 --> KC
    I4 --> KC

    X -.->|"used by every layer"| P
```

| Layer | Files | Responsibility |
| --- | --- | --- |
| **Presentation** | `*.controller.ts` | Routes, input validation, status codes |
| **Application** | `keycloak-users.service.ts`, `keycloak-admin.service.ts` | Rules: logical delete, representation merge, enabled filter |
| **Infrastructure** | `keycloak-*.client.ts`, `keycloak-token-verifier.service.ts` | Talking to Keycloak: admin token, timeouts, upstream failures |
| **Cross-cutting** | `common/`, `errors/`, `config/`, `telemetry/` | Guards, error envelope, settings, metrics |

Only four files in the whole service call `fetch`, and all four are in the
infrastructure layer — that is what keeps the boundary real rather than
aspirational.

### Request flow

`GET /users` end to end:

```mermaid
sequenceDiagram
    participant C as Client
    participant G as BearerAuthGuard
    participant R as AdministratorRoleGuard
    participant Ctl as UsersController
    participant S as KeycloakUsersService
    participant Cli as KeycloakAdminClient
    participant KC as Keycloak

    C->>G: GET /users + Bearer token
    G->>KC: UserInfo (is this token valid?)
    KC-->>G: 200 — caller identity
    Note over G: decode JWT for client roles<br/>(UserInfo omits them)
    G->>R: request.user
    Note over R: administrator role present?
    R->>Ctl: allowed
    Ctl->>S: listUsers(enabled = true)
    S->>Cli: request('/users?enabled=true')
    Note over Cli: cached admin token<br/>(service credentials, never the caller's)
    Cli->>KC: Admin REST
    KC-->>Cli: user list
    Cli-->>S: response
    S-->>Ctl: KeycloakUser[]
    Ctl-->>C: 200 — hyphenated representation
```

A failure anywhere in that chain is thrown as an `OaException` and formatted by
the global `OaExceptionFilter`, so every route answers the same envelope.

### Patterns used

**Dependency Injection** (NestJS container) throughout. This is what makes the
test suite possible: integration tests swap `KeycloakTokenVerifierService` for
a stub without touching production code.

**Guard chain** — `BearerAuthGuard` answers *who is calling* (`401`), and only
then `AdministratorRoleGuard` answers *may they* (`403`). Two separate
questions, two separate guards.

**Shared client** — `KeycloakAdminClient` is injected by both the users and the
roles modules rather than duplicated, so admin-token caching, timeouts and
failure mapping exist in exactly one place.

### The central architectural decision

**Authorization is delegated to Keycloak Authorization Services; it is never
decided locally.** `POST /authz/validate` asks Keycloak through the UMA-ticket
grant and relays the answer. There is no role-to-resource table in the code,
and a test pins that absence.

The reason is concrete: the permission matrix in the T1 brief **disagrees** with
what `constrsw.json` actually configures — the realm's bindings are
hierarchical, the brief's are disjoint. A table copied into code would have
shipped that contradiction. Keycloak is the single source of truth.

### What this architecture is not

Worth stating plainly, because the names are close:

- **Not Hexagonal / Ports & Adapters.** There are no abstract ports between
  layers. `KeycloakUsersService` depends on the concrete `KeycloakAdminClient`,
  not on an interface.
- **Not Clean Architecture.** There are no framework-independent domain
  entities. `user.types.ts` holds DTOs shaped by Keycloak's own contract, and
  the services import from `@nestjs/common`.

Layered is the honest description of what is here.

## Environment contract

This service reads its configuration **only from the process environment**,
which the professor's root `docker-compose.yml` injects into the container.

`ConfigModule` is loaded with `ignoreEnvFile: true` on purpose — this service
must never grow its own `.env`. The root `.env` in the `base` repo is
professor-owned and is the single source of these values.

> Never commit secret **values** — not here, not in code, not in tests.
> Only the variable **names** below are documented.

### Required — the service refuses to boot without them

| Variable | Purpose |
| --- | --- |
| `KEYCLOAK_SERVER_URL` | Keycloak base URL, e.g. the internal compose hostname |
| `KEYCLOAK_CLIENT_SECRET` | Secret of the confidential client `oauth` |

A missing or blank value throws `MissingEnvironmentVariableError` during
bootstrap, so a misconfigured container dies immediately instead of failing
later on the first Keycloak call.

### Optional — defaults applied when unset

| Variable | Default |
| --- | --- |
| `KEYCLOAK_REALM` | `constrsw` |
| `KEYCLOAK_CLIENT_ID` | `oauth` |
| `OAUTH_INTERNAL_API_PORT` | `3001` |

### Bound when present

`KEYCLOAK_ADMIN` and `KEYCLOAK_ADMIN_PASSWORD` — used by the Keycloak Admin API
in the user and role stories (Epics 4-5). Not required to boot.

### Not read by this service

`KEYCLOAK_GRANT_TYPE`, `KEYCLOAK_TOKEN_ALGORITHM`, `KEYCLOAK_INTERNAL_*` and
`KEYCLOAK_EXTERNAL_*` exist in the root `.env` but are not injected into this
container. Grant types are fixed by the contract (`password` for login,
`refresh_token` for refresh), not driven by environment.

Do **not** introduce `KEYCLOAK_URL`, `KEYCLOAK_BASE_URL` or `KEYCLOAK_JWKS_URL`.
The professor's names are the ones above.

## URL construction

Keycloak 26 dropped the `/auth` path segment, so the builder **never inserts
one**:

```
{KEYCLOAK_SERVER_URL}/realms/{realm}/protocol/openid-connect/token
{KEYCLOAK_SERVER_URL}/realms/{realm}/protocol/openid-connect/userinfo
{KEYCLOAK_SERVER_URL}/admin/realms/{realm}
```

If `KEYCLOAK_SERVER_URL` already ends in `/auth` (an older Keycloak), that
prefix is part of the base and is preserved as-is. Trailing slashes are stripped
so joining never produces `//realms`.

Get these URLs from `KeycloakSettingsService` — `tokenUrl`, `userInfoUrl`,
`adminRealmUrl`, plus `realmPath()` / `adminPath()` for anything else. **Do not
read `process.env` in feature modules**; that is what makes the rules above
enforceable in one place.

```ts
constructor(private readonly keycloak: KeycloakSettingsService) {}

// this.keycloak.tokenUrl
// this.keycloak.adminPath('users')
```

## `POST /login`

Exchanges username/password for Keycloak tokens (Story 2.1, CAP-1). No
`Authorization` header required.

- **Body**: `multipart/form-data` **or** `application/x-www-form-urlencoded` —
  `username`, `password`. Any other content-type is rejected. The T1 brief's
  form also lists `client_id` and `grant_type`; if the caller sends them they
  are **accepted and silently ignored** — the client credentials sent to
  Keycloak always come from `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET`,
  never from the request.
- **Scope**: the service always adds `scope=openid` on the Keycloak token
  call (not taken from the caller). Access tokens without `openid` are
  rejected by UserInfo, so `/users`, `/roles` and `/authz/validate` would
  all answer `OA-401` even though `/login` succeeded.
- **Success**: `200` (not `201` — nothing is created) with JSON:

  ```json
  {
    "token_type": "Bearer",
    "access_token": "...",
    "expires_in": 300,
    "refresh_token": "...",
    "referesh_expires_in": 1800
  }
  ```

  `referesh_expires_in` keeps the brief's spelling on purpose — it is mapped
  from Keycloak's `refresh_expires_in`.
- **Errors**: `400` when `username`/`password` is missing/blank or the
  content-type is unsupported (local `OA-400`, checked before any Keycloak
  call); `401` when Keycloak rejects the credentials (`error_code` relays
  Keycloak's `error`, typically `invalid_grant`). Both use the OA envelope
  above.

## `POST /refresh`

Trades a valid `refresh_token` for a new token pair without re-entering the
password (Story 2.2, CAP-7). No `Authorization` header required — the refresh
token itself is the credential.

- **Body**: `multipart/form-data` **or** `application/x-www-form-urlencoded` —
  `refresh_token` only.
- **Success**: `200` with the same field set as `/login` (`token_type`,
  `access_token`, `expires_in`, `refresh_token`, `referesh_expires_in` when
  Keycloak returns a refresh expiry).
- **Errors**: `400` when `refresh_token` is missing/blank or the content-type
  is unsupported; `401` when the refresh token is invalid or expired
  (`error_code` relays Keycloak's `error`, typically `invalid_grant`).

## Authentication on protected routes

Routes under `/users` and `/roles` require the caller's Keycloak access token:

```
Authorization: Bearer <access_token>
```

`BearerAuthGuard` runs before the handler. Apply it with `@UseGuards`; the
resolved caller is attached to the request:

```ts
@UseGuards(BearerAuthGuard)
@Controller('users')
export class UsersController {
  @Get()
  list(@Req() req: AuthenticatedRequest) {
    req.user; // { sub, email, preferredUsername, name, raw }
  }
}
```

`CommonModule` is `@Global()`, so importing it once in `AppModule` is enough.

### How the guard verifies a token

It calls Keycloak's UserInfo endpoint rather than validating the JWT signature
locally. Keycloak therefore stays the authority on whether a token is still
usable, so a logout or a disabled account takes effect immediately instead of
lingering until the token expires. The cost is one upstream call per request,
which this service accepts.

No JWKS variable is introduced — see the environment contract above.

### 401 vs 403 vs 503

| Status | Meaning | Decided by |
| --- | --- | --- |
| `401` | No token, malformed header, or Keycloak does not accept the token | The guard |
| `403` | The caller is known but not allowed to perform the operation | Keycloak, or the required client role |
| `503` | Keycloak could not be reached | The guard |

`503` on an unreachable Keycloak is a rule the whole API follows — the guard,
`/login`, `/refresh` and `/authz/validate` all answer it. A caller whose
credentials or token are perfectly valid must never be told to sign in again
because a dependency of ours is down.

**The guard never decides permissions.** Any caller with a token Keycloak
accepts passes it, even with no roles at all. Authorization is answered by
Keycloak when the route calls the Admin API: a caller lacking the required
`realm-management` rights gets a `403` from Keycloak, which the route relays.

This is deliberate. Holding a local role-to-operation table here would
duplicate the realm's own configuration and drift from it — the B.2 permission
matrix in the brief already differs from what `constrsw.json` actually
configures. Keycloak is the single source of truth.

`503` exists so that an outage is not reported as an authentication failure: a
caller with a perfectly valid token must not be told to sign in again because a
dependency is down.

### Error format

The guard throws the standard Nest exceptions. Once the uniform error envelope
(Story 3.1) registers its exception filter, those responses are formatted into
`{ error_code, error_description, error_source, error_stack }` with no change
required here.

## Error format (uniform envelope)

Every error response — from every module — uses the same JSON body:

```json
{
  "error_code": "OA-400",
  "error_description": "username is required",
  "error_source": "OAuthAPI",
  "error_stack": [{ "field": "username", "reason": "missing" }]
}
```

| Field | Rule |
| --- | --- |
| `error_code` | Relays Keycloak's `error` field when the failure came from a Keycloak call; otherwise a local `OA-xxx` code (below) |
| `error_description` | Group-provided human message |
| `error_source` | Always `OAuthAPI` |
| `error_stack` | **Array of objects**, chaining causes down to the root cause — never a string or an array of strings |

### Local `OA-xxx` code family

Used when the failure is local (bad structure, missing/invalid auth) and Keycloak was not called or returned no code. Epics 4-6 **must reuse this table** — do not invent a new prefix (`ERR_USER_*`, etc.):

| Code | HTTP status | Meaning |
| --- | --- | --- |
| `OA-400` | 400 | Bad request structure / validation failure |
| `OA-401` | 401 | Missing or invalid authentication (local, no Keycloak code to relay) |
| `OA-403` | 403 | Forbidden (local) |
| `OA-404` | 404 | Entity not found |
| `OA-409` | 409 | Conflict (e.g. duplicate username) |
| `OA-500` | 500 | Unexpected internal error |

### How it's wired

- `src/errors/oa-error.mapper.ts` — `OaException` + `OaErrorMapper` factory (`badRequest`, `unauthorized`, `forbidden`, `notFound`, `conflict`, `fromKeycloak`). Route/service code throws these instead of building JSON by hand.
- `src/errors/oa-exception.filter.ts` — global Nest exception filter (`app.useGlobalFilters` in `main.ts`). Formats **any** thrown value — `OaException`, a standard Nest `HttpException`, or an unexpected error — into the envelope above. No route can fall back to Nest's default `{ statusCode, message }` body.

## Authorization model (client `oauth`)

Stories 6.1-6.4 are **verify-first**: the professor's realm import
(`infrastructure/dev.local/services/keycloak/constrsw.json`) already ships the
full B.2 authorization model for client `oauth`. This group does **not** own
or edit that file — it verifies what the import contains and would only
gap-fill genuinely missing objects via the running Admin Console or Admin API.

> **Verification method used here:** static inspection of `constrsw.json` as
> committed to the `base` repo (the same file `start-dev --import-realm`
> loads). This confirms what *will* be imported. Docker/Keycloak were not
> running in this environment, so **live** Admin Console re-confirmation after
> an actual `docker compose up` + volume import is still recommended before
> treating 6.1-6.4 as fully closed — see the caveat under each story below.

### Story 6.1 — client roles

Client roles on `oauth`, confirmed present in `constrsw.json`:

| Role | Present |
| --- | --- |
| `administrator` | ✅ |
| `coordinator` | ✅ |
| `professor` | ✅ |
| `student` | ✅ |

These are the canonical **English** B.2 names — never `funcionario` /
`coordenador`. No gap to fill; no realm JSON change needed. (The client also
defines `uma_protection`, a Keycloak-internal role for its own Authorization
Services client — not part of the B.2 set, left as-is.)

Story 5.6's logical-delete representation for roles created via this API is
separate from these four client roles; B.2 policy binding always uses the
canonical **active** role names above, never an inactive/soft-deleted form.

**Still pending:** confirm the same four roles appear under **Client roles**
for `oauth` in the running Admin Console (`:8081`) after the volume import,
since a stale `constrsw-keycloak-data` volume would not pick up JSON changes.

### Story 6.2 — authorization resources

`authorizationServicesEnabled: true` on client `oauth`, confirmed in
`constrsw.json`. The eight named resources, each with its URL, are present:

| Resource | URL |
| --- | --- |
| `classes` | `/classes` |
| `courses` | `/courses` |
| `lessons` | `/lessons` |
| `professors` | `/professors` |
| `reservations` | `/reservations` |
| `resources` | `/resources` |
| `rooms` | `/rooms` |
| `students` | `/students` |

No gap to fill. The import also defines a `Default Resource` (`/*`) — that is
Keycloak's own catch-all, not one of the eight B.2 resources; it is left as-is
and ignored by this list.

These URLs exist only to identify the resource inside Keycloak Authorization
Services — this SPEC does **not** implement `classes`/`courses`/etc. as real
HTTP routes or domain services (NFR11).

**Still pending:** same live-console caveat as 6.1 — confirm Authorization →
Resources on client `oauth` lists these eight after the actual volume import.

### Story 6.3 — role policies

One **Role** policy per B.2 permission-bearing role, each filtered by client
`oauth` (`oauth/<role>` in the export), confirmed in `constrsw.json`:

| Policy | Bound role |
| --- | --- |
| `administrator-policy` | `administrator` |
| `coordinator-policy` | `coordinator` |
| `professor-policy` | `professor` |

No gap to fill.

The import **also** contains `student-policy` (bound to `student`, filtered by
client `oauth`). It exists in the realm but — see Story 6.4 — is **not**
applied to any resource permission, so `student` still ends up with **no**
resource grants, matching B.2. This is documented, not removed: this group
does not edit the professor's realm to make it match the brief.

**Still pending:** same live-console caveat as 6.1 — confirm Authorization →
Policies on client `oauth` lists these four (three B.2 + `student-policy`)
after the actual volume import.

### Story 6.4 — resource-based permissions

Three resource-based permissions, all `decisionStrategy: AFFIRMATIVE`,
confirmed present in `constrsw.json` (the export nests `resources` /
`applyPolicies` as JSON strings under each permission's `config` — read those,
not the top-level `permissions` array, which the export leaves empty):

| Permission | Resources | Applied policies |
| --- | --- | --- |
| `administrator-permissions` | `professors`, `resources`, `rooms`, `students` | `administrator-policy` |
| `coordinator-permissions` | `classes`, `courses` | `coordinator-policy`, `administrator-policy` |
| `professor-permissions` | `lessons`, `reservations` | `coordinator-policy`, `professor-policy`, `administrator-policy` |

**These extra policies (`coordinator-permissions` also granting
`administrator-policy`; `professor-permissions` also granting
`coordinator-policy` + `administrator-policy`) are intentional, not gaps.**
They are **not** removed to match the Moodle brief's single-policy table —
see "Divergence from the T1 brief" in `keycloak-authz.md` (group decision,
2026-09-09: the realm wins). With `AFFIRMATIVE` strategy, access is
hierarchical:

- `administrator` → all eight resources
- `coordinator` → `courses`, `classes`, `lessons`, `reservations`
- `professor` → `lessons`, `reservations`
- `student` → none (`student-policy` applied to no permission)

No gap to fill; configuration is exactly what the professor's realm already
specifies. This matrix is the **test oracle** for Story 6.5.

**Still pending:** same live-console caveat as 6.1 — confirm Authorization →
Permissions on client `oauth` shows these effective bindings (not just the
raw JSON shape) after the actual volume import.

### Story 6.5 — `POST /authz/validate`

Asks whether the caller's token grants access to one named resource. **Decided
by Keycloak Authorization Services, not by this API** — there is no local
role→resource table anywhere in `src/authz/`.

- **Header**: `Authorization: Bearer <access_token>` (checked by the same
  `BearerAuthGuard` used elsewhere — see 401/503 rules above).
- **Body**: JSON `{ "resource": "<name>" }`, `<name>` one of the eight
  resources from Story 6.2.
- **How it decides**: the route calls the Keycloak token endpoint again, this
  time with `grant_type=urn:ietf:params:oauth:grant-type:uma-ticket`,
  `audience=oauth`, `permission=<resource>`, and the **caller's** access token
  as the `Authorization` header (not the service's client credentials). A
  `200` from Keycloak means permitted; a `403` means denied. Any other status
  is treated as a failure to evaluate, not as an allow/deny answer.
- **Responses**:

  | Status | Meaning |
  | --- | --- |
  | `200` | Keycloak permits access (empty body) |
  | `403` | Keycloak denies access (OA envelope) |
  | `401` | Missing/invalid token (OA envelope, from `BearerAuthGuard`) |
  | `400` | Malformed body / unknown resource name (OA envelope, local `OA-400`) |

- **Expected outcomes** — the realm-derived matrix in `keycloak-authz.md`
  (Stories 6.1-6.4), reproduced here as the test oracle:

  | Role | Allowed resources |
  | --- | --- |
  | `administrator` | all eight |
  | `coordinator` | `courses`, `classes`, `lessons`, `reservations` |
  | `professor` | `lessons`, `reservations` |
  | `student` | none |

  This is **hierarchical** (`administrator` ⊇ `coordinator` ⊇ `professor`) by
  the realm's own multi-policy configuration (Story 6.4) — it is **not** the
  Moodle brief's disjoint table, which this group is not using as the oracle
  (2026-09-09 decision).

**Live verification pending:** unit tests mock the Keycloak permission check
and confirm the route's plumbing reaches the right decision for every
role→resource pair above (`src/authz/authz-matrix.spec.ts`), proving no local
matrix exists. Running these assertions against the actual professor Keycloak
stack (login as each of the four test users, then call `/authz/validate`) was
not possible in this environment — Docker is not installed here — and is the
one remaining step before closing 6.5 end-to-end.

## Errors never carry credentials

`error_stack` relays the upstream payload so failures stay diagnosable, but
`access_token`, `refresh_token`, `id_token`, `client_secret` and `password` are
replaced with `[redacted]` first. An incomplete token response, for instance,
is unusable yet still carries real tokens, and the envelope is both returned to
the caller and read by whatever logs it.

## Running locally

Dependencies:

```bash
npm install
```

Tests:

```bash
npm test
```

The required variables must be present in your shell. From the `base` repo root
you can reuse the professor's file for a local run:

```bash
set -a && . ../../.env && set +a && npm run start
```

Do not create an `.env` inside this service, and do not add an `.env.example` —
`.gitignore` blocks the former, and the latter would become an invented group
deliverable.

Docker image, `GET /health` and running the full compose stack are covered by
stories 1.2 and 1.3.

## Health and Docker

`GET /health` returns `200` with `{ "status": "ok" }`. The process listens on
`OAUTH_INTERNAL_API_PORT` and binds to `0.0.0.0`. The image is built from this
directory's multi-stage `Dockerfile`; the root compose file remains professor-owned.

To run the professor stack, create the external volume first:

```bash
docker volume create constrsw-keycloak-data
docker compose up -d keycloak oauth
```

The Keycloak console is available at `http://localhost:8081` and the oauth API
at `http://localhost:8181`. The realm import runs only when the realm is absent
from the existing volume. Our future `POST /login` contract remains
`application/x-www-form-urlencoded` with HTTP `200`, even if the Keycloak README
shows a JSON example.

## Users

All user routes require the shared Bearer guard **and the `administrator` client
role** on client `oauth` (same rule as `/roles`). UserInfo is used only to prove
the token is still valid; the client role is read from the **access token**
(`resource_access.oauth.roles`), because UserInfo does not include it. Without
that overlay, `admin@pucrs.br` would get `403` on `/users` even after a
successful login. A student token still gets `403`.

Because the service authenticates to the Admin API with `KEYCLOAK_ADMIN`,
Keycloak itself would never answer `403` — so this check is what makes the
"known caller, not permitted" case observable.

JSON fields use the hyphenated names from the brief (`first-name`, `last-name`),
which differ from Keycloak's own `firstName` / `lastName`.

| Method | Path | Success | Notes |
| --- | --- | --- | --- |
| `POST` | `/users` | `201` | Body `username`, `password`, `first-name`, `last-name` |
| `GET` | `/users` | `200` | Enabled users only unless `?enabled=false` |
| `GET` | `/users/:id` | `200` | |
| `PUT` | `/users/:id` | `200`, empty body | `first-name`, `last-name`, `enabled` |
| `PATCH` | `/users/:id` | `200`, empty body | Body `{ "password": "..." }` |
| `DELETE` | `/users/:id` | `204`, empty body | Logical delete |

### Creating a user

`username` must be a valid e-mail address: the realm uses e-mail as the
username, so the value is stored as both `username` and `email`. The generated
id comes from the `Location` header Keycloak returns, since its `201` has no
body. A username already in use answers `409`.

### `PUT` accepts only three attributes

`first-name`, `last-name` and `enabled`. Any other field is rejected with `400`,
**including `username`** — changing it would change the account's identity,
because the username is the e-mail. Use `PATCH` for the password.

Keycloak's user update replaces the whole representation, so the service reads
the stored user first and merges the changes onto it; otherwise omitting a field
would clear it.

### `DELETE` never removes anything

It disables the user. Calling it on an already-disabled user still answers
`204`, so the route is idempotent. Re-enable through `PUT` with
`{ "enabled": true }`.

### `GET /users` defaults to enabled

With no query string the list contains only enabled users, following the
brief's wording. `?enabled=false` returns the disabled ones; any other value is
a `400`. The filter is also reapplied locally, because some Keycloak setups
ignore the query parameter.

## Roles and assignments

All role routes require the shared Bearer guard **and the `administrator` client
role**. Users with a valid token but without `administrator` receive `403`.
The routes operate on **client roles** of the configured `oauth` client, never realm roles. A role representation is
the Keycloak object with at least `id` and `name`; `description` and
`attributes` are also accepted.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/roles` | Create a client role |
| `GET` | `/roles` | List client roles |
| `GET` | `/roles/:id` | Get a client role by UUID |
| `PUT` | `/roles/:id` | Replace role attributes |
| `PATCH` | `/roles/:id` | Partially update role attributes |
| `DELETE` | `/roles/:id` | Logical delete |
| `POST` | `/users/:id/roles` | Assign by `roleId`, `roleName`, or `name` |
| `DELETE` | `/users/:id/roles/:roleId` | Unassign a client role |

The canonical role names are `administrator`, `coordinator`, `professor`, and
`student`. They must not be renamed because the Authorization Services policies
depend on them. Logical deletion never calls Keycloak DELETE; it sets the client
role attribute `inactive=true` and is idempotent.

Authorization checks `resource_access.oauth.roles` on the **access token**
(after UserInfo has accepted it) and requires `administrator` there. A realm
role named `administrator` without the oauth **client** role is not enough.

`PUT /roles/:id` requires a complete replacement body with `name` and may
replace `description` and `attributes`. `PATCH /roles/:id` accepts a non-empty
subset of those mutable fields and preserves fields that are omitted.

All errors use the OA envelope:

```json
{
  "error_code": "OA-400",
  "error_description": "...",
  "error_source": "OAuthAPI",
  "error_stack": [{ "description": "..." }]
}
```

The global error filter covers validation, Bearer, Keycloak upstream, and
unexpected application errors. The caller authorization decision remains
delegated to the current Bearer/Admin API arrangement and is intentionally
tracked separately.

## End-to-end tests (`e2e/`)

Black-box suite in bash + `curl` + `jq` that drives a **running** stack — the
real `oauth` container in front of the real Keycloak with the professor's realm
import. Nothing is mocked, so it also proves the realm wiring (client roles,
the openid scope, the B.2 authorization matrix). It complements the Jest unit
and integration specs, which stay the fast gate in CI.

```bash
# from the base repo: docker compose up -d keycloak oauth
npm run test:e2e                      # every suite
./e2e/run.sh users roles              # only some suites
BASE_URL=http://localhost:8181 ./e2e/run.sh
```

Requires `curl` and `jq`. The runner waits for `/health`, logs in the four
seeded users once, runs the suites and exits `0` (all green), `1` (assertion
failures) or `2` (stack unavailable / seeded login failed).

| Variable | Default |
| --- | --- |
| `BASE_URL` | `http://localhost:8181` |
| `METRICS_URL` | `http://localhost:8381` (check skipped if unreachable) |
| `E2E_PASSWORD` | `a12345678` (password of the seeded users) |
| `E2E_ADMIN_USER`, `E2E_COORDINATOR_USER`, `E2E_PROFESSOR_USER`, `E2E_STUDENT_USER` | `<role>@pucrs.br` |
| `E2E_WAIT_SECONDS` | `120` |

| Suite | Endpoints and behaviour covered |
| --- | --- |
| `health` | `GET /health`, unknown route → OA 404, Prometheus scrape port |
| `auth` | `POST /login` (urlencoded, multipart, ignored `client_id`/`grant_type`, 400/401 cases, JWT claims), `POST /refresh` |
| `bearer_guard` | Every protected route → 401 without/with bad token; non-admins → 403 |
| `users` | `POST/GET/PUT/PATCH/DELETE /users`, `?enabled` filter, validation, password change verified by login, logical delete and re-enable |
| `roles` | `POST/GET/PUT/PATCH/DELETE /roles`, `POST /users/:id/roles`, `DELETE /users/:id/roles/:roleId` (by id and by name), assignment verified in the user's next token |
| `authz` | `POST /authz/validate`: full role × resource matrix (4 × 8) plus 400/401 cases |

Every error assertion also checks the uniform OA envelope (`error_source`,
array-of-objects `error_stack`).

**Seeded coordinator/professor.** The realm import gives `coordinator@` and
`professor@pucrs.br` only *realm* roles, while the Authorization policies bind
the *client* roles `oauth/coordinator` and `oauth/professor`, so those seeds are
denied every resource. The `authz` suite therefore creates fixture users and
assigns the client role through the API before checking the matrix (`admin` and
`student` are used as seeded).

**Test data.** Each run creates users named `e2e-<timestamp>@pucrs.br` and
roles named `e2e-role-<timestamp>`. The API has no hard delete, so users are
disabled and roles are marked `inactive` when the run ends; they stay in the
realm and can be purged by resetting the Keycloak volume. E2E is intentionally
not part of the CI workflow, which has no Keycloak to talk to.
