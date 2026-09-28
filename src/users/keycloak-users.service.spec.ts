import { KeycloakAdminClient } from '../common';
import { KeycloakSettingsService } from '../config';
import { OaException } from '../errors';
import {
  KeycloakUsersService,
  idFromLocation,
} from './keycloak-users.service';

const ADMIN_BASE = 'http://keycloak/admin/realms/constrsw';
const USER_ID = '11111111-1111-4111-8111-111111111111';

function jsonResponse(value: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json', ...headers },
  });
}

async function rejection(promise: Promise<unknown>): Promise<OaException> {
  try {
    await promise;
  } catch (error) {
    return error as OaException;
  }
  throw new Error('expected the call to reject');
}

describe('KeycloakUsersService', () => {
  let fetchMock: jest.Spied<typeof fetch>;
  let service: KeycloakUsersService;

  const storedUser = {
    id: USER_ID,
    username: 'aluno@pucrs.br',
    firstName: 'Ana',
    lastName: 'Silva',
    enabled: true,
  };

  beforeEach(() => {
    fetchMock = jest.spyOn(global, 'fetch');
    const settings = {
      adminRealmUrl: ADMIN_BASE,
      serverUrl: 'http://keycloak',
      clientId: 'oauth',
      adminUser: 'admin',
      adminPassword: 'test-only',
    } as KeycloakSettingsService;
    service = new KeycloakUsersService(new KeycloakAdminClient(settings));
  });

  afterEach(() => {
    fetchMock.mockRestore();
  });

  /** Answers the admin token; `routes` handles the Admin API by URL suffix. */
  function upstream(routes: (url: string, init?: RequestInit) => Response | undefined): void {
    fetchMock.mockImplementation((url, init) => {
      const target = String(url);
      if (target.includes('/protocol/openid-connect/token')) {
        return Promise.resolve(
          jsonResponse({ access_token: 'admin-token', expires_in: 300 }),
        );
      }
      const answer = routes(target, init as RequestInit);
      return answer
        ? Promise.resolve(answer)
        : Promise.reject(new Error(`unexpected upstream call: ${target}`));
    });
  }

  describe('createUser', () => {
    it('sends the password as a non-temporary credential and enables the user', async () => {
      let sent: Record<string, unknown> = {};
      upstream((url, init) => {
        if (url === `${ADMIN_BASE}/users` && init?.method === 'POST') {
          sent = JSON.parse(String(init.body));
          return new Response(null, {
            status: 201,
            headers: { location: `${ADMIN_BASE}/users/${USER_ID}` },
          });
        }
        if (url === `${ADMIN_BASE}/users/${USER_ID}`) {
          return jsonResponse(storedUser);
        }
        return undefined;
      });

      await service.createUser({ username: 'aluno@pucrs.br' }, 'segredo');

      expect(sent).toMatchObject({
        username: 'aluno@pucrs.br',
        enabled: true,
        credentials: [
          { type: 'password', value: 'segredo', temporary: false },
        ],
      });
    });

    it('takes the generated id from the Location header', async () => {
      upstream((url, init) => {
        if (url === `${ADMIN_BASE}/users` && init?.method === 'POST') {
          return new Response(null, {
            status: 201,
            headers: { location: `${ADMIN_BASE}/users/${USER_ID}` },
          });
        }
        if (url === `${ADMIN_BASE}/users/${USER_ID}`) {
          return jsonResponse(storedUser);
        }
        return undefined;
      });

      const created = await service.createUser(
        { username: 'aluno@pucrs.br' },
        'segredo',
      );

      expect(created.id).toBe(USER_ID);
    });

    it('reports 409 when the username is taken', async () => {
      upstream((url, init) =>
        url === `${ADMIN_BASE}/users` && init?.method === 'POST'
          ? jsonResponse({ errorMessage: 'User exists' }, 409)
          : undefined,
      );

      const error = await rejection(
        service.createUser({ username: 'aluno@pucrs.br' }, 'segredo'),
      );

      expect(error.getStatus()).toBe(409);
      expect(error.toEnvelope().error_code).toBe('OA-409');
    });

    it('reports 502 when Keycloak omits the Location header', async () => {
      upstream((url, init) =>
        url === `${ADMIN_BASE}/users` && init?.method === 'POST'
          ? new Response(null, { status: 201 })
          : undefined,
      );

      const error = await rejection(
        service.createUser({ username: 'aluno@pucrs.br' }, 'segredo'),
      );

      expect(error.getStatus()).toBe(502);
      expect(error.toEnvelope().error_description).toMatch(/did not report its id/);
    });
  });

  describe('listUsers', () => {
    it('asks Keycloak only for enabled users when restricted', async () => {
      let requested = '';
      upstream((url) => {
        requested = url;
        return jsonResponse([storedUser]);
      });

      await service.listUsers(true);

      expect(requested).toBe(`${ADMIN_BASE}/users?enabled=true`);
    });

    it('does not filter when no state is requested', async () => {
      let requested = '';
      upstream((url) => {
        requested = url;
        return jsonResponse([storedUser, { ...storedUser, id: 'x', enabled: false }]);
      });

      const users = await service.listUsers();

      expect(requested).toBe(`${ADMIN_BASE}/users`);
      expect(users).toHaveLength(2);
    });

    it('drops entries Keycloak returns that do not match the requested state', async () => {
      // Some Keycloak setups ignore the query parameter, so the filter is
      // reapplied locally rather than trusted.
      upstream(() =>
        jsonResponse([storedUser, { ...storedUser, id: 'other', enabled: false }]),
      );

      const users = await service.listUsers(true);

      expect(users).toEqual([storedUser]);
    });

    it('reports 502 when the payload is not a list', async () => {
      upstream(() => jsonResponse({ not: 'a list' }));

      const error = await rejection(service.listUsers(true));

      expect(error.getStatus()).toBe(502);
    });
  });

  describe('getUser', () => {
    it('returns the stored user', async () => {
      upstream((url) =>
        url === `${ADMIN_BASE}/users/${USER_ID}` ? jsonResponse(storedUser) : undefined,
      );

      await expect(service.getUser(USER_ID)).resolves.toEqual(storedUser);
    });

    it('reports 404 when Keycloak does not know the id', async () => {
      upstream(() => jsonResponse({}, 404));

      const error = await rejection(service.getUser(USER_ID));

      expect(error.getStatus()).toBe(404);
      expect(error.toEnvelope().error_code).toBe('OA-404');
    });

    it('reports 502 when Keycloak returns a user without an id', async () => {
      upstream(() => jsonResponse({ username: 'aluno@pucrs.br' }));

      const error = await rejection(service.getUser(USER_ID));

      expect(error.getStatus()).toBe(502);
    });
  });

  describe('updateUser', () => {
    it('merges changes onto the stored user so omitted fields survive', async () => {
      let sent: Record<string, unknown> = {};
      upstream((url, init) => {
        if (url === `${ADMIN_BASE}/users/${USER_ID}` && init?.method === 'PUT') {
          sent = JSON.parse(String(init.body));
          return new Response(null, { status: 204 });
        }
        if (url === `${ADMIN_BASE}/users/${USER_ID}`) {
          return jsonResponse(storedUser);
        }
        return undefined;
      });

      await service.updateUser(USER_ID, { firstName: 'Beatriz' });

      // Keycloak's update replaces the representation: without the merge,
      // lastName and enabled would be wiped.
      expect(sent).toEqual({
        firstName: 'Beatriz',
        lastName: 'Silva',
        enabled: true,
      });
    });

    it('reports 404 for an unknown id', async () => {
      upstream(() => jsonResponse({}, 404));

      const error = await rejection(service.updateUser(USER_ID, { firstName: 'X' }));

      expect(error.getStatus()).toBe(404);
    });

    it('reports 409 on an update conflict', async () => {
      upstream((url, init) => {
        if (url === `${ADMIN_BASE}/users/${USER_ID}` && init?.method === 'PUT') {
          return jsonResponse({}, 409);
        }
        if (url === `${ADMIN_BASE}/users/${USER_ID}`) {
          return jsonResponse(storedUser);
        }
        return undefined;
      });

      const error = await rejection(service.updateUser(USER_ID, { firstName: 'X' }));

      expect(error.getStatus()).toBe(409);
    });
  });

  describe('changePassword', () => {
    it('uses the reset-password endpoint with a permanent credential', async () => {
      let sent: Record<string, unknown> = {};
      let requested = '';
      upstream((url, init) => {
        if (url.endsWith('/reset-password')) {
          requested = url;
          sent = JSON.parse(String(init?.body));
          return new Response(null, { status: 204 });
        }
        if (url === `${ADMIN_BASE}/users/${USER_ID}`) {
          return jsonResponse(storedUser);
        }
        return undefined;
      });

      await service.changePassword(USER_ID, 'nova-senha');

      expect(requested).toBe(`${ADMIN_BASE}/users/${USER_ID}/reset-password`);
      expect(sent).toEqual({
        type: 'password',
        value: 'nova-senha',
        temporary: false,
      });
    });

    it('reports 404 before touching the password of an unknown user', async () => {
      upstream((url) =>
        url === `${ADMIN_BASE}/users/${USER_ID}` ? jsonResponse({}, 404) : undefined,
      );

      const error = await rejection(service.changePassword(USER_ID, 'nova'));

      expect(error.getStatus()).toBe(404);
    });

    it('reports 404 when the reset-password call itself 404s', async () => {
      upstream((url) => {
        if (url.endsWith('/reset-password')) return jsonResponse({}, 404);
        if (url === `${ADMIN_BASE}/users/${USER_ID}`) return jsonResponse(storedUser);
        return undefined;
      });

      const error = await rejection(service.changePassword(USER_ID, 'nova'));

      expect(error.getStatus()).toBe(404);
    });
  });

  describe('disableUser', () => {
    it('disables rather than deleting', async () => {
      const methods: string[] = [];
      let sent: Record<string, unknown> = {};
      upstream((url, init) => {
        methods.push(`${init?.method ?? 'GET'} ${url}`);
        if (url === `${ADMIN_BASE}/users/${USER_ID}` && init?.method === 'PUT') {
          sent = JSON.parse(String(init.body));
          return new Response(null, { status: 204 });
        }
        if (url === `${ADMIN_BASE}/users/${USER_ID}`) {
          return jsonResponse(storedUser);
        }
        return undefined;
      });

      await service.disableUser(USER_ID);

      expect(sent).toMatchObject({ enabled: false });
      expect(methods.some((call) => call.startsWith('DELETE'))).toBe(false);
    });

    it('is idempotent for a user who is already disabled', async () => {
      const methods: string[] = [];
      upstream((url, init) => {
        methods.push(init?.method ?? 'GET');
        return url === `${ADMIN_BASE}/users/${USER_ID}`
          ? jsonResponse({ ...storedUser, enabled: false })
          : undefined;
      });

      await expect(service.disableUser(USER_ID)).resolves.toBeUndefined();
      expect(methods).not.toContain('PUT');
    });

    it('reports 404 for an unknown id', async () => {
      upstream(() => jsonResponse({}, 404));

      const error = await rejection(service.disableUser(USER_ID));

      expect(error.getStatus()).toBe(404);
    });
  });
});

describe('idFromLocation', () => {
  it('takes the last path segment', () => {
    expect(idFromLocation(`http://keycloak/admin/realms/constrsw/users/${USER_ID}`)).toBe(
      USER_ID,
    );
  });

  it('tolerates a trailing slash', () => {
    expect(idFromLocation(`http://k/users/${USER_ID}/`)).toBe(USER_ID);
  });

  it.each([null, ''])('returns undefined for %p', (value) => {
    expect(idFromLocation(value as string | null)).toBeUndefined();
  });
});
