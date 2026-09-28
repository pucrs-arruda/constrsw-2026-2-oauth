# infra-local (patches locais para o compose do professor)

Esta pasta **não faz parte da API `oauth`** em si — é infraestrutura auxiliar
para rodar o `docker-compose.yml` oficial do professor localmente em Mac com
Apple Silicon recente (M4), sem depender de nenhuma alteração na `base`
(onde o grupo03 não tem permissão de commit).

## Problemas contornados

1. **SIGILL da JVM do Keycloak** — bug conhecido do OpenJDK 21 em macOS
   15.2+ com Apple Silicon recente, relacionado a detecção de SVE. Ver
   [keycloak#36008](https://github.com/keycloak/keycloak/issues/36008) e
   JDK-8345296. Contornado com a env var `JAVA_TOOL_OPTIONS=-XX:UseSVE=0`.
2. **Healthcheck do Keycloak depende de `curl`** — a imagem oficial do
   Keycloak 26 não tem `curl` nem `dnf`/`microdnf` para instalá-lo (o
   Dockerfile do professor contornava isso sobrepondo o rootfs de uma imagem
   `redhat/ubi9`, o que por sua vez é a causa do problema 1 acima, em Mac
   Apple Silicon). Contornado com um healthcheck via `/dev/tcp` (builtin do
   bash) em vez de `curl`.
3. **`prometheus.yml` central aponta pro hostname errado** — o arquivo
   oficial do professor (`infrastructure/dev.local/services/prometheus/
   prometheus.yml`, repo `base`, commit `e201543`) tem o job `auth` (e uma
   entrada do job `health-checks`) apontando pro hostname `auth`, mas o
   nosso serviço no `docker-compose.yml` se chama `oauth` — sem correção, o
   Prometheus nunca resolve o DNS e o job fica `down` pra sempre, em
   **qualquer** máquina (não é bug de Apple Silicon, é um erro de
   configuração que sobrou de antes do serviço ser renomeado de `auth` pra
   `oauth`, em 2026-09-02). Como não temos permissão de commit na `base`,
   corrigido com uma cópia local do arquivo
   (`backend/oauth/infra-local/prometheus.yml`, só com o hostname trocado),
   montada por cima do arquivo oficial via `docker-compose.override.yml`.

## Como usar

A partir da raiz do repo `base` (T1):

```bash
# so na primeira vez (o compose oficial usa volumes externos)
docker volume create constrsw-keycloak-data
docker volume create constrsw-prometheus-data

docker compose -f docker-compose.yml -f backend/oauth/docker-compose.override.yml up -d --build
```

Isso soma o `docker-compose.override.yml` (que mora aqui, dentro de
`backend/oauth`) por cima do `docker-compose.yml` oficial, sem alterar
nenhum arquivo da `base`. Os arquivos oficiais do professor
(`docker-compose.yml`, `infrastructure/dev.local/services/keycloak/Dockerfile`
e `infrastructure/dev.local/services/prometheus/prometheus.yml`) continuam
intocados — só usamos `volumes:`/`build.dockerfile` no override pra
substituir, dentro do container, o que precisa de correção.

Pra conferir que a correção do Prometheus pegou, depois do `up`:

```bash
curl -s 'http://localhost:9090/api/v1/targets' | grep -o '"scrapeUrl":"[^"]*9464[^"]*"'
# esperado: "scrapeUrl":"http://oauth:9464/metrics"  (nao "auth:9464")
```

## Reportado ao professor

Os dois primeiros problemas foram reportados como bugs de ambiente (Docker
Desktop + macOS + Apple Silicon recente), não como erro na configuração
dele — os outros grupos com Mac M4 (ou similar) provavelmente vão precisar
do mesmo contorno. O terceiro (hostname `auth`/`oauth` no `prometheus.yml`)
é um erro de configuração dele mesmo, independente de máquina — vale
avisar separadamente, já que afeta todo mundo que rodar o compose oficial
como está, em qualquer sistema operacional.
