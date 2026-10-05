import type { ConfigService } from "@nestjs/config";
import type { Request, Response } from "express";
import { AuthController } from "./auth.controller";
import type { TokenResponse } from "./keycloak.client";

describe("AuthController", () => {
  const tokenResponse: TokenResponse = {
    token_type: "Bearer",
    access_token: "access-token",
    refresh_token: "refresh-token",
    expires_in: 300,
    refresh_expires_in: 1800,
  };

  function createController() {
    const auth = {
      login: jest.fn().mockResolvedValue(tokenResponse),
      refresh: jest.fn().mockResolvedValue(tokenResponse),
    };
    const config = {
      getOrThrow: jest.fn(
        (key: string) =>
          ({
            SESSION_COOKIE_NAME: "session",
            COOKIE_SECURE: "true",
            COOKIE_SAME_SITE: "strict",
          })[key],
      ),
    };

    return {
      controller: new AuthController(
        auth as never,
        config as unknown as ConfigService,
      ),
      auth,
      config,
    };
  }

  function createResponse() {
    return {
      cookie: jest.fn(),
      clearCookie: jest.fn(),
    } as unknown as Response;
  }

  it("returns a public token payload and stores the session on login", async () => {
    const { controller } = createController();
    const response = createResponse();

    await expect(
      controller.login({ username: "alice", password: "secret" }, response),
    ).resolves.toEqual({
      token_type: "Bearer",
      access_token: "access-token",
      expires_in: 300,
      refresh_token: "refresh-token",
      refresh_expires_in: 1800,
    });
    expect(response.cookie).toHaveBeenCalledWith(
      "session",
      JSON.stringify({
        access_token: "access-token",
        refresh_token: "refresh-token",
      }),
      { httpOnly: true, secure: true, sameSite: "strict" },
    );
  });

  it("reads the refresh token from the session cookie", async () => {
    const { controller, auth } = createController();
    const response = createResponse();
    const request = {
      cookies: { session: JSON.stringify({ refresh_token: "refresh-token" }) },
    } as unknown as Request;

    await expect(controller.refresh(request, response)).resolves.toEqual({
      token_type: "Bearer",
      access_token: "access-token",
      expires_in: 300,
      refresh_token: "refresh-token",
      refresh_expires_in: 1800,
    });
    expect(auth.refresh).toHaveBeenCalledWith("refresh-token");
  });

  it("uses an empty token when the session cookie is missing", async () => {
    const { controller, auth } = createController();

    await controller.refresh(
      { cookies: {} } as unknown as Request,
      createResponse(),
    );

    expect(auth.refresh).toHaveBeenCalledWith("");
  });

  it("clears the configured session cookie on logout", () => {
    const { controller } = createController();
    const response = createResponse();

    expect(controller.logout(response)).toEqual({ status: "signed_out" });
    expect(response.clearCookie).toHaveBeenCalledWith("session");
  });
});
