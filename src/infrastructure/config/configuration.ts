export default () => ({
  // OAUTH_INTERNAL_API_PORT: nome usado no docker-compose.yml oficial do
  // professor (infrastructure/dev.local + raiz do repo). Mantemos PORT como
  // fallback para quem rodar a API fora do compose (npm run start:dev).
  port: parseInt(
    process.env.OAUTH_INTERNAL_API_PORT ?? process.env.PORT ?? '3001',
    10,
  ),
  keycloak: {
    // KEYCLOAK_SERVER_URL ja vem pronto (protocolo+host+porta) do .env da
    // raiz, ex.: http://keycloak:8080 - sem prefixo /auth (Keycloak 26.0.1,
    // Quarkus, caminho padrao).
    baseUrl: process.env.KEYCLOAK_SERVER_URL ?? 'http://localhost:8080',
    realm: process.env.KEYCLOAK_REALM ?? 'constrsw',
    clientId: process.env.KEYCLOAK_CLIENT_ID ?? 'oauth',
    clientSecret: process.env.KEYCLOAK_CLIENT_SECRET ?? '',
  },
});
