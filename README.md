# OAuth API

REST API for Group 02 that integrates the application with Keycloak.

## Clean Architecture

The API is stateless. Keycloak remains the source of truth for users and roles;
the OAuth service does not store them locally. Dependencies point toward the
application core:

```text
HTTP -> controller + dto -> service -> port <- client -> Keycloak REST API
                              |
                            domain
```

| Package | Responsibility |
|---|---|
| `domain` | Framework-independent user, role, token, and command models. `Role` owns the partial-update merge and logical-delete naming rules. |
| `port` | Outbound interfaces that describe what the use cases need from an identity provider. No Spring, HTTP, or Keycloak types appear here. |
| `service` | Plain-Java use cases for login, users, roles, and role mapping. They depend on ports and domain models, never on a Keycloak client or `RestClient`. |
| `client` | Keycloak adapters implementing the ports. They translate between domain models and Keycloak HTTP requests/responses and map provider failures. |
| `controller` and `dto` | HTTP input/output adapters. Controllers validate requests, convert API DTOs to domain inputs, call use cases, and convert results back to the existing JSON contract. |
| `config` | Spring composition root. `UseCaseConfig` wires use cases to their adapters; `RestClientConfig` configures the Keycloak connection. |
| `error` | API error mapping and shared application exceptions. |

This keeps the application rules testable without Spring or Keycloak. For
example, a role PATCH fetches the current role through `RoleGateway`, merges
only provided fields in `Role`, then saves it through the same port. The
Keycloak adapter can change without changing the use case or HTTP contract.

## Technology

- Java 21
- Spring Boot 3.5
- Maven
- Spring Web and Bean Validation
- Spring Boot Actuator
- Springdoc OpenAPI 2.8

## Run locally

Use Java 21 and Maven. Make sure Keycloak is reachable and configure the variables listed below, then run:

```bash
mvn spring-boot:run
```

The API listens on port `3001` by default.

## Run with Docker Compose

From the `base` repository root, create the external Keycloak data volume once and start the services:

```bash
docker volume create constrsw-keycloak-data
docker compose up --build -d keycloak oauth
```

Docker Compose reads the service configuration from the root `.env` file.

## Configuration

| Variable | Purpose | Default |
|---|---|---|
| `OAUTH_INTERNAL_API_PORT` | HTTP port used by the API | `3001` |
| `KEYCLOAK_SERVER_URL` | Keycloak base URL | `http://localhost:8081` for local runs |
| `KEYCLOAK_REALM` | Keycloak realm | `constrsw` |
| `KEYCLOAK_CLIENT_ID` | Keycloak client | `oauth` |
| `KEYCLOAK_CLIENT_SECRET` | Confidential client secret | Required |
| `OAUTH_INTERNAL_METRICS_PORT` | HTTP port used by Actuator metrics | `9464` |
| `GRAFANA_EXTERNAL_PORT` | Grafana UI port on localhost | `3000` |
| `GRAFANA_ADMIN_USER` | Local Grafana administrator | `admin` |
| `GRAFANA_ADMIN_PASSWORD` | Local Grafana administrator password | `localdev` |

Inside Docker Compose, `KEYCLOAK_SERVER_URL` points to the `keycloak` service. Keep the client secret outside source control and do not send it from API clients.

## API documentation

When the API is running through Docker Compose:

- Swagger UI: <http://localhost:8181/swagger-ui.html>
- OpenAPI document: <http://localhost:8181/v3/api-docs>
- Health check: <http://localhost:8181/health>

## Prometheus and Grafana telemetry

From the `base` repository root, start the stack with the Prometheus Compose overlay:

```bash
docker compose -f docker-compose.yml -f backend/oauth/docker-compose.prometheus.yml up --build -d
```

Prometheus is available at <http://localhost:9090>. Its targets page should show
the `oauth`, `keycloak`, and `prometheus` jobs as `UP`. The OAuth metrics endpoint is available
at <http://localhost:8381/actuator/prometheus> with the default `.env` ports.
The OAuth Actuator runs on a separate internal port (`9464` by default), while
the application API remains on port `3001` inside Docker. If the internal
metrics ports are changed, update `prometheus/prometheus.yml` accordingly.
For ready-to-use HTTP, latency, JVM, availability, and Prometheus health
graphs, see [Prometheus graphs](prometheus/GRAPHS.md).

Grafana is available at <http://localhost:3000>, bound to localhost only.
Sign in with the local-development defaults `admin` / `localdev`, then change
the password. Set `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` before the
first start to choose different credentials; existing `grafana-data` volumes
retain the initial administrator account. Do not use the default password on
a shared or public deployment. The provisioned Prometheus data source and
**OAuth and Keycloak Observability** dashboard require no manual setup and
include availability, HTTP traffic/errors/latency, JVM resources, and scrape
health. Grafana data persists in the `grafana-data` Docker volume.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Reports API health. |
| `POST` | `/login` | Authenticates a user with Keycloak. Accepts `username` and `password` as `multipart/form-data`; returns access and refresh tokens with HTTP `201`. |
| `POST` | `/refresh` | Accepts `refresh_token` as `multipart/form-data`; returns new access and refresh tokens with HTTP `200`. |
| `GET` | `/access?resource=/courses` | Evaluates the Bearer token against a Keycloak resource URI; returns `200` when access is granted, `403` when denied, and `401` for an invalid token. |
| `POST` | `/users` | Creates a user. |
| `GET` | `/users` | Lists users. |
| `GET` | `/users/{id}` | Retrieves a user. |
| `PUT` | `/users/{id}` | Updates user profile data. |
| `PATCH` | `/users/{id}` | Updates a user's password. |
| `DELETE` | `/users/{id}` | Disables a user. |
| `POST` | `/roles` | Creates a role. Requires a Bearer token. |
| `GET` | `/roles` | Lists roles. Requires a Bearer token. |
| `GET` | `/roles/{id}` | Retrieves a role. Requires a Bearer token. |
| `PUT` | `/roles/{id}` | Replaces a role. Requires a Bearer token. |
| `PATCH` | `/roles/{id}` | Partially updates a role. Requires a Bearer token. |
| `DELETE` | `/roles/{id}` | Logically deletes a role by renaming it with the `DELETED_` prefix. Requires a Bearer token. |
| `POST` | `/users/{id}/roles/{roleId}` | Assigns a role to a user. Requires a Bearer token. |
| `DELETE` | `/users/{id}/roles/{roleId}` | Removes a role from a user. Requires a Bearer token. |

### Login example

```bash
curl --request POST http://localhost:8181/login \
  --form 'username=YOUR_USERNAME' \
  --form 'password=YOUR_PASSWORD'
```

### Refresh-token example

```bash
curl --request POST http://localhost:8181/refresh \
  --form 'refresh_token=YOUR_REFRESH_TOKEN'
```

### Resource-access example

Pass the resource URI configured in Keycloak, such as `/courses`, along with the user's access token:

```bash
curl --get http://localhost:8181/access \
  --data-urlencode 'resource=/courses' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

## Tests

Run the Maven test suite from this directory:

```bash
mvn test
```
