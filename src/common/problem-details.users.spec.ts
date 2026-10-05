import { ArgumentsHost, UnauthorizedException } from "@nestjs/common";
import { KeycloakDependencyError } from "./errors";
import { ProblemDetailsFilter } from "./problem-details.filter";

describe("ProblemDetailsFilter user errors", () => {
  it.each([
    [400, "OA-400"],
    [401, "OA-401"],
    [403, "OA-403"],
    [404, "OA-404"],
  ])("maps Keycloak status %s", (status, code) => {
    const send = jest.fn();
    const response = {
      status: jest.fn().mockReturnThis(),
      type: jest.fn().mockReturnThis(),
      send,
    };
    const host = {
      switchToHttp: () => ({
        getResponse: () => response,
      }),
    } as unknown as ArgumentsHost;

    new ProblemDetailsFilter().catch(
      new KeycloakDependencyError("upstream_rejected", status),
      host,
    );

    expect(response.status).toHaveBeenCalledWith(status);
    expect(send).toHaveBeenCalledWith(
      expect.objectContaining({
        error_code: code,
        error_source: "OAuthAPI",
        error_stack: [
          {
            error_code: String(status),
            error_description: "Erro retornado pelo Keycloak.",
            error_source: "Keycloak",
          },
          expect.objectContaining({
            error_code: code,
            error_source: "OAuthAPI",
          }),
        ],
      }),
    );
    expect(Object.keys(send.mock.calls[0][0])).toEqual([
      "error_code",
      "error_description",
      "error_source",
      "error_stack",
    ]);
  });

  it("returns the required envelope for a missing bearer token", () => {
    const send = jest.fn();
    const response = {
      status: jest.fn().mockReturnThis(),
      type: jest.fn().mockReturnThis(),
      send,
    };
    const host = {
      switchToHttp: () => ({
        getResponse: () => response,
      }),
    } as unknown as ArgumentsHost;

    new ProblemDetailsFilter().catch(new UnauthorizedException(), host);

    expect(send).toHaveBeenCalledWith(
      expect.objectContaining({
        error_code: "OA-401",
        error_description: "O access token é obrigatório ou inválido.",
        error_source: "OAuthAPI",
      }),
    );
  });
});
