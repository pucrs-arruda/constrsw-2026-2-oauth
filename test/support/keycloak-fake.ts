/**
 * Keycloak de mentira em memória, plugado em `global.fetch`. Modela o que o
 * gateway consome de verdade: grants de token (password / refresh_token /
 * client_credentials) e a Admin REST API (usuários, roles, role-mappings,
 * exclusão lógica de role). Assim o fluxo HTTP completo pode ser exercitado
 * sem nenhum Keycloak vivo.
 */
export interface FakeUser {
  id: string;
  username: string;
  firstName?: string;
  lastName?: string;
  enabled: boolean;
  password?: string;
  /** Realm roles returned by introspection via `realm_access.roles`. */
  realmRoles?: string[];
}

export interface FakeRole {
  id: string;
  name: string;
  description?: string;
  attributes?: Record<string, string[]>;
  clientRole?: boolean;
}

export interface KeycloakFakeOptions {
  users?: FakeUser[];
  roles?: FakeRole[];
  assignments?: Record<string, string[]>;
}

export interface LoggedRequest {
  method: string;
  path: string;
  authorization?: string;
}

export interface KeycloakFake {
  impl: typeof fetch;
  users: Map<string, FakeUser>;
  roles: Map<string, FakeRole>;
  assignments: Map<string, Set<string>>;
  requests: LoggedRequest[];
  tokens: { user: string; refresh: string; admin: string; forbidden: string };
}

const REALM = "constrsw";
const REALM_BASE = `/admin/realms/${REALM}`;

const DEFAULT_USERS: FakeUser[] = [
  {
    id: "u1",
    username: "one@pucrs.br",
    firstName: "One",
    lastName: "User",
    enabled: true,
    password: "secret",
    realmRoles: ["administrator", "USER"],
  },
  { id: "u2", username: "two@pucrs.br", enabled: false, password: "secret" },
  { id: "sa", username: "service-account-oauth", enabled: true },
];

function response(
  status: number,
  body?: unknown,
  headers?: Record<string, string>,
): Response {
  const text = body === undefined ? "" : JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(headers ?? {}),
    json: async () => body ?? {},
    text: async () => text,
  } as unknown as Response;
}

function asForm(body: unknown): URLSearchParams {
  if (body instanceof URLSearchParams) return body;
  if (typeof body === "string") return new URLSearchParams(body);
  return new URLSearchParams();
}

function asJson(body: unknown): Record<string, any> {
  if (typeof body !== "string" || !body) return {};
  try {
    const parsed = JSON.parse(body);
    return typeof parsed === "object" && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}

export function createKeycloakFake(
  options: KeycloakFakeOptions = {},
): KeycloakFake {
  const users = new Map<string, FakeUser>(
    (options.users ?? DEFAULT_USERS).map((user) => [user.id, { ...user }]),
  );
  const roles = new Map<string, FakeRole>(
    (options.roles ?? []).map((role) => [role.id, { ...role }]),
  );
  const assignments = new Map<string, Set<string>>(
    Object.entries(options.assignments ?? {}).map(([userId, roleIds]) => [
      userId,
      new Set(roleIds),
    ]),
  );

  const tokenUsers = new Map<string, string>([["test-token", "u1"]]);
  const refreshUsers = new Map<string, string>([["refresh-token", "u1"]]);
  const requests: LoggedRequest[] = [];
  const tokens = {
    user: "test-token",
    refresh: "refresh-token",
    admin: "admin-token",
    forbidden: "forbidden-token",
  };
  let seq = 0;

  function issueTokens(userId: string, rotateAccess = true) {
    const access = rotateAccess ? `access-${++seq}` : tokens.user;
    const refresh = `refresh-${++seq}`;
    tokenUsers.set(access, userId);
    refreshUsers.set(refresh, userId);
    return {
      token_type: "Bearer",
      access_token: access,
      expires_in: 300,
      refresh_token: refresh,
      refresh_expires_in: 1800,
    };
  }

  function bearer(init?: RequestInit): string | undefined {
    const header = new Headers(init?.headers as HeadersInit | undefined).get(
      "authorization",
    );
    return header?.match(/^Bearer\s+(.+)$/i)?.[1];
  }

  return {
    users,
    roles,
    assignments,
    requests,
    tokens,
    impl: (async (input: URL | RequestInfo, init?: RequestInit) => {
      const url = new URL(String(input));
      const path = url.pathname;
      const method = (init?.method ?? "GET").toUpperCase();
      const token = bearer(init);
      requests.push({ method, path, authorization: token });

      if (path.endsWith("/protocol/openid-connect/token")) {
        const form = asForm(init?.body);
        const grant = form.get("grant_type");
        if (grant === "password") {
          const username = form.get("username") ?? "";
          const password = form.get("password") ?? "";
          const user = [...users.values()].find(
            (candidate) =>
              candidate.username === username && candidate.password === password,
          );
          if (!user || !user.enabled) {
            return response(401, {
              error: "invalid_grant",
              error_description: "Invalid user credentials",
            });
          }
          return response(200, issueTokens(user.id));
        }
        if (grant === "refresh_token") {
          const presented = form.get("refresh_token") ?? "";
          const userId = refreshUsers.get(presented);
          const user = userId ? users.get(userId) : undefined;
          if (!user || !user.enabled) {
            return response(401, { error: "invalid_grant" });
          }
          // Rotação: o refresh apresentado é consumido e deixa de valer.
          refreshUsers.delete(presented);
          return response(200, issueTokens(user.id));
        }
        if (grant === "client_credentials") {
          return response(200, {
            token_type: "Bearer",
            access_token: tokens.admin,
            expires_in: 300,
          });
        }
        return response(400, { error: "unsupported_grant_type" });
      }

      if (path.endsWith("/protocol/openid-connect/token/introspect")) {
        const presented = asForm(init?.body).get("token") ?? "";
        const inactive = () => response(200, { active: false });
        if (presented === tokens.admin) {
          return response(200, {
            active: true,
            realm_access: { roles: ["administrator"] },
          });
        }
        // Token válido, porém sem o role exigido pelas rotas administrativas.
        if (presented === tokens.forbidden) {
          return response(200, { active: true, realm_access: { roles: [] } });
        }
        const userId = tokenUsers.get(presented);
        const user = userId ? users.get(userId) : undefined;
        if (!user) return inactive();
        return response(200, {
          active: true,
          realm_access: { roles: user.realmRoles ?? [] },
        });
      }

      const forbidden = token === tokens.forbidden;
      const guardUser = (): Response | undefined => {
        if (!token) return response(401, { error: "unauthorized" });
        if (forbidden) return response(403, { error: "forbidden" });
        if (!tokenUsers.has(token)) return response(401, { error: "unauthorized" });
        return undefined;
      };
      const guardAdmin = (): Response | undefined => {
        if (!token) return response(401, { error: "unauthorized" });
        if (forbidden) return response(403, { error: "forbidden" });
        if (token !== tokens.admin) return response(401, { error: "unauthorized" });
        return undefined;
      };

      const mapping = path.match(
        /^\/admin\/realms\/constrsw\/users\/([^/]+)\/role-mappings\/realm$/,
      );
      if (mapping) {
        const guard = guardAdmin();
        if (guard) return guard;
        const userId = decodeURIComponent(mapping[1]);
        const set = assignments.get(userId) ?? new Set<string>();
        const parsed = asJson(init?.body);
        const refs = Array.isArray(parsed) ? (parsed as Array<{ id: string }>) : [];
        if (method === "POST") refs.forEach((role) => set.add(role.id));
        if (method === "DELETE") refs.forEach((role) => set.delete(role.id));
        assignments.set(userId, set);
        return response(204);
      }

      if (path === `${REALM_BASE}/users`) {
        const guard = guardUser();
        if (guard) return guard;
        if (method === "GET") {
          const enabled = url.searchParams.get("enabled");
          const list = [...users.values()].filter(
            (user) => enabled === null || String(user.enabled) === enabled,
          );
          return response(200, list);
        }
        if (method === "POST") {
          const body = asJson(init?.body);
          if ([...users.values()].some((user) => user.username === body.username)) {
            return response(409, { errorMessage: "User exists" });
          }
          const id = `user-${++seq}`;
          const credentials = Array.isArray(body.credentials)
            ? body.credentials
            : [];
          users.set(id, {
            id,
            username: body.username,
            firstName: body.firstName,
            lastName: body.lastName,
            enabled: body.enabled ?? true,
            password: credentials[0]?.value,
          });
          return response(201, undefined, {
            location: `${REALM_BASE}/users/${id}`,
          });
        }
      }

      const resetPassword = path.match(
        /^\/admin\/realms\/constrsw\/users\/([^/]+)\/reset-password$/,
      );
      if (resetPassword && method === "PUT") {
        const guard = guardUser();
        if (guard) return guard;
        const user = users.get(decodeURIComponent(resetPassword[1]));
        if (!user) return response(404, { errorMessage: "User not found" });
        user.password = asJson(init?.body).value;
        return response(204);
      }

      const userById = path.match(
        /^\/admin\/realms\/constrsw\/users\/([^/]+)$/,
      );
      if (userById) {
        const guard = guardUser();
        if (guard) return guard;
        const id = decodeURIComponent(userById[1]);
        const user = users.get(id);
        if (method === "GET") {
          return user
            ? response(200, user)
            : response(404, { errorMessage: "User not found" });
        }
        if (method === "PUT") {
          if (!user) return response(404, { errorMessage: "User not found" });
          const body = asJson(init?.body);
          if (
            [...users.values()].some(
              (candidate) =>
                candidate.id !== id && candidate.username === body.username,
            )
          ) {
            return response(409, { errorMessage: "User exists" });
          }
          users.set(id, {
            ...user,
            username: body.username ?? user.username,
            firstName: body.firstName ?? user.firstName,
            lastName: body.lastName ?? user.lastName,
            enabled: body.enabled ?? user.enabled,
          });
          return response(204);
        }
      }

      if (path === `${REALM_BASE}/roles`) {
        const guard = guardAdmin();
        if (guard) return guard;
        if (method === "POST") {
          const body = asJson(init?.body);
          if ([...roles.values()].some((role) => role.name === body.name)) {
            return response(409, { errorMessage: "Role exists" });
          }
          const id = `role-${++seq}`;
          roles.set(id, {
            id,
            name: body.name,
            description: body.description,
            attributes: body.attributes,
          });
          return response(201);
        }
        if (method === "GET") {
          const full = url.searchParams.get("briefRepresentation") === "false";
          return response(
            200,
            [...roles.values()].map((role) =>
              full ? role : { ...role, attributes: undefined },
            ),
          );
        }
      }

      const holders = path.match(
        /^\/admin\/realms\/constrsw\/roles\/([^/]+)\/users$/,
      );
      if (holders && method === "GET") {
        const guard = guardAdmin();
        if (guard) return guard;
        const role = [...roles.values()].find(
          (candidate) => candidate.name === decodeURIComponent(holders[1]),
        );
        if (!role) return response(404, { errorMessage: "Role not found" });
        const first = Number(url.searchParams.get("first") ?? 0);
        const max = Number(url.searchParams.get("max") ?? 100);
        const ids = [...assignments.entries()]
          .filter(([, roleIds]) => roleIds.has(role.id))
          .map(([userId]) => ({ id: userId }))
          .sort((a, b) => a.id.localeCompare(b.id));
        return response(200, ids.slice(first, first + max));
      }

      const roleByName = path.match(
        /^\/admin\/realms\/constrsw\/roles\/([^/]+)$/,
      );
      if (roleByName && method === "GET") {
        const guard = guardAdmin();
        if (guard) return guard;
        const role = [...roles.values()].find(
          (candidate) => candidate.name === decodeURIComponent(roleByName[1]),
        );
        return role
          ? response(200, role)
          : response(404, { errorMessage: "Role not found" });
      }

      const roleById = path.match(
        /^\/admin\/realms\/constrsw\/roles-by-id\/([^/]+)$/,
      );
      if (roleById) {
        const guard = guardAdmin();
        if (guard) return guard;
        const id = decodeURIComponent(roleById[1]);
        const role = roles.get(id);
        if (method === "GET") {
          return role
            ? response(200, role)
            : response(404, { errorMessage: "Role not found" });
        }
        if (method === "PUT") {
          if (!role) return response(404, { errorMessage: "Role not found" });
          const body = asJson(init?.body);
          if (
            [...roles.values()].some(
              (candidate) => candidate.id !== id && candidate.name === body.name,
            )
          ) {
            return response(409, { errorMessage: "Role exists" });
          }
          roles.set(id, {
            ...role,
            name: body.name ?? role.name,
            description: body.description,
            attributes: body.attributes ?? role.attributes,
          });
          return response(204);
        }
      }

      return response(404, { errorMessage: "Not found" });
    }) as unknown as typeof fetch,
  };
}
