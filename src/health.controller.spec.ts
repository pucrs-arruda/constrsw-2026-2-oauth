import { HealthController } from "./health.controller";

describe("HealthController", () => {
  it("returns the OAuth health response", () => {
    expect(new HealthController().check()).toEqual({
      status: "ok",
      service: "oauth",
    });
  });
});
