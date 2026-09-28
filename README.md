# OAuth ConstrSW

Gateway autocontido de identidade institucional do ConstrSW.

## Arquitetura

O módulo `backend/oauth` é um gateway entre o cliente da aplicação e o Keycloak.
O browser fala apenas com este serviço; o serviço, por sua vez, chama o Keycloak
(endpoint OIDC de token para login e a Admin REST API para roles) e devolve ao
consumidor apenas o que ele precisa.

A infraestrutura local é composta por dois containers principais, orquestrados
pelo `docker-compose.yml` da raiz:

- `keycloak`: sobe a partir de uma imagem customizada que importa o realm local
  `constrsw` (`infrastructure/dev.local/services/keycloak/constrsw.json`), expõe
  o console/admin e emite os tokens JWT.
- `oauth`: sobe a API NestJS que implementa login, refresh, logout, healthcheck
  e o CRUD de roles, usando o Keycloak como provedor externo.

Os dois serviços compartilham a rede `constrsw`, e o `oauth` depende de um
Keycloak saudável antes de iniciar. O estado do realm fica persistido no volume
externo `constrsw-keycloak-data`; a importação inicial acontece uma vez e depois
o volume mantém a configuração.

## Tecnologias utilizadas

- Node.js 24 / NestJS 11 / TypeScript
- Keycloak 26 (realm `constrsw`)
- Docker e Docker Compose
- `class-validator` / `class-transformer` para validação de payloads
- `cookie-parser` para o cookie de sessão
- Swagger/OpenAPI para documentação (`/docs`)
- Jest + Supertest para testes unitários e de integração

## Decisões importantes

- **O login pertence a este módulo.** O `backend/oauth` é um serviço executável e
  autocontido. A rota pública é `POST /login`; não existe login no BFF. Isso
  elimina a duplicação de clientes Keycloak e deixa autenticação, sessão e
  configuração num único lugar.
- **O browser nunca chama o Keycloak.** O serviço faz o Direct Access Grant
  internamente, devolve os tokens no corpo de login conforme o contrato e também
  mantém access token e refresh token em cookie `httpOnly`. Assim, tokens não ficam
  expostos ao JavaScript nem ao `localStorage`.
- **`client_credentials` não autentica pessoas.** Esse fluxo é o do **Admin API**:
  o client de service account `oauth-admin` (`KEYCLOAK_ADMIN_CLIENT_ID` /
  `KEYCLOAK_ADMIN_CLIENT_SECRET`) obtém o token administrativo usado pelas rotas de
  roles (as rotas de usuários repassam o bearer do chamador). O login de usuário
  continua em `grant_type=password` no cliente confidencial de `KEYCLOAK_CLIENT_ID`.
  Como `client_credentials` identifica uma aplicação, não uma pessoa, os dois fluxos
  ficam separados.
- **Roles são realm roles do Keycloak.** O serviço não mantém banco próprio: o CRUD
  de roles é um proxy sobre a Admin API, através do mesmo cliente de service
  account. Como realm roles não têm exclusão nativa, o `DELETE` é uma **exclusão
  lógica** (grava o atributo `deleted=true` no role); roles marcados somem das
  leituras e suas atribuições são removidas dos usuários.
- **Erros são seguros.** A API responde `application/json` no envelope acordado
  pelo grupo: `error_code`, `error_description`, `error_source` e `error_stack`
  (array de `{ error_code, error_description, error_source }`, do upstream
  `Keycloak` até o `OAuthAPI`). O status HTTP permanece na resposta HTTP, e nunca
  devolvemos senha, token, segredo, payload do Keycloak ou stack trace de runtime —
  em produção nem a causa de um erro inesperado, que fica só no log.
- **As mutações de usuários repassam o bearer recebido.** O gateway encaminha o
  access token do chamador à Admin API — produzindo `401`/`403` reais — para
  criar, atualizar, alterar senha e desabilitar usuários.
- **`enabled` é server-side.** `enabled` aparece só na resposta: novos usuários
  nascem habilitados e o `DELETE` desabilita. `create`/`PUT`/`PATCH` não aceitam
  `enabled` como entrada, e o `PUT` omite o campo ao Keycloak — assim uma
  atualização de rotina não reativa um usuário excluído logicamente. Os atributos
  de nome seguem `first-name`/`last-name` em todas as rotas de usuário.
- **A escrita de usuários e o CRUD de roles moram aqui.** `POST/PUT/PATCH/DELETE
  /users` (trilha DEV B) e `/roles` + role-mapping (trilha DEV D) estão
  implementados sobre o Admin API, protegidos pelo bearer do chamador.

### Por que um serviço separado?

O OAuth é autocontido porque precisa ser executado, testado e atualizado sem
depender do código de um contexto de domínio. Ele concentra a integração com o
Keycloak (login e administração de identidade), mas não vira dono dos dados de
domínio da aplicação. Os demais módulos consomem uma fronteira estável em vez de
cada equipe implementar seu próprio `fetch` para o provedor.

## Rotas

### Autenticação

| Método | Rota      | Descrição                                        |
| ------ | --------- | ------------------------------------------------ |
| POST   | `/login`  | Autentica um usuário via `multipart/form-data` e devolve os tokens. |
| POST   | `/refresh`| Renova a sessão a partir do cookie.              |
| POST   | `/logout` | Limpa o cookie de sessão.                        |

As falhas do provedor são normalizadas no envelope de quatro chaves, sem vazar payload bruto, segredo ou stack trace. O status e o motivo do Keycloak aparecem como entradas de `error_stack` (`KC-<status>`), e a resposta final do gateway é `OA-<status>` — é o que permite distinguir um `500` de um `504` do lado do cliente.

### Roles (`/roles`)

| Método | Rota                      | Descrição                                    |
| ------ | ------------------------- | -------------------------------------------- |
| POST   | `/`                       | Cria um role. → `201`                        |
| GET    | `/`                       | Lista os roles (exclui os excluídos).        |
| GET    | `/{id}`                   | Recupera um role pelo id. → `404` se ausente |
| PUT    | `/{id}`                   | Atualização total.                           |
| PATCH  | `/{id}`                   | Atualização parcial.                         |
| DELETE | `/{id}`                   | **Exclusão lógica.** → `204`                 |
| POST   | `/{id}/users/{userId}`    | Atribui o role a um usuário. → `204`         |
| DELETE | `/{id}/users/{userId}`    | Remove a atribuição. → `204`                 |

As rotas de roles e de escrita de usuários exigem as credenciais do service account
administrativo (`KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET`, via
`grant_type=client_credentials`); sem elas o serviço não consegue token de admin e
responde `503`.

### Usuários (`/users`)

| Método | Rota | Descrição |
| ------ | ---- | --------- |
| POST | `/` | Cria um usuário e retorna o `id` do header `Location`. → `201` |
| GET | `/` | Lista usuários; aceita `?enabled=true|false`. |
| GET | `/{id}` | Recupera um usuário. |
| PUT | `/{id}` | Atualiza os dados do usuário (não altera `enabled`). → `200` |
| PATCH | `/{id}` | Atualiza parcialmente o usuário ou sua senha. → `200` |
| DELETE | `/{id}` | Desabilita logicamente o usuário. → `204` |

## Variáveis de ambiente

| Variável                                  | Exemplo                | Uso                                  |
| ----------------------------------------- | ---------------------- | ------------------------------------ |
| `PORT`                                    | `8088`                 | Porta interna da API                 |
| `KEYCLOAK_URL`                            | `http://keycloak:8080` | Base do Keycloak                     |
| `KEYCLOAK_REALM`                          | `constrsw`             | Realm da aplicação                   |
| `KEYCLOAK_CLIENT_ID` / `_SECRET`          | `oauth` / `…`          | Cliente usado no login               |
| `KEYCLOAK_TIMEOUT_MS`                     | `5000`                 | Timeout das chamadas ao Keycloak     |
| `KEYCLOAK_ADMIN_CLIENT_ID`                | `oauth-admin`          | Client do service account administrativo |
| `KEYCLOAK_ADMIN_CLIENT_SECRET`            | `…`                    | Segredo do client administrativo     |
| `SESSION_COOKIE_NAME`                     | `closed_cras_session`  | Nome do cookie de sessão             |
| `COOKIE_SECURE` / `COOKIE_SAME_SITE`      | `false` / `lax`        | Flags do cookie                      |

Em produção, segredo do cliente e credenciais administrativas devem vir de
secret management. Os valores no Compose existem apenas para desenvolvimento.

## Executar

### Via Docker (recomendado)

A partir da **raiz do repositório** — sobe Keycloak (realm `constrsw`) e a API
com a configuração consistente do `docker-compose.yml`:

```bash
docker volume create constrsw-keycloak-data   # apenas na primeira vez
docker compose up -d --build --wait
```

Endereços: API `http://localhost:8181`, saúde `GET /health`, Swagger
`http://localhost:8181/docs`, Keycloak `http://localhost:8081`.

> Após mudar `.env` ou o compose, recrie o container:
> `docker compose up -d --build --force-recreate oauth`.

### Fora do container (opcional)

Requer Node.js 24. Instale com `--legacy-peer-deps` (há um conflito de peers
`typescript`/`ts-jest` conhecido nesta base):

```bash
cp .env.example .env
npm install --legacy-peer-deps
npm run start:dev
```

O `.env.example` já traz as credenciais do service account (`KEYCLOAK_ADMIN_CLIENT_*`),
mas os valores de conexão apontam para nomes internos de container. Para rodar
localmente **contra o Keycloak do Compose**, ajuste no seu `.env`:

```dotenv
KEYCLOAK_URL=http://localhost:8081
KEYCLOAK_REALM=constrsw
KEYCLOAK_CLIENT_ID=oauth
KEYCLOAK_CLIENT_SECRET=wsNXUxaupU9X6jCncsn3rOEy6PDt7oJO
KEYCLOAK_ADMIN_CLIENT_ID=oauth-admin
KEYCLOAK_ADMIN_CLIENT_SECRET=local-development-admin-secret
```

## Rodar os testes
Quando usar `-d`, os containers ficam em segundo plano e os logs não aparecem no
terminal. Para acompanhar o startup e visualizar as URLs impressas pelo OAuth:

```bash
docker compose logs -f oauth
```

As URLs públicas são API `http://localhost:8181`, saúde
`http://localhost:8181/health`, Swagger `http://localhost:8181/docs` e Keycloak
`http://localhost:8081`.

Endereços locais: API `http://localhost:8181`, saúde `GET /health`, Swagger `http://localhost:8181/docs` e Keycloak `http://localhost:8081`.

```bash
npm run test        # unitários (Jest)
npm run test:e2e    # integração: sobe a app Nest e simula o Keycloak (não precisa de Keycloak vivo)
npm run build       # compila/verifica tipos
```

## Teste rápido

Com o stack no ar, as rotas de roles exigem um `Authorization: Bearer <token>`
(obtenha um token via `POST /login`; o serviço usa a service account
`oauth-admin` internamente para falar com a Admin API):

```bash
BASE=http://localhost:8181
TOKEN="<access_token de POST /login>"

# Criar um role → 201 com { id, name, description }
curl -i -X POST $BASE/roles -H "Authorization: Bearer $TOKEN" \
  -H 'content-type: application/json' \
  -d '{"name":"professor","description":"Docente"}'

# Listar / buscar / excluir logicamente
curl -s  $BASE/roles -H "Authorization: Bearer $TOKEN" | jq
curl -i -X DELETE $BASE/roles/<ID> -H "Authorization: Bearer $TOKEN"   # 204
curl -i $BASE/roles/<ID> -H "Authorization: Bearer $TOKEN"             # 404 (some após a exclusão lógica)
```

O realm `constrsw` já vem com usuários (`admin@pucrs.br`, `coordinator@pucrs.br`,
`professor@pucrs.br`, `student@pucrs.br`); as senhas são gerenciadas no Keycloak
(console em `http://localhost:8081`). Para testar o login, use um desses usuários:

```bash
curl -i -X POST $BASE/login -F 'username=professor@pucrs.br' \
  -F 'password=<senha-no-keycloak>'
```

O corpo de sucesso contém `token_type`, `access_token`, `expires_in`,
`refresh_token` e `refresh_expires_in`; a sessão também é armazenada no cookie
`httpOnly`.

## Contratos

- `contracts/identity-gateway.yaml`: fragmento OpenAPI das rotas de autenticação,
  usuários e roles. **Parcial:** algumas rotas ainda não estão descritas.
- `infrastructure/dev.local/services/keycloak/constrsw.json` (na raiz do repo):
  realm importado pelo container do Keycloak. Traz o client `oauth-admin` (service
  account com `manage-users` / `view-users` / `query-users` / `manage-realm`).
- `keycloak/realm-closed-cras.json`: realm legado de exemplo; **não** é o que sobe
  em desenvolvimento (o realm ativo é `constrsw`).
- `Planning/Rotas.md`: documento legado do enunciado; não é fonte executável e
  contém decisões superadas.
