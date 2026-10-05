import { validateEnvironment } from "./env.config";

const validEnvironment = {
  PORT: "8088",
  NODE_ENV: "test",
  KEYCLOAK_URL: "http://keycloak:8080",
  KEYCLOAK_REALM: "constrsw",
  KEYCLOAK_CLIENT_ID: "oauth",
  KEYCLOAK_CLIENT_SECRET: "secret",
  KEYCLOAK_ADMIN_CLIENT_ID: "oauth-admin",
  KEYCLOAK_ADMIN_CLIENT_SECRET: "admin-secret",
  KEYCLOAK_TIMEOUT_MS: "5000",
  SESSION_COOKIE_NAME: "session",
  COOKIE_SECURE: "false",
  COOKIE_SAME_SITE: "lax",
};

describe("validateEnvironment", () => {
  it("parses valid values and coerces numbers", () => {
    expect(validateEnvironment(validEnvironment)).toEqual({
      ...validEnvironment,
      PORT: 8088,
      KEYCLOAK_TIMEOUT_MS: 5000,
    });
  });

  it.each([
    ["PORT", "0"],
    ["KEYCLOAK_URL", "not-a-url"],
    ["KEYCLOAK_CLIENT_SECRET", ""],
    ["COOKIE_SECURE", "yes"],
    ["COOKIE_SAME_SITE", "invalid"],
  ])("rejects invalid %s", (key, value) => {
    expect(() =>
      validateEnvironment({ ...validEnvironment, [key]: value }),
    ).toThrow();
  });

  it("rejects missing required values", () => {
    const { KEYCLOAK_CLIENT_SECRET: _, ...missingSecret } = validEnvironment;

    expect(() => validateEnvironment(missingSecret)).toThrow();
  });
});
