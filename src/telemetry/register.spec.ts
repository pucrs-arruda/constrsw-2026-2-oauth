const METRICS_PORT = 19464;
const PASSWORD = 'pw-sentinel-do-not-leak';
const CLIENT_SECRET = 'client-secret-sentinel-do-not-leak';
const ACCESS_TOKEN = 'access-token-sentinel-do-not-leak';

describe('oauth prometheus metrics', () => {
  let baseUrl = '';
  let closeApp: () => Promise<void> = async () => undefined;
  let stopTelemetry: () => Promise<void> = async () => undefined;
  let startTelemetry: () => void = () => undefined;

  beforeAll(async () => {
    process.env.OAUTH_INTERNAL_METRICS_PORT = String(METRICS_PORT);
    process.env.OAUTH_INTERNAL_API_PORT = '3001';
    process.env.KEYCLOAK_SERVER_URL = 'http://127.0.0.1:1';
    process.env.KEYCLOAK_CLIENT_SECRET = CLIENT_SECRET;

    const telemetry = await import('./register');
    stopTelemetry = telemetry.stopTelemetry;
    startTelemetry = telemetry.startTelemetry;

    const { Test } = await import('@nestjs/testing');
    const { AppModule } = await import('../app.module');
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    const app = moduleRef.createNestApplication();
    await app.listen(0, '127.0.0.1');
    const address = app.getHttpServer().address();
    const apiPort = typeof address === 'object' && address ? address.port : 0;
    baseUrl = `http://127.0.0.1:${apiPort}`;
    closeApp = () => app.close();
  });

  afterAll(async () => {
    await closeApp();
    await stopTelemetry();
  });

  async function scrapeMetrics(): Promise<string> {
    const response = await fetch(`http://127.0.0.1:${METRICS_PORT}/metrics`);
    expect(response.status).toBe(200);
    return response.text();
  }

  it('exposes Prometheus text and records an HTTP server metric', async () => {
    const health = await fetch(`${baseUrl}/health`, {
      headers: { Authorization: `Bearer ${ACCESS_TOKEN}` },
    });
    expect(health.status).toBe(200);
    expect(await health.json()).toEqual({ status: 'ok' });

    const body = await scrapeMetrics();

    expect(body).toMatch(/# (HELP|TYPE) /);
    expect(body).toMatch(/http_server_request_duration|http_server_duration/);
    expect(body).not.toContain(ACCESS_TOKEN);
    expect(body).not.toContain(CLIENT_SECRET);
  });

  it('does not put a login password into the metrics text', async () => {
    await fetch(`${baseUrl}/login`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${ACCESS_TOKEN}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        username: 'admin@pucrs.br',
        password: PASSWORD,
      }),
    });

    const body = await scrapeMetrics();

    expect(body).not.toContain(PASSWORD);
    expect(body).not.toContain(ACCESS_TOKEN);
    expect(body).not.toContain(CLIENT_SECRET);
  });

  it('is idempotent: starting an already-started SDK is a no-op', () => {
    expect(() => startTelemetry()).not.toThrow();
  });

  it('is safe to stop twice', async () => {
    await stopTelemetry();
    await expect(stopTelemetry()).resolves.toBeUndefined();
    // Restart so afterAll's stopTelemetry() call still has an SDK to close
    // cleanly, keeping this test isolated from suite teardown.
    startTelemetry();
  });
});
