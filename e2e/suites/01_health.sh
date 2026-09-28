# GET /health, unknown routes, and the optional Prometheus scrape port.
suite "health"

api GET /health
assert_status 200 "GET /health"
assert_json '.status' 'ok' "GET /health reports status ok"

api GET /this-route-does-not-exist
assert_error 404 "*" "unknown route uses the OA envelope"

if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$METRICS_URL/metrics")" = "200" ]; then
  pass "metrics endpoint reachable at $METRICS_URL/metrics"
else
  printf '  - metrics endpoint not reachable at %s (skipped)\n' "$METRICS_URL"
fi
