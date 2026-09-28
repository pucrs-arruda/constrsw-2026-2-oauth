# Guia de Monitoramento e Observabilidade — OAuth Microservice

Este documento orienta como consumir e visualizar as métricas do microserviço **OAuth / Keycloak** utilizando **Prometheus** e **Grafana**.

---

## 1. Métricas Expostas pelo Microserviço

O microserviço expõe suas métricas no endpoint público `GET /metrics` no padrão **OpenMetrics / Prometheus Exposition Format (v0.0.4)**.

### A. Tráfego e Latência HTTP (Padrão RED)
* `http_requests_total{method="...", route="...", status="..."}`: Contador acumulado de requisições HTTP recebidas por rota e código de resposta.
* `http_request_duration_seconds_count{method="...", route="..."}`: Número de execuções mensuradas para cálculo de latência.
* `http_request_duration_seconds_sum{method="...", route="..."}`: Soma acumulada do tempo de resposta (em segundos) por rota.

### B. Métricas Especializadas de Negócio e Segurança
* `oauth_logins_total{status="success|failure"}`: Contagem de tentativas de login efetuadas (útil para detectar tentativas de força bruta ou falhas em massa).
* `oauth_token_refreshes_total{status="success|failure"}`: Contagem de renovações de tokens de acesso via refresh token.
* `oauth_authorizations_total{result="granted|denied|error"}`: Contagem de avaliações de permissão na matriz RBAC (`granted` = 200 OK, `denied` = 403 Forbidden).

### C. Runtime e Informações do Serviço
* `oauth_service_info{service="oauth", framework="symfony", architecture="hexagonal", realm="constrsw"}`: Metadados descritivos da instância.
* `php_info{version="8.2.x", sapi="cli-server"}`: Versão do interpretador PHP.
* `php_memory_bytes`: Quantidade de memória RAM alocada pelo PHP em tempo real.
* `php_memory_peak_bytes`: Pico histórico de memória RAM consumida pelo processo.

---

## 2. Como Configurar no Prometheus

Caso você (ou a equipe de infraestrutura da turma) vá configurar o Prometheus para raspar este microserviço, basta adicionar o seguinte bloco ao arquivo `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'oauth'
    metrics_path: '/metrics'
    scrape_interval: 10s
    static_configs:
      # Use oauth:3001 na rede Docker interna, ou localhost:8181 fora do Docker
      - targets: ['oauth:3001']
        labels:
          service: 'oauth'
          environment: 'dev'
```

### Regras de Gravação Recomendadas (Recording Rules)
Para acelerar consultas em dashboards com alto volume de dados:
```yaml
groups:
  - name: oauth_recording_rules
    interval: 15s
    rules:
      - record: job:http_requests_total:rate1m
        expr: sum(rate(http_requests_total{job="oauth"}[1m])) by (job, route)

      - record: job:http_request_duration_seconds:avg5m
        expr: >-
          sum(rate(http_request_duration_seconds_sum{job="oauth"}[5m])) by (job, route)
          /
          clamp_min(sum(rate(http_request_duration_seconds_count{job="oauth"}[5m])) by (job, route), 0.001)

      - record: job:http_requests_error_ratio:rate5m
        expr: >-
          sum(rate(http_requests_total{job="oauth", status=~"5.."}[5m]))
          /
          clamp_min(sum(rate(http_requests_total{job="oauth"}[5m])), 0.001)
```

### Regras de Alerta Recomendadas (Alerting Rules)
```yaml
groups:
  - name: oauth_alerts
    rules:
      - alert: OAuthServiceDown
        expr: up{job="oauth"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Microserviço OAuth está fora do ar"
          description: "O serviço OAuth não responde ao scraper do Prometheus há mais de 1 minuto."

      - alert: OAuthHigh5xxErrorRate
        expr: (sum(rate(http_requests_total{job="oauth", status=~"5.."}[5m])) / clamp_min(sum(rate(http_requests_total{job="oauth"}[5m])), 0.001)) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Taxa de erro 5xx elevada no OAuth (> 5%)"
          description: "O microserviço está gerando erros internos contínuos."

      - alert: OAuthPossibleBruteForceAttack
        expr: sum(rate(oauth_logins_total{status="failure"}[1m])) * 60 > 30
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Pico de falhas de autenticação"
          description: "Mais de 30 falhas de login por minuto detectadas."
```

---

## 3. Como Importar o Dashboard no Grafana (2 Passos)

O repositório já inclui um arquivo de dashboard pronto para uso em:  
[`docs/monitoring/grafana-dashboard.json`](grafana-dashboard.json)

### Passo a Passo:
1. Acesse o Grafana no navegador em `http://localhost:3300` (Login padrão: `admin` / Senha: `admin`).
2. No menu lateral esquerdo, clique no ícone **Dashboards** → **New** → **Import** (ou acesse diretamente `http://localhost:3300/dashboard/import`).
3. Clique no botão **Upload JSON file** e selecione o arquivo `docs/monitoring/grafana-dashboard.json` (ou abra o arquivo, copie o conteúdo e cole na caixa *"Import via panel json"*).
4. No campo **Prometheus**, certifique-se de que a sua fonte de dados Prometheus está selecionada e clique em **Import**.

### Painéis Inclusos no Dashboard:
* **Status da API:** Indicador visual de saúde (ONLINE / OFFLINE).
* **Throughput Geral (RPS):** Total de requisições por segundo recebidas pelo serviço.
* **Taxa de Erro (%):** Percentual de erros 5xx em relação ao total de requisições.
* **Latência Média (ms):** Tempo médio de resposta da API.
* **Consumo de Memória PHP (MB):** Memória alocada pelo runtime PHP.
* **Tráfego por Rota e Método:** Gráfico temporal exibindo o volume em `/login`, `/users`, `/roles`, `/authorize`, etc.
* **Latência por Rota:** Tempo médio de resposta segmentado por cada endpoint.
* **Segurança e Logins:** Gráfico temporal comparando logins bem-sucedidos vs falhos.
* **Avaliações RBAC:** Gráfico de acessos autorizados (`200 OK`) vs acessos negados (`403 Forbidden`).
* **Distribuição de Status:** Donut chart com proporção de respostas 200, 201, 204, 400, 401, 403, 404, 500.

---

## 4. Teste Rápido no Terminal

Para verificar as métricas diretamente sem precisar abrir o Grafana:

```bash
# Verificação rápida com curl
curl -s http://localhost:8181/metrics

# Filtrar apenas contadores de negócio
curl -s http://localhost:8181/metrics | grep -E "(oauth_|http_requests_total)"
```
