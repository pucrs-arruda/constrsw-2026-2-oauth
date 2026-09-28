import {
  buildAppConfig,
  DEFAULT_CLIENT_ID,
  DEFAULT_INTERNAL_API_PORT,
  DEFAULT_INTERNAL_METRICS_PORT,
  DEFAULT_REALM,
  keycloakConfig,
  MissingEnvironmentVariableError,
  normalizeServerUrl,
  readInternalMetricsPort,
} from './keycloak.config';

/** Minimum set the professor's compose injects. */
function validEnv(overrides: NodeJS.ProcessEnv = {}): NodeJS.ProcessEnv {
  return {
    KEYCLOAK_SERVER_URL: 'http://keycloak:8080',
    KEYCLOAK_CLIENT_SECRET: 'test-secret-not-a-real-one',
    ...overrides,
  };
}

describe('buildAppConfig', () => {
  describe('required variables', () => {
    it('throws when KEYCLOAK_SERVER_URL is missing', () => {
      const env = validEnv();
      delete env.KEYCLOAK_SERVER_URL;

      expect(() => buildAppConfig(env)).toThrow(MissingEnvironmentVariableError);
      expect(() => buildAppConfig(env)).toThrow(/KEYCLOAK_SERVER_URL/);
    });

    it('throws when KEYCLOAK_CLIENT_SECRET is missing', () => {
      const env = validEnv();
      delete env.KEYCLOAK_CLIENT_SECRET;

      expect(() => buildAppConfig(env)).toThrow(MissingEnvironmentVariableError);
      expect(() => buildAppConfig(env)).toThrow(/KEYCLOAK_CLIENT_SECRET/);
    });

    it.each(['', '   '])(
      'treats a blank KEYCLOAK_SERVER_URL (%p) as missing',
      (blank) => {
        expect(() =>
          buildAppConfig(validEnv({ KEYCLOAK_SERVER_URL: blank })),
        ).toThrow(MissingEnvironmentVariableError);
      },
    );
  });

  describe('defaults', () => {
    it('falls back to realm constrsw and client oauth when unset', () => {
      const { keycloak } = buildAppConfig(validEnv());

      expect(keycloak.realm).toBe(DEFAULT_REALM);
      expect(keycloak.realm).toBe('constrsw');
      expect(keycloak.clientId).toBe(DEFAULT_CLIENT_ID);
      expect(keycloak.clientId).toBe('oauth');
    });

    it('prefers the provided realm and client id over the defaults', () => {
      const { keycloak } = buildAppConfig(
        validEnv({ KEYCLOAK_REALM: 'other', KEYCLOAK_CLIENT_ID: 'gateway' }),
      );

      expect(keycloak.realm).toBe('other');
      expect(keycloak.clientId).toBe('gateway');
    });

    it('defaults the internal API port to the compose value', () => {
      const { oauth } = buildAppConfig(validEnv());

      expect(oauth.internalApiPort).toBe(DEFAULT_INTERNAL_API_PORT);
      expect(oauth.internalApiPort).toBe(3001);
    });

    it('reads OAUTH_INTERNAL_API_PORT as a number', () => {
      const { oauth } = buildAppConfig(
        validEnv({ OAUTH_INTERNAL_API_PORT: '3001' }),
      );

      expect(oauth.internalApiPort).toBe(3001);
    });

    it.each(['0', '-1', 'abc', '70000', '3001.5'])(
      'rejects an invalid port (%p)',
      (bad) => {
        expect(() =>
          buildAppConfig(validEnv({ OAUTH_INTERNAL_API_PORT: bad })),
        ).toThrow(/must be a valid port number/);
      },
    );

    it('defaults the internal metrics port to 9464', () => {
      const { oauth } = buildAppConfig(validEnv());

      expect(oauth.internalMetricsPort).toBe(DEFAULT_INTERNAL_METRICS_PORT);
      expect(oauth.internalMetricsPort).toBe(9464);
      expect(readInternalMetricsPort(validEnv())).toBe(9464);
    });

    it('reads OAUTH_INTERNAL_METRICS_PORT without Keycloak secrets', () => {
      expect(
        readInternalMetricsPort({ OAUTH_INTERNAL_METRICS_PORT: '19464' }),
      ).toBe(19464);
    });

    it.each(['0', '-1', 'abc', '70000', '9464.5'])(
      'rejects an invalid metrics port (%p)',
      (bad) => {
        expect(() =>
          readInternalMetricsPort({ OAUTH_INTERNAL_METRICS_PORT: bad }),
        ).toThrow(/must be a valid port number/);
      },
    );
  });

  describe('optional admin credentials', () => {
    it('binds the admin credentials when compose injects them', () => {
      const { keycloak } = buildAppConfig(
        validEnv({
          KEYCLOAK_ADMIN: 'admin',
          KEYCLOAK_ADMIN_PASSWORD: 'test-only',
        }),
      );

      expect(keycloak.adminUser).toBe('admin');
      expect(keycloak.adminPassword).toBe('test-only');
    });

    it('leaves them undefined rather than blank when absent', () => {
      const { keycloak } = buildAppConfig(validEnv());

      expect(keycloak.adminUser).toBeUndefined();
      expect(keycloak.adminPassword).toBeUndefined();
    });
  });
});

describe('keycloakConfig', () => {
  it('reads settings from process.env by default', () => {
    const previous = process.env;
    process.env = {
      ...previous,
      KEYCLOAK_SERVER_URL: 'http://keycloak:8080',
      KEYCLOAK_CLIENT_SECRET: 'test-secret-not-a-real-one',
    };
    try {
      const { keycloak } = keycloakConfig();
      expect(keycloak.serverUrl).toBe('http://keycloak:8080');
    } finally {
      process.env = previous;
    }
  });
});

describe('normalizeServerUrl', () => {
  it('strips a trailing slash so joining never yields //realms', () => {
    expect(normalizeServerUrl('http://keycloak:8080/')).toBe(
      'http://keycloak:8080',
    );
  });

  it('strips repeated trailing slashes', () => {
    expect(normalizeServerUrl('http://keycloak:8080///')).toBe(
      'http://keycloak:8080',
    );
  });

  it('leaves a clean base untouched', () => {
    expect(normalizeServerUrl('http://keycloak:8080')).toBe(
      'http://keycloak:8080',
    );
  });

  it('never strips or adds an /auth segment', () => {
    expect(normalizeServerUrl('http://keycloak:8080/auth')).toBe(
      'http://keycloak:8080/auth',
    );
    expect(normalizeServerUrl('http://keycloak:8080/auth/')).toBe(
      'http://keycloak:8080/auth',
    );
  });
});
