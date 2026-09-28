import { OaException } from '../errors';
import { KeycloakAdminClient } from '../common/keycloak-admin.client';
import { KeycloakSettingsService } from '../config';
import { KeycloakAdminService } from './keycloak-admin.service';

/** Rejects with the OA exception so its envelope can be asserted. */
async function rejection(promise: Promise<unknown>): Promise<OaException> {
  try {
    await promise;
  } catch (error) {
    return error as OaException;
  }
  throw new Error('expected the call to reject');
}

describe('KeycloakAdminService', () => {
  const settings = {
    adminRealmUrl: 'http://keycloak/admin/realms/constrsw',
    serverUrl: 'http://keycloak',
    clientId: 'oauth',
    adminUser: 'admin',
    adminPassword: 'password',
  } as KeycloakSettingsService;

  let service: KeycloakAdminService;
  let fetchMock: jest.Spied<typeof fetch>;

  beforeEach(() => {
    // Real client: these tests exercise token handling and failure mapping too.
    service = new KeycloakAdminService(new KeycloakAdminClient(settings), settings);
    fetchMock = jest.spyOn(global, 'fetch');
  });

  afterEach(() => {
    fetchMock.mockRestore();
  });

  it('maps an Admin API network failure to OA 503', async () => {
    fetchMock.mockRejectedValue(new Error('network down'));

    const error = await rejection(service.listRoles());

    expect(error).toBeInstanceOf(OaException);
    expect(error.getStatus()).toBe(503);
    expect(error.toEnvelope()).toMatchObject({
      error_code: 'OA-503',
      error_source: 'OAuthAPI',
    });
  });

  it('maps malformed client-role JSON to OA 502', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ access_token: 'admin-token' }))
      .mockResolvedValueOnce(jsonResponse([{ id: 'client-uuid', clientId: 'oauth' }]))
      .mockResolvedValueOnce(jsonResponse({ roles: [] }));

    const error = await rejection(service.listRoles());

    expect(error.getStatus()).toBe(502);
    expect(error.toEnvelope().error_code).toBe('OA-502');
  });

  it('uses the client role mapping endpoint for assignment', async () => {
    // Routed by URL, not by call order: the client caches the admin token, so
    // how many times it authenticates is an implementation detail this test
    // should not encode.
    fetchMock.mockImplementation((url) => {
      const target = String(url);
      if (target.includes('/protocol/openid-connect/token')) {
        return Promise.resolve(jsonResponse({ access_token: 'admin-token' }));
      }
      if (target.includes('/clients?clientId=')) {
        return Promise.resolve(jsonResponse([{ id: 'client-uuid', clientId: 'oauth' }]));
      }
      if (target.endsWith('/clients/client-uuid/roles')) {
        return Promise.resolve(
          jsonResponse([
            { id: 'role-uuid', name: 'professor', clientRole: true, containerId: 'client-uuid' },
          ]),
        );
      }
      if (target.endsWith('/users/user-uuid')) {
        return Promise.resolve(jsonResponse({ id: 'user-uuid' }));
      }
      if (target.endsWith('/users/user-uuid/role-mappings/clients/client-uuid')) {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.reject(new Error(`unexpected upstream call: ${target}`));
    });

    await service.assignRole('user-uuid', 'professor');

    expect(fetchMock.mock.calls.at(-1)?.[0]).toBe(
      'http://keycloak/admin/realms/constrsw/users/user-uuid/role-mappings/clients/client-uuid',
    );
    expect(fetchMock.mock.calls.at(-1)?.[1]).toMatchObject({ method: 'POST' });
  });

  it('authenticates once and reuses the admin token across calls', async () => {
    fetchMock.mockImplementation((url) => {
      const target = String(url);
      if (target.includes('/protocol/openid-connect/token')) {
        return Promise.resolve(
          jsonResponse({ access_token: 'admin-token', expires_in: 300 }),
        );
      }
      if (target.includes('/clients?clientId=')) {
        return Promise.resolve(jsonResponse([{ id: 'client-uuid', clientId: 'oauth' }]));
      }
      return Promise.resolve(jsonResponse([]));
    });

    await service.listRoles();
    await service.listRoles();

    const tokenCalls = fetchMock.mock.calls.filter(([url]) =>
      String(url).includes('/protocol/openid-connect/token'),
    );
    expect(tokenCalls).toHaveLength(1);
  });

  /** Routes by URL suffix; every scenario needs the token + client lookup. */
  function stubUpstream(routes: (url: string, init?: RequestInit) => Response | undefined): void {
    fetchMock.mockImplementation((url, init) => {
      const target = String(url);
      if (target.includes('/protocol/openid-connect/token')) {
        return Promise.resolve(jsonResponse({ access_token: 'admin-token' }));
      }
      if (target.includes('/clients?clientId=')) {
        return Promise.resolve(jsonResponse([{ id: 'client-uuid', clientId: 'oauth' }]));
      }
      const answer = routes(target, init as RequestInit);
      return answer
        ? Promise.resolve(answer)
        : Promise.reject(new Error(`unexpected upstream call: ${target}`));
    });
  }

  const roleRecord = {
    id: 'role-uuid',
    name: 'professor',
    clientRole: true,
    containerId: 'client-uuid',
  };

  describe('createRole', () => {
    it('creates the role and returns it, looked up by name', async () => {
      stubUpstream((url, init) => {
        if (url.endsWith('/clients/client-uuid/roles') && init?.method === 'POST') {
          return new Response(null, { status: 201 });
        }
        if (url.endsWith('/clients/client-uuid/roles')) {
          return jsonResponse([roleRecord]);
        }
        return undefined;
      });

      await expect(service.createRole({ name: 'professor' })).resolves.toEqual(roleRecord);
    });

    it('maps a 409 from Keycloak to a role conflict', async () => {
      stubUpstream((url, init) =>
        url.endsWith('/clients/client-uuid/roles') && init?.method === 'POST'
          ? jsonResponse({}, 409)
          : undefined,
      );

      const error = await rejection(service.createRole({ name: 'professor' }));

      expect(error.getStatus()).toBe(409);
    });

    it('reports 502 when Keycloak creates the role but never returns it', async () => {
      stubUpstream((url, init) => {
        if (url.endsWith('/clients/client-uuid/roles') && init?.method === 'POST') {
          return new Response(null, { status: 201 });
        }
        if (url.endsWith('/clients/client-uuid/roles')) {
          return jsonResponse([]);
        }
        return undefined;
      });

      const error = await rejection(service.createRole({ name: 'professor' }));

      expect(error.getStatus()).toBe(502);
    });
  });

  describe('getRole', () => {
    it('returns the role when it belongs to this client', async () => {
      stubUpstream((url) =>
        url.endsWith('/roles-by-id/role-uuid') ? jsonResponse(roleRecord) : undefined,
      );

      await expect(service.getRole('role-uuid')).resolves.toEqual(roleRecord);
    });

    it('reports 404 when Keycloak does not know the id', async () => {
      stubUpstream((url) =>
        url.endsWith('/roles-by-id/role-uuid') ? jsonResponse({}, 404) : undefined,
      );

      const error = await rejection(service.getRole('role-uuid'));

      expect(error.getStatus()).toBe(404);
    });

    it('reports 502 for a malformed role payload', async () => {
      stubUpstream((url) =>
        url.endsWith('/roles-by-id/role-uuid') ? jsonResponse({ noId: true }) : undefined,
      );

      const error = await rejection(service.getRole('role-uuid'));

      expect(error.getStatus()).toBe(502);
    });

    it('reports 404 for a role that belongs to a different client', async () => {
      stubUpstream((url) =>
        url.endsWith('/roles-by-id/role-uuid')
          ? jsonResponse({ ...roleRecord, containerId: 'other-client' })
          : undefined,
      );

      const error = await rejection(service.getRole('role-uuid'));

      expect(error.getStatus()).toBe(404);
    });

    it('reports 404 for a realm (non-client) role', async () => {
      stubUpstream((url) =>
        url.endsWith('/roles-by-id/role-uuid')
          ? jsonResponse({ ...roleRecord, clientRole: false })
          : undefined,
      );

      const error = await rejection(service.getRole('role-uuid'));

      expect(error.getStatus()).toBe(404);
    });
  });

  describe('updateRole / replaceRole', () => {
    it('merges changes onto the current role', async () => {
      let sentBody: Record<string, unknown> = {};
      stubUpstream((url, init) => {
        if (url.endsWith('/roles-by-id/role-uuid')) return jsonResponse(roleRecord);
        if (url.endsWith('/clients/client-uuid/roles/professor') && init?.method === 'PUT') {
          sentBody = JSON.parse(String(init.body));
          return new Response(null, { status: 204 });
        }
        return undefined;
      });

      await service.updateRole('role-uuid', { description: 'new description' });

      expect(sentBody).toMatchObject({
        id: 'role-uuid',
        name: 'professor',
        description: 'new description',
      });
    });

    it('sends the full replacement body for PUT', async () => {
      let sentBody: Record<string, unknown> = {};
      stubUpstream((url, init) => {
        if (url.endsWith('/roles-by-id/role-uuid')) return jsonResponse(roleRecord);
        if (url.endsWith('/clients/client-uuid/roles/professor') && init?.method === 'PUT') {
          sentBody = JSON.parse(String(init.body));
          return new Response(null, { status: 204 });
        }
        return undefined;
      });

      await service.replaceRole('role-uuid', { name: 'coordinator', description: 'x' });

      expect(sentBody).toMatchObject({ name: 'coordinator', description: 'x' });
    });

    it('reports 404 when the role disappears between lookup and update', async () => {
      stubUpstream((url, init) => {
        if (url.endsWith('/roles-by-id/role-uuid')) return jsonResponse(roleRecord);
        if (url.endsWith('/clients/client-uuid/roles/professor') && init?.method === 'PUT') {
          return jsonResponse({}, 404);
        }
        return undefined;
      });

      const error = await rejection(service.updateRole('role-uuid', { description: 'x' }));

      expect(error.getStatus()).toBe(404);
    });

    it('reports 409 on a rename collision', async () => {
      stubUpstream((url, init) => {
        if (url.endsWith('/roles-by-id/role-uuid')) return jsonResponse(roleRecord);
        if (url.endsWith('/clients/client-uuid/roles/professor') && init?.method === 'PUT') {
          return jsonResponse({}, 409);
        }
        return undefined;
      });

      const error = await rejection(service.updateRole('role-uuid', { name: 'coordinator' }));

      expect(error.getStatus()).toBe(409);
    });
  });

  describe('logicalDeleteRole', () => {
    it('marks the role inactive instead of deleting it', async () => {
      let sentBody: Record<string, unknown> = {};
      stubUpstream((url, init) => {
        if (url.endsWith('/roles-by-id/role-uuid')) return jsonResponse(roleRecord);
        if (url.endsWith('/clients/client-uuid/roles/professor') && init?.method === 'PUT') {
          sentBody = JSON.parse(String(init.body));
          return new Response(null, { status: 204 });
        }
        return undefined;
      });

      await service.logicalDeleteRole('role-uuid');

      expect(sentBody).toMatchObject({ attributes: { inactive: ['true'] } });
    });
  });

  const ROLE_UUID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  const USER_UUID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

  describe('unassignRole', () => {
    it('resolves the role by uuid and tolerates a mapping that is already gone', async () => {
      stubUpstream((url, init) => {
        if (url.endsWith(`/roles-by-id/${ROLE_UUID}`)) {
          return jsonResponse({ ...roleRecord, id: ROLE_UUID });
        }
        if (url.endsWith(`/users/${USER_UUID}`)) return jsonResponse({ id: USER_UUID });
        if (url.endsWith('/role-mappings/clients/client-uuid') && init?.method === 'DELETE') {
          return new Response(null, { status: 404 });
        }
        return undefined;
      });

      await expect(
        service.unassignRole(USER_UUID, ROLE_UUID),
      ).resolves.toBeUndefined();
    });

    it('reports 404 when the user does not exist', async () => {
      stubUpstream((url) => {
        if (url.endsWith(`/roles-by-id/${ROLE_UUID}`)) {
          return jsonResponse({ ...roleRecord, id: ROLE_UUID });
        }
        if (url.endsWith(`/users/${USER_UUID}`)) return jsonResponse({}, 404);
        return undefined;
      });

      const error = await rejection(service.unassignRole(USER_UUID, ROLE_UUID));

      expect(error.getStatus()).toBe(404);
    });
  });

  describe('resolving a role by name', () => {
    it('finds it by name for assignRole when given a non-uuid identifier', async () => {
      stubUpstream((url, init) => {
        if (url.endsWith('/clients/client-uuid/roles')) return jsonResponse([roleRecord]);
        if (url.endsWith('/users/user-uuid')) return jsonResponse({ id: 'user-uuid' });
        if (url.endsWith('/role-mappings/clients/client-uuid') && init?.method === 'POST') {
          return new Response(null, { status: 204 });
        }
        return undefined;
      });

      await expect(
        service.assignRole('user-uuid', 'professor'),
      ).resolves.toBeUndefined();
    });

    it('reports 404 when no role has that name', async () => {
      stubUpstream((url) =>
        url.endsWith('/clients/client-uuid/roles') ? jsonResponse([]) : undefined,
      );

      const error = await rejection(service.assignRole('user-uuid', 'does-not-exist'));

      expect(error.getStatus()).toBe(404);
    });
  });

  describe('clientUuid', () => {
    it('reports 404 when the configured client is not found in the realm', async () => {
      fetchMock.mockImplementation((url) => {
        const target = String(url);
        if (target.includes('/protocol/openid-connect/token')) {
          return Promise.resolve(jsonResponse({ access_token: 'admin-token' }));
        }
        if (target.includes('/clients?clientId=')) {
          return Promise.resolve(jsonResponse([]));
        }
        return Promise.reject(new Error(`unexpected upstream call: ${target}`));
      });

      const error = await rejection(service.listRoles());

      expect(error.getStatus()).toBe(404);
    });
  });
});

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}