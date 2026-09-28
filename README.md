# oauth — constrsw-2026-2 (grupo03)

API REST de autenticacao/autorizacao do T1. Nao guarda nenhum dado propria:
e um adapter fino sobre a REST API do Keycloak (realm `constrsw`, client
`oauth`), em **NestJS 11 + TypeScript 6**.

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
