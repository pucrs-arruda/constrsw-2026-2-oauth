// Deterministic environment for the integration suite. Set before AppModule is
// imported so the zod validation in ConfigModule succeeds without a real .env.
// The Keycloak boundary is faked per-test via global.fetch (see the spec files),
// so none of these hosts are ever actually contacted.
process.env.PORT = process.env.PORT ?? "8088";
process.env.NODE_ENV = "test";
process.env.KEYCLOAK_URL = "http://keycloak.test";
process.env.KEYCLOAK_REALM = "constrsw";
process.env.KEYCLOAK_CLIENT_ID = "oauth";
process.env.KEYCLOAK_CLIENT_SECRET = "secret";
process.env.KEYCLOAK_TIMEOUT_MS = "5000";
process.env.KEYCLOAK_ADMIN_CLIENT_ID = "oauth-admin";
process.env.KEYCLOAK_ADMIN_CLIENT_SECRET = "admin-secret";
process.env.SESSION_COOKIE_NAME = "session";
process.env.COOKIE_SECURE = "false";
process.env.COOKIE_SAME_SITE = "lax";
// OpenTelemetry never starts in tests (AppModule doesn't import tracing), but be explicit.
process.env.OTEL_ENABLED = "false";

// Log de início por teste: o nome completo vem do estado do Jest, então o
// terminal mostra exatamente qual caso está rodando antes de cada asserção.
beforeEach(() => {
  const name = expect.getState().currentTestName ?? "(teste sem nome)";
  process.stdout.write(`\n[E2E] ▶ Iniciando: ${name}\n`);
});
