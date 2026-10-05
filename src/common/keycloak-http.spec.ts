import { KeycloakDependencyError } from "./errors";
import { keycloakJson, requestToken } from "./keycloak-http";

function response(body: unknown, ok = true, status = 200): Response {
  const text = body === undefined ? "" : JSON.stringify(body);
  return {
    ok,
    status,
    json: jest.fn().mockResolvedValue(body ?? {}),
    text: jest.fn().mockResolvedValue(text),
  } as unknown as Response;
}

describe("Keycloak HTTP helpers", () => {
  beforeEach(() => jest.restoreAllMocks());

  it("sends token requests as form data and returns the payload", async () => {
    const fetchMock = jest
      .spyOn(global, "fetch")
      .mockResolvedValue(response({ access_token: "token", expires_in: 60 }));

    await expect(
      requestToken(
        new URL("http://keycloak/token"),
        new URLSearchParams({ grant_type: "password" }),
        1000,
      ),
    ).resolves.toMatchObject({ access_token: "token" });

    expect(fetchMock).toHaveBeenCalledWith(
      new URL("http://keycloak/token"),
      expect.objectContaining({
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
      }),
    );
  });

  it("maps token 401 to invalid credentials and other statuses upstream_rejected", async () => {
    for (const [status, reason] of [
      [401, "invalid_credentials"],
      [400, "upstream_rejected"],
    ] as const) {
      jest
        .spyOn(global, "fetch")
        .mockResolvedValue(response({}, false, status));

      await expect(
        requestToken(
          new URL("http://keycloak/token"),
          new URLSearchParams(),
          1000,
        ),
      ).rejects.toEqual(new KeycloakDependencyError(reason, status));
      jest.restoreAllMocks();
    }
  });

  it("maps token network failures and aborts to unavailable", async () => {
    jest.spyOn(global, "fetch").mockRejectedValue(new Error("network"));
    await expect(
      requestToken(
        new URL("http://keycloak/token"),
        new URLSearchParams(),
        1000,
      ),
    ).rejects.toEqual(new KeycloakDependencyError("unavailable"));

    jest.spyOn(global, "fetch").mockImplementation(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () =>
            reject(new Error("aborted")),
          );
        }),
    );
    await expect(
      requestToken(new URL("http://keycloak/token"), new URLSearchParams(), 1),
    ).rejects.toEqual(new KeycloakDependencyError("unavailable"));
  });

  it.each([
    [400, "bad_request"],
    [401, "unauthorized"],
    [403, "forbidden"],
    [404, "not_found"],
    [409, "conflict"],
    [500, "upstream_rejected"],
  ] as const)("maps JSON status %s to %s", async (status, reason) => {
    jest.spyOn(global, "fetch").mockResolvedValue(response({}, false, status));

    await expect(
      keycloakJson(new URL("http://keycloak/users"), { timeoutMs: 1000 }),
    ).rejects.toEqual(new KeycloakDependencyError(reason, status));
  });

  it("sends bearer and JSON body and parses JSON and empty responses", async () => {
    const fetchMock = jest
      .spyOn(global, "fetch")
      .mockResolvedValueOnce(response({ id: "1" }))
      .mockResolvedValueOnce(response(undefined, true, 204));

    await expect(
      keycloakJson(new URL("http://keycloak/users/1"), {
        method: "PUT",
        token: "access-token",
        body: { enabled: false },
        timeoutMs: 1000,
      }),
    ).resolves.toEqual({ id: "1" });
    await expect(
      keycloakJson(new URL("http://keycloak/users/1"), { timeoutMs: 1000 }),
    ).resolves.toBeUndefined();

    expect(fetchMock.mock.calls[0][1]).toEqual(
      expect.objectContaining({
        method: "PUT",
        headers: {
          authorization: "Bearer access-token",
          "content-type": "application/json",
        },
        body: JSON.stringify({ enabled: false }),
      }),
    );
  });
});
