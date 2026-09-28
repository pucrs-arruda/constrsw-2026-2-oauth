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
