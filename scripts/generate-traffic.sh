#!/usr/bin/env bash
# Gera tráfego variado contra a API oauth para popular o dashboard do Grafana
# durante a apresentação: CRUD de usuários/roles (admin), 403 (student),
# 401 (sem token / token inválido), 404, 409 e logins.
#
# Uso: ./scripts/generate-traffic.sh [segundos]    (padrão: 240)
# Logins com senha errada (e o alerta de força bruta) ficam em
# ./scripts/trigger-login-alert.sh.
# Os usuários de demonstração criados terminam desabilitados; as roles, removidas.
B=${BASE_URL:-http://localhost:8181}
DUR=${1:-240}
END=$((SECONDS + DUR))
tok() { curl -s -d "username=${1:-admin@pucrs.br}&password=a12345678" $B/login | python3 -c "import json,sys;print(json.load(sys.stdin)['access_token'])"; }
T=$(tok); ST=$(tok student@pucrs.br); n=0
while [ $SECONDS -lt $END ]; do
  n=$((n+1))
  [ $((n % 40)) -eq 0 ] && T=$(tok) && ST=$(tok student@pucrs.br)
  H="Authorization: Bearer $T"
  for i in 1 2 3; do curl -s -o /dev/null -H "$H" $B/users & done
  curl -s -o /dev/null -H "$H" "$B/users?enabled=true" &
  curl -s -o /dev/null -H "$H" $B/roles &
  curl -s -o /dev/null -H "$H" $B/users/00000000-0000-0000-0000-000000000000 &   # 404
  curl -s -o /dev/null $B/users &                                                  # 401 sem token
  curl -s -o /dev/null -H "Authorization: Bearer $ST" $B/users &                  # 403 sem permissão
  curl -s -o /dev/null -H "Authorization: Bearer abc.def.ghi" $B/roles &          # 401 token inválido
  wait
  if [ $((n % 3)) -eq 0 ]; then
    curl -s -o /dev/null -d "username=admin@pucrs.br&password=a12345678" $B/login
  fi
  if [ $((n % 4)) -eq 0 ]; then
    R="demo-role-$RANDOM"
    RID=$(curl -s -H "$H" -H 'Content-Type: application/json' -d "{\"name\":\"$R\",\"description\":\"demo\"}" $B/roles | python3 -c "import json,sys;print(json.load(sys.stdin).get('id',''))")
    curl -s -o /dev/null -X PATCH -H "$H" -H 'Content-Type: application/json' -d '{"description":"editada"}' $B/roles/$RID
    curl -s -o /dev/null -H "$H" $B/roles/$RID
    curl -s -o /dev/null -X DELETE -H "$H" $B/roles/$RID
  fi
  if [ $((n % 6)) -eq 0 ]; then
    E="demo.$RANDOM$RANDOM@example.com"
    UID_=$(curl -s -H "$H" -H 'Content-Type: application/json' -d "{\"username\":\"$E\",\"password\":\"senha-forte-123\",\"first-name\":\"Demo\",\"last-name\":\"Grafana\"}" $B/users | python3 -c "import json,sys;print(json.load(sys.stdin).get('id',''))")
    curl -s -o /dev/null -H "$H" -H 'Content-Type: application/json' -d "{\"username\":\"$E\",\"password\":\"senha-forte-123\",\"first-name\":\"Demo\",\"last-name\":\"Grafana\"}" $B/users  # 409
    RN=$(curl -s -H "$H" $B/roles | python3 -c "import json,sys;print([r['id'] for r in json.load(sys.stdin) if r['name']=='user'][0])" 2>/dev/null)
    [ -n "$RN" ] && curl -s -o /dev/null -X POST -H "$H" $B/users/$UID_/roles/$RN && curl -s -o /dev/null -X DELETE -H "$H" $B/users/$UID_/roles/$RN
    curl -s -o /dev/null -X PATCH -H "$H" -H 'Content-Type: application/json' -d '{"password":"outra-senha-456"}' $B/users/$UID_
    curl -s -o /dev/null -X DELETE -H "$H" $B/users/$UID_
  fi
  sleep 0.5
done
echo "iterações: $n"
