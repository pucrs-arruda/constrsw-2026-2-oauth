# OAuth/OIDC Authentication & Authorization Microservice

**Course:** Software Construction (2026/2) — PUCRS
**Team:** Group 01 · **Base branch:** `grupo01`
**Stack:** PHP 8.2+ / Symfony 6.4 LTS · Nginx · Keycloak 22+ (Quarkus)
**Architecture:** Hexagonal (Ports & Adapters)

The security facade for the whole academic mesh: every other microservice authenticates users, manages accounts/roles, and checks permissions through this API — none of them ever talk to Keycloak directly.

---

## Why it's built this way

This isn't a thin wrapper around Keycloak's REST API — it's a proper **anti-corruption layer**. Four engineers built independent slices (auth/tokens, user management, roles, authorization) in parallel, on separate branches, against interfaces frozen upfront on the base branch. Zero merge conflicts, zero integration surprises. That only works because of a few deliberate engineering decisions:

- **Strict hexagonal isolation.** `src/Domain` is pure PHP — no Symfony, no Guzzle, no Keycloak SDK, no framework annotations. It only knows about `Ports` (interfaces). Everything that talks to the outside world lives in `src/Infrastructure` and implements those interfaces. You could swap Keycloak for Auth0 tomorrow and the domain/application layers wouldn't notice.
- **1:1 symmetry between use cases and inbound ports.** 17 use cases, 17 matching interfaces. Every capability the API exposes has an explicit, typed contract before it has an implementation — that's what let 4 people build against each other's work without seeing each other's code.
- **4 outbound ports, one per integration surface** (`KeycloakAuthPortInterface`, `KeycloakUserPortInterface`, `KeycloakRolePortInterface`, `KeycloakAuthorizationPortInterface`) — each with a single Keycloak adapter behind it.
- **Contract-first, but from code, not YAML by hand.** OpenAPI isn't a hand-maintained spec that drifts from the code — it's generated at runtime from a custom `#[OpenApiOperation(...)]` attribute on each controller method (`OpenApiSpecificationBuilder`). The spec you see in Swagger UI is *always* what the code actually does.
- **One error envelope, everywhere.** A single global `JsonExceptionListener` intercepts every uncaught exception — domain exceptions carry their own HTTP status + error code + validation details; everything else degrades to a safe 500, with stack traces only surfaced when `APP_DEBUG=true`. No controller ever hand-rolls an error response.
- **Metrics for free.** An `EventSubscriber` (`MetricsRequestListener`) hooks `kernel.request`/`kernel.response` once, globally, and records request count + duration (RED-style) for every route automatically — no per-endpoint instrumentation, no risk of forgetting to add it to a new controller.
- **Soft-delete as an invariant, not a convention.** "Delete" never means `DELETE FROM` here — it means `enabled: false` on Keycloak. Enforced at the domain level (`DisableUserUseCase`, role deletion), never left to controller discipline.
- **Admin token caching.** Client-credentials tokens for the Keycloak Admin API are cached in-memory with their `expires_in` and only refreshed when they actually expire — not fetched on every single admin call.
- **Tested where it matters.** 165 test methods across 36 files: unit tests per use case and per adapter, integration tests per controller, and end-to-end tests for complete workflows (`unit`, `integration`, `e2e` suites). `phpunit.dist.xml` runs with `failOnDeprecation` / `failOnNotice` / `failOnWarning` — silent warnings are treated as build failures, not ignored.
- **Zero manual setup.** Keycloak boots with the realm, client, roles and policies already provisioned via `--import-realm`; Grafana boots with its Prometheus datasource and dashboard folder already provisioned. `docker compose up -d` is the entire onboarding process.

---

## What it does

| Capability | Endpoints | Notes |
| :--- | :--- | :--- |
| **Auth & tokens (OIDC)** | `POST /login`, `POST /refresh`, `GET /me` | Password grant + refresh grant against Keycloak; `/login` accepts JSON *and* form-encoded bodies |
| **User management** | `POST/GET /users`, `GET/PUT/PATCH/DELETE /users/{id}` | Full lifecycle via Keycloak Admin API; delete is always logical |
| **Role management** | `POST/GET /roles`, `GET/PUT/PATCH/DELETE /roles/{id}` | Dynamic roles, not hardcoded — created/edited at runtime |
| **Role assignment** | `POST /users/{userId}/roles`, `DELETE /users/{userId}/roles/{roleId}` | Attach/detach roles to a user |
| **Policy enforcement** | `POST /authorize` | Any downstream microservice calls this to check "does this token grant access to resource X?" — token validation + role↔resource matrix in one hop |
| **Observability** | `GET /metrics` | Prometheus exposition format; scraped by the platform's Prometheus and rendered in Grafana |
| **Living docs** | `GET /docs`, `GET /docs/openapi.json` | Swagger UI generated straight from controller attributes |
| **Health** | `GET /health`, `GET /api/health` | Container/orchestration healthcheck |

---

## Running it

The full stack (Keycloak, this service, Prometheus, Grafana) is orchestrated by the **root monorepo's** `docker-compose.yml` (`base`), not a compose file local to this submodule:

```bash
# from the base repo root
cp .env.example .env   # if you don't have one yet
docker compose up -d
```

| Service | URL | Notes |
| :--- | :--- | :--- |
| oauth API | `http://localhost:8181` | Healthcheck at `/api/health` |
| Swagger UI | `http://localhost:8181/docs` | Code-generated OpenAPI |
| Metrics | `http://localhost:8181/metrics` | Prometheus exposition format |
| Keycloak | `http://localhost:8081` | admin / a12345678 · realm `constrsw` |
| Prometheus | `http://localhost:9090` | Scrapes oauth + Keycloak |
| Grafana | `http://localhost:3300` | admin / admin · Prometheus datasource pre-wired |

---

## Observability: Prometheus & Grafana Setup

### 1. Changes made in the `base` repository (Prometheus)

In the root monorepo (`base`), Prometheus is configured via `infrastructure/dev.local/services/prometheus/prometheus.yml`. Two essential updates were made to scrape this service:

1. **Scrape Target Configuration:**
   Replaced the legacy job (`auth:9464`) with the PHP/Symfony OAuth service target running on the internal Docker network (`oauth:3001`):
   ```yaml
   # infrastructure/dev.local/services/prometheus/prometheus.yml
   - job_name: 'oauth'
     metrics_path: '/metrics'
     scrape_interval: 10s
     static_configs:
       - targets: ['oauth:3001']
         labels:
           service: 'oauth'
           environment: 'dev'
   ```
2. **Health Check Probing:**
   Added `http://oauth:3001/health` to the blackbox probe targets to monitor container liveness.

You can verify Prometheus is actively scraping the service by opening `http://localhost:9090/targets` — the `oauth` job should report state **UP (1/1)**.

---

### 2. How to use Grafana with our Dashboard (`.json`)

A production-ready dashboard with **16 panels** is included in [`docs/monitoring/grafana-dashboard.json`](docs/monitoring/grafana-dashboard.json). It visualizes RED metrics (Rate, Errors, Duration), business KPIs (Logins, Refreshes, RBAC Authorizations), and PHP runtime health.

#### Step-by-step Import Guide:

1. **Access Grafana:**
   Open [http://localhost:3300](http://localhost:3300) in your browser.
   - Default credentials: `admin` / `admin` (skip password reset if prompted).

2. **Navigate to Import:**
   - In the left sidebar, click **Dashboards** (or the four-squares icon).
   - Click **New** (top-right button) → **Import** (or go directly to [http://localhost:3300/dashboard/import](http://localhost:3300/dashboard/import)).

3. **Upload the JSON File:**
   - Click the blue **Upload dashboard JSON file** button.
   - Select the file: `backend/oauth/docs/monitoring/grafana-dashboard.json`.
   - *Alternative:* Open `grafana-dashboard.json`, copy the entire raw JSON contents, paste it into the **Import via panel json** text area, and click **Load**.

4. **Select Datasource & Finish:**
   - Under the **Prometheus** dropdown at the bottom, select the provisioned Prometheus datasource (e.g., `Prometheus`).
   - Click **Import**.

5. **Generate Traffic:**
   To see live data populate across all 16 panels immediately, run the full smoke test:
   ```bash
   python3 scripts/smoke_test.py --delay 0
   ```
   Refresh Grafana or set the auto-refresh to **5s** in the top-right corner.

#### What the Dashboard Monitors:
* **System & Runtime:** Service status (`UP`), Current RAM memory allocation, Peak RAM memory, PHP Version & SAPI.
* **Traffic & RED Metrics:** Total HTTP Request Rate (`req/s`), HTTP Status code distribution (2xx, 4xx, 5xx), Request Duration / Latency per route.
* **Security & Business Activity:**
  - Login Attempts: Successful logins vs. Failed attempts (brute-force detection).
  - Token Refreshes: Session renewal volume.
  - RBAC Policy Authorization: Requests granted (`200 OK`) vs. denied (`403 Forbidden`).

---

## Full-flow smoke test

[`scripts/smoke_test.py`](scripts/smoke_test.py) exercises **every endpoint in this service, in the correct dependency order**, against a real running stack — testing both happy paths and failure modes (400, 401, 403, 404, 409). Zero dependencies beyond the Python 3 standard library, so it runs anywhere without a `pip install`.

```bash
# with the stack up (docker compose up -d from the base repo root)
python3 scripts/smoke_test.py                        # presentation mode: colored, 1.2s between steps
python3 scripts/smoke_test.py --delay 2               # slower, easier for an audience to follow live
python3 scripts/smoke_test.py --delay 0 --no-color    # fast, plain output — good for CI/logs
```

For each of the 44 checks it prints the method + path, a one-line description, the HTTP status (color-coded), response time, and a pretty-printed preview of the body (long values like JWTs are truncated so the terminal stays readable) — then a pass/fail summary at the end.

What it walks through across 10 sections:
1. **Health Checks:** `/`, `/health`, `/api/health`.
2. **Authentication:** Failed login (`401`), successful login (`200`), profile check (`200`), and invalid token rejection (`401`).
3. **User Management:** Empty payload validation (`400`), user creation (`201`), duplicate email conflict (`409`), user listing (`200`), non-existent UUID lookup (`404`), profile update (`200`), and password change (`200`).
4. **Role Management:** Blank name validation (`400`), role creation (`201`), duplicate role conflict (`409`), role listing (`200`), non-existent UUID lookup (`404`), full update (`200`), and partial patch (`200`).
5. **Role Assignment & Inspection:** Assigning role to user (`200`) and inspecting user roles (`200`).
6. **Policy Matrix Authorization:** Valid access to `rooms` (`200`) and denied access to `courses` (`403`).
7. **Security Guard & Non-Admin Enforcement:** Login as non-admin student, followed by verified `403 Forbidden` denials when attempting to assign roles, delete roles, create roles, create users, delete users, modify other users' profiles, or change other users' passwords.
8. **Session Renewal:** Refresh with invalid token (`401`) and renewal with valid refresh token (`200`).
9. **Cleanup:** Unassigning roles and soft-deleting test entities (`204`).
10. **Observability & Docs:** Prometheus metrics (`200`), Swagger UI (`200`), and OpenAPI specification (`200`).

---

## Under the hood

```
src/
├── Domain/              # pure PHP — zero framework/infra dependencies
│   ├── Model/            # User, AuthTokens, Role
│   ├── Port/Inbound/     # 17 use-case interfaces (what the API can do)
│   ├── Port/Outbound/    # 4 interfaces the infra layer must implement
│   └── Exception/        # 9 typed domain exceptions, each owning its HTTP status
├── Application/          # 17 use cases + typed request/response DTOs
└── Infrastructure/       # the only layer that knows Symfony/Keycloak exist
    ├── Keycloak/          # HTTP client (token caching) + adapters implementing Outbound ports
    ├── Http/              # Controllers, global exception listener, metrics listener
    ├── Metrics/           # Prometheus registry (counters + histograms)
    └── OpenApi/           # Attribute-driven spec builder behind /docs
```

Dependency rule: arrows always point inward. `Infrastructure → Application → Domain`. Nothing in `Domain` or `Application` imports a single Symfony or Keycloak class.

---

## Project docs

Requirements, architecture decisions and their history live in [`_bmad-output/planning-artifacts/`](_bmad-output/planning-artifacts/) (PRD, architecture spine, epics). Team branching/PR conventions are in [`docs/team-integration-guide.md`](docs/team-integration-guide.md).
