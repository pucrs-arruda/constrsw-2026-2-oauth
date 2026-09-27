# oauth — API de Autenticação e Usuários (ConstrSW 2026/2 · Grupo 04)

API REST em **Spring Boot 3.3 / Java 21**, organizada em **Clean Architecture**, que
encapsula a REST API do **Keycloak 26** para:

- **autenticação** de usuários (`POST /login`);
- **gestão de usuários** (CRUD em `/users`, com exclusão lógica);
- **gestão de roles** (CRUD em `/roles`, com exclusão lógica) e **atribuição de roles a usuários**.

Tudo sobe com um único `docker compose up` na raiz do repositório `base`, junto com o
Keycloak, o **Prometheus** e o **Grafana**.

---

## Sumário

1. [Início rápido](#1-início-rápido)
2. [URLs do ambiente](#2-urls-do-ambiente)
3. [Documentação Swagger](#3-documentação-swagger)
4. [Usuários de teste](#4-usuários-de-teste)
5. [Rotas da API](#5-rotas-da-api)
6. [Exemplos de uso](#6-exemplos-de-uso)
7. [Payloads e validações](#7-payloads-e-validações)
8. [Tratamento de erros](#8-tratamento-de-erros)
9. [Autenticação e autorização](#9-autenticação-e-autorização)
10. [Arquitetura de software](#10-arquitetura-de-software)
11. [Observabilidade: Prometheus e Grafana](#11-observabilidade-prometheus-e-grafana)
12. [Testes](#12-testes)
13. [Configuração](#13-configuração)
14. [Execução fora do docker compose](#14-execução-fora-do-docker-compose)
15. [Solução de problemas](#15-solução-de-problemas)

---

## 1. Início rápido

**Pré-requisito:** Docker Desktop (ou Docker Engine + Compose v2) em execução. Java e Maven
**não** são necessários: a aplicação é compilada dentro do `Dockerfile`.

Na **raiz do repositório `base`**:

```bash
# 1. Apenas na primeira vez: volume onde o Keycloak persiste seus dados
docker volume create constrsw-keycloak-data

# 2. Sobe Keycloak, oauth, Prometheus e Grafana
docker compose up -d --build

# 3. Aguarde todos ficarem "healthy" (o Keycloak leva ~1 min na primeira subida)
docker compose ps
```

Pronto: abra o Swagger em **http://localhost:8181/swagger-ui/index.html**.

Para derrubar tudo: `docker compose down`. Os dados do Keycloak continuam no volume.

---

## 2. URLs do ambiente

| Serviço                         | URL                                          | Observação                                  |
| ------------------------------- | -------------------------------------------- | ------------------------------------------- |
| **Swagger UI**                  | http://localhost:8181/swagger-ui/index.html  | documentação interativa da API             |
| OpenAPI (JSON)                  | http://localhost:8181/v3/api-docs            | especificação consumível por ferramentas    |
| **API oauth** (base URL)        | http://localhost:8181                        | `{{base-api-url}}`                          |
| Health da API                   | http://localhost:8381/actuator/health        | porta de gerenciamento                      |
| Métricas da API                 | http://localhost:8381/actuator/prometheus    | formato Prometheus, sem autenticação        |
| **Grafana**                     | http://localhost:3000                        | dashboard abre direto; edição: `admin` / `a12345678` |
| **Prometheus**                  | http://localhost:9090                        | `/targets`, `/alerts`, `/graph`             |
| Keycloak Admin Console          | http://localhost:8081                        | `admin` / `a12345678`                       |
| Keycloak health / métricas      | http://localhost:9001/health · `/metrics`    | management interface do Keycloak            |

As portas externas vêm do arquivo `.env` da raiz (seção [13. Configuração](#13-configuração)).

---

## 3. Documentação Swagger

- **URL:** http://localhost:8181/swagger-ui/index.html (`/swagger-ui.html` redireciona para ela)
- Todas as rotas estão documentadas, com os schemas de request/response e os códigos HTTP
  possíveis, incluindo o envelope de erro.

**Como testar rotas protegidas pelo Swagger:**

1. Execute `POST /login` com `username = admin@pucrs.br` e `password = a12345678`.
2. Copie o valor de `access_token` da resposta.
3. Clique em **Authorize** (cadeado, canto superior direito), cole o token (sem o prefixo
   `Bearer`) e confirme.
4. As demais rotas passam a enviar `Authorization: Bearer <token>`.

> O access token do Keycloak expira em poucos minutos (`expires_in`). Se começar a receber
> `401`, faça login de novo.

---

## 4. Usuários de teste

O realm `constrsw` é importado automaticamente na primeira subida do Keycloak. Todos os
usuários têm a senha **`a12345678`**:

| Usuário                | Realm role      | Pode administrar usuários/roles pela API? |
| ---------------------- | --------------- | ----------------------------------------- |
| `admin@pucrs.br`       | `administrator` | **Sim**                                   |
| `coordinator@pucrs.br` | `coordinator`   | Não (`403`)                               |
| `professor@pucrs.br`   | `professor`     | Não (`403`)                               |
| `student@pucrs.br`     | `student`       | Não (`403`)                               |

O motivo das respostas `403` está explicado em [9. Autenticação e autorização](#9-autenticação-e-autorização).

---

## 5. Rotas da API

Todas as rotas, exceto `POST /login`, exigem o header `Authorization: Bearer {{access_token}}`.

### Autenticação

| Método | Rota     | Descrição                         | Sucesso | Erros           | Rota do Keycloak consumida                                   |
| ------ | -------- | --------------------------------- | ------- | --------------- | ------------------------------------------------------------ |
| POST   | `/login` | autentica e devolve os tokens     | `201`   | `400` `401`     | `POST /realms/{realm}/protocol/openid-connect/token` (`grant_type=password`) |

### Users

| Método | Rota                       | Descrição                                   | Sucesso | Erros                     | Rota do Keycloak consumida                           |
| ------ | -------------------------- | ------------------------------------------- | ------- | ------------------------- | ---------------------------------------------------- |
| POST   | `/users`                   | cria usuário                                | `201`   | `400` `401` `403` `409`   | `POST /admin/realms/{realm}/users` (id lido do header `Location`) |
| GET    | `/users`                   | lista usuários                              | `200`   | `400` `401` `403`         | `GET /admin/realms/{realm}/users`                    |
| GET    | `/users?enabled=true\|false` | lista filtrando por habilitado            | `200`   | `400` `401` `403`         | `GET /admin/realms/{realm}/users?enabled=...`        |
| GET    | `/users/{id}`              | busca usuário por id                        | `200`   | `400` `401` `403` `404`   | `GET /admin/realms/{realm}/users/{id}`               |
| PUT    | `/users/{id}`              | atualiza atributos do usuário               | `200`   | `400` `401` `403` `404`   | `PUT /admin/realms/{realm}/users/{id}`               |
| PATCH  | `/users/{id}`              | troca a senha                               | `200`   | `400` `401` `403` `404`   | `PUT /admin/realms/{realm}/users/{id}/reset-password` |
| DELETE | `/users/{id}`              | **exclusão lógica** (`enabled=false`)       | `204`   | `400` `401` `403` `404`   | `PUT /admin/realms/{realm}/users/{id}` com `enabled=false` |

> **`PATCH /users/{id}` (senha):** o enunciado pede para consumir "a rota do Keycloak que
> atualiza um usuário (método PATCH)", mas a Admin REST API do Keycloak não tem método PATCH.
> A troca de senha usa a rota dedicada do Keycloak para isso,
> `PUT /users/{id}/reset-password`, com uma credencial do tipo `password` e
> `temporary=false`.

### Roles

| Método | Rota                               | Descrição                                   | Sucesso | Erros                     | Rota do Keycloak consumida                          |
| ------ | ---------------------------------- | ------------------------------------------- | ------- | ------------------------- | --------------------------------------------------- |
| POST   | `/roles`                           | cria role                                   | `201`   | `400` `401` `403` `409`   | `POST /admin/realms/{realm}/roles` + `GET .../roles/{name}` |
| GET    | `/roles`                           | lista roles (aceita `?enabled=true\|false`) | `200`   | `400` `401` `403`         | `GET /admin/realms/{realm}/roles?briefRepresentation=false` |
| GET    | `/roles/{id}`                      | busca role por id                           | `200`   | `400` `401` `403` `404`   | `GET /admin/realms/{realm}/roles-by-id/{id}`        |
| PUT    | `/roles/{id}`                      | **substitui** a role inteira                | `200`   | `400` `401` `403` `404` `409` | `GET` + `PUT /admin/realms/{realm}/roles-by-id/{id}` |
| PATCH  | `/roles/{id}`                      | atualização parcial (só os campos enviados) | `200`   | `400` `401` `403` `404` `409` | `GET` + `PUT /admin/realms/{realm}/roles-by-id/{id}` (merge) |
| DELETE | `/roles/{id}`                      | **exclusão lógica** (`enabled=false`)       | `204`   | `400` `401` `403` `404`   | `PUT /admin/realms/{realm}/roles-by-id/{id}`        |
| POST   | `/roles/{roleId}/users/{userId}`   | **atribui** o role ao usuário               | `204`   | `401` `403` `404`         | `POST /admin/realms/{realm}/users/{id}/role-mappings/realm` |
| DELETE | `/roles/{roleId}/users/{userId}`   | **remove** a atribuição                     | `204`   | `401` `403` `404`         | `DELETE /admin/realms/{realm}/users/{id}/role-mappings/realm` |

> **Exclusão lógica de roles:** o Keycloak não tem um campo `enabled` para roles. A API grava
> o atributo customizado `attributes.enabled = ["false"]` na role e o expõe como o campo
> `enabled` nas respostas. A role continua existindo no Keycloak.

> **`PUT` × `PATCH` de roles:**
> - `PUT` **substitui** a role: `name` é obrigatório, `description` ausente é apagada e
>   `enabled` ausente volta a `true`, como numa criação.
> - `PATCH` altera **só os campos enviados**; os demais mantêm o valor atual.
>
> Nos dois casos a API lê a role no Keycloak antes de gravar e reenvia todos os
> `attributes` que já existem, trocando só `enabled`. Isso é necessário porque o `PUT` do
> Keycloak substitui o mapa de atributos inteiro, e atributos criados por outros sistemas
> seriam apagados.

> **Prefixo `/auth`:** o enunciado cita `{{base-keycloak-url}}/auth/realms/...`. O Keycloak
> removeu o context-path `/auth` a partir da versão 17, e este projeto usa a v26. Por isso as
> URLs são `/realms/...` e `/admin/realms/...`. É só um ajuste de versão, não um desvio do
> enunciado.

---

## 6. Exemplos de uso

### Bash / Git Bash (`curl` + `jq`)

```bash
API=http://localhost:8181

# Login (form-data) -> 201
TOKEN=$(curl -s -X POST $API/login \
  -F username=admin@pucrs.br -F password=a12345678 | jq -r .access_token)
AUTH="Authorization: Bearer $TOKEN"

# Criar usuário -> 201
USER_ID=$(curl -s -X POST $API/users -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"username":"aluno@pucrs.br","password":"senha123","first-name":"Aluno","last-name":"Teste"}' \
  | jq -r .id)

# Listar (todos / só habilitados) e buscar por id -> 200
curl -s $API/users -H "$AUTH"
curl -s "$API/users?enabled=true" -H "$AUTH"
curl -s $API/users/$USER_ID -H "$AUTH"

# Atualizar atributos -> 200
curl -s -X PUT $API/users/$USER_ID -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"first-name":"Aluno","last-name":"Atualizado"}'

# Trocar senha -> 200
curl -s -X PATCH $API/users/$USER_ID -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"password":"nova-senha"}'

# Criar role e atribuir ao usuário -> 201 / 204
ROLE_ID=$(curl -s -X POST $API/roles -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"monitor","description":"Monitor de disciplina"}' | jq -r .id)
curl -s -X POST   $API/roles/$ROLE_ID/users/$USER_ID -H "$AUTH"
curl -s -X DELETE $API/roles/$ROLE_ID/users/$USER_ID -H "$AUTH"

# Atualização parcial da role -> 200
curl -s -X PATCH $API/roles/$ROLE_ID -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"description":"Nova descrição"}'

# Exclusões lógicas -> 204
curl -s -X DELETE $API/roles/$ROLE_ID -H "$AUTH"
curl -s -X DELETE $API/users/$USER_ID -H "$AUTH"
```

### PowerShell (Windows)

```powershell
$api = "http://localhost:8181"

# Login (form-data): no Windows PowerShell 5.1, use curl.exe
$token = (curl.exe -s -X POST "$api/login" -F "username=admin@pucrs.br" -F "password=a12345678" |
          ConvertFrom-Json).access_token
$h = @{ Authorization = "Bearer $token" }

# Criar usuário
$body = @{ username = "aluno@pucrs.br"; password = "senha123"
           "first-name" = "Aluno"; "last-name" = "Teste" } | ConvertTo-Json
$user = Invoke-RestMethod -Method Post -Uri "$api/users" -Headers $h `
        -ContentType "application/json" -Body $body

# Listar e buscar
Invoke-RestMethod -Uri "$api/users?enabled=true" -Headers $h
Invoke-RestMethod -Uri "$api/users/$($user.id)" -Headers $h

# Exclusão lógica
Invoke-RestMethod -Method Delete -Uri "$api/users/$($user.id)" -Headers $h
```

### Exemplo de resposta de `POST /login`

```json
{
  "token_type": "Bearer",
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIi...",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJIUzUxMiIsInR5cCIgOiAiSldUIi...",
  "refresh_expires_in": 1800
}
```

---

## 7. Payloads e validações

Os campos de nome usam **kebab-case** (`first-name`, `last-name`), como no enunciado. Se
enviar `firstName`/`lastName` em camelCase, esses campos são **ignorados**.

| Rota                 | Content-Type          | Body                                                                 | Obrigatórios                            |
| -------------------- | --------------------- | -------------------------------------------------------------------- | --------------------------------------- |
| `POST /login`        | `multipart/form-data` (também aceita `application/x-www-form-urlencoded`) | `username`, `password` | ambos                        |
| `POST /users`        | `application/json`    | `username` (e-mail), `password`, `first-name`, `last-name`           | `username`, `password`                  |
| `PUT /users/{id}`    | `application/json`    | qualquer um de `username`, `first-name`, `last-name`, `enabled`      | ao menos um campo                       |
| `PATCH /users/{id}`  | `application/json`    | `password`                                                           | `password` (não vazio)                  |
| `POST /roles`        | `application/json`    | `name`, `description`                                                | `name`                                  |
| `PUT /roles/{id}`    | `application/json`    | `name`, `description`, `enabled` (substitui a role)                  | `name`                                  |
| `PATCH /roles/{id}`  | `application/json`    | qualquer um de `name`, `description`, `enabled`                      | ao menos um campo                       |

**Resposta de usuário** (`POST /users`, `GET /users`, `GET /users/{id}`):

```json
{ "id": "7c1e…", "username": "aluno@pucrs.br", "first-name": "Aluno", "last-name": "Teste", "enabled": true }
```

**Resposta de role:**

```json
{ "id": "a3f0…", "name": "monitor", "description": "Monitor de disciplina", "enabled": true }
```

**Regras de negócio aplicadas antes de chamar o Keycloak** (camada `application`):

- `username` precisa ser um e-mail válido pela **regex RFC 5322 do enunciado** no `POST` e
  no `PUT` de usuários. Caso contrário, a resposta é `400`. A validação fica em
  `domain/util/EmailValidator`, com casos válidos e inválidos em `EmailValidatorTest`.
  O texto do enunciado tem dois erros de cópia em relação à regex original, corrigidos no
  código e documentados no Javadoc da classe:
  - `(\\(\t -~])` passou a ser `(\\[\t -~])`;
  - `\([\t -Z^-~]*]` passou a ser `\[[\t -Z^-~]*]`.

  Exemplos:
  - aceitos: `aluno@pucrs.br`, `nome+tag@example.com`, `"joao silva"@example.com`, `user@[192.168.0.1]`;
  - recusados: `nao-eh-email`, `.aluno@pucrs.br`, `alu..no@pucrs.br`, `joão@pucrs.br`.
- O `username` também é gravado como `email` no Keycloak (`username = e-mail`).
- `PUT` sem nenhum campo e `PATCH` de senha vazia retornam `400`.
- O `DELETE` de usuários e de roles **nunca apaga** registros: só marca `enabled=false`.
  Um usuário desabilitado não consegue mais fazer login (`401`).

---

## 8. Tratamento de erros

**Todas** as respostas de erro usam o envelope do enunciado. Isso vale para validação,
token ausente ou inválido, falta de permissão, erro do Keycloak e até exceções não tratadas:

```json
{
  "error_code": "409",
  "error_description": "Username already exists: aluno@pucrs.br",
  "error_source": "OAuthAPI",
  "error_stack": [
    {
      "source": "Keycloak",
      "type": "UpstreamErrorException",
      "message": "HTTP 409 - User exists with same username"
    },
    {
      "source": "OAuthAPI",
      "type": "UserAlreadyExistsException",
      "message": "Username already exists: aluno@pucrs.br"
    }
  ]
}
```

| Campo               | Conteúdo                                                                    |
| ------------------- | --------------------------------------------------------------------------- |
| `error_code`        | código HTTP da resposta; repassa o código devolvido pelo Keycloak           |
| `error_description` | descrição do erro escrita pelo grupo                                        |
| `error_source`      | origem do erro final: `OAuthAPI`                                            |
| `error_stack`       | pilha de todos os erros até o erro final, **na ordem em que ocorreram**. Cada item tem `{ source, type, message }` |

**Como a pilha é montada:** quando o erro nasce no Keycloak, o gateway preserva a resposta
original (status e mensagem) como uma `UpstreamErrorException`, com `source: "Keycloak"`, e a
anexa como *causa* da exceção de domínio que a traduz. O `ApiExceptionHandler` percorre essa
cadeia de causas e monta o `error_stack` do erro de origem até o erro final. Erros que nascem
na própria API, como uma validação, geram uma pilha com um único item (`source: "OAuthAPI"`).

Mapeamento das exceções de domínio para códigos HTTP (`ApiExceptionHandler`):

| Situação                                              | Exceção                                   | HTTP  |
| ----------------------------------------------------- | ----------------------------------------- | ----- |
| body/parâmetro malformado, campo obrigatório ausente  | `InvalidInputException`, erros de binding | `400` |
| e-mail inválido                                       | `InvalidEmailException`                   | `400` |
| usuário/senha inválidos no login, usuário desabilitado | `InvalidCredentialsException`            | `401` |
| token ausente, inválido ou expirado                   | `AuthorizationRequiredException`          | `401` |
| token válido sem permissão                            | `AccessDeniedException`                   | `403` |
| usuário / role / rota inexistente                     | `UserNotFoundException`, `RoleNotFoundException`, `NoResourceFoundException` | `404` |
| username / nome de role já existente                  | `UserAlreadyExistsException`, `RoleAlreadyExistsException` | `409` |
| Keycloak fora do ar ou resposta inesperada            | `IdentityProviderUnavailableException`    | `503` |
| erro não previsto                                     | `Exception`                               | `500` |

---

## 9. Autenticação e autorização

A autorização acontece em duas camadas:

1. **Na API (Spring Security, Resource Server JWT):** toda rota protegida exige um JWT
   válido, **assinado pelo Keycloak do realm `constrsw`**. O issuer é
   `spring.security.oauth2.resourceserver.jwt.issuer-uri`, e a assinatura é conferida pelo
   JWKS do Keycloak. Um token ausente, malformado ou expirado resulta em `401` antes de
   chegar ao controller.
2. **No Keycloak (Admin REST API):** a API **repassa o token do próprio chamador** nas chamadas
   à Admin API. Assim, quem decide se o usuário pode criar, listar ou alterar usuários e roles
   é o Keycloak, com base nas permissões `realm-management` do usuário. `admin@pucrs.br` tem
   essas permissões. `student@pucrs.br`, por exemplo, não tem, e a resposta é `403`.

Rotas públicas (sem token): `POST /login`, `/swagger-ui/**`, `/v3/api-docs/**`,
`/actuator/health` e `/actuator/prometheus`.

---

## 10. Arquitetura de software

### 10.1 Visão geral

O projeto segue a **Clean Architecture** (no estilo Ports & Adapters). O código está dividido
em três camadas concêntricas, e a **regra de dependência aponta sempre para dentro**:

```
┌──────────────────────────────────────────────────────────────────────┐
│ infrastructure/   (frameworks, HTTP, Keycloak, config)               │
│                                                                      │
│   adapter/in/rest  ──chama──►  port/in  (casos de uso)               │
│   adapter/out/keycloak ◄─implementa─  port/out (gateways)            │
│                                                                      │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │ application/   (casos de uso + portas)                       │   │
│   │                                                              │   │
│   │   port/in   : LoginUseCase, CreateUserUseCase, ...           │   │
│   │   port/out  : AuthGateway, UserGateway, RoleGateway          │   │
│   │   usecase   : LoginService, CreateUserService, ...           │   │
│   │                                                              │   │
│   │   ┌──────────────────────────────────────────────────────┐   │   │
│   │   │ domain/   (Java puro: sem Spring, HTTP ou Keycloak)  │   │   │
│   │   │                                                      │   │   │
│   │   │   model     : User, Role, AuthTokens, Credentials,   │   │   │
│   │   │               NewUser, UserUpdate, NewRole, RoleUpdate│  │   │
│   │   │   exception : DomainException e subclasses           │   │   │
│   │   │   util      : EmailValidator                         │   │   │
│   │   └──────────────────────────────────────────────────────┘   │   │
│   └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

| Camada           | Pode depender de                 | Responsabilidade                                                                 |
| ---------------- | -------------------------------- | -------------------------------------------------------------------------------- |
| `domain`         | nada                             | entidades, value objects, exceções e regras puras (validação de e-mail)          |
| `application`    | `domain`                         | casos de uso (orquestração + regras de aplicação) e as **portas** (interfaces). Java puro, sem Spring |
| `infrastructure` | `application` e `domain`         | adaptadores de entrada (REST) e saída (Keycloak), segurança, configuração        |

### 10.2 Estrutura de diretórios

```
src/main/java/br/pucrs/constrsw/oauth/
├── OauthApplication.java
├── domain/
│   ├── model/           User, Role, AuthTokens, Credentials, NewUser, UserUpdate, NewRole, RoleUpdate
│   ├── exception/       DomainException, InvalidEmail/InvalidInput/InvalidCredentials,
│   │                    AuthorizationRequired, AccessDenied, UserNotFound, UserAlreadyExists,
│   │                    RoleNotFound, RoleAlreadyExists, IdentityProviderUnavailable,
│   │                    UpstreamError (erro original de um sistema externo, vira item do error_stack)
│   └── util/            EmailValidator
├── application/
│   ├── port/in/         um *UseCase por operação (Login, CreateUser, ListUsers, GetUser,
│   │                    UpdateUser, UpdatePassword, DisableUser, CreateRole, ListRoles, GetRole,
│   │                    ReplaceRole, UpdateRole, DeleteRole, AttachRoleToUser, DetachRoleFromUser)
│   ├── port/out/        AuthGateway, UserGateway, RoleGateway
│   └── usecase/         um *Service por caso de uso (implementa a porta de entrada, sem anotações)
└── infrastructure/
    ├── adapter/in/rest/ AuthRestController, UserRestController, RoleRestController,
    │   │                ApiExceptionHandler
    │   └── dto/         DTOs HTTP com toDomain()/fromDomain() e o envelope de erro
    ├── adapter/out/keycloak/
    │                    KeycloakAuthGateway, KeycloakUserGateway, KeycloakRoleGateway
    ├── config/          UseCaseConfig (registra os casos de uso como beans), KeycloakProperties,
    │                    HttpClientConfig, SecurityConfig, OpenApiConfig
    └── security/        CustomAuthenticationEntryPoint (401), CustomAccessDeniedHandler (403)
```

### 10.3 Fluxo de uma requisição (`POST /users`)

```
Cliente ──HTTP──► Spring Security (valida JWT)                     infrastructure
                   │
                   ▼
                  UserRestController   DTO ──toDomain()──► NewUser   infrastructure/adapter/in
                   │ createUser.execute(bearer, newUser)
                   ▼
                  CreateUserService    valida e-mail (EmailValidator) application/usecase
                   │ userGateway.create(bearer, newUser)
                   ▼
                  «UserGateway»        interface                      application/port/out
                   │ (implementação injetada via UseCaseConfig)
                   ▼
                  KeycloakUserGateway  POST /admin/realms/constrsw/users  infrastructure/adapter/out
                   │                   lê o id do header Location
                   ▼                   traduz 400/401/403/409 → exceções de domínio
                  Keycloak Admin REST API
```

O caminho de volta é o inverso: o gateway devolve um `User` de domínio, e o controller o
converte em `UserResponseDto` com status `201`. Se algo falha, uma exceção de domínio sobe até
o `ApiExceptionHandler`, que monta o envelope de erro.

### 10.4 Decisões e justificativas

- **Por que Clean Architecture:**
  - **Testabilidade:** os casos de uso são testados com gateways *mockados*, sem Spring e
    sem Keycloak.
  - **Substituibilidade:** o Keycloak é um detalhe de infraestrutura. Para trocá-lo por
    outro provedor de identidade (Auth0, Okta…), basta escrever outro `*Gateway` em
    `adapter/out`; nada muda em `domain` ou `application`.
  - **Legibilidade:** o que a API faz está em `application/usecase`; o como (HTTP,
    Keycloak) fica isolado em `infrastructure`.
- **`domain` e `application` sem framework:** nenhuma classe dessas camadas importa Spring,
  nem mesmo `@Service`. Os casos de uso são criados em `infrastructure/config/UseCaseConfig`
  (o *composition root*), que liga cada `*Service` ao seu gateway. Assim a regra de
  dependência vale também para o framework: trocar Spring por outro container de injeção
  mexeria só em `infrastructure`.
- **Portas de entrada por caso de uso** (uma interface por operação): os controllers dependem
  só do que usam, e cada caso de uso tem uma responsabilidade única.
- **Token do chamador repassado ao Keycloak**, em vez de uma conta de serviço: a
  autorização fica centralizada no Keycloak, e o `403` reflete as permissões reais do
  usuário que está chamando.
- **`RestTemplate` com error handler "no-op"**: os gateways leem o status devolvido pelo
  Keycloak e o traduzem em exceções de domínio. Nenhuma exceção HTTP do Spring vaza para as
  camadas internas.
- **Exclusão lógica** em usuários (`enabled=false`, nativo do Keycloak) e em roles
  (`attributes.enabled`, porque roles não têm esse campo nativo).
- **DTOs só na infraestrutura**: o formato HTTP (kebab-case, `snake_case` do login) não
  contamina o domínio.

### 10.5 Stack

Java 21 · Spring Boot 3.3 (Web, Security, OAuth2 Resource Server, Validation, Actuator) ·
springdoc-openapi 2.6 · Micrometer + Prometheus · Micrometer Tracing (OpenTelemetry) ·
Apache HttpClient 5 · JUnit 5, Mockito, MockMvc, MockRestServiceServer · Docker multi-stage
(Maven → `eclipse-temurin:21-jre-alpine`).

---

## 11. Observabilidade: Prometheus e Grafana

```
 ┌──────────┐ /actuator/prometheus (:9464) ┌────────────┐          ┌─────────┐
 │  oauth   │◄──────────────────────────── │            │  PromQL  │         │
 └──────────┘                              │ Prometheus │◄──────── │ Grafana │
 ┌──────────┐ /metrics (:9001)             │  (:9090)   │          │ (:3000) │
 │ Keycloak │◄──────────────────────────── │            │          │         │
 └──────────┘                              └────────────┘          └─────────┘
```

### 11.1 O que é coletado

| Job          | Alvo (na rede do compose)                | O que responde                                                |
| ------------ | ---------------------------------------- | ------------------------------------------------------------- |
| `oauth`      | `oauth:9464/actuator/prometheus`         | requisições HTTP por rota/método/status (com histograma de latência), JVM, CPU |
| `keycloak`   | `keycloak:9001/metrics`                  | requisições HTTP do Keycloak por rota, JVM, pool de conexões  |
| `prometheus` | `localhost:9090`                         | o próprio Prometheus                                          |

A métrica **`up{job="..."}`** vale `1` quando o Prometheus consegue coletar o serviço e `0`
quando não consegue. É assim que se sabe se a API e o Keycloak **estão no ar**.

### 11.2 Dashboard do Grafana

Abra **http://localhost:3000**. O dashboard **"ConstrSW - OAuth API e Keycloak"** é a página
inicial e já vem provisionado (pasta *ConstrSW*). O acesso anônimo é somente leitura; para
editar, entre com `admin` / `a12345678`. Ele se atualiza a cada 10 s:

| Seção              | Painéis                                                                                                   |
| ------------------ | --------------------------------------------------------------------------------------------------------- |
| **Disponibilidade** | API no ar / fora do ar · Keycloak no ar / fora do ar · % de disponibilidade no período (API e Keycloak) · histórico de quedas |
| **Acessos**        | total de acessos na API no período · requisições/s por rota · respostas por status HTTP · ranking de acessos por rota · requisições/s no Keycloak |
| **Latência**       | latência média da API · p50/p95/p99 da API · latência média por rota (API e Keycloak) · memória heap das JVMs |

As rotas `/actuator/*` são excluídas dos gráficos da API para que as coletas do Prometheus
não distorçam os números.

### 11.3 Alertas (Prometheus)

Definidos em `infrastructure/dev.local/services/prometheus/alert.rules.yml` e visíveis em
http://localhost:9090/alerts:

| Alerta                  | Condição                                    |
| ----------------------- | ------------------------------------------- |
| `OAuthApiDown`          | `up{job="oauth"} == 0` por 30 s             |
| `KeycloakDown`          | `up{job="keycloak"} == 0` por 30 s          |
| `OAuthApiHighLatency`   | latência p95 da API > 1 s por 1 min         |
| `OAuthApiServerErrors`  | qualquer resposta 5xx na API por 1 min      |

### 11.4 Consultas úteis (em http://localhost:9090/graph)

```promql
# Serviços no ar (1) / fora do ar (0)
up{job=~"oauth|keycloak"}

# Acessos por rota nos últimos 15 minutos
sum by (method, uri) (increase(http_server_requests_seconds_count{job="oauth", uri!~"/actuator.*"}[15m]))

# Latência p95 da API
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="oauth", uri!~"/actuator.*"}[5m])))

# Latência média por rota do Keycloak
sum by (uri) (rate(http_server_requests_seconds_sum{job="keycloak"}[5m]))
  / sum by (uri) (rate(http_server_requests_seconds_count{job="keycloak"}[5m]))
```

### 11.5 Como está configurado

- **API:**
  - no container, o Actuator roda numa porta de gerenciamento separada
    (`management.server.port = OAUTH_INTERNAL_METRICS_PORT = 9464`), exposta como `8381`;
  - o histograma `http.server.requests` está habilitado (necessário para os percentis);
  - `/actuator/prometheus` é público na `SecurityConfig`.
- **Keycloak:** `KC_METRICS_ENABLED=true` e `KC_HEALTH_ENABLED=true` na management interface
  (`KC_HTTP_MANAGEMENT_PORT=9001`).
- **Arquivos:**
  - `infrastructure/dev.local/services/prometheus/`: `prometheus.yml` e `alert.rules.yml`;
  - `infrastructure/dev.local/services/grafana/provisioning/`: datasource e provider de dashboards;
  - `infrastructure/dev.local/services/grafana/dashboards/oauth-overview.json`: o dashboard.
- **Traces (opcional, desligado por padrão):** com `OTEL_TRACING_ENABLED=true`, a aplicação
  exporta traces via OTLP para `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` (padrão
  `http://localhost:4318/v1/traces`). Fica desligado porque o compose não tem um collector
  OpenTelemetry, e sem ele o exporter registraria erros no log a cada envio.

> Limitação: o Keycloak 26.0.1 não publica histograma de latência, então para ele o dashboard
> mostra a latência **média**. Os percentis (p50/p95/p99) existem só para a API.

---

## 12. Testes

### 12.1 Camadas de teste

| Tipo                  | Classe                                  | O que cobre                                                                     | Precisa da stack? |
| --------------------- | --------------------------------------- | ------------------------------------------------------------------------------- | ----------------- |
| Unitário              | `UseCaseServicesTest`                   | regras dos casos de uso (validação de e-mail, campos obrigatórios) com gateways mockados | não     |
| Unitário              | `EmailValidatorTest`                    | regex RFC 5322: e-mails válidos (inclusive com aspas e domínio literal) e inválidos | não |
| Integração            | `RestControllersIntegrationTest`        | contrato HTTP dos controllers (MockMvc): status, JSON, segurança, 404, `/actuator/prometheus` | não |
| Contrato              | `KeycloakGatewayContractTest`           | chamadas exatas à API do Keycloak (URL, método, headers, payload) com `MockRestServiceServer`, incluindo o merge do `PATCH`, a substituição do `PUT` e a preservação dos atributos de roles | não |
| **Fim a fim (E2E)**   | `e2e/OAuthApiE2ETest`                   | a API **real** falando com o Keycloak **real**: 28 cenários de login, users e roles | **sim** |

O teste **fim a fim** percorre o ciclo completo, verificando status e corpo em cada passo:

- **Login:**
  - `201` com os cinco campos de token, via `form-data` e via `x-www-form-urlencoded`;
  - `401` com senha errada;
  - `400` sem senha;
  - `401` sem token ou com token inválido.
- **Users:**
  - criar: `201`; `400` com e-mail inválido; `409` com duplicado (com o `error_stack` trazendo
    o erro do Keycloak e depois o da OAuthAPI); `403` com o usuário `student`;
  - listar com e sem `?enabled=`;
  - consultar: `200` por id e `404` com id inexistente;
  - `PUT`: `200` e `404`;
  - `PATCH` de senha: o login com a senha nova funciona e com a antiga dá `401`;
  - `DELETE` lógico: o usuário fica `enabled=false`, o login dele passa a dar `401`, e o `DELETE` com id inexistente dá `404`.
- **Roles:**
  - criar: `201`; `409` com duplicada; `400` sem nome;
  - listar e consultar: `200` e `404`;
  - `PATCH` (muda só a descrição) e `PUT` (substitui: sem `description`, a descrição é
    apagada), conferindo no Keycloak que um atributo customizado da role **não se perde**;
  - `PUT` sem `name`: `400`;
  - atribuir e remover de um usuário (conferindo direto no Keycloak);
  - `DELETE` lógico: a role aparece em `?enabled=false`.
- **Formato de erro:** em todos os erros o teste valida o envelope
  `error_code / error_description / error_source / error_stack`.

**Limpeza dos dados de teste:** todo dado criado pelo E2E usa o prefixo `e2e-`
(`e2e-xxxx@e2e.constrsw.test`, `e2e-role-xxxx`). Ao final, o teste **exclui fisicamente**
esses usuários e roles direto na Admin API do Keycloak, porque o `DELETE` da API é só lógico.
Sobras de execuções interrompidas também são removidas.

### 12.2 Script que roda tudo e limpa depois (recomendado)

Na **raiz do repositório `base`**:

```powershell
# Windows (PowerShell)
.\scripts\run-all-tests.ps1              # ou: .\scripts\run-all-tests.ps1 -KeepStack
```

```bash
# Linux / macOS / Git Bash
./scripts/run-all-tests.sh               # ou: ./scripts/run-all-tests.sh --keep-stack
```

O script:

1. roda os **testes isolados** (unitário + integração + contrato);
2. faz `docker compose up -d --build` (testa o código atual) e espera Keycloak e oauth ficarem `healthy`;
3. roda o **teste fim a fim** dentro da rede do compose;
4. **limpa**:
   - os dados de teste do Keycloak (apagados pelo próprio E2E);
   - a pasta `backend/oauth/target`;
   - a stack: se ela **não** estava no ar antes, faz `docker compose down`;
5. imprime um resumo `[OK]`/`[FALHA]` por etapa e termina com código `≠ 0` se algo falhar.

Java e Maven não precisam estar instalados: o Maven roda no container
`maven:3.9-eclipse-temurin-21`. O cache de dependências fica em `~/.m2` para acelerar as
próximas execuções.

### 12.3 Rodando manualmente (com Maven instalado)

```bash
cd backend/oauth
mvn test                 # testes isolados (o E2E é excluído por padrão)
mvn test -Pe2e           # só o E2E: exige a stack no ar (docker compose up)
```

Variáveis do E2E (os valores padrão servem para rodar do host com a stack local):

| Variável                   | Padrão                    |
| -------------------------- | ------------------------- |
| `E2E_BASE_URL`             | `http://localhost:8181`   |
| `E2E_KEYCLOAK_URL`         | `http://localhost:8081`   |
| `E2E_ADMIN_USER` / `E2E_ADMIN_PASSWORD` | `admin@pucrs.br` / `a12345678` |
| `E2E_UNPRIVILEGED_USER` / `E2E_UNPRIVILEGED_PASSWORD` | `student@pucrs.br` / `a12345678` |
| `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` | `admin` / `a12345678` (admin do realm `master`, usado na limpeza) |
| `KEYCLOAK_REALM`           | `constrsw`                |

---

## 13. Configuração

### 13.1 Variáveis lidas pela aplicação (`application.yml`)

| Variável                       | Descrição                                               | Padrão (fora do compose)  |
| ------------------------------ | ------------------------------------------------------- | ------------------------- |
| `OAUTH_INTERNAL_API_PORT`      | porta HTTP da API                                       | `8080`                    |
| `OAUTH_INTERNAL_METRICS_PORT`  | porta do Actuator (health + métricas)                   | mesma porta da API        |
| `KEYCLOAK_SERVER_URL`          | URL do Keycloak                                         | `http://localhost:8080`   |
| `KEYCLOAK_REALM`               | realm                                                   | `constrsw`                |
| `KEYCLOAK_CLIENT_ID`           | client confidencial usado no login                      | `oauth`                   |
| `KEYCLOAK_CLIENT_SECRET`       | secret do client                                        | *(obrigatório)*           |
| `OTEL_TRACING_ENABLED`         | liga a exportação de traces OTLP                        | `false`                   |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | destino dos traces OTLP                           | `http://localhost:4318/v1/traces` |
| `OTEL_TRACES_SAMPLER_ARG`      | taxa de amostragem de traces                            | `1.0`                     |

O `docker-compose.yml` injeta essas variáveis a partir do `.env` da raiz.

### 13.2 Portas (arquivo `.env` da raiz)

| Serviço     | Interna | Externa | Variáveis                                                   |
| ----------- | ------- | ------- | ----------------------------------------------------------- |
| oauth API   | 3001    | 8181    | `OAUTH_INTERNAL_API_PORT` / `OAUTH_EXTERNAL_API_PORT`       |
| oauth Actuator | 9464 | 8381 (e 8281) | `OAUTH_INTERNAL_METRICS_PORT` / `OAUTH_EXTERNAL_METRICS_PORT` (`OAUTH_EXTERNAL_DEBUG_PORT`) |
| Keycloak    | 8080    | 8081    | `KEYCLOAK_INTERNAL_API_PORT` / `KEYCLOAK_EXTERNAL_CONSOLE_PORT` |
| Keycloak management | 9001 | 9001 | `KC_HTTP_MANAGEMENT_PORT` / `KEYCLOAK_EXTERNAL_METRICS_PORT` |
| Prometheus  | 9090    | 9090    | `PROMETHEUS_INTERNAL_PORT` / `PROMETHEUS_EXTERNAL_PORT`     |
| Grafana     | 3000    | 3000    | `GRAFANA_INTERNAL_PORT` / `GRAFANA_EXTERNAL_PORT`           |

> O `.env` original define a porta de debug igual à de métricas (`9464`). Por isso `8281` e
> `8381` apontam para o mesmo Actuator.

---

## 14. Execução fora do docker compose

Útil para depurar na IDE. O Keycloak ainda precisa estar no ar (`docker compose up -d keycloak`):

```bash
cd backend/oauth
export KEYCLOAK_SERVER_URL=http://localhost:8081
export KEYCLOAK_CLIENT_SECRET=wsNXUxaupU9X6jCncsn3rOEy6PDt7oJO   # valor do .env
mvn spring-boot:run                                             # API em http://localhost:8080
```

> Atenção ao *issuer*: tokens obtidos via `localhost:8081` têm `iss=http://localhost:8081/...`,
> e tokens obtidos pelo container têm `iss=http://keycloak:8080/...`. A API só aceita tokens
> cujo `iss` bata com o seu `KEYCLOAK_SERVER_URL`. Por isso, obtenha o token pelo `POST /login`
> **da mesma instância** da API que você vai chamar.

Só a imagem Docker, sem o compose:

```bash
docker build -t constrsw/oauth backend/oauth
docker run --rm -p 8181:3001 --network constrsw_constrsw \
  -e OAUTH_INTERNAL_API_PORT=3001 \
  -e KEYCLOAK_SERVER_URL=http://keycloak:8080 \
  -e KEYCLOAK_CLIENT_SECRET=<secret do .env> \
  constrsw/oauth
```

---

## 15. Solução de problemas

| Sintoma                                                        | Causa / solução                                                                                                   |
| -------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `external volume "constrsw-keycloak-data" not found`           | crie o volume: `docker volume create constrsw-keycloak-data`                                                      |
| `oauth` não fica `healthy`                                     | ele espera o Keycloak ficar `healthy` (~1 min na 1ª vez). Veja `docker compose logs -f oauth keycloak`             |
| Alterei `constrsw.json` e nada mudou                           | o realm só é importado se ainda não existir no volume. Para reimportar: `docker compose down`, `docker volume rm constrsw-keycloak-data`, `docker volume create constrsw-keycloak-data`, `docker compose up -d --build` |
| `401` em todas as rotas mesmo com token                        | token expirado (faça login de novo) ou emitido por outra URL do Keycloak (ver *issuer* na seção 14)              |
| `403` ao criar/listar usuários                                 | o usuário logado não tem permissões de administração no Keycloak; use `admin@pucrs.br`                           |
| `503` com `error_description` citando o Keycloak               | a API não consegue falar com o Keycloak; confira `docker compose ps` e `KEYCLOAK_SERVER_URL`                    |
| Grafana sem dados                                              | confira http://localhost:9090/targets (os três alvos devem estar `UP`) e gere tráfego na API                     |
| Porta ocupada (`3000`, `9090`, `8181`…)                        | altere a porta externa correspondente no `.env`                                                                   |
| Git Bash converte `/app` em `C:/Program Files/Git/app`         | o `run-all-tests.sh` já exporta `MSYS_NO_PATHCONV=1`; faça o mesmo em comandos `docker run` manuais              |
