import { KeycloakSettingsService } from '../config';
import { OaException } from '../errors';
import { KeycloakAuthorizationClient, UMA_TICKET_GRANT_TYPE } from './keycloak-authorization.client';

function settingsStub(): KeycloakSettingsService {
  return {
    tokenUrl: 'http://keycloak:8080/realms/constrsw/protocol/openid-connect/token',
    clientId: 'oauth',
    clientSecret: 'super-secret',
  } as unknown as KeycloakSettingsService;
}

function response(status: number, body: unknown = {}): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

describe('KeycloakAuthorizationClient', () => {
  let fetchMock: jest.Mock;
  let client: KeycloakAuthorizationClient;

  beforeEach(() => {
    fetchMock = jest.fn();
    global.fetch = fetchMock as unknown as typeof fetch;
    client = new KeycloakAuthorizationClient(settingsStub());
  });

  it('asks Keycloak with the UMA-ticket grant, the caller token, and the resource as the permission', async () => {
    fetchMock.mockResolvedValue(response(200, { access_token: 'rpt' }));

    const permitted = await client.checkPermission('caller-access-token', 'classes');

    expect(permitted).toBe(true);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://keycloak:8080/realms/constrsw/protocol/openid-connect/token');
    expect(init.headers.Authorization).toBe('Bearer caller-access-token');
    const sentBody = new URLSearchParams(init.body as string);
    expect(sentBody.get('grant_type')).toBe(UMA_TICKET_GRANT_TYPE);
    expect(sentBody.get('audience')).toBe('oauth');
    expect(sentBody.get('permission')).toBe('classes');
  });

  it('resolves false — not an exception — when Keycloak denies with 403', async () => {
    fetchMock.mockResolvedValue(
      response(403, { error: 'access_denied', error_description: 'not_authorized' }),
    );

    await expect(client.checkPermission('caller-access-token', 'rooms')).resolves.toBe(false);
  });

  it('does not decide permission itself — this is the only branch point in the client', async () => {
    // No `if (role === 'administrator')` anywhere: the only inputs to the
    // decision are Keycloak's HTTP status codes handled above.
    fetchMock.mockResolvedValue(response(200));
    await expect(client.checkPermission('token', 'lessons')).resolves.toBe(true);

    fetchMock.mockResolvedValue(response(403));
    await expect(client.checkPermission('token', 'lessons')).resolves.toBe(false);
  });

  it('throws (does not silently allow or deny) on an unexpected Keycloak status', async () => {
    fetchMock.mockResolvedValue(response(500, { error: 'server_error' }));

    await expect(client.checkPermission('token', 'classes')).rejects.toBeInstanceOf(
      OaException,
    );
  });

  it('throws when Keycloak is unreachable', async () => {
    fetchMock.mockRejectedValue(new Error('connect ECONNREFUSED'));

    await expect(client.checkPermission('token', 'classes')).rejects.toBeInstanceOf(
      OaException,
    );
  });

  it('tolerates a non-JSON body on an unexpected status', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => {
        throw new Error('not json');
      },
    } as unknown as Response);

    await expect(client.checkPermission('token', 'classes')).rejects.toBeInstanceOf(
      OaException,
    );
  });

  it('falls back to a generic description when the body is not a record', async () => {
    fetchMock.mockResolvedValue(response(500, null));

    let error: OaException | undefined;
    try {
      await client.checkPermission('token', 'classes');
    } catch (e) {
      error = e as OaException;
    }

    expect(error?.toEnvelope().error_description).toMatch(/unexpected status/);
  });
});
