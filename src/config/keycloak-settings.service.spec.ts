import { ConfigModule } from '@nestjs/config';
import { Test } from '@nestjs/testing';

import { keycloakConfig } from './keycloak.config';
import { KeycloakSettingsService } from './keycloak-settings.service';

async function serviceWithEnv(
  env: NodeJS.ProcessEnv,
): Promise<KeycloakSettingsService> {
  const previous = process.env;
  process.env = { ...env };
  try {
    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          ignoreEnvFile: true,
          load: [keycloakConfig],
        }),
      ],
      providers: [KeycloakSettingsService],
    }).compile();

    return moduleRef.get(KeycloakSettingsService);
  } finally {
    process.env = previous;
  }
}

const professorEnv: NodeJS.ProcessEnv = {
  KEYCLOAK_SERVER_URL: 'http://keycloak:8080',
  KEYCLOAK_REALM: 'constrsw',
  KEYCLOAK_CLIENT_ID: 'oauth',
  KEYCLOAK_CLIENT_SECRET: 'test-secret-not-a-real-one',
  OAUTH_INTERNAL_API_PORT: '3001',
};

describe('KeycloakSettingsService', () => {
  describe('with the professor-provided environment', () => {
    let service: KeycloakSettingsService;

    beforeEach(async () => {
      service = await serviceWithEnv(professorEnv);
    });

    it('exposes the bound settings', () => {
      expect(service.serverUrl).toBe('http://keycloak:8080');
      expect(service.realm).toBe('constrsw');
      expect(service.clientId).toBe('oauth');
      expect(service.internalApiPort).toBe(3001);
      expect(service.internalMetricsPort).toBe(9464);
    });

    it('builds the token URL without inserting /auth', () => {
      expect(service.tokenUrl).toBe(
        'http://keycloak:8080/realms/constrsw/protocol/openid-connect/token',
      );
      expect(service.tokenUrl).not.toContain('/auth/');
    });

    it('builds the userinfo URL', () => {
      expect(service.userInfoUrl).toBe(
        'http://keycloak:8080/realms/constrsw/protocol/openid-connect/userinfo',
      );
    });

    it('builds the Admin API base for Epics 4-5', () => {
      expect(service.adminRealmUrl).toBe(
        'http://keycloak:8080/admin/realms/constrsw',
      );
    });

    it('joins realm-relative paths, tolerating a leading slash', () => {
      expect(service.realmPath('protocol/openid-connect/logout')).toBe(
        'http://keycloak:8080/realms/constrsw/protocol/openid-connect/logout',
      );
      expect(service.realmPath('/protocol/openid-connect/logout')).toBe(
        'http://keycloak:8080/realms/constrsw/protocol/openid-connect/logout',
      );
    });

    it('joins admin-relative paths', () => {
      expect(service.adminPath('users')).toBe(
        'http://keycloak:8080/admin/realms/constrsw/users',
      );
      expect(service.adminPath('/users/abc-123')).toBe(
        'http://keycloak:8080/admin/realms/constrsw/users/abc-123',
      );
    });
  });

  describe('URL edge cases (NFR3)', () => {
    it('does not produce //realms when the base has a trailing slash', async () => {
      const service = await serviceWithEnv({
        ...professorEnv,
        KEYCLOAK_SERVER_URL: 'http://keycloak:8080/',
      });

      expect(service.tokenUrl).toBe(
        'http://keycloak:8080/realms/constrsw/protocol/openid-connect/token',
      );
      expect(service.tokenUrl).not.toContain('//realms');
    });

    it('keeps an /auth prefix that is already part of the base', async () => {
      const service = await serviceWithEnv({
        ...professorEnv,
        KEYCLOAK_SERVER_URL: 'http://legacy-keycloak:8080/auth',
      });

      expect(service.tokenUrl).toBe(
        'http://legacy-keycloak:8080/auth/realms/constrsw/protocol/openid-connect/token',
      );
      expect(service.adminRealmUrl).toBe(
        'http://legacy-keycloak:8080/auth/admin/realms/constrsw',
      );
    });

    it('does not double an /auth segment', async () => {
      const service = await serviceWithEnv({
        ...professorEnv,
        KEYCLOAK_SERVER_URL: 'http://legacy-keycloak:8080/auth',
      });

      expect(service.tokenUrl.match(/\/auth\//g)).toHaveLength(1);
    });
  });

  describe('defaults', () => {
    it('resolves realm and client id when compose omits them', async () => {
      const service = await serviceWithEnv({
        KEYCLOAK_SERVER_URL: 'http://keycloak:8080',
        KEYCLOAK_CLIENT_SECRET: 'test-secret-not-a-real-one',
      });

      expect(service.realm).toBe('constrsw');
      expect(service.clientId).toBe('oauth');
      expect(service.tokenUrl).toContain('/realms/constrsw/');
    });
  });
});
