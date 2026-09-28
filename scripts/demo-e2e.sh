#!/usr/bin/env bash
#
# Script de demonstracao fim a fim do microsservico oauth (Grupo 08).
# Roda o fluxo real de login/RBAC contra a API e narra cada passo, com
# PASS/FAIL colorido - pensado pra ser executado ao vivo na apresentacao
# em vez de digitar cada curl na mao.
#
# Uso:
#   ./scripts/demo-e2e.sh                # so os testes funcionais (rapido)
#   ./scripts/demo-e2e.sh --with-alert    # tambem gera trafego de forca
#                                         # bruta pra disparar o alerta no
#                                         # Alertmanager (leva ~2min)
#
# Pre-requisito: stack no ar (ver README / runbook) e `python3` disponivel
# no PATH (usado so pra parsear o JSON do token, sem dependencias extras).

set -uo pipefail

OAUTH_URL="${OAUTH_URL:-http://localhost:8181}"
ADMIN_USER="admin@pucrs.br"
ADMIN_PASS="a12345678"
STUDENT_USER="student@pucrs.br"
STUDENT_PASS="a12345678"

WITH_ALERT=0
[[ "${1:-}" == "--with-alert" ]] && WITH_ALERT=1

# --- cores ---------------------------------------------------------------
RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[0;33m'
BLUE=$'\033[0;34m'; BOLD=$'\033[1m'; RESET=$'\033[0m'

PASS_COUNT=0
FAIL_COUNT=0

section() {
  echo
  echo "${BOLD}${BLUE}== $1 ==${RESET}"
}

say() {
  echo "${YELLOW}  → $1${RESET}"
}

expect() {
  # expect "descricao" "esperado" "obtido"
  local desc="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo "  ${GREEN}✓ PASS${RESET}  ${desc} (HTTP ${actual})"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  ${RED}✗ FAIL${RESET}  ${desc} (esperado ${expected}, obtido ${actual})"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

login() {
  # login <username> <password>  -> imprime o access_token no stdout
  curl -s -X POST "${OAUTH_URL}/login" \
    -d "username=$1&password=$2" \
    | python3 -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null
}

http_status() {
  # http_status <curl args...> -> so o codigo HTTP, descarta o corpo
  curl -s -o /dev/null -w "%{http_code}" "$@"
}

echo "${BOLD}Demonstração fim a fim — oauth (Grupo 08)${RESET}"
echo "Alvo: ${OAUTH_URL}"

# --- 1. health -------------------------------------------------------------
section "1. Health check"
STATUS=$(http_status "${OAUTH_URL}/health")
expect "GET /health" "200" "$STATUS"

# --- 2. login admin ----------------------------------------------------------
section "2. Login como admin (grant_type=password)"
ADMIN_TOKEN=$(login "$ADMIN_USER" "$ADMIN_PASS")
if [[ -n "$ADMIN_TOKEN" ]]; then
  echo "  ${GREEN}✓ PASS${RESET}  Token recebido (${#ADMIN_TOKEN} caracteres)"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo "  ${RED}✗ FAIL${RESET}  Não recebeu token — confira usuário/senha e se o Keycloak está de pé"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --- 3. sem token -> 401 -----------------------------------------------------
section "3. GET /users sem token"
STATUS=$(http_status "${OAUTH_URL}/users")
expect "sem Authorization" "401" "$STATUS"

# --- 4. com token de admin -> 200 --------------------------------------------
section "4. GET /users com token de admin"
STATUS=$(http_status "${OAUTH_URL}/users" -H "Authorization: Bearer ${ADMIN_TOKEN}")
expect "com Bearer de admin" "200" "$STATUS"

# --- 5. login student ---------------------------------------------------------
section "5. Login como student"
STUDENT_TOKEN=$(login "$STUDENT_USER" "$STUDENT_PASS")
if [[ -n "$STUDENT_TOKEN" ]]; then
  echo "  ${GREEN}✓ PASS${RESET}  Token recebido"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo "  ${RED}✗ FAIL${RESET}  Não recebeu token pra student"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --- 6. student tentando criar role -> 403 -----------------------------------
section "6. RBAC: student tentando POST /roles"
STATUS=$(http_status -X POST "${OAUTH_URL}/roles" \
  -H "Authorization: Bearer ${STUDENT_TOKEN}" -H "Content-Type: application/json" \
  -d '{"name":"tentativa-nao-autorizada","description":"nao deveria existir"}')
expect "student sem permissão de admin" "403" "$STATUS"

# --- 7. admin cria role de teste -> 201, depois remove ------------------------
section "7. CRUD completo: admin cria e remove uma role de teste"
ROLE_NAME="demo-e2e-$(date +%s)"
CREATE_BODY=$(curl -s -X POST "${OAUTH_URL}/roles" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" \
  -d "{\"name\":\"${ROLE_NAME}\",\"description\":\"criada pelo script de demo\"}")
ROLE_ID=$(echo "$CREATE_BODY" | python3 -c "import sys,json;print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
if [[ -n "$ROLE_ID" ]]; then
  echo "  ${GREEN}✓ PASS${RESET}  Role '${ROLE_NAME}' criada (id ${ROLE_ID})"
  PASS_COUNT=$((PASS_COUNT + 1))
  STATUS=$(http_status -X DELETE "${OAUTH_URL}/roles/${ROLE_ID}" -H "Authorization: Bearer ${ADMIN_TOKEN}")
  expect "DELETE /roles/${ROLE_ID} (limpeza)" "204" "$STATUS"
else
  echo "  ${RED}✗ FAIL${RESET}  Não conseguiu criar a role de teste"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --- resumo -------------------------------------------------------------------
section "Resumo"
echo "  ${GREEN}${PASS_COUNT} passaram${RESET}, ${RED}${FAIL_COUNT} falharam${RESET}"

if [[ "$FAIL_COUNT" -gt 0 ]]; then
  echo
  echo "  ${RED}Algo falhou — confira se a stack está de pé (docker compose ps) antes de seguir.${RESET}"
fi

echo
echo "${BOLD}Agora é ir pro navegador:${RESET}"
echo "  Prometheus targets   → ${OAUTH_URL/8181/9090}/targets"
echo "  Prometheus rules     → ${OAUTH_URL/8181/9090}/rules"
echo "  Grafana              → ${OAUTH_URL/8181/3300}  (admin/admin)"
echo "  Alertmanager         → ${OAUTH_URL/8181/9093}"
echo
echo "Cada requisição que acabou de rodar já apareceu nas métricas — é só abrir"
echo "o Grafana e apontar pro painel 'Requisições por status HTTP' se mexendo."

# --- opcional: gera trafego pra disparar o alerta de forca bruta -------------
if [[ "$WITH_ALERT" -eq 1 ]]; then
  section "Gerando tráfego de força bruta (--with-alert)"
  say "Isso vai rodar por um tempo, gerando ~75% de falha de login."
  say "O alerta HighLoginFailureRatio precisa de 2 minutos de condição sustentada pra disparar."
  say "Deixe rodando em segundo plano e continue a apresentação — ele dispara sozinho."
  echo
  for i in $(seq 1 40); do
    curl -s -o /dev/null -X POST "${OAUTH_URL}/login" -d "username=${ADMIN_USER}&password=errada${i}"
    if (( i % 3 == 0 )); then
      curl -s -o /dev/null -X POST "${OAUTH_URL}/login" -d "username=${ADMIN_USER}&password=${ADMIN_PASS}"
    fi
    printf "."
    sleep 1
  done
  echo
  say "Tráfego gerado. Acompanhe em ${OAUTH_URL/8181/9090}/alerts (pending → firing) e depois em ${OAUTH_URL/8181/9093}."
fi

[[ "$FAIL_COUNT" -eq 0 ]] && exit 0 || exit 1
