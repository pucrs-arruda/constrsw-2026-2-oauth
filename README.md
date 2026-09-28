# oauth — constrsw-2026-2 (grupo03)

API REST de autenticacao/autorizacao do T1. Nao guarda nenhum dado propria:
e um adapter fino sobre a REST API do Keycloak (realm `constrsw`, client
`oauth`), em **NestJS 11 + TypeScript 6**.

## Stack

| Item | Tecnologia |
| --- | --- |
| Framework | NestJS 11 (Express) + TypeScript 6, Node 24 |
| Validacao | `class-validator` / `class-transformer` (`ValidationPipe` global, `whitelist`) |
| Cliente HTTP | `@nestjs/axios` (axios + rxjs) |
| Documentacao | `@nestjs/swagger` (OpenAPI em `/swagger`) |
| Observabilidade | OpenTelemetry (`sdk-node`, HTTP/Express) + `PrometheusExporter` |
| Testes | Jest + ts-jest (unitarios), supertest (e2e) |
| Identity provider | Keycloak 26.0.1 (realm `constrsw`, client `oauth`) |
| Containers | Docker multi-stage (`node:24-alpine`), Docker Compose |

## Arquitetura

O `oauth` e um **adapter/gateway sem estado e sem banco proprio**: traduz
chamadas REST simples (JSON / form-data) para a REST API do Keycloak e
devolve as respostas num formato padronizado. Toda a autenticacao e
autorizacao "de verdade" fica no Keycloak.

```
                +---------------------------- docker network ----------------------------+
                |                                                                        |
 Cliente  --->  |  oauth (NestJS :3001)  ---- HTTP ---->  Keycloak (:8080, realm constrsw)|
 (curl/Swagger) |    |                                                                   |
 :8181          |    +-- /metrics (:9464, OpenTelemetry) <---- scrape ---- Prometheus :9090|
                +------------------------------------------------------------------------+
```

Portas externas (definidas no `.env` da raiz): API `8181`, metricas `8381`,
Keycloak `8081`, Prometheus `9090`.

### Estilo arquitetural

**Layered architecture** com 4 camadas. A dependencia aponta sempre para o
`domain`:

```
presentation  ->  application  ->  domain  <-  infrastructure
```

| Camada | O que tem | Depende de |
| --- | --- | --- |
| `domain` | entidades (`User`, `Role`, `TokenSet`), interfaces/ports (`UserRepository`, `RoleRepository`, `AuthGateway`) e `DomainError` — TypeScript puro | nada |
| `application` | casos de uso (`AuthService`, `UsersService`, `RolesService`) | `domain` |
| `infrastructure` | adapters do Keycloak que implementam os ports, cliente HTTP, mapeamento de erros, config e telemetria | `domain` |
| `presentation` | controllers, DTOs (validacao + Swagger), guard, filtro global de excecoes | `application`, `domain` |

A `application` fala apenas com as interfaces do `domain`; quem sabe que o
provedor e o Keycloak (paths, payloads, `firstName` vs `first-name`) e a
`infrastructure`. O `InfrastructureModule` liga cada port ao adapter por
injecao de dependencia (`USER_REPOSITORY`, `ROLE_REPOSITORY`, `AUTH_GATEWAY`).

### Decisoes de arquitetura

1. **Repasse do token do chamador.** As rotas administrativas nao usam token
   de servico: o `access_token` do usuario logado e repassado como esta para a
   Admin REST API (por isso os ports recebem `token` como parametro). A
   autorizacao (RBAC) e inteiramente delegada ao Keycloak — usuario sem role
   `realm-management` recebe `403` direto do Keycloak, sem logica extra aqui.
   Apenas `POST /login` usa o client `oauth` (client id + secret, grant `password`).
2. **Guard so valida a estrutura.** `BearerTokenGuard` responde `400` se o
   header `Authorization: Bearer <token>` estiver ausente/malformado; a
   validade do token e verificada pelo Keycloak.
3. **Erros de dominio, HTTP so na borda.** A `infrastructure` converte erros
   do Keycloak em `DomainError` (`kind`: BadRequest, Unauthorized,
   InvalidCredentials, Forbidden, NotFound, Conflict, Unavailable, Upstream).
   O `OAuthExceptionFilter` (global) traduz `DomainError`, erros do
   `ValidationPipe` e erros nao tratados no envelope do T1
   (ver [Formato de erro](#formato-de-erro)). Keycloak fora do ar vira `503`.
4. **Stateless.** Sem persistencia local; escala horizontalmente sem coordenacao.
5. **Metricas por pull.** OpenTelemetry expoe `/metrics` numa porta propria
   (sem `otel-collector`); o Prometheus raspa direto.

### Estrutura de pastas

```
src/
  main.ts                          bootstrap (telemetria, pipes, filtro, Swagger)
  app.module.ts                    modulo raiz
  domain/
    entities/                      user, role, token-set
    repositories/                  ports: user.repository, role.repository, auth.gateway
    errors/                        domain-error
  application/
    application.module.ts
    auth/ users/ roles/            casos de uso (*.service.ts)
  infrastructure/
    infrastructure.module.ts       liga ports -> adapters
    keycloak/                      keycloak-client.service, keycloak-*.repository,
                                   keycloak-auth.gateway, keycloak-error.mapper, mappers/
    config/                        configuration.ts (variaveis de ambiente)
    telemetry/                     tracing.ts (OpenTelemetry + Prometheus)
  presentation/http/
    presentation.module.ts
    controllers/                   auth, users, roles, user-roles, health
    dto/                           auth/ users/ roles/ + error-response.dto
    guards/ decorators/            BearerTokenGuard, @BearerToken()
    filters/ exceptions/           OAuthExceptionFilter, OAuthApiException
    validators/                    regex de e-mail (RFC 5322)
test/                              testes e2e (auth, users, roles, health)
scripts/smoke-test.sh              percorre todos os endpoints em ordem de dependencia
infra-local/                       override local (Keycloak Dockerfile, prometheus.yml)
docs/, SPEC.md                     analise, especificacao do T1 e spec da layered arch
```

## Configuracao (variaveis de ambiente)

Vem do `.env` da raiz do repo `base` quando roda via compose;
`.env.example` serve para rodar a API isolada.

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `OAUTH_INTERNAL_API_PORT` (ou `PORT`) | `3001` | porta interna da API |
| `OAUTH_INTERNAL_METRICS_PORT` | `9464` | porta do `/metrics` |
| `KEYCLOAK_SERVER_URL` | `http://localhost:8080` | URL base do Keycloak (sem `/auth`) |
| `KEYCLOAK_REALM` | `constrsw` | realm |
| `KEYCLOAK_CLIENT_ID` | `oauth` | client usado no login |
| `KEYCLOAK_CLIENT_SECRET` | — | secret do client |

## Rodando

**IMPORTANTE**: o `docker-compose.yml`, o `.env` da raiz e o backup do
Keycloak (`infrastructure/dev.local/services/keycloak/`) sao os oficiais
publicados pelo professor no repo `base` — nao criamos/nao mexemos mais
neles aqui. Da raiz do repo (pasta `T1`):

```bash
# so na primeira vez (o compose usa um volume externo pro Keycloak)
docker volume create constrsw-keycloak-data

docker compose up -d --build
```

**Mac com Apple Silicon recente (M4) / macOS 15.2+**: a imagem oficial do
Keycloak crasha (`SIGILL` na JVM) e o healthcheck dela depende de `curl`
(que nao existe na imagem) - dois bugs de ambiente, reportados ao professor,
sem relacao com a configuracao dele. Alem disso, o `prometheus.yml` central
(commit `e201543` do professor, sem a correcao de hostname que fizemos)
aponta o job `auth` pro hostname `auth`, que nao existe na rede docker (o
servico se chama `oauth`) - isso faz o Prometheus nunca conseguir raspar
nossas metricas, em qualquer maquina, nao so Apple Silicon. Contornamos os
tres **sem alterar nenhum arquivo da `base`**, com um override que mora
todo dentro deste submodulo (`backend/oauth/docker-compose.override.yml` +
`backend/oauth/infra-local/keycloak.Dockerfile` +
`backend/oauth/infra-local/prometheus.yml` - detalhes em
[`infra-local/README.md`](./infra-local/README.md)). Para subir usando o
contorno, rode este comando em vez do `docker compose up` acima (ainda a
partir da raiz do repo, `T1`):

```bash
docker compose -f docker-compose.yml -f backend/oauth/docker-compose.override.yml up -d --build
```

Isso sobe `keycloak` (realm `constrsw` importado de `constrsw.json`, sem
prefixo `/auth`) e esta API (`oauth`) em `http://localhost:8181` (porta
externa definida em `OAUTH_EXTERNAL_API_PORT` no `.env` da raiz; a porta
interna do container e `3001`).

- Swagger: http://localhost:8181/swagger
- Health check: http://localhost:8181/health
- Metricas (Prometheus/OpenMetrics): http://localhost:8381/metrics
- Keycloak (console admin): http://localhost:8081 (`admin` / `a12345678`)
- Prometheus (raiz do repo `base`, `docker volume create constrsw-prometheus-data` antes do primeiro `up`; use o comando com o override acima, senao o job `auth` fica sempre `down`): http://localhost:9090/targets

Para rodar so a API localmente (sem Docker), com o Keycloak do compose ja
de pe:

```bash
npm install
cp .env.example .env   # ja aponta pro Keycloak em localhost:8081
npm run start:dev
```

## Usuarios do realm `constrsw` (backup oficial do professor)

Todos com senha `a12345678`:

| username | realm role | tem permissao de Admin REST API? |
| --- | --- | --- |
| `admin@pucrs.br` | `administrator` | sim (roles `realm-management` completas — usar para testar `/users` e `/roles`) |
| `coordinator@pucrs.br` | `coordinator` | nao — util para testar 403 |
| `professor@pucrs.br` | `professor` | nao — util para testar 403 |
| `student@pucrs.br` | `student` | nao — util para testar 403 |

## Rotas

Ver o Swagger para o contrato completo. Resumo:

- `POST /login` — form-data `username`/`password` → token do Keycloak (Direct Access Grant, client `oauth`).
- `POST /users`, `GET /users`, `GET /users/:id`, `PUT /users/:id`, `PATCH /users/:id` (senha), `DELETE /users/:id` (desabilita).
- `POST /roles`, `GET /roles`, `GET /roles/:id`, `PUT /roles/:id`, `PATCH /roles/:id`, `DELETE /roles/:id`.
- `POST /users/:userId/roles/:roleId` e `DELETE /users/:userId/roles/:roleId` — atribuir/remover role de um usuario.

Todas as rotas (exceto `/login` e `/health`) exigem `Authorization: Bearer <access_token>`,
que e repassado como esta para a Admin REST API do Keycloak.

## Decisoes e suposicoes (documentar para a entrega)

1. **Infra oficial do professor.** O `docker-compose.yml`, `.env` da raiz e
   o backup do Keycloak (`infrastructure/dev.local/services/keycloak/`)
   sao os publicados pelo professor no repo `base` (commits de
   2026-09-02) - inicialmente tinhamos montado uma versao propria do zero
   (compose, realm, `.env`), que foi substituida por essa oficial assim
   que ficou disponivel. So mexemos em `backend/oauth` a partir daqui.
2. **Sem prefixo `/auth`.** O Keycloak do professor (26.0.1, Quarkus) nao
   usa `/auth` — `KEYCLOAK_SERVER_URL` ja vem pronto (`http://keycloak:8080`)
   e usamos direto.
3. **`error_code`**: por padrao, o proprio codigo HTTP da resposta (string),
   inclusive quando o erro vem do Keycloak — ex.: `"401"`. `error_source` e
   sempre `"OAuthAPI"`.
4. **Regex de e-mail**: o regex "RFC 5322" colado no enunciado veio
   corrompido na copia (PDF/Doc). Usamos uma variante amplamente adotada e
   equivalente na pratica (`src/common/validators/email-rfc5322.validator.ts`).
5. **Exclusao de role**: o Keycloak nao tem "desabilitar" um role (so
   usuarios tem esse flag). `DELETE /roles/:id` remove o role de fato.
6. **`refresh_expires_in`**: o enunciado tem um typo (`referesh_expires_in`);
   devolvemos o campo exatamente como o Keycloak retorna (`refresh_expires_in`).
7. **Metricas Prometheus**: instrumentacao automatica via OpenTelemetry
   (`HttpInstrumentation`/`ExpressInstrumentation`), exportadas em `/metrics`
   (porta `OAUTH_INTERNAL_METRICS_PORT`) via `PrometheusExporter` (pull, sem
   passar pelo `otel-collector`). O `prometheus.yml` oficial do professor
   (repo `base`, commit `e201543`) aponta o job `auth` pro hostname `auth`,
   que nao existe (nosso servico se chama `oauth`) - como nao temos
   permissao de commit na `base`, corrigimos isso com uma copia local do
   arquivo (`backend/oauth/infra-local/prometheus.yml`), aplicada via
   `docker-compose.override.yml`, sem tocar no arquivo do professor. Ver
   [`infra-local/README.md`](./infra-local/README.md).

## Formato de erro

Todos os erros seguem o envelope do T1:

```json
{
  "error_code": "401",
  "error_description": "invalid_grant: Invalid user credentials",
  "error_source": "OAuthAPI",
  "error_stack": [
    { "error_code": "401", "error_description": "...", "error_source": "Keycloak" }
  ]
}
```

| Status | Quando |
| --- | --- |
| `400` | payload invalido ou header `Authorization` ausente/malformado |
| `401` | credenciais/token invalidos (vindo do Keycloak) |
| `403` | usuario sem permissao administrativa no Keycloak |
| `404` / `409` | recurso inexistente / duplicado (repassado do Keycloak) |
| `503` | Keycloak indisponivel |

## Testes

```bash
npm test            # unitarios (use cases com ports mockados, adapters Keycloak, guard, filtro)
npm run test:cov    # com cobertura
npm run test:e2e    # e2e (precisa do compose de pe: Keycloak + API)
bash scripts/smoke-test.sh   # smoke test de todos os endpoints (API=http://localhost:8181)
```

## Observabilidade

- `GET /health` — usado pelo healthcheck do compose.
- `GET :8381/metrics` — metricas HTTP/Express (OpenTelemetry -> Prometheus).
- Prometheus em http://localhost:9090/targets (job `auth`).

## Cheat-sheet de curl

```bash
export API=http://localhost:8181

# login (admin) -> guarde o access_token
curl -s -X POST $API/login -F "username=admin@pucrs.br" -F "password=a12345678" | tee /tmp/login.json
export TOKEN=$(python3 -c "import json;print(json.load(open('/tmp/login.json'))['access_token'])")

# users
curl -s $API/users -H "Authorization: Bearer $TOKEN"
curl -s "$API/users?enabled=true" -H "Authorization: Bearer $TOKEN"
curl -s -X POST $API/users -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"username":"fulano@pucrs.br","password":"123456","first-name":"Fulano","last-name":"Silva"}'
curl -s $API/users/<id> -H "Authorization: Bearer $TOKEN"
curl -s -X PUT $API/users/<id> -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"first-name":"Novo Nome"}'
curl -s -X PATCH $API/users/<id> -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"password":"novaSenha"}'
curl -s -X DELETE $API/users/<id> -H "Authorization: Bearer $TOKEN"   # desabilita (logico)

# roles
curl -s -X POST $API/roles -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"coordenador","description":"Coordenador de curso"}'
curl -s $API/roles -H "Authorization: Bearer $TOKEN"
curl -s $API/roles/<roleId> -H "Authorization: Bearer $TOKEN"
curl -s -X PUT $API/roles/<roleId> -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"coordenador","description":"editado"}'
curl -s -X PATCH $API/roles/<roleId> -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"description":"so a descricao"}'
curl -s -X DELETE $API/roles/<roleId> -H "Authorization: Bearer $TOKEN"

# atribuir/remover role de um user
curl -s -X POST $API/users/<userId>/roles/<roleId> -H "Authorization: Bearer $TOKEN"
curl -s -X DELETE $API/users/<userId>/roles/<roleId> -H "Authorization: Bearer $TOKEN"
```
