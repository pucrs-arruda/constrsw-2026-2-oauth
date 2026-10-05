import { ArgumentsHost, BadRequestException, Logger } from "@nestjs/common";
import { KeycloakDependencyError } from "./errors";
import { ProblemDetailsFilter } from "./problem-details.filter";

describe("ProblemDetailsFilter", () => {
  beforeEach(() => {
    // O filtro loga 5xx de propósito; aqui os 503/500 são casos de teste,
    // então o log esperado não deve poluir a saída da suíte.
    jest.spyOn(Logger.prototype, "error").mockImplementation(() => undefined);
  });

  function createHost() {
    const response = {
      status: jest.fn().mockReturnThis(),
      type: jest.fn().mockReturnThis(),
      send: jest.fn(),
    };
    const host = {
      switchToHttp: () => ({
        getResponse: () => response,
      }),
    } as unknown as ArgumentsHost;

    return { host, response };
  }

  it.each([
    [new KeycloakDependencyError("invalid_credentials", 401), 401, "OA-401"],
    [new KeycloakDependencyError("unavailable"), 503, "OA-503"],
    [new BadRequestException(), 400, "OA-400"],
    [new Error("unexpected"), 500, "OA-500"],
  ])("maps %s to the expected problem response", (exception, status, code) => {
    const { host, response } = createHost();

    new ProblemDetailsFilter().catch(exception, host);

    expect(response.status).toHaveBeenCalledWith(status);
    expect(response.type).toHaveBeenCalledWith("application/json");
    expect(response.send).toHaveBeenCalledWith(
      expect.objectContaining({ error_code: code }),
    );
    expect(Object.keys(response.send.mock.calls[0][0])).toEqual([
      "error_code",
      "error_description",
      "error_source",
      "error_stack",
    ]);
  });
});
