import { KeycloakDependencyError } from "../common/errors";
import { KeycloakClient } from "./keycloak.client";

describe("KeycloakClient users API", () => {
  const original = { ...process.env };

  beforeEach(() => {
    process.env.KEYCLOAK_URL = "http://keycloak:8080";
    process.env.KEYCLOAK_REALM = "constrsw";
    jest.restoreAllMocks();
  });

  afterAll(() => {
    process.env = original;
  });

  it("lists users using the bearer token and enabled query", async () => {
    const response = { ok: true, json: jest.fn().mockResolvedValue([]) };
    const fetchMock = jest
      .spyOn(global, "fetch")
      .mockResolvedValue(response as unknown as Response);

    await new KeycloakClient().listUsers("access-token", false);

    expect(fetchMock).toHaveBeenCalledWith(
      new URL("http://keycloak:8080/admin/realms/constrsw/users?enabled=false"),
      expect.objectContaining({
        headers: { authorization: "Bearer access-token" },
      }),
    );
  });

  it("gets one user by id", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue({
      ok: true,
      json: jest.fn().mockResolvedValue({ id: "user/1" }),
    } as unknown as Response);

    await expect(
      new KeycloakClient().getUser("token", "user/1"),
    ).resolves.toEqual({ id: "user/1" });
    expect(global.fetch).toHaveBeenCalledWith(
      new URL("http://keycloak:8080/admin/realms/constrsw/users/user%2F1"),
      expect.anything(),
    );
  });

  it("maps an empty user response to not_found", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue({
      ok: true,
      text: jest.fn().mockResolvedValue(""),
    } as unknown as Response);

    await expect(
      new KeycloakClient().getUser("token", "missing"),
    ).rejects.toEqual(new KeycloakDependencyError("not_found", 404));
  });

  it.each([400, 401, 403, 404])(
    "preserves upstream status %s",
    async (status) => {
      jest.spyOn(global, "fetch").mockResolvedValue({
        ok: false,
        status,
        json: jest.fn().mockResolvedValue({}),
      } as unknown as Response);

      await expect(new KeycloakClient().listUsers("token")).rejects.toEqual(
        new KeycloakDependencyError("upstream_rejected", status),
      );
    },
  );
});
