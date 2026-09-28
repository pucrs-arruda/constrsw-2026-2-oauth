# Reorganização do `oauth` em Layered Architecture (4 camadas)

**Data:** 2026-09-28
**Escopo:** serviço `oauth` inteiro (`backend/oauth`, branch `grupo03`). Os demais serviços do repo `base` não são tocados.
**Tipo:** refatoração estrutural pura. O contrato externo (rotas, payloads, status HTTP, formato de erro) **não muda**.

## 1. Objetivo e critérios de sucesso

Tornar explícita a arquitetura em camadas que hoje existe só de forma implícita (Controller → Service → `KeycloakClientService`), separando `domain`, `application`, `infrastructure` e `presentation`.

Critérios de sucesso:

1. `npm run build`, `npm test` e `npm run test:e2e` passam.
2. Os testes e2e (`test/*.e2e-spec.ts`) e o `scripts/smoke-test.sh` passam **sem alteração** — prova de que o contrato externo não mudou.
3. Nenhum arquivo em `domain/` importa `@nestjs/*`, `axios`, `express` ou qualquer arquivo de `application/`, `infrastructure/` ou `presentation/`.
4. Nenhum arquivo em `application/` importa `infrastructure/` ou `presentation/`.
5. README e `SPEC.md` descrevem a nova estrutura.

Fora de escopo: novas funcionalidades, mudança de rotas, mudança de infra (compose, Keycloak), mudança de stack.

## 2. Regra de dependência

```
presentation  ->  application  ->  domain  <-  infrastructure
```

| Camada | Responsabilidade | Pode depender de |
| --- | --- | --- |
| `domain` | Entidades, interfaces (ports) e erros de domínio | nada (TypeScript puro) |
| `application` | Casos de uso (orquestração) | `domain` |
| `infrastructure` | Adapters que implementam as interfaces do domain: Keycloak, configuração, telemetria | `domain` |
| `presentation` | HTTP: controllers, DTOs, guard, filtro, Swagger | `application`, `domain` |

`application` usa o NestJS apenas via `@Injectable()`/`@Inject()`; isso é aceito como exceção pragmática, mas o código dela não conhece HTTP nem Keycloak.

## 3. Estrutura de pastas alvo

```
src/
  main.ts
  app.module.ts
  domain/
    entities/
      user.entity.ts            # User { id, username, firstName, lastName, enabled }
      role.entity.ts            # Role { id, name, description? }
      token-set.entity.ts       # TokenSet (resposta do login)
    repositories/
      user.repository.ts        # interface UserRepository + token USER_REPOSITORY
      role.repository.ts        # interface RoleRepository + token ROLE_REPOSITORY
      auth.gateway.ts           # interface AuthGateway  + token AUTH_GATEWAY
    errors/
      domain-error.ts           # DomainError + DomainErrorKind
  application/
    auth/auth.service.ts
    users/users.service.ts
    roles/roles.service.ts
  infrastructure/
    infrastructure.module.ts    # liga tokens do domain aos adapters
    keycloak/
      keycloak-client.service.ts
      keycloak-error.mapper.ts  # AxiosError -> DomainError
      keycloak-user.repository.ts
      keycloak-role.repository.ts
      keycloak-auth.gateway.ts
      mappers/
        user.mapper.ts          # KeycloakUserRepresentation <-> User
        role.mapper.ts
    config/configuration.ts
    telemetry/tracing.ts
  presentation/
    http/
      controllers/
        auth.controller.ts
        users.controller.ts
        roles.controller.ts
        user-roles.controller.ts
        health.controller.ts
      dto/
        auth/    login.dto.ts, token-response.dto.ts
        users/   create-user.dto.ts, update-user.dto.ts, update-password.dto.ts,
                 list-users-query.dto.ts, user-response.dto.ts
        roles/   create-role.dto.ts, update-role.dto.ts, patch-role.dto.ts,
                 role-response.dto.ts
        error-response.dto.ts
      guards/bearer-token.guard.ts
      decorators/bearer-token.decorator.ts
      filters/oauth-exception.filter.ts
      exceptions/oauth-api.exception.ts
      validators/email-rfc5322.validator.ts
      presentation.module.ts    # registra controllers, importa módulos de application
test/                           # e2e, inalterados
```

Movimentações 1-para-1 (feitas com `git mv` para preservar histórico); os specs acompanham o arquivo que testam.

## 4. Domain

### 4.1 Entidades

Tipos simples (interfaces/classes sem decorators). Nomes em camelCase; o formato hifenizado do contrato (`first-name`, `last-name`) é responsabilidade dos DTOs, na presentation.

```ts
interface User  { id: string; username: string; firstName: string; lastName: string; enabled: boolean }
interface Role  { id: string; name: string; description?: string }
interface TokenSet { tokenType: string; accessToken: string; expiresIn: number;
                     refreshToken: string; refreshExpiresIn: number;
                     raw: Record<string, unknown> }
```

`TokenResponseDto` (presentation) devolve os campos exatamente como o Keycloak retorna hoje (snake_case, incluindo campos extras). Para não mudar o contrato, `TokenSet` mantém os campos extras: o campo `raw: Record<string, unknown>` guarda a resposta completa e o controller devolve `raw`. (Alternativa de tipar campo a campo foi descartada por risco de perder campos hoje repassados.)

### 4.2 Interfaces (ports)

Todas recebem `token: string` (bearer do chamador) como primeiro parâmetro, exceto `AuthGateway`. Isso é um vazamento consciente: o `SPEC.md` (§1.1) exige repassar o token do usuário à Admin API, sem token de serviço.

```ts
interface UserRepository {
  create(token, data: NewUser): Promise<User>;
  findAll(token, filter: { enabled?: boolean }): Promise<User[]>;
  findById(token, id): Promise<User>;
  update(token, id, changes: UserChanges): Promise<void>;
  updatePassword(token, id, password): Promise<void>;
  disable(token, id): Promise<void>;
}
interface RoleRepository {
  create(token, data: NewRole): Promise<Role>;
  findAll(token): Promise<Role[]>;
  findById(token, id): Promise<Role>;
  update(token, id, changes: RoleChanges): Promise<void>;   // PUT e PATCH
  remove(token, id): Promise<void>;
  assignToUser(token, userId, roleId): Promise<void>;
  removeFromUser(token, userId, roleId): Promise<void>;
}
interface AuthGateway {
  login(username, password): Promise<TokenSet>;
}
```

`replace` (PUT) e `patch` (PATCH) de role viram casos de uso distintos em `application/roles`, mas compartilham `RoleRepository.update`, com a diferença de semântica resolvida no caso de uso (PATCH preserva campos não enviados; PUT sobrescreve `name` e `description`). O merge com o role atual (`getRaw` hoje) passa a ser detalhe interno do adapter Keycloak, que precisa reenviar a representação completa ao `PUT /roles-by-id/{id}`.

### 4.3 Erros

```ts
enum DomainErrorKind {
  BadRequest, Unauthorized, InvalidCredentials, Forbidden,
  NotFound, Conflict, Unavailable, Upstream
}
class DomainError extends Error {
  kind: DomainErrorKind;
  description: string;
  status?: number;        // só usado quando kind === Upstream
  source: 'Keycloak' | 'OAuthAPI';
  cause?: DomainError[];  // vira error_stack
}
```

`status` só existe para o `Upstream`: quando o Keycloak devolve um código sem `kind` dedicado (ex.: 500, 429), a presentation precisa preservá-lo, como hoje.

## 5. Application

Os services atuais perdem a dependência de `KeycloakClientService`, dos DTOs e dos tipos `Keycloak*Representation`. Passam a receber os ports por `@Inject(TOKEN)`.

- `AuthService.login(username, password)`: chama `AuthGateway.login`. Se lançar `DomainError` com kind ≠ `Unavailable`, relança `DomainError(InvalidCredentials, 'username e/ou password invalidos.')` preservando a causa. `Unavailable` passa direto. (Regra hoje dentro de `AuthService`; comentário sobre o 400→401 do Keycloak é mantido.)
- `UsersService`: `create`, `findAll`, `findOne`, `update`, `updatePassword`, `disable`, delegando ao `UserRepository`. A regra "e-mail = username" (`email: dto.username`) é regra de montagem do payload Keycloak e fica no adapter, não no caso de uso.
- `RolesService`: `create`, `findAll`, `findOne`, `replace`, `patch`, `remove`, `assignToUser`, `removeFromUser`, delegando ao `RoleRepository`.

Assinaturas recebem/retornam **entidades e tipos de domínio**, nunca DTOs.

## 6. Infrastructure

- `KeycloakClientService` move-se para `infrastructure/keycloak/` **sem mudança de comportamento**, exceto: em vez de `OAuthApiException`, lança `DomainError` via `keycloak-error.mapper.ts`.
- **Mapeamento de erro** (`keycloak-error.mapper.ts`), a partir do status HTTP do Keycloak: 400→`BadRequest`, 401→`Unauthorized`, 403→`Forbidden`, 404→`NotFound`, 409→`Conflict`, sem resposta→`Unavailable` (fonte `Keycloak`, `error_code` 503), qualquer outro→`Upstream` com `status` original. A descrição é extraída como hoje (`error_description || errorMessage || error`).
- `KeycloakUserRepository`, `KeycloakRoleRepository`, `KeycloakAuthGateway`: contêm os paths, payloads e mapeamentos hoje espalhados nos services (`/users`, `/users/{id}/reset-password`, `/roles-by-id/{id}`, `/users/{id}/role-mappings/realm`, endpoint de token). `toUserResponse`/`toRoleResponse` viram `user.mapper.ts`/`role.mapper.ts`.
- `InfrastructureModule`: importa `HttpModule`, declara os adapters e exporta os tokens `USER_REPOSITORY`, `ROLE_REPOSITORY`, `AUTH_GATEWAY`.
- `configuration.ts` e `tracing.ts` mudam só de pasta. `main.ts` continua importando `tracing` como **primeira linha** (requisito do OpenTelemetry) com o novo caminho.

## 7. Presentation

- Controllers e DTOs movem-se; os DTOs mantêm validação e `@ApiProperty`. Cada controller converte DTO → tipo de domínio na entrada e entidade → DTO de resposta na saída (`first-name`/`last-name` aplicados aqui).
- `OAuthExceptionFilter` passa a tratar também `DomainError`: converte `kind` em status HTTP (`BadRequest`→400, `Unauthorized`/`InvalidCredentials`→401, `Forbidden`→403, `NotFound`→404, `Conflict`→409, `Unavailable`→503, `Upstream`→`err.status`) e monta o envelope `{ error_code, error_description, error_source, error_stack }` **idêntico ao atual**. `error_source` do topo continua `"OAuthAPI"`; o `error_stack` carrega a entrada com `error_source: "Keycloak"`.
- `OAuthApiException` permanece na presentation, usada pelo `BearerTokenGuard` (400 de header) e pelo filtro, mantendo o tratamento existente de `HttpException`/erros não mapeados.
- `HealthController` e `presentation.module.ts` na presentation; `app.module.ts` importa `PresentationModule` e `ConfigModule`.

## 8. Fluxo de dados (exemplo: `POST /users`)

`UsersController` (valida `CreateUserDto`, extrai bearer) → `UsersService.create(token, NewUser)` → `UserRepository.create` (implementado por `KeycloakUserRepository`, que monta o payload Keycloak e chama `KeycloakClientService.adminRequest`) → `User` volta pelas camadas → controller converte para `UserResponseDto`. Se o Keycloak retornar 409, o mapper gera `DomainError(Conflict)`, que atravessa `application` sem tratamento e o `OAuthExceptionFilter` devolve 409 com o envelope padrão.

## 9. Testes

- Specs existentes migram com os arquivos. Os de `application` passam a mockar os **ports** (`UserRepository` etc.) em vez do `KeycloakClientService`; as asserções sobre payload/paths Keycloak migram para specs novos dos adapters.
- Novos specs: `keycloak-user.repository`, `keycloak-role.repository`, `keycloak-auth.gateway`, `keycloak-error.mapper`, e caso de `DomainError` no `oauth-exception.filter.spec`.
- E2E e smoke test: inalterados e obrigatoriamente verdes (critério 2).
- Verificação das regras de dependência (critérios 3 e 4): checagem por `grep` dos imports durante a validação; sem adicionar ferramenta nova (YAGNI).

## 10. Plano de migração (alto nível)

Feito em passos, cada um deixando build e testes verdes:

1. Criar `domain/` (entidades, ports, `DomainError`).
2. Criar adapters em `infrastructure/` e mover `KeycloakClientService`, config, telemetria.
3. Adaptar `application/` aos ports (services movidos).
4. Mover presentation (controllers, DTOs, guard, filtro) e ajustar o filtro a `DomainError`.
5. Reescrever `app.module.ts`, `main.ts`, módulos; atualizar imports e `tsconfig`/`nest-cli` se preciso.
6. Rodar build + unit + e2e + smoke test; atualizar README e `SPEC.md`.

## 11. Riscos e decisões

- **Over-engineering:** `domain` e `application` ficam finos porque o serviço é essencialmente um adaptador para o Keycloak. Aceito por decisão explícita do grupo (layered com `domain`); as entidades e ports mantêm o custo baixo.
- **Token do chamador nos ports:** vazamento consciente (§4.2), imposto pelo `SPEC.md` §1.1.
- **Fidelidade de erros:** o único risco funcional real é mudar status/mensagem de algum erro; mitigado pelo `Upstream` com status preservado e pelos e2e inalterados.
- **`TokenSet.raw`:** guarda a resposta completa do Keycloak para não perder campos hoje repassados.
- **Os e2e exigem Keycloak de pé;** se o ambiente não subir, a verificação do critério 2 fica pendente e será reportada como tal.
