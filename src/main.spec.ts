jest.mock('./telemetry/register', () => ({ startTelemetry: jest.fn() }));

const listen = jest.fn().mockResolvedValue(undefined);
const get = jest.fn().mockReturnValue({
  internalApiPort: 3001,
  realm: 'constrsw',
  serverUrl: 'http://keycloak:8080',
});
const create = jest.fn().mockResolvedValue({ listen, get });

jest.mock('@nestjs/core', () => ({
  NestFactory: { create },
}));

describe('bootstrap', () => {
  it('creates the Nest app and listens on the configured internal API port', async () => {
    await import('./main');
    // bootstrap() is fire-and-forget (`void bootstrap()`); flush microtasks.
    await new Promise((resolve) => setImmediate(resolve));

    expect(create).toHaveBeenCalled();
    expect(listen).toHaveBeenCalledWith(3001, '0.0.0.0');
  });
});
