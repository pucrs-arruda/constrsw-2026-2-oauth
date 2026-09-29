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
npx --yes @usebruno/cli run --env Local

# Da raiz do projeto:
npx --yes @usebruno/cli run backend/oauth/bruno --env Local
```

### Alerta do Prometheus: `HighLoginFailureRatio`
* **Métrica:** `oauth_login_attempts_total` (métrica própria de negócio).
* **Regra:** Dispara se a taxa de falha de login passar de 50% por 2 minutos (`for: 2m`).

### Script para Forçar o Alerta no Terminal
Você pode rodar o script interativo pronto:
```bash
./scripts/forcar-alerta.sh
```

Ou colar diretamente o loop rápido:
```bash
for i in {1..30}; do
  curl -s -X POST http://localhost:8181/login -d "username=admin@pucrs.br&password=senha-errada" > /dev/null
done
echo "30 falhas de login geradas com sucesso!"
```

### Ordem para Apresentar na Hora:
1. Rode o script acima no terminal (ou clique 15x em `05 - Login Invalido` no Bruno).
2. Abra o **Grafana** (http://localhost:3300): mostre a linha vermelha disparando no painel *"Tentativas de login: sucesso vs falha"*.
3. Abra o **Prometheus** (http://localhost:9090/alerts): mostre o alerta `HighLoginFailureRatio` em amarelo (`Pending`). Explique que aguarda 2 min para evitar falsos positivos.
4. Após 2 min: mostre o alerta vermelho (`Firing`) e abra o **Alertmanager** (http://localhost:9093) para mostrar o alerta ativo e agrupado.

