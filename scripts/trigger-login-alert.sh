#!/usr/bin/env bash
#
# Simula um ataque de forca bruta no POST /login para disparar o alerta
# HighLoginFailureRatio (mais de 50% de falhas de login por 2 minutos) e
# acompanha o estado dele no Prometheus e no Alertmanager ate virar firing.
#
# As senhas erradas vao para um usuario inexistente: a protecao contra forca
# bruta do Keycloak bloquearia temporariamente uma conta real (ex.: admin)
# depois de 5 falhas, o que atrapalharia o resto da apresentacao. Os logins
# corretos intercalados usam o admin, para o grafico mostrar os dois lados.
#
# Uso:
#   ./scripts/trigger-login-alert.sh          # ate o alerta disparar (max. 5 min)
#   ./scripts/trigger-login-alert.sh 120      # limite de tempo em segundos

set -uo pipefail

OAUTH_URL="${OAUTH_URL:-http://localhost:8181}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
ALERTMANAGER_URL="${ALERTMANAGER_URL:-http://localhost:9093}"
MAX_SECONDS="${1:-300}"
ALERT="HighLoginFailureRatio"

if [[ ! "$MAX_SECONDS" =~ ^[1-9][0-9]*$ ]] || (( ${#MAX_SECONDS} > 5 )); then
  echo "Uso: $0 [segundos inteiros entre 1 e 99999]" >&2
  exit 2
fi

ATTACKER_USER="oauth-alert-probe@example.invalid"
ADMIN_USER="admin@pucrs.br"
ADMIN_PASS="a12345678"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[0;33m'
BOLD=$'\033[1m'; RESET=$'\033[0m'

alert_state() {
  curl -fsS --connect-timeout 3 --max-time 10 "${PROMETHEUS_URL}/api/v1/alerts" | python3 -c "
import json, sys
alerts = [a for a in json.load(sys.stdin)['data']['alerts'] if a['labels']['alertname'] == '${ALERT}']
print(alerts[0]['state'] if alerts else 'inactive')" 2>/dev/null || echo "?"
}

failure_ratio() {
  curl -fsS --connect-timeout 3 --max-time 10 --get "${PROMETHEUS_URL}/api/v1/query" \
    --data-urlencode 'query=sum(oauth:login_failure_ratio:rate5m)' | python3 -c "
import json, sys
r = json.load(sys.stdin)['data']['result']
print(f\"{float(r[0]['value'][1]) * 100:.0f}%\" if r else '-')" 2>/dev/null || echo "-"
}

echo "${BOLD}Simulando força bruta em ${OAUTH_URL}/login${RESET}"
echo "Alerta: ${ALERT} (> 50% de falhas por 2 min). Limite: ${MAX_SECONDS}s. Ctrl+C para parar."
echo

END=$((SECONDS + MAX_SECONDS))
LAST_STATE=""
i=0
while (( SECONDS < END )); do
  i=$((i + 1))
  for _ in 1 2 3; do
    CODE=$(curl -sS --connect-timeout 3 --max-time 10 -o /dev/null -w '%{http_code}' \
      -X POST "${OAUTH_URL}/login" \
      --data-urlencode "username=${ATTACKER_USER}" \
      --data-urlencode "password=chute${i}${RANDOM}") || exit 1
    if [[ "$CODE" != "401" ]]; then
      echo "Falha na simulação: login inválido retornou HTTP ${CODE}; esperado 401." >&2
      exit 1
    fi
  done
  CODE=$(curl -sS --connect-timeout 3 --max-time 10 -o /dev/null -w '%{http_code}' \
    -X POST "${OAUTH_URL}/login" \
    --data-urlencode "username=${ADMIN_USER}" \
    --data-urlencode "password=${ADMIN_PASS}") || exit 1
  if [[ "$CODE" != "201" ]]; then
    echo "Login do administrador retornou HTTP ${CODE}; esperado 201. Verifique as credenciais e a conta." >&2
    exit 1
  fi

  if (( i % 5 == 0 )); then
    STATE=$(alert_state)
    if [[ "$STATE" != "$LAST_STATE" ]]; then
      case "$STATE" in
        pending) COLOR=$YELLOW ;;
        firing)  COLOR=$RED ;;
        *)       COLOR=$GREEN ;;
      esac
      printf "\n  [%3ss] alerta %s%s%s (falha de login: %s)" "$SECONDS" "$COLOR" "$STATE" "$RESET" "$(failure_ratio)"
      LAST_STATE=$STATE
    else
      printf "."
    fi
    if [[ "$STATE" == "firing" ]]; then
      echo
      echo
      echo "${RED}${BOLD}🚨 ${ALERT} disparou!${RESET}"
      echo "  Prometheus   → ${PROMETHEUS_URL}/alerts"
      echo "  Alertmanager → ${ALERTMANAGER_URL}"
      echo "  Grafana      → seção Segurança (linha do tempo dos alertas e anotação vermelha)"
      echo
      echo "Sem novas falhas, o alerta é resolvido sozinho em alguns minutos."
      exit 0
    fi
  fi
  sleep 1
done

echo
echo "Tempo esgotado sem o alerta disparar (estado atual: $(alert_state))."
exit 1
