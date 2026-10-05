import {
  ConflictError,
  KeycloakError,
  NotFoundError,
  ValidationError,
} from "../common/errors";
import { KeycloakAdminClient } from "./keycloak-admin.client";

type FakeResponse = Pick<Response, "ok" | "status" | "headers" | "text">;

function respond(
  status: number,
  body: unknown = "",
  headers: Record<string, string> = {},
): FakeResponse {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(headers),
    text: jest
      .fn()
      .mockResolvedValue(typeof body === "string" ? body : JSON.stringify(body)),
  };
}

function tokenResponse(accessToken = "admin-token", expiresIn = 60) {
  return respond(200, { access_token: accessToken, expires_in: expiresIn });
}

function createClient() {
  const config = {
    getOrThrow: jest.fn(
      (key: string) =>
        ({
          KEYCLOAK_URL: "http://keycloak:8080",
          KEYCLOAK_REALM: "closed-cras",
          KEYCLOAK_TIMEOUT_MS: 5000,
          KEYCLOAK_ADMIN_CLIENT_ID: "oauth-admin",
          KEYCLOAK_ADMIN_CLIENT_SECRET: "admin-secret",
        })[key],
    ),
  };

  return new KeycloakAdminClient(config as never);
}

function mockFetch(...responses: FakeResponse[]) {
  const fetchMock = jest.spyOn(global, "fetch");
  for (const response of responses) {
    fetchMock.mockResolvedValueOnce(response as unknown as Response);
  }
  return fetchMock;
}

describe("KeycloakAdminClient", () => {
  // `clearMocks` não desfaz o spy nem drena a fila de mockResolvedValueOnce:
  // sem isto um teste desbalanceado cairia no fetch real.
  afterEach(() => jest.restoreAllMocks());

  it("obtains an admin token with client_credentials before the first call", async () => {
    const fetchMock = mockFetch(tokenResponse(), respond(204));

    await createClient().put("/users/u-1", { enabled: false });

    const [tokenUrl, tokenInit] = fetchMock.mock.calls[0];
    expect(String(tokenUrl)).toBe(
      "http://keycloak:8080/realms/closed-cras/protocol/openid-connect/token",
    );
    expect(String(tokenInit?.body)).toContain("grant_type=client_credentials");
    expect(String(tokenInit?.body)).toContain("client_id=oauth-admin");

    const [adminUrl, adminInit] = fetchMock.mock.calls[1];
    expect(String(adminUrl)).toBe(
      "http://keycloak:8080/admin/realms/closed-cras/users/u-1",
    );
    expect(
      (adminInit?.headers as Record<string, string>).authorization,
    ).toBe("Bearer admin-token");
  });

  it("obtains a fresh admin token on every call", async () => {
    const fetchMock = mockFetch(
      tokenResponse("first"),
      respond(204),
      tokenResponse("second"),
      respond(204),
    );
    const client = createClient();

    await client.put("/users/u-1", { enabled: false });
    await client.put("/users/u-2", { enabled: false });

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(String(fetchMock.mock.calls[0][0])).toContain("/token");
    expect(String(fetchMock.mock.calls[2][0])).toContain("/token");
    expect(
      (fetchMock.mock.calls[3][1]?.headers as Record<string, string>)
        .authorization,
    ).toBe("Bearer second");
  });

  it("translates a 401 from the admin API without retrying", async () => {
    const fetchMock = mockFetch(tokenResponse(), respond(401));

    await expect(createClient().post("/users", {})).rejects.toMatchObject({
      status: 401,
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("preserves a path prefix on KEYCLOAK_URL", async () => {
    const fetchMock = mockFetch(tokenResponse(), respond(204));
    const config = {
      getOrThrow: jest.fn(
        (key: string) =>
          ({
            KEYCLOAK_URL: "http://kc:8080/auth/",
            KEYCLOAK_REALM: "closed-cras",
            KEYCLOAK_TIMEOUT_MS: 5000,
            KEYCLOAK_ADMIN_CLIENT_ID: "oauth-admin",
            KEYCLOAK_ADMIN_CLIENT_SECRET: "admin-secret",
          })[key],
      ),
    };

    await new KeycloakAdminClient(config as never).put("/users/u-1", {});

    expect(String(fetchMock.mock.calls[0][0])).toBe(
      "http://kc:8080/auth/realms/closed-cras/protocol/openid-connect/token",
    );
    expect(String(fetchMock.mock.calls[1][0])).toBe(
      "http://kc:8080/auth/admin/realms/closed-cras/users/u-1",
    );
  });

  it("carries the upstream status and reason into the error chain", async () => {
    mockFetch(
      tokenResponse(),
      respond(409, { errorMessage: "User exists with same username" }),
    );

    await expect(createClient().post("/users", {})).rejects.toMatchObject({
      status: 409,
      chain: [
        {
          source: "keycloak",
          code: "KC-409",
          description: "User exists with same username",
        },
      ],
    });
  });

  it("reports a stalled body read as an unavailable provider", async () => {
    jest.spyOn(global, "fetch").mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers(),
      text: jest.fn().mockRejectedValue(new Error("aborted")),
    } as unknown as Response);

    await expect(createClient().get("/users")).rejects.toMatchObject({
      status: 503,
    });
  });

  it("rejects a token response without an access_token", async () => {
    mockFetch(respond(200, { expires_in: 60 }));

    await expect(createClient().get("/users")).rejects.toMatchObject({
      status: 502,
    });
  });

  it("returns the response headers so the caller can read Location", async () => {
    mockFetch(
      tokenResponse(),
      respond(201, "", {
        location: "http://keycloak:8080/admin/realms/closed-cras/users/new-id",
      }),
    );

    const response = await createClient().post("/users", { username: "ana" });

    expect(response.headers.get("location")).toContain("new-id");
  });

  it.each([
    [400, ValidationError],
    [404, NotFoundError],
    [409, ConflictError],
    [403, KeycloakError],
    [500, KeycloakError],
  ])("translates upstream %s into the matching exception", async (status, expected) => {
    mockFetch(tokenResponse(), respond(status, { error: "nope" }));

    await expect(createClient().post("/users", {})).rejects.toBeInstanceOf(
      expected,
    );
  });

  it("preserves forbidden status from the admin API", async () => {
    mockFetch(tokenResponse(), respond(403, { error: "forbidden" }));

    await expect(createClient().post("/users", {})).rejects.toMatchObject({
      status: 403,
      code: "OA-403",
      source: "keycloak-admin",
      upstreamStatus: 403,
    });
  });

  it("answers 503 when the admin API is unreachable", async () => {
    jest
      .spyOn(global, "fetch")
      .mockResolvedValueOnce(tokenResponse() as unknown as Response)
      .mockRejectedValueOnce(new Error("ECONNREFUSED"));

    await expect(createClient().get("/users")).rejects.toMatchObject({
      status: 503,
      code: "OA-503",
    });
  });

  it("answers 502 when the token endpoint rejects the service account", async () => {
    mockFetch(respond(401, { error: "invalid_client" }));

    await expect(createClient().get("/users")).rejects.toMatchObject({
      status: 502,
      code: "OA-502",
    });
  });

  it("treats a 204 as an empty body instead of a parse failure", async () => {
    mockFetch(tokenResponse(), respond(204));

    await expect(createClient().delete("/users/u-1")).resolves.toEqual({
      status: 204,
      body: undefined,
      headers: expect.any(Headers),
    });
  });
});
