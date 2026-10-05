import { AuthService } from "./auth.service";
import type { TokenResponse } from "./keycloak.client";

describe("AuthService", () => {
  const tokenResponse: TokenResponse = {
    token_type: "Bearer",
    access_token: "access",
    expires_in: 300,
  };

  it("delegates login to KeycloakClient", async () => {
    const keycloak = { login: jest.fn().mockResolvedValue(tokenResponse) };
    const service = new AuthService(keycloak as never);

    await expect(service.login("alice", "password")).resolves.toEqual(
      tokenResponse,
    );
    expect(keycloak.login).toHaveBeenCalledWith("alice", "password");
  });

  it("delegates refresh to KeycloakClient", async () => {
    const keycloak = { refresh: jest.fn().mockResolvedValue(tokenResponse) };
    const service = new AuthService(keycloak as never);

    await expect(service.refresh("refresh-token")).resolves.toEqual(
      tokenResponse,
    );
    expect(keycloak.refresh).toHaveBeenCalledWith("refresh-token");
  });
});
