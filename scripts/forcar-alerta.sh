#!/usr/bin/env bash
# Atalho em português para a simulação de falhas de login.
# Uso: ./scripts/forcar-alerta.sh [limite-em-segundos] (padrão: 300)
# Usa um usuário inexistente para não bloquear a conta do administrador.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/trigger-login-alert.sh" "$@"
