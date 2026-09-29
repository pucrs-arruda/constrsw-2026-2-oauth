# Microsserviço de Autenticação e Gestão de Identidades (OAuth) — Grupo 08

> **Instituição:** Pontifícia Universidade Católica do Rio Grande do Sul (PUCRS)  
> **Curso:** Engenharia de Software / Ciência da Computação  
> **Disciplina:** Construção de Software (2026/2)  
> **Equipe (Grupo 08):** Arthur Mendes Maciel, Lucas Silva, Anthony, João Biasoli  
> **Repositório:** `backend/oauth` (Submódulo de `constru-sw-2026-2`)  
> **Branch de Trabalho:** `grupo08-feat/telemetry-lucas` (baseada em `grupo08`)  

---

## Sumário Executivo

- [1. Visão Geral do Sistema](#1-visão-geral-do-sistema)
- [2. Stack Tecnológica Detalhada](#2-stack-tecnológica-detalhada)
- [3. Arquitetura de Software (Clean Architecture & Ports and Adapters)](#3-arquitetura-de-software-clean-architecture--ports-and-adapters)
- [4. Arquitetura de Segurança, Autenticação e RBAC](#4-arquitetura-de-segurança-autenticação-e-rbac)
  - [4.1 Fluxo OAuth2 Direct Access Grant & Refresh Token](#41-fluxo-oauth2-direct-access-grant--refresh-token)
  - [4.2 Validação Descentralizada de Tokens via JWKS Criptográfico em Memória](#42-validação-descentralizada-de-tokens-via-jwks-criptográfico-em-memória)
  - [4.3 Filtros de Segurança e Padronização de Erros 401/403](#43-filtros-de-segurança-e-padronização-de-erros-401403)
  - [4.4 RBAC (Role-Based Access Control) e Permissões Administrativas](#44-rbac-role-based-access-control-e-permissões-administrativas)
  - [4.5 Validação Rigorosa de E-mails conforme RFC 5322](#45-validação-rigorosa-de-e-mails-conforme-rfc-5322)
  - [4.6 Proteção Contra Vazamento de Segredos e Credenciais](#46-proteção-contra-vazamento-de-segredos-e-credenciais)
- [5. Telemetria, Observabilidade e Métricas (Prometheus & OpenTelemetry)](#5-telemetria-observabilidade-e-métricas-prometheus--opentelemetry)
  - [5.1 Isolamento Físico de Portas de Rede (API vs. Métricas)](#51-isolamento-físico-de-portas-de-rede-api-vs-métricas)
  - [5.2 Rastreamento Distribuído (Distributed Tracing com OpenTelemetry)](#52-rastreamento-distribuído-distributed-tracing-com-opentelemetry)
  - [5.3 Métricas Coletadas e Consultas PromQL Úteis](#53-métricas-coletadas-e-consultas-promql-úteis)
- [6. Contrato Padronizado de Erros (Especificação da Disciplina)](#6-contrato-padronizado-de-erros-especificação-da-disciplina)
- [7. Catálogo Completo de Endpoints da API REST](#7-catálogo-completo-de-endpoints-da-api-rest)
- [8. Documentação Interativa OpenAPI / Swagger UI](#8-documentação-interativa-openapi--swagger-ui)
- [9. Instruções de Execução com Docker Compose](#9-instruções-de-execução-com-docker-compose)
  - [9.1 Pré-requisito: Volume Persistente do Keycloak](#91-pré-requisito-volume-persistente-do-keycloak)
  - [9.2 Justificativa Técnica do docker-compose.base.override.yml](#92-justificativa-técnica-do-docker-composebaseoverrideyml)
  - [9.3 Subindo o Ambiente Completo do Zero](#93-subindo-o-ambiente-completo-do-zero)
- [10. Suítes de Testes Automatizados e Evidências](#10-suítes-de-testes-automatizados-e-evidências)
  - [10.1 Testes Unitários e de Integração (Maven / JUnit 5 / Mockito)](#101-testes-unitários-e-de-integração-maven--junit-5--mockito)
  - [10.2 Testes E2E Automatizados com Bruno CLI](#102-testes-e2e-automatizados-com-bruno-cli)
  - [10.3 Coleção e Ambiente do Postman](#103-coleção-e-ambiente-do-postman)
- [11. Divisão de Responsabilidades e Rastreabilidade](#11-divisão-de-responsabilidades-e-rastreabilidade)

---

## 1. Visão Geral do Sistema

O microsserviço **OAuth** do Grupo 08 atua como o **Provedor de Identidade, Autenticação Centralizada e Gateway de Autorização** do ecossistema distribuído de microsserviços da disciplina Construção de Software (ConstrSW 2026/2).

Concebido segundo o padrão de arquitetura **Ports & Adapters (Clean Architecture)**, este serviço funciona como uma camada mediadora de abstração (*Adapter*) sobre as APIs REST administrativas e de autenticação do **Keycloak**, fornecendo:

1. **Autenticação Segura:** Emissão de tokens JWT OpenID Connect através de fluxos OAuth 2.0 padrão da indústria (`grant_type=password` e `grant_type=refresh_token`).
2. **Gerenciamento Completo de Identidades (Users):** CRUD completo de usuários, redefinição segura de credenciais e exclusão lógica (`enabled=false`), assegurando conformidade estrita com a validação oficial de e-mails via **RFC 5322**.
3. **Controle de Acesso Baseado em Papéis (RBAC - Roles):** CRUD completo de perfis/roles de realm e endpoints dedicados de vinculação e desvinculação de papéis a usuários.
4. **Isolamento de Segurança e Performance:** Validação criptográfica local de tokens via **JWKS em memória**, eliminando round-trips de rede ao Keycloak a cada chamada realizada por clientes ou outros microsserviços.
5. **Observabilidade Contínua em Tempo Real:** Coleta e exportação de métricas nativas e rastreamento distribuído (**OpenTelemetry** e **Prometheus**) expostos em porta de rede isolada da API de negócio.

---

## 2. Stack Tecnológica Detalhada

Todas as tecnologias e bibliotecas foram criteriosamente selecionadas para garantir robustez empresarial, tipagem estática moderna, tempos de resposta na escala de milissegundos e conteinerização reprodutível:

| Tecnologia / Biblioteca | Versão | Função e Justificativa Arquitetural |
|---|---|---|
| **Java** | `17 LTS (Eclipse Temurin)` | Linguagem base moderna com suporte a Records, Pattern Matching e alta performance |
| **Spring Boot** | `3.3.4` | Framework corporativo para ecossistemas de microsserviços REST |
| **Spring Security** | `6.3.3` | Camada de controle de acesso, filtros de segurança e proteção de rotas |
| **Spring Resource Server** | `6.3.3` | Validação descentralizada de tokens JWT via JWKS (`oauth2ResourceServer`) |
| **Spring WebFlux / WebClient** | `6.1.13` | Cliente HTTP reativo, não bloqueante e resiliente para comunicação com o Keycloak |
| **Spring Boot Actuator** | `3.3.4` | Monitoramento de ciclo de vida e exposição de endpoints operacionais |
| **Micrometer Prometheus** | `1.13.4` | Exportação de métricas em formato OpenMetrics padrão para o Prometheus |
| **Micrometer Tracing (OTel)** | `1.3.4` | Bridge de rastreamento distribuído integrado ao padrão **OpenTelemetry** |
| **SpringDoc OpenAPI / Swagger** | `2.6.0` | Geração dinâmica da documentação interativa OpenAPI 3.0 em `/docs` |
| **Jakarta Bean Validation** | `3.0.2` | Validação declarativa de payloads e regex estrita de e-mail RFC 5322 |
| **Jackson JSON Databind** | `2.17.2` | Serialização/desserialização JSON imutável em snake_case |
| **Apache Maven** | `3.9` | Gerenciamento de dependências e compilação do projeto |
| **Docker & Docker Compose** | `v2.x` | Orquestração de containers com multi-stage build |
| **Keycloak** | `26.x` | Servidor de Identidade (IdP) e Provedor OAuth2/OIDC |
| **Prometheus** | `v2.54.0` | Motor de coleta temporal (scraping) e armazenamento de telemetria |
| **JUnit 5 / Mockito / AssertJ** | `5.10 / 5.11` | Pirâmide de testes unitários e de integração com cobertura total dos componentes |
| **Bruno CLI / Postman** | Recentes | Testes funcionais automatizados e coleções de validação de ponta a ponta |

---

## 3. Arquitetura de Software (Clean Architecture & Ports and Adapters)

A estrutura do código foi projetada seguindo **Clean Architecture / Ports & Adapters**, garantindo desacoplamento estrito entre os protocolos de transporte (HTTP/REST), regras de orquestração e clientes de rede externos:

```
src/main/java/com/seugrupo/oauth/
├── config/
│   ├── KeycloakProperties.java   # Configurações tipadas injetadas via variáveis de ambiente
│   ├── SecurityConfig.java       # Configuração Spring Security, JWKS e handlers customizados 401/403
│   └── WebClientConfig.java      # Configuração do WebClient com buffers e timeouts resilientes
├── controller/
│   ├── AuthController.java       # Endpoints de login (form/multipart) e renovação de sessão
│   ├── HealthController.java     # Endpoint oficial de saúde (HTTP 200) na porta da API de negócio
│   ├── RoleController.java       # CRUD completo e atualização parcial (PATCH) de roles
│   ├── UserController.java       # CRUD completo, atualização de senha e desativação de usuários
│   └── UserRoleController.java   # Atribuição e desassociação de roles a usuários
├── dto/
│   ├── CreateRoleRequest.java    # Record imutável para criação de role
│   ├── CreateUserRequest.java    # Record com validação de e-mail RFC 5322 e credenciais
│   ├── LoginRequest.java         # Record imutável para formulários de autenticação
│   ├── PatchRoleRequest.java     # Record para atualização parcial de atributos de role
│   ├── RefreshTokenRequest.java  # Record para renovação de sessão
│   ├── RoleResponse.java         # Contrato de saída de dados de papéis
│   ├── TokenResponse.java        # Contrato de saída de tokens OAuth2 (snake_case)
│   ├── UpdatePasswordRequest.java# Record para redefinição de senha
│   ├── UpdateRoleRequest.java    # Record para substituição completa de role
│   ├── UpdateUserRequest.java    # Record para atualização de dados cadastrais
│   └── UserResponse.java         # Contrato de saída de usuários sem vazamento de senha
├── exception/
│   ├── ErrorResponse.java        # Record do contrato padronizado de erro da disciplina
│   ├── GlobalExceptionHandler.java# Interceptor global (@RestControllerAdvice) para o Spring MVC
│   ├── KeycloakErrorMapper.java  # Tradutor de exceções do Keycloak para a resposta padronizada
│   └── OAuthApiException.java    # Exceção base tipada do domínio da aplicação
└── OauthApplication.java         # Ponto de entrada da aplicação Spring Boot
```

### Princípios de Engenharia Adotados:
* **Imutabilidade Total nos Contratos:** Todos os DTOs utilizam Java `record`, eliminando mutabilidade acidental, estados inconsistentes e código boilerplate.
* **Resiliência e Prevenção de Cascading Failures:** Chamadas via `WebClient` ao Keycloak possuem timeout rígido de 10 segundos. Se o Keycloak estiver indisponível ou sofrer lentidão, o cliente não esgota o pool de threads e converte a falha imediatamente em `502 Bad Gateway` (`OA-502`).
* **Injeção de Dependências Tipada:** Nenhuma string de URL ou credencial fica hardcoded; todas são carregadas tipadamente via `@ConfigurationProperties` em [`KeycloakProperties.java`](./src/main/java/com/seugrupo/oauth/config/KeycloakProperties.java).

---

## 4. Arquitetura de Segurança, Autenticação e RBAC

A arquitetura de segurança do microsserviço foi concebida para atender tanto a clientes interativos quanto à comunicação segura entre microsserviços do ecossistema:

```
   ┌───────────────┐
   │  Cliente Web  │
   │ Postman/Bruno │
   └───────┬───────┘
           │ 1. POST /login (username/password)
           ▼
┌───────────────────────┐
│     Microsserviço     │── 2. Direct Grant (password) ──▶ ┌──────────────┐
│         OAuth         │◀── 3. Tokens JWT (RS256) ───────│   Keycloak   │
└──────────┬────────────┘                                  └──────┬───────┘
           │ 4. Retorna access_token                              │
           ▼                                                      │
   ┌───────────────┐                                              │ 5. Download inicial
   │  Cliente Web  │                                              │    das chaves públicas
   └───────┬───────┘                                              ▼    (JWKS /certs)
           │ 6. Requisições subsequentes                  ┌───────────────┐
           │    Authorization: Bearer <token>             │ Spring Sec.   │
           │    (ex: GET /users, POST /roles)             │ Resource Srv  │ (Validação local
           └─────────────────────────────────────────────▶│ (Em Memória)  │  em microssegundos)
                                                          └───────────────┘
```

### 4.1 Fluxo OAuth2 Direct Access Grant & Refresh Token
1. O cliente envia credenciais via `POST /login` em formato `application/x-www-form-urlencoded` ou `multipart/form-data`.
2. O microsserviço OAuth orquestra a chamada ao endpoint `/protocol/openid-connect/token` do Keycloak utilizando o `grant_type=password`, o `client_id` (`oauth`) e o `client_secret`.
3. O Keycloak valida as credenciais contra a base de usuários do realm `constrsw` e emite os tokens criptografados com assinatura **RS256**.
4. A rota `POST /refresh-token` permite a renovação periódica da sessão através do `refresh_token`, mantendo o `access_token` com ciclo de vida curto para mitigar riscos de interceptação.

### 4.2 Validação Descentralizada de Tokens via JWKS Criptográfico em Memória
* O serviço atua como um **OAuth2 Resource Server** configurado nativamente com `issuer-uri: ${keycloak.base-url}/realms/${keycloak.realm}`.
* Na inicialização, o Spring Security consulta o endpoint `/protocol/openid-connect/certs` do Keycloak e armazena o conjunto de chaves públicas criptográficas (**JWKS - JSON Web Key Set**) em cache em memória.
* **Vantagem de Performance e Resiliência:** Toda requisição subsequente com cabeçalho `Authorization: Bearer <token>` tem sua assinatura criptográfica, emissor (`iss`), audiência (`aud`) e expiração (`exp`) validadas **localmente no próprio processo Java em microssegundos**, sem incorrer em sobrecarga de chamadas de rede adicionais ao Keycloak.

### 4.3 Filtros de Segurança e Padronização de Erros 401/403
A maioria das APIs Spring Security retorna respostas vazias ou HTML genérico em falhas de autenticação de baixo nível. Para cumprir 100% dos requisitos da disciplina, implementamos manipuladores customizados diretamente na cadeia de filtros do Spring Security ([`SecurityConfig.java`](./src/main/java/com/seugrupo/oauth/config/SecurityConfig.java)):

1. **`CustomAuthenticationEntryPoint` (HTTP 401):**
   * Intercepta requisições desprovidas do cabeçalho `Authorization`, tokens malformados, assinaturas inválidas ou tokens expirados.
   * Adiciona o cabeçalho oficial `WWW-Authenticate: Bearer error="invalid_token"`.
   * Retorna o JSON padronizado com código de erro `OA-401` e descrição amigável.
2. **`CustomAccessDeniedHandler` (HTTP 403):**
   * Intercepta requisições onde o token JWT é válido, porém o usuário não possui papéis ou permissões suficientes para executar a operação.
   * Retorna o JSON padronizado com código de erro `OA-403`.

### 4.4 RBAC (Role-Based Access Control) e Permissões Administrativas
* O Keycloak gerencia os papéis no nível de Realm e de Client.
* Endpoints administrativos de gestão de usuários e perfis (`/users`, `/roles`) exigem privilégios elevados (`realm-management` com `manage-users`).
* Em ambiente de homologação e avaliação da disciplina, o usuário administrador pré-provisionado no backup é:
  * **Usuário:** `admin@pucrs.br`
  * **Senha:** `a12345678`
* O usuário comum de testes `student@pucrs.br` possui acesso como usuário regular, permitindo validar na prática a rejeição com `OA-403` quando este tenta alterar roles administrativas.

### 4.5 Validação Rigorosa de E-mails conforme RFC 5322
O endpoint `POST /users` aplica a expressão regular oficial preconizada pelo padrão RFC 5322 antes de repassar a requisição ao Keycloak:
```regex
([-!#-'*+/-9=?A-Z^-~]+(\.[-!#-'*+/-9=?A-Z^-~]+)*|"([]!#-[^-~ \t]|(\\(\t -~]))+")@([-!#-'*+/-9=?A-Z^-~]+(\.[-!#-'*+/-9=?A-Z^-~]+)*|\[[\t -Z^-~]*\])
```
Se um e-mail violar a norma (como formatos maliciosos ou sem domínio válido), a requisição é abortada na camada de DTO com resposta imediata `OA-400`.

### 4.6 Proteção Contra Vazamento de Segredos e Credenciais
* **Zero Exposição de Hash:** As senhas dos usuários nunca são retornadas em endpoints de consulta (`GET /users`, `GET /users/{id}`) nem são exibidas nos logs estruturados.
* **Credenciais Mascaradas:** Em caso de exceção de infraestrutura, a pilha de erros (`error_stack`) sanitiza campos confidenciais.

---

## 5. Telemetria, Observabilidade e Métricas (Prometheus & OpenTelemetry)

O microsserviço OAuth inclui observabilidade nativa de nível empresarial através do ecossistema **Micrometer** e do padrão **OpenTelemetry**:

### 5.1 Isolamento Físico de Portas de Rede (API vs. Métricas)
Para impedir que tarefas de monitoramento (como raspagens periódicas do Prometheus ou ataques de DoS voltados ao Actuator) concorram por conexões e threads com as requisições de negócio dos usuários, o serviço expõe duas portas HTTP completamente independentes:

| Porta Host | Porta Container | Finalidade | Tecnologias / Endpoints |
|---|---|---|---|
| **`8181`** | **`3001`** (`OAUTH_INTERNAL_API_PORT`) | **API de Negócio Principal** | Swagger UI (`/docs`), OpenAPI, CRUDs e Health Check oficial (`/health`) |
| **`8381`** | **`9464`** (`OAUTH_INTERNAL_METRICS_PORT`) | **Telemetria e Observabilidade** | Spring Boot Actuator, Prometheus scraping (`/actuator/prometheus`) |
| **`9090`** | **`9090`** | **Servidor Prometheus** | Painel Web e motor de coleta de métricas de telemetria |

### 5.2 Rastreamento Distribuído (Distributed Tracing com OpenTelemetry)
Através da dependência `micrometer-tracing-bridge-otel`, cada requisição HTTP processada pela aplicação recebe automaticamente identificadores de contexto W3C TraceContext:
* **`traceId`**: Identificador global da transação distribuída entre múltiplos microsserviços.
* **`spanId`**: Identificador do segmento local de execução na pilha de chamadas.
* Esses identificadores correlacionam métricas de latência com linhas de log, permitindo auditoria detalhada de desempenho.

### 5.3 Métricas Coletadas e Consultas PromQL Úteis
O scraper do Prometheus consulta `http://oauth:9464/actuator/prometheus` a cada 15 segundos. O professor ou avaliador pode acessar [http://localhost:9090](http://localhost:9090) e executar as seguintes consultas **PromQL**:

* **Taxa de Requisições por Segundo na API:**
  ```promql
  rate(http_server_requests_seconds_count[1m])
  ```
* **Latência em Percentil 95 (p95) por Endpoint:**
  ```promql
  histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))
  ```
* **Uso de Memória Heap da JVM (em Megabytes):**
  ```promql
  jvm_memory_used_bytes{area="heap"} / 1024 / 1024
  ```
* **Uso de CPU do Processo Java:**
  ```promql
  process_cpu_usage * 100
  ```
* **Status de Conectividade do Alvo de Scraping (deve ser 1 = UP):**
  ```promql
  up{job="oauth-service"}
  ```

---

## 6. Contrato Padronizado de Erros (Especificação da Disciplina)

Todas as respostas de falha da API seguem rigorosamente a estrutura JSON especificada pelo contrato da disciplina:

```json
{
  "error_code": "OA-400",
  "error_description": "Erro de validação nos dados enviados.",
  "error_source": "OAuthAPI.Users",
  "error_stack": [
    {
      "message": "username: deve ser um e-mail válido conforme RFC 5322"
    }
  ]
}
```

### Tabela de Mapeamento de Códigos de Erro:
| Código | Status HTTP | Significado |
|---|---|---|
| **`OA-400`** | `400 Bad Request` | Erro na estrutura do request, payload malformado ou e-mail fora da RFC 5322 |
| **`OA-401`** | `401 Unauthorized` | Credenciais inválidas no login ou Bearer token ausente/expirado |
| **`OA-403`** | `403 Forbidden` | Access token válido, porém sem permissão administrativa para o recurso |
| **`OA-404`** | `404 Not Found` | Usuário ou Role não localizado no Keycloak |
| **`OA-409`** | `409 Conflict` | Username ou nome de Role já existente no realm |
| **`OA-502`** | `502 Bad Gateway` | Falha ou timeout na comunicação entre a API OAuth e o Keycloak |

---

## 7. Catálogo Completo de Endpoints da API REST

Todas as rotas de negócio estão disponíveis na porta principal **`8181`** (`http://localhost:8181`).

### 7.1 Autenticação e Sessão
| Método | Rota | Cabeçalhos / Content-Type | Request Body | Response Body | Status |
|---|---|---|---|---|---|
| `POST` | `/login` | `application/x-www-form-urlencoded` ou `multipart/form-data` | `username`<br>`password` | `{ token_type, access_token, expires_in, refresh_token, refresh_expires_in }` | `201 Created` |
| `POST` | `/refresh-token` | `application/x-www-form-urlencoded` | `refresh_token` | `{ token_type, access_token, expires_in, refresh_token, refresh_expires_in }` | `200 OK` |

### 7.2 Gestão de Usuários (`Authorization: Bearer <access_token>`)
| Método | Rota | Descrição | Request Body | Response Body | Status |
|---|---|---|---|---|---|
| `POST` | `/users` | Criação de novo usuário | `{ username, password, first-name, last-name }` | `{ id, username, first-name, last-name, enabled }` | `201 Created` |
| `GET` | `/users` | Listagem de usuários (suporta filtro `?enabled=true\|false`) | *(Vazio)* | `[ { id, username, first-name, last-name, enabled } ]` | `200 OK` |
| `GET` | `/users/{id}` | Consulta de usuário por ID | *(Vazio)* | `{ id, username, first-name, last-name, enabled }` | `200 OK` |
| `PUT` | `/users/{id}` | Atualização completa dos atributos cadastrais | `{ username, first-name, last-name, enabled }` | *(Vazio)* | `200 OK` |
| `PATCH` | `/users/{id}` | Atualização e redefinição de senha | `{ password }` | *(Vazio)* | `200 OK` |
| `DELETE` | `/users/{id}` | Exclusão lógica (`enabled=false`) | *(Vazio)* | *(Vazio)* | `204 No Content` |

### 7.3 Gestão de Perfis / Roles (`Authorization: Bearer <access_token>`)
| Método | Rota | Descrição | Request Body | Response Body | Status |
|---|---|---|---|---|---|
| `POST` | `/roles` | Criação de role de realm | `{ name, description }` | `{ id, name, description, composite, clientRole, containerId }` | `201 Created` |
| `GET` | `/roles` | Listagem de todas as roles | *(Vazio)* | `[ { id, name, description, ... } ]` | `200 OK` |
| `GET` | `/roles/{id}` | Consulta de role por ID | *(Vazio)* | `{ id, name, description, ... }` | `200 OK` |
| `PUT` | `/roles/{id}` | Atualização completa de role | `{ name, description }` | *(Vazio)* | `200 OK` |
| `PATCH` | `/roles/{id}` | Atualização parcial (nome/descrição) | `{ name?, description? }` | *(Vazio)* | `200 OK` |
| `DELETE` | `/roles/{id}` | Remoção física da role | *(Vazio)* | *(Vazio)* | `204 No Content` |
| `POST` | `/users/{userId}/roles/{roleId}` | Atribui a role especificada ao usuário | *(Vazio)* | *(Vazio)* | `204 No Content` |
| `DELETE` | `/users/{userId}/roles/{roleId}` | Desassocia a role do usuário | *(Vazio)* | *(Vazio)* | `204 No Content` |

### 7.4 Endpoint de Saúde da API
| Método | Rota | Descrição | Resposta | Status |
|---|---|---|---|---|
| `GET` | `/health` | Checagem de disponibilidade na porta da API | `{ "status": "UP" }` | `200 OK` |

---

## 8. Documentação Interativa OpenAPI / Swagger UI

A aplicação expõe a especificação viva da API através do **SpringDoc OpenAPI 3.0**:

* **Interface Visual Interativa (Swagger UI):** [http://localhost:8181/docs](http://localhost:8181/docs)
* **Especificação OpenAPI em JSON:** [http://localhost:8181/v3/api-docs](http://localhost:8181/v3/api-docs)
* **Especificação OpenAPI em YAML:** [http://localhost:8181/v3/api-docs.yaml](http://localhost:8181/v3/api-docs.yaml)

A interface do Swagger permite autorizar chamadas diretamente via botão `Authorize` inserindo o token JWT, testar payloads em tempo real e visualizar os modelos completos de entrada e saída.

---

## 9. Instruções de Execução com Docker Compose

### 9.1 Pré-requisito: Volume Persistente do Keycloak
O `docker-compose.yml` da raiz utiliza um volume externo compartilhado para o banco de dados embutido do Keycloak. Execute este comando uma única vez caso o volume ainda não exista:

```bash
docker volume create constrsw-keycloak-data
```

### 9.2 Justificativa Técnica do docker-compose.base.override.yml

> ⚠️ **Por que usamos o arquivo de override?**  
> 1. **Compatibilidade do Healthcheck:** O arquivo compartilhado original (`docker-compose.yml` na raiz) possui um comando de healthcheck baseado em Node.js (`test: ["CMD", "node", "-e", ...]`). Como a nossa API é desenvolvida em **Java**, a imagem enxuta de execução JRE não possui o binário `node`, o que faria o Docker marcar erroneamente o container como `unhealthy`. O arquivo override ajusta o healthcheck para executar `curl -f http://127.0.0.1:3001/health`.  
> 2. **Integração do Prometheus:** Como o serviço do Prometheus ainda não foi unificado na branch principal do projeto compartilhado, o arquivo override declara o container oficial do **Prometheus** (`prom/prometheus:v2.54.0`), montando as configurações de scraping em tempo real da porta `9464`.

### 9.3 Subindo o Ambiente Completo do Zero

Execute os comandos a partir da raiz do projeto (`constru-sw-2026-2`):

```bash
# 1. Navegar até a raiz do repositório base (substitua pelo seu caminho local)
cd <caminho-para>/constru-sw-2026-2

# 2. Subir todos os serviços (Keycloak, OAuth e Prometheus)
docker compose \
  -f docker-compose.yml \
  -f backend/oauth/docker-compose.base.override.yml \
  up -d --build --force-recreate keycloak oauth prometheus
```

### 9.4 Verificação dos Serviços em Execução
```bash
# Verificar o status dos containers (todos devem estar 'Up' e 'healthy')
docker compose -f docker-compose.yml -f backend/oauth/docker-compose.base.override.yml ps

# Acompanhar os logs do microsserviço OAuth
docker compose logs -f oauth

# Acompanhar os logs do Prometheus
docker compose logs -f prometheus
```

---

## 10. Suítes de Testes Automatizados e Evidências

O projeto conta com **três níveis complementares de testes automatizados**, cobrindo desde regras unitárias até integração ponta a ponta:

### 10.1 Testes Unitários e de Integração (Maven / JUnit 5 / Mockito)
Executados diretamente no diretório do microsserviço:
```bash
cd <caminho-para>/constru-sw-2026-2/backend/oauth
mvn clean test
```
* **Resultado Comprovado:** **83 testes executados com 100% de sucesso (0 falhas, 0 erros)** cobrindo:
  * Controladores REST (`AuthControllerTest`, `UserControllerTest`, `RoleControllerTest`, `UserRoleControllerTest`, `HealthControllerTest`).
  * Tratamento de exceções e mapeamento do Keycloak (`GlobalExceptionHandlerTest`, `KeycloakErrorMapperTest`).
  * Segurança e interceptores 401/403 (`SecurityConfigTest`).
  * Validações de DTO e regras de formato RFC 5322.

### 10.2 Testes E2E Automatizados com Bruno CLI
A pasta [`bruno/`](./bruno/) contém **19 requisições organizadas e encadeadas**, validando todo o fluxo da API de forma automatizada:
```bash
cd <caminho-para>/constru-sw-2026-2/backend/oauth/bruno
cp -n environments/Local.bru.example environments/Local.bru
npx --yes @usebruno/cli@4.1.0 run --env Local --bail
```
* **Resultado:** **19/19 testes aprovados (`✓ PASS`)**, validando:
  * Autenticação via `application/x-www-form-urlencoded` e `multipart/form-data`.
  * Renovação de tokens via `POST /refresh-token`.
  * Criação, consulta, atualização e exclusão lógica de usuários.
  * Criação, consulta, atualização parcial (`PATCH`) e exclusão de roles.
  * Atribuição e desassociação de papéis a usuários.
  * Respostas negativas padronizadas (`OA-400`, `OA-401`, `OA-403`, `OA-404`, `OA-409`).

### 10.3 Coleção e Ambiente do Postman
Na pasta [`postman/`](./postman/) estão disponíveis os arquivos padrão prontos para importação no Postman Desktop ou Web:
* **Coleção:** [`postman/oauth.postman_collection.json`](./postman/oauth.postman_collection.json)
* **Ambiente:** [`postman/oauth.postman_environment.json`](./postman/oauth.postman_environment.json)

A coleção contém scripts de pré-requisito e testes automáticos que:
1. Extraem dinamicamente o `access_token` gerado em `/login` e o injetam automaticamente nas requisições seguintes.
2. Validam os códigos de status HTTP e os schemas JSON de resposta.
3. Incluem chamadas para os endpoints operacionais do Actuator e métricas do Prometheus.

---

## 11. Divisão de Responsabilidades e Rastreabilidade

O trabalho em equipe foi organizado com divisão clara de módulos e validações cruzadas:

| Membro | Principais Frentes Desenvolvidas |
|---|---|
| **Lucas Silva** | Camada completa de autenticação (`/login`, `/refresh-token`), integração com telemetria (OpenTelemetry e Prometheus), padronização de segurança em 401/403 no Spring Security e testes de integração. |
| **Anthony** | Camada completa de Gestão de Usuários e Roles (CRUD completo de users e roles, redefinição de senhas, validação RFC 5322 e mapeamentos de papéis). |
| **Arthur Mendes** | Infraestrutura base, configurações iniciais de rede (`KeycloakProperties`, `WebClientConfig`), sincronização de portas no compose e contratos de erro. |
| **João Biasoli** | Especificação dos modelos de domínio (Astah), suporte na coleção Postman, revisão e apresentação. |

---

*Documento formal do microsserviço OAuth — ConstrSW 2026/2.*
