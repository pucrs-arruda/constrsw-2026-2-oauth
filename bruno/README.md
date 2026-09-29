# Colecao Bruno - OAuth Grupo 08

Esta colecao testa os endpoints implementados no servico OAuth sem precisar
montar manualmente headers, formularios ou copiar o refresh token.

## Preparar os servicos

Na raiz do repositorio base, execute:

```bash
docker compose \
  -f docker-compose.yml \
  -f backend/oauth/docker-compose.base.override.yml \
  up -d --build keycloak oauth
```

## Usar no aplicativo Bruno

Copie o modelo de ambiente uma vez, a partir de `backend/oauth/bruno`:

```bash
cp environments/Local.bru.example environments/Local.bru
```

O arquivo `Local.bru` é ignorado pelo Git. O modelo contém somente os dados
de demonstração; credenciais locais devem ficar na cópia ignorada.

1. Clique em **Open Collection** e selecione a pasta `backend/oauth/bruno`.
2. Selecione o ambiente **Local** no canto superior direito.
3. Execute as requisicoes na ordem numerica ou use **Run Collection**.

As requisicoes `02` e `03` salvam `accessToken` e `refreshToken` apenas como
variaveis temporarias de runtime. A requisicao `04` usa o `refreshToken`
automaticamente e salva os tokens renovados.

As requisicoes `05`, `06` e `07` sao testes negativos: os status esperados
sao, respectivamente, `401`, `400` e `400`.

## Usar pela linha de comando

Dentro desta pasta, use a mesma versao da CLI com a qual a colecao foi
validada:

```bash
npx --yes @usebruno/cli@4.1.0 run --env Local --bail
```

O ambiente Local usa o usuario administrador provisionado na infraestrutura
central da disciplina: `admin@pucrs.br` / `a12345678`.

---

## Guia Rápido de Apresentação

### Credenciais
* **Admin (padrão):** `admin@pucrs.br` / `a12345678`
* **Usuário criado no Bruno (08/12):** `pessoa4.user@example.com` / `pessoa4-new-password`

### Comandos de Teste E2E (19/19)
```bash
# Na pasta backend/oauth/bruno:
npx --yes @usebruno/cli@4.1.0 run --env Local --bail

# Da raiz do projeto:
(cd backend/oauth/bruno && npx --yes @usebruno/cli@4.1.0 run --env Local --bail)
```

### Alerta do Prometheus: `HighLoginFailureRatio`
* **Métrica:** `oauth_login_attempts_total` (métrica própria de negócio).
* **Regra:** Dispara se a taxa de falha de login passar de 50% por 2 minutos (`for: 2m`).

### Script para Forçar o Alerta no Terminal
Na pasta `backend/oauth`, execute o script. Ele acompanha o estado real do
alerta por até 300 segundos e retorna erro se o alerta não disparar:
```bash
./scripts/forcar-alerta.sh
```

`forcar-alerta.sh` é um atalho para `trigger-login-alert.sh`. As tentativas
inválidas usam um usuário inexistente, evitando bloquear o administrador.
Também é possível passar um limite de tempo: `./scripts/forcar-alerta.sh 180`.

### Ordem para Apresentar na Hora:
1. Rode o script acima no terminal e mantenha-o ativo até o alerta disparar.
2. Abra o **Grafana** (http://localhost:3300): mostre a linha vermelha disparando no painel *"Tentativas de login: sucesso vs falha"*.
3. Abra o **Prometheus** (http://localhost:9090/alerts): mostre o alerta `HighLoginFailureRatio` em amarelo (`Pending`). Explique que aguarda 2 min para evitar falsos positivos.
4. Após a condição permanecer acima de 50% por 2 min, mostre o alerta vermelho (`Firing`) e abra o **Alertmanager** (http://localhost:9093). O tempo total depende do tráfego anterior e dos intervalos de coleta e avaliação.
