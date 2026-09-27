# 🔐 Serviço OAuth - Keycloak Gateway API (ConstrSW 2026/2)

Microsserviço Spring Boot 3 (Java 21) responsável pelo gerenciamento de identidade, autenticação, emissão e validação de tokens JWT (via Keycloak), autorização baseada em cargos (Role-Based Access Control - RBAC) e administração centralizada de Usuários e Roles da plataforma acadêmica.

---

## 🚀 Como Executar com Docker Compose

A aplicação foi projetada para execução 100% conteinerizada, subindo automaticamente com o Keycloak provisionado e integrado:

```bash
# Na raiz do repositório:
docker compose up -d --build
```

### Portas e Serviços
| Serviço | Porta Host | Protocolo / Descrição |
|---|---|---|
| **OAuth Gateway API** | `8181` | REST API, Login e Administração |
| **Swagger UI (OpenAPI 3)** | `8181` | `http://localhost:8181/swagger-ui.html` |
| **Métricas Prometheus** | `8381` | `http://localhost:8381/actuator/prometheus` |
| **Health Check Ativo** | `8181` / `8381` | `GET /health` e `GET /actuator/health` |
| **Keycloak Console** | `8081` | `http://localhost:8081` (Admin: `admin` / `a12345678`) |

---

## 📖 Documentação Interativa da API (Swagger / OpenAPI)

A documentação interativa gerada pela biblioteca **Springdoc OpenAPI 3** está disponível em tempo real quando o container sobe:

- **Swagger UI**: [http://localhost:8181/swagger-ui.html](http://localhost:8181/swagger-ui.html) (ou `/swagger-ui/index.html`)
- **OpenAPI JSON Spec**: [http://localhost:8181/v3/api-docs](http://localhost:8181/v3/api-docs)

---

## 📋 Catálogo Completo de Endpoints

### 1. Autenticação e Autorização (`/`)
- `POST /login`: Autentica usuário via Keycloak (`grant_type=password`) e retorna `access_token`, `refresh_token` e `expires_in`.
- `POST /validate` ou `GET /validate?resource={nome}`: Valida a integridade do token Bearer JWT e permissão para o recurso.
- `POST /authorize` ou `GET /authorize?resource={nome}`: Verifica autorização com base nas roles do usuário.
- `GET /health`: Health check simples da API (retorna `"UP"`).

### 2. Gestão de Usuários (`/users`)
- `POST /users`: Cria usuário no Keycloak com validação de regex de email. Retorna `201 Created` e cabeçalho `Location: /users/{id}`.
- `GET /users`: Lista usuários do Realm, com suporte ao filtro `?enabled=true|false`.
- `GET /users/{id}`: Busca dados detalhados do usuário por ID único.
- As rotas de usuários e papéis recebem `Authorization: Bearer {access_token}`; a API valida o token e encaminha esse mesmo token à API administrativa do Keycloak, que decide as permissões.
- `PUT /users/{id}`: Atualiza cadastro do usuário (`firstName`, `lastName`, `email`, `enabled`).
- `PATCH /users/{id}`: Redefine/atualiza senha do usuário (estrutura `CredentialRepresentation`).
- `DELETE /users/{id}`: **Deleção lógica** do usuário (obtém dados do usuário, altera `enabled: false` e atualiza no Keycloak).
- `POST /users/{id}/roles/{roleId}`: Atribui um cargo (Role) a um usuário (Keycloak role-mapping). Retorna `204 No Content`.
- `DELETE /users/{id}/roles/{roleId}`: Remove um cargo (Role) de um usuário. Retorna `204 No Content`.

### 3. Gestão de Cargos / Roles (`/roles`)
- `POST /roles`: Cria um cargo no Keycloak, com suporte a atributo customizado de ativação. Retorna `201 Created` e `Location`.
- `GET /roles`: Lista todos os cargos do Realm, mapeando o status de ativação.
- `GET /roles/{id}`: Busca cargo por ID único.
- `PUT /roles/{id}`: Atualização completa do cargo (`name`, `description`, `enabled`).
- `PATCH /roles/{id}`: Atualização parcial de campos do cargo.
- `DELETE /roles/{id}`: **Deleção lógica** de cargo (como o Keycloak não tem flag `enabled` nativa para roles, define `attributes.enabled = ["false"]`). Retorna `204 No Content`.

---

## 🛡️ Tratamento Global de Erros Padronizado

Todas as respostas de exceção da API seguem uma estrutura JSON uniforme interceptada pelo `@RestControllerAdvice`:

```json
{
  "error_code": "404",
  "error_description": "Objeto não localizado",
  "error_source": "OAuthAPI",
  "error_stack": [
    {
      "error_code": "404",
      "error_description": "Objeto não localizado",
      "error_source": "OAuthAPI"
    }
  ]
}
```

---

## 📊 Observabilidade e Monitoramento

- **Prometheus**: Métricas de negócio customizadas expostas em `/actuator/prometheus`:
  - `oauth.logins.total`
  - `oauth.users.created.total`, `oauth.users.listed.total`, `oauth.users.updated.total`, `oauth.users.deleted.total`
  - `oauth.roles.created.total`, `oauth.roles.listed.total`, `oauth.roles.updated.total`, `oauth.roles.deleted.total`
  - `oauth.validations.total`, `oauth.validations.denied.total`
- **OpenTelemetry**: Rastreamento distribuído via OTLP gRPC/HTTP exporter configurado para o collector.
- Especificação detalhada: [ESPECIFICACAO_OBSERVABILIDADE.md](./ESPECIFICACAO_OBSERVABILIDADE.md)
