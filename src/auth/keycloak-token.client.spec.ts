import { KeycloakSettingsService } from '../config';
import { OaException } from '../errors';
import { KeycloakTokenClient } from './keycloak-token.client';

function settingsStub(): KeycloakSettingsService {
  return {
    tokenUrl: 'http://keycloak:8080/realms/constrsw/protocol/openid-connect/token',
    clientId: 'oauth',
    clientSecret: 'super-secret',
  } as unknown as KeycloakSettingsService;
}

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

describe('KeycloakTokenClient', () => {
  let fetchMock: jest.Mock;
  let client: KeycloakTokenClient;

  beforeEach(() => {
    fetchMock = jest.fn();
    global.fetch = fetchMock as unknown as typeof fetch;
    client = new KeycloakTokenClient(settingsStub());
  });

  describe('passwordGrant', () => {
    it('posts grant_type=password with the server client credentials, never the caller-supplied ones', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse(200, {
          token_type: 'Bearer',
          access_token: 'access-token',
          expires_in: 300,
          refresh_token: 'refresh-token',
          refresh_expires_in: 1800,
        }),
      );

      const result = await client.passwordGrant('aluno@pucrs.br', 'secret');

      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [url, init] = fetchMock.mock.calls[0];
      expect(url).toBe(
        'http://keycloak:8080/realms/constrsw/protocol/openid-connect/token',
      );
      const sentBody = new URLSearchParams(init.body as string);
      expect(sentBody.get('grant_type')).toBe('password');
      expect(sentBody.get('scope')).toBe('openid');
      expect(sentBody.get('client_id')).toBe('oauth');
      expect(sentBody.get('client_secret')).toBe('super-secret');
      expect(sentBody.get('username')).toBe('aluno@pucrs.br');
      expect(sentBody.get('password')).toBe('secret');

      expect(result).toEqual({
        tokenType: 'Bearer',
        accessToken: 'access-token',
        expiresIn: 300,
        refreshToken: 'refresh-token',
        refreshExpiresIn: 1800,
      });
    });

    it('relays the Keycloak error code as error_code on failure', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse(400, {
          error: 'invalid_grant',
          error_description: 'Invalid user credentials',
        }),
      );

      let thrown: OaException | undefined;
      try {
        await client.passwordGrant('aluno@pucrs.br', 'wrong');
      } catch (error) {
        thrown = error as OaException;
      }

      expect(thrown).toBeInstanceOf(OaException);
      expect(thrown?.getStatus()).toBe(401);
      expect(thrown?.toEnvelope().error_code).toBe('invalid_grant');
    });

    it('answers 503, not 401, when Keycloak is unreachable', async () => {
      fetchMock.mockRejectedValue(new Error('connect ECONNREFUSED'));

      let thrown: OaException | undefined;
      try {
        await client.passwordGrant('aluno@pucrs.br', 'secret');
      } catch (error) {
        thrown = error as OaException;
      }

      // The caller's credentials may be perfectly good; blaming them for our
      // outage would send them to re-authenticate for nothing. Same rule as
      // the Bearer guard and the authorization client.
      expect(thrown).toBeInstanceOf(OaException);
      expect(thrown?.getStatus()).toBe(503);
    });

    it('never puts tokens in the error body of an incomplete response', async () => {
      // Keycloak answered 200 but omitted token_type, so the response is
      // unusable — yet it still carries real tokens.
      fetchMock.mockResolvedValue(
        new Response(
          JSON.stringify({
            access_token: 'real-access-token',
            refresh_token: 'real-refresh-token',
            expires_in: 300,
          }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        ),
      );

      let thrown: OaException | undefined;
      try {
        await client.passwordGrant('aluno@pucrs.br', 'secret');
      } catch (error) {
        thrown = error as OaException;
      }

      const serialized = JSON.stringify(thrown?.toEnvelope());
      expect(serialized).not.toContain('real-access-token');
      expect(serialized).not.toContain('real-refresh-token');
      expect(serialized).toContain('[redacted]');
      // Non-sensitive context is kept, so the error stays diagnosable.
      expect(thrown?.toEnvelope().error_stack[0]).toMatchObject({
        expires_in: 300,
      });
    });
  });

  describe('malformed upstream responses', () => {
    it('tolerates a body that is not valid JSON on failure', async () => {
      fetchMock.mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => {
          throw new Error('not json');
        },
      } as unknown as Response);

      let error: OaException | undefined;
      try {
        await client.passwordGrant('a@b.com', 'pw');
      } catch (e) {
        error = e as OaException;
      }

      expect(error).toBeInstanceOf(OaException);
      expect(error?.getStatus()).toBe(401);
    });

    it('treats a non-object JSON body (e.g. null) as an incomplete token response', async () => {
      fetchMock.mockResolvedValue(jsonResponse(200, null));

      let error: OaException | undefined;
      try {
        await client.passwordGrant('a@b.com', 'pw');
      } catch (e) {
        error = e as OaException;
      }

      expect(error).toBeInstanceOf(OaException);
      expect(error?.getStatus()).toBe(401);
      expect(error?.toEnvelope().error_stack[0]).toMatchObject({ raw: null });
    });
  });

  describe('refreshGrant', () => {
    it('posts grant_type=refresh_token with the server client credentials and the given refresh token', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse(200, {
          token_type: 'Bearer',
          access_token: 'new-access-token',
          expires_in: 300,
          refresh_token: 'new-refresh-token',
          refresh_expires_in: 1800,
        }),
      );

      const result = await client.refreshGrant('old-refresh-token');

      const [, init] = fetchMock.mock.calls[0];
      const sentBody = new URLSearchParams(init.body as string);
      expect(sentBody.get('grant_type')).toBe('refresh_token');
      expect(sentBody.get('scope')).toBe('openid');
      expect(sentBody.get('client_id')).toBe('oauth');
      expect(sentBody.get('client_secret')).toBe('super-secret');
      expect(sentBody.get('refresh_token')).toBe('old-refresh-token');

      expect(result).toEqual({
        tokenType: 'Bearer',
        accessToken: 'new-access-token',
        expiresIn: 300,
        refreshToken: 'new-refresh-token',
        refreshExpiresIn: 1800,
      });
    });

    it('throws a 401 OaException relaying Keycloak on an invalid/expired refresh token', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse(400, {
          error: 'invalid_grant',
          error_description: 'Token is not active',
        }),
      );

      let thrown: OaException | undefined;
      try {
        await client.refreshGrant('expired-refresh-token');
      } catch (error) {
        thrown = error as OaException;
      }

      expect(thrown).toBeInstanceOf(OaException);
      expect(thrown?.getStatus()).toBe(401);
      expect(thrown?.toEnvelope().error_code).toBe('invalid_grant');
    });
  });
});
