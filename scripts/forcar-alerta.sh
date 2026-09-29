#!/usr/bin/env bash
#
# Script para simular ataque de forca bruta (senhas incorretas) e disparar
# o alerta HighLoginFailureRatio no Prometheus e Alertmanager.
#
# Uso:
#   ./scripts/forcar-alerta.sh
#

set -uo pipefail

OAUTH_URL="${OAUTH_URL:-http://localhost:8181}"
TARGET_USER="admin@pucrs.br"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
RESET='\033[0m'

echo -e "${BOLD}${BLUE}======================================================${RESET}"
echo -e "${BOLD} Simulação de Força Bruta — Disparar Alerta Prometheus${RESET}"
echo -e "${BOLD}${BLUE}======================================================${RESET}"
echo -e "Alvo: ${OAUTH_URL}/login"
echo -e "Usuário alvo: ${TARGET_USER}\n"

echo -e "${YELLOW}Passo 1/2: Enviando 30 tentativas consecutivas com senha errada...${RESET}"

for i in {1..30}; do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${OAUTH_URL}/login" \
    -d "username=${TARGET_USER}&password=senha-errada-${i}")
  printf "${RED}✖${RESET} Tentativa %02d: HTTP %s (401 esperado)\n" "$i" "$HTTP_CODE"
  sleep 0.1
done

echo -e "\n${GREEN}✓ 30 tentativas com erro enviadas!${RESET}"
echo -e "A taxa de falha de login agora ultrapassou 50% no Prometheus."

echo -e "\n${BOLD}${BLUE}== Acompanhe nos painéis agora ==${RESET}"
echo -e "1. ${BOLD}Grafana:${RESET}      http://localhost:3300"
echo -e "   -> Veja a linha vermelha subindo no painel 'Tentativas de login: sucesso vs falha'"
echo -e "2. ${BOLD}Prometheus:${RESET}   http://localhost:9090/alerts"
echo -e "   -> O alerta 'HighLoginFailureRatio' entrou em estado ${YELLOW}PENDING${RESET}"
echo -e "3. ${BOLD}Alertmanager:${RESET} http://localhost:9093"
echo -e "   -> Após 2 minutos de condição sustentada, o alerta vira ${RED}FIRING${RESET} e aparece aqui.\n"

echo -e "${YELLOW}Passo 2/2: Mantendo o tráfego de falha a cada 3s para sustentar os 2 minutos do alerta...${RESET}"
echo -e "(Pressione ${BOLD}Ctrl+C${RESET} a qualquer momento para parar)\n"

COUNT=30
while true; do
  COUNT=$((COUNT + 1))
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${OAUTH_URL}/login" \
    -d "username=${TARGET_USER}&password=senha-errada-${COUNT}")
  printf "[%s] Tentativa %d: HTTP %s | Alerta sustentado...\n" "$(date +%H:%M:%S)" "$COUNT" "$HTTP_CODE"
  sleep 3
done
