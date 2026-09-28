# Especificação — API `oauth` (grupo03)

**Disciplina:** Construção de Software — PUCRS — 2026/2
**Componente:** T1 — microsserviço `oauth`
**Repositório:** `pucrs-constrsw-2026-2/oauth`, branch `grupo03` (submódulo de `pucrs-constrsw-2026-2/base`)
**Stack:** NestJS 11 + TypeScript 6

## 1. Visão geral

O `oauth` é um wrapper REST em cima da API do Keycloak: não implementa autenticação/autorização por conta própria, apenas traduz chamadas HTTP simples (JSON / form-data) para a REST API do Keycloak (realm `constrsw`) e devolve as respostas num formato padronizado.

Duas responsabilidades:

- **Login** (`POST /login`): troca usuário/senha por um token OAuth2 do Keycloak (grant `password`).
- **Administração** (`/users`, `/roles`, atribuição de roles): CRUD completo de usuários e roles do realm, e vínculo/desvínculo de roles a usuários.

### 1.1 Decisão de arquitetura: repasse do token do chamador

O `oauth` **não** usa um token de serviço (client credentials) para operar a Admin API do Keycloak. Todas as rotas administrativas (`/users/*`, `/roles/*`) exigem `Authorization: Bearer <access_token>` do próprio usuário autenticado, e esse token é repassado **diretamente** para a Admin REST API do Keycloak (`KeycloakClientService.adminRequest`).

Consequência: a autorização "de verdade" é inteiramente delegada ao RBAC do Keycloak (roles do client `realm-management` atribuídas a cada usuário no realm). Isso significa que:
- Um usuário sem permissão administrativa no Keycloak recebe **403** diretamente da Admin API, sem lógica extra no `oauth`.
- Só `admin@pucrs.br` tem essas roles atribuídas no realm oficial do professor — os demais usuários de teste (`coordinator`, `professor`, `student`) autenticam normalmente mas levam 403 em qualquer rota administrativa.

### 1.2 Módulos

| Módulo | Responsabilidade |
|---|---|
| `auth` | `POST /login` |
| `users` | CRUD de usuários |
| `roles` | CRUD de roles + atribuição/remoção de role em usuário (`UserRolesController`) |
| `health` | `GET /health`, usado pelo healthcheck do `docker-compose.yml` |
| `common/keycloak` | `KeycloakClientService` — cliente HTTP fino para a REST API do Keycloak (login via `password grant`, chamadas genéricas à Admin API) |
| `common/exceptions` + `common/filters` | `OAuthApiException` + `OAuthExceptionFilter` — normalizam qualquer erro (de negócio, de validação do Nest, ou do próprio Keycloak) no formato de erro do T1 |
| `common/guards` | `BearerTokenGuard` — valida a *presença/formato* do header `Authorization`, não o token em si |

## 2. Autenticação — `POST /login`

Autentica no realm `constrsw` via grant `password` (Direct Access Grant) e devolve o token do Keycloak.

- **Content-Type:** `multipart/form-data` (conforme enunciado do T1)
- **Body:** `username` (string, obrigatório), `password` (string, obrigatório)
- **Sucesso:** `201 Created`

```json
{
  "token_type": "Bearer",
  "access_token": "...",
  "expires_in": 600,
  "refresh_token": "...",
  "refresh_expires_in": 1800
}
```

- **Erros:**
  | Cenário | Status |
  |---|---|
  | `username`/`password` ausentes ou não-string | 400 |
  | Credenciais inválidas (usuário inexistente ou senha errada) | 401 |
  | Keycloak fora do ar / inacessível | 503 |

  > O endpoint de token do Keycloak segue o RFC 6749 e devolve HTTP 400 (`invalid_grant`) para credencial inválida — **não** 401. Como a estrutura da chamada já foi validada pelo `LoginDto` antes de chegar ao Keycloak, qualquer erro do Keycloak nesse ponto só pode ser credencial inválida; `AuthService` remapeia isso para 401 (exigido pelo enunciado do T1). 503 é preservado como está.

## 3. Usuários — `/users`

Todas as rotas exigem `Authorization: Bearer <access_token>` (`BearerTokenGuard` + repasse do token para a Admin API do Keycloak, ver 1.1).

`UserResponseDto`:
```json
{ "id": "uuid", "username": "string", "first-name": "string", "last-name": "string", "enabled": true }
```
> Campos usam nomes com hífen (`first-name`, `last-name`) propositalmente, para casar com os nomes de campo exigidos no enunciado.

| Método | Rota | Descrição | Body | Sucesso | Erros |
|---|---|---|---|---|---|
| `POST` | `/users` | Cria usuário (`username` é usado também como `email` no Keycloak) | `{ username, password, "first-name", "last-name" }` | 201 + `UserResponseDto` | 400 (validação/email inválido), 401, 403, 409 (username/email já existe) |
| `GET` | `/users` | Lista usuários; filtro opcional `?enabled=true\|false` | — | 200 + `UserResponseDto[]` | 401, 403 |
| `GET` | `/users/:id` | Busca por id | — | 200 + `UserResponseDto` | 401, 403, 404 |
| `PUT` | `/users/:id` | Atualiza atributos (`username`, `first-name`, `last-name`, `enabled` — todos opcionais, atualiza só o que vier) | `UpdateUserDto` | 200 (vazio) | 400, 401, 403, 404 |
| `PATCH` | `/users/:id` | Atualiza **somente a senha** (`PUT /users/:id/reset-password` no Keycloak) | `{ password }` | 200 (vazio) | 400, 401, 403, 404 |
| `DELETE` | `/users/:id` | Exclusão **lógica**: `PUT /users/:id { enabled: false }` no Keycloak — o registro **não** é removido | — | 204 | 401, 403, 404 |

Validação de e-mail: usa um regex prático (`EMAIL_REGEX`) como substituto do RFC 5322 citado no enunciado — decisão documentada porque o regex colado no enunciado original veio corrompido na cópia.

## 4. Roles — `/roles`

Mesma exigência de `Authorization: Bearer`. `RoleResponseDto`: `{ id, name, description? }`.

| Método | Rota | Descrição | Body | Sucesso | Erros |
|---|---|---|---|---|---|
| `POST` | `/roles` | Cria role no realm | `{ name, description? }` | 201 + `RoleResponseDto` | 400, 401, 403, 409 (nome já existe) |
| `GET` | `/roles` | Lista todos os roles do realm | — | 200 + `RoleResponseDto[]` | 401, 403 |
| `GET` | `/roles/:id` | Busca role por id | — | 200 + `RoleResponseDto` | 401, 403, 404 |
| `PUT` | `/roles/:id` | Atualiza role (completo — `name` obrigatório) | `{ name, description? }` | 200 (vazio) | 400, 401, 403, 404 |
| `PATCH` | `/roles/:id` | Atualiza role (parcial) | `{ name?, description? }` | 200 (vazio) | 400, 401, 403, 404 |
| `DELETE` | `/roles/:id` | Exclusão **real** do role | — | 204 | 401, 403, 404 |

> **Atenção ao nome da rota:** `/roles/:id` é a rota pública da nossa API. Internamente, o `RolesService` chama o endpoint `/admin/realms/{realm}/roles-by-id/{id}` da **Admin API do Keycloak** (é assim que o Keycloak expõe busca/edição de role por id) — isso é um detalhe de implementação, não afeta o contrato da nossa API.

> **Exclusão é real, não lógica:** diferente de usuário, o Keycloak não tem um flag de habilitado/desabilitado para roles. Por isso `DELETE /roles/:id` remove o role de fato do realm — decisão documentada também no `README.md` do submódulo.

## 5. Atribuição de role a usuário — `/users/:userId/roles/:roleId`

Controller separado (`UserRolesController`), mesma exigência de `Authorization: Bearer`.

| Método | Rota | Descrição | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/users/:userId/roles/:roleId` | Atribui o role ao usuário (`role-mappings/realm` no Keycloak) | 204 | 401, 403, 404 (usuário ou role inexistente) |
| `DELETE` | `/users/:userId/roles/:roleId` | Remove a atribuição | 204 | 401, 403, 404 |

## 6. Formato padronizado de erro

Todo erro (de negócio, de validação do Nest/`ValidationPipe`, ou não tratado) passa pelo `OAuthExceptionFilter` global e sai neste formato:

```json
{
  "error_code": "404",
  "error_description": "descrição legível do erro",
  "error_source": "OAuthAPI",
  "error_stack": [
    { "error_code": "404", "error_description": "...", "error_source": "Keycloak" }
  ]
}
```

- `error_code`: por padrão, o próprio código HTTP da resposta (como string). Pode ser sobrescrito por uma rota específica quando o enunciado exigir um código diferente do que o Keycloak devolveu (ex.: login remapeando 400→401, ver seção 2).
- `error_source`: origem do erro **final** — sempre `"OAuthAPI"` (a rota que respondeu), mesmo quando a causa raiz veio do Keycloak.
- `error_stack`: cadeia de erros que levou ao erro final. Quando o erro se origina no Keycloak, carrega o erro cru devolvido por ele (`error_source: "Keycloak"`); erros de validação da própria API carregam só a entrada da própria `OAuthAPI`.

Regra de guarda importante: `Authorization` ausente ou mal formado → **400** (erro de estrutura da chamada, validado antes de qualquer chamada ao Keycloak); token presente mas inválido/expirado → **401** (rejeitado pelo próprio Keycloak).

## 6.1 Observabilidade — métricas Prometheus

`src/common/telemetry/tracing.ts` inicializa o OpenTelemetry Node SDK **antes** de qualquer outro módulo ser importado (é o primeiro `import` de `main.ts` — obrigatório, é assim que a instrumentação de `http`/`express` consegue interceptar os módulos antes deles serem exigidos pelo resto da aplicação).

- **`HttpInstrumentation` + `ExpressInstrumentation`**: geram automaticamente o histograma `http_server_request_duration` (contagem + latência) para toda rota da API, sem precisar instrumentar cada controller na mão.
- **`PrometheusExporter`**: expõe essas métricas em `GET /metrics` (formato OpenMetrics), na porta `OAUTH_INTERNAL_METRICS_PORT` (padrão `9464`) — **servidor HTTP separado** do `main.ts` (`OAUTH_INTERNAL_API_PORT`/3001), scrapeado diretamente pelo Prometheus central (job `auth` → target `oauth:9464`).
- Resource attribute `service.name=oauth` identifica a origem das métricas no Prometheus/Grafana.

Não há push de métricas/traces para o `otel-collector` (serviço central em `docker-compose.yml`) — o exporter Prometheus é *pull-based* e já cobre o requisito.

**Sobre o hostname `auth` vs `oauth` no `prometheus.yml`:** o Prometheus, o OTel Collector e o Blackbox Exporter foram habilitados pelo professor direto no `main` do repo `base` (commit `e201543`, 2026-09-16) — não precisamos criar nada disso, só dar `git pull`. O `prometheus.yml` desse commit, porém, ainda aponta o job `auth` (e uma entrada do `health-checks`) pro hostname `auth`, que não existe na rede docker (nosso serviço se chama `oauth` desde 2026-09-02). Como o grupo não tem permissão de commit na `base`, a correção não foi feita lá — está em `backend/oauth/infra-local/prometheus.yml` (cópia local, só com o hostname trocado), aplicada por cima do arquivo oficial via `docker-compose.override.yml`. Ver [`infra-local/README.md`](./infra-local/README.md) para o comando completo e como conferir que pegou.

## 7. Configuração / variáveis de ambiente

Lidas via `ConfigModule` (`src/common/config/configuration.ts`):

| Variável | Uso | Default |
|---|---|---|
| `OAUTH_INTERNAL_API_PORT` (ou `PORT`) | Porta HTTP da API | `3001` |
| `KEYCLOAK_SERVER_URL` | Base URL do Keycloak (sem `/auth`, ex.: `http://keycloak:8080`) | `http://localhost:8080` |
| `KEYCLOAK_REALM` | Realm | `constrsw` |
| `KEYCLOAK_CLIENT_ID` | Client usado no grant `password` | `oauth` |
| `KEYCLOAK_CLIENT_SECRET` | Secret do client `oauth` | — |

No `docker-compose.yml` oficial (raiz do repo `base`), o serviço `oauth` recebe essas variáveis prontas do `.env` da raiz, mais `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` (não usadas diretamente pelo `oauth` hoje) e as portas internas/externas (`OAUTH_INTERNAL_*` / `OAUTH_EXTERNAL_*`).

## 8. Rodando localmente

```bash
# na raiz do repo base (T1)
docker volume create constrsw-keycloak-data
docker compose up -d --build
```

- API/Swagger: `http://localhost:8181` (Swagger em `/swagger`, healthcheck em `/health`)
- Console do Keycloak: `http://localhost:8081`

Usuários de teste do realm oficial (senha `a12345678` para todos): `admin@pucrs.br` (único com permissão de Admin REST API), `coordinator@pucrs.br`, `professor@pucrs.br`, `student@pucrs.br`.

Exemplo de fluxo via `curl`:

```bash
export API=http://localhost:8181
TOKEN=$(curl -s -X POST "$API/login" -F username=admin@pucrs.br -F password=a12345678 | jq -r .access_token)

curl -s "$API/users" -H "Authorization: Bearer $TOKEN"
curl -s -X POST "$API/users" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"username":"joao@pucrs.br","password":"trocarDepois123","first-name":"Joao","last-name":"Silva"}'
```

## 9. Testes

### 9.1 Testes unitários (`src/**/*.spec.ts`)

Testam cada classe isoladamente (services, guard, filter, cliente HTTP do Keycloak), com `KeycloakClientService`/`HttpService` mockados — não precisam de nada rodando, são rápidos.

```bash
npm test          # roda uma vez
npm run test:watch
npm run test:cov  # com relatorio de cobertura
```

Cobrem: `AuthService` (login com sucesso, remapeamento 400→401 do Keycloak, preservação do 503, erros inesperados repassados), `UsersService` (payload de criação, extração do id via header `Location`, filtro `enabled`, mapeamento de campos, `PUT` parcial, `PATCH` de senha via `reset-password`, exclusão lógica), `RolesService` (criação por nome + busca do id gerado, `GET`/`PUT`/`PATCH` via `/roles-by-id/:id` preservando campos não informados, exclusão real, atribuição/remoção de role via `role-mappings/realm`), `BearerTokenGuard` (400 para header ausente/malformado/vazio) e `OAuthExceptionFilter` (normalização de `OAuthApiException`, `HttpException` padrão do Nest, mensagens de validação em array, e erro não tratado → 500), além do mapeamento de erros do `KeycloakClientService` (rede → 503, erro HTTP → status/descrição preservados).

### 9.2 Testes de integração (`test/*.e2e-spec.ts`)

Sobem a aplicação Nest de verdade (mesmos guards/pipes/filtros globais do `main.ts`) e batem via HTTP real contra ela, que por sua vez fala com o **Keycloak de verdade** — nada é mockado aqui, ao contrário dos unitários.

**Pré-requisito:** `docker compose up -d --build` rodando na raiz do repo base (`T1`), com o Keycloak saudável e o realm `constrsw` oficial importado.

```bash
npm run test:e2e
```

Cobrem, contra o realm real: login (válido/inválido/campos faltando, para os 4 usuários de teste), CRUD completo de usuários (criar, duplicado, e-mail inválido, listar com/sem filtro, buscar, `PUT`, `PATCH` de senha, exclusão lógica e confirmação, 403 com usuário sem permissão), CRUD completo de roles (criar, duplicado, buscar, `PUT`/`PATCH`, exclusão real e confirmação), e atribuição/remoção de role em usuário (incluindo 404 para usuário/role inexistente). Cada suíte cria dados próprios com nome único e limpa depois (`afterAll`), pra não sujar o realm entre execuções.

> Os arquivos ficam em `test/`, fora de `src/`, e a config de unit tests (`jest` em `package.json`) ignora esse diretório — então `npm test` nunca tenta rodar os de integração (que precisam do Keycloak de pé), e vice-versa.

Toda essa cobertura (unitária + integração) foi validada rodando localmente contra o ambiente oficial do professor, sem divergências em relação ao comportamento descrito neste documento.
