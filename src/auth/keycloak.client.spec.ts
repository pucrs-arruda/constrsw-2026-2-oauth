import { ConfigService } from "@nestjs/config";
import { KeycloakDependencyError } from "../common/errors";
import { KeycloakClient } from "./keycloak.client";

describe("KeycloakClient", () => {
  const configValues = {
    KEYCLOAK_URL: "http://keycloak:8080",
    KEYCLOAK_REALM: "constrsw",
    KEYCLOAK_TIMEOUT_MS: 5000,
    KEYCLOAK_CLIENT_ID: "oauth",
    KEYCLOAK_CLIENT_SECRET: "secret",
  };

  function createClient() {
    const config = {
      getOrThrow: jest.fn(
        (key: string) => configValues[key as keyof typeof configValues],
      ),
    };

    return new KeycloakClient(config as unknown as ConfigService);
  }

  beforeEach(() => {
    jest.restoreAllMocks();
  });

  it("sends password grant credentials to Keycloak", async () => {
    const response = {
      ok: true,
      json: jest.fn().mockResolvedValue({
        token_type: "Bearer",
        access_token: "access",
        expires_in: 300,
      }),
    };
    const fetchMock = jest
      .spyOn(global, "fetch")
      .mockResolvedValue(response as unknown as Response);
    const client = createClient();

    await expect(client.login("alice", "password")).resolves.toMatchObject({
      access_token: "access",
    });
    expect(fetchMock).toHaveBeenCalledWith(
      new URL(
        "http://keycloak:8080/realms/constrsw/protocol/openid-connect/token",
      ),
      expect.objectContaining({ method: "POST" }),
    );
    const [, options] = fetchMock.mock.calls[0];
    expect(String((options?.body as URLSearchParams).toString())).toContain(
      "grant_type=password",
    );
    expect(String((options?.body as URLSearchParams).toString())).toContain(
      "client_secret=secret",
    );
  });

  it("sends refresh grant credentials to Keycloak", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue({
      ok: true,
      json: jest.fn().mockResolvedValue({ access_token: "access" }),
    } as unknown as Response);
    const client = createClient();

    await client.refresh("refresh-token");

    const fetchMock = global.fetch as jest.Mock;
    const [, options] = fetchMock.mock.calls[0];
    expect(String((options.body as URLSearchParams).toString())).toContain(
      "grant_type=refresh_token",
    );
    expect(String((options.body as URLSearchParams).toString())).toContain(
      "refresh_token=refresh-token",
    );
  });

  it("maps unauthorized responses to invalid credentials", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue({
      ok: false,
      status: 401,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);

    await expect(createClient().login("alice", "wrong")).rejects.toEqual(
      new KeycloakDependencyError("invalid_credentials", 401),
    );
  });

  it("maps other upstream responses to upstream_rejected", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue({
      ok: false,
      status: 500,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);

    await expect(createClient().login("alice", "password")).rejects.toEqual(
      new KeycloakDependencyError("upstream_rejected", 500),
    );
  });

  it("maps network errors to unavailable", async () => {
    jest.spyOn(global, "fetch").mockRejectedValue(new Error("network"));

    await expect(createClient().login("alice", "password")).rejects.toEqual(
      new KeycloakDependencyError("unavailable"),
    );
  });
});
