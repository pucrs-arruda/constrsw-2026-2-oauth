# OAuth API

REST API for Group 02 that integrates the application with Keycloak.

## Architecture

The API is stateless and does not store users or roles locally. Keycloak is the identity provider and source of truth.

```text
HTTP clients -> REST controllers -> application services -> Keycloak REST API
```

`LoginController` and `LoginService` use `KeycloakClient` to exchange user credentials for tokens. Role and role-mapping services call Keycloak Admin REST endpoints. User routes are defined, but their Keycloak client is not implemented yet.

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

Inside Docker Compose, `KEYCLOAK_SERVER_URL` points to the `keycloak` service. Keep the client secret outside source control and do not send it from API clients.

## API documentation

When the API is running through Docker Compose:

- Swagger UI: <http://localhost:8181/swagger-ui.html>
- OpenAPI document: <http://localhost:8181/v3/api-docs>
- Health check: <http://localhost:8181/health>

## Prometheus telemetry

From the `base` repository root, start the stack with the Prometheus Compose overlay:

```bash
docker compose -f docker-compose.yml -f backend/oauth/docker-compose.prometheus.yml up --build -d
```

Prometheus is available at <http://localhost:9090>. Its targets page should show
the `oauth` and `keycloak` jobs as `UP`. The OAuth metrics endpoint is available
at <http://localhost:8381/actuator/prometheus> with the default `.env` ports.
The OAuth Actuator runs on a separate internal port (`9464` by default), while
the application API remains on port `3001` inside Docker. If the internal
metrics ports are changed, update `prometheus/prometheus.yml` accordingly.
For ready-to-use HTTP, latency, JVM, availability, and Prometheus health
graphs, see [Prometheus graphs](prometheus/GRAPHS.md).

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Reports API health. |
| `POST` | `/login` | Authenticates a user with Keycloak. Accepts `username` and `password` as `multipart/form-data`; returns access and refresh tokens with HTTP `201`. |
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
| `POST` | `/users/{id}/roles` | Assigns a role to a user. Requires a Bearer token. |
| `DELETE` | `/users/{id}/roles/{roleId}` | Removes a role from a user. Requires a Bearer token. |

### Login example

```bash
curl --request POST http://localhost:8181/login \
  --form 'username=YOUR_USERNAME' \
  --form 'password=YOUR_PASSWORD'
```

## Current limitations

- User routes are present in the API contract, but the Keycloak user-management client is not implemented; these operations currently return HTTP `501`.
- The API returns a refresh token from login, but does not expose a refresh-token endpoint.
- The API does not yet expose an endpoint that evaluates access to a Keycloak resource.

## Tests

Run the Maven test suite from this directory:

```bash
mvn test
```
