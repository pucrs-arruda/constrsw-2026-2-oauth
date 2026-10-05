import { NotFoundException } from "@nestjs/common";
import { KeycloakDependencyError, ValidationError } from "../common/errors";
import { CreateUserDto } from "./dto/create-user.dto";
import { UsersService } from "./users.service";

function createAdmin(
  location = "http://kc/admin/realms/closed-cras/users/u-9",
) {
  return {
    post: jest.fn().mockResolvedValue({
      status: 201,
      body: undefined,
      headers: new Headers({ location }),
    }),
    put: jest.fn().mockResolvedValue({
      status: 204,
      body: undefined,
      headers: new Headers(),
    }),
    get: jest.fn().mockResolvedValue({
      status: 200,
      body: {
        id: "u-9",
        username: "ana.souza@pucrs.br",
        firstName: "Ana",
        lastName: "Souza",
        enabled: true,
      },
      headers: new Headers(),
    }),
    delete: jest.fn(),
  };
}

function createReadClient() {
  return {
    listUsers: jest.fn(),
    getUser: jest.fn(),
  };
}

const newUser: CreateUserDto = {
  username: "ana.souza@pucrs.br",
  "first-name": "Ana",
  "last-name": "Souza",
  password: "senha-segura",
};

describe("UsersService", () => {
  it("maps Keycloak user fields to the public response contract", async () => {
    const keycloak = createReadClient();
    keycloak.listUsers.mockResolvedValue([
      {
        id: "1",
        username: "a@b.com",
        firstName: "Ada",
        lastName: "Lovelace",
        enabled: true,
      },
    ]);
    const service = new UsersService(keycloak as never, undefined as never);

    await expect(service.list("token", false)).resolves.toEqual([
      {
        id: "1",
        username: "a@b.com",
        "first-name": "Ada",
        "last-name": "Lovelace",
        enabled: true,
      },
    ]);
    expect(keycloak.listUsers).toHaveBeenCalledWith("token", false);
  });

  it("uses empty strings when Keycloak omits names", async () => {
    const keycloak = createReadClient();
    keycloak.getUser.mockResolvedValue({
      id: "1",
      username: "a@b.com",
      enabled: false,
    });
    const service = new UsersService(keycloak as never, undefined as never);

    await expect(service.get("token", "1")).resolves.toEqual({
      id: "1",
      username: "a@b.com",
      "first-name": "",
      "last-name": "",
      enabled: false,
    });
  });

  it("does not expose Keycloak service-account users", async () => {
    const keycloak = createReadClient();
    keycloak.listUsers.mockResolvedValue([
      { id: "1", username: "admin@pucrs.br", enabled: true },
      { id: "2", username: "service-account-oauth", enabled: true },
    ]);
    const service = new UsersService(keycloak as never, undefined as never);

    await expect(service.list("token", true)).resolves.toHaveLength(1);
  });

  it("maps a missing user to NotFound and preserves other dependency errors", async () => {
    const missingClient = createReadClient();
    missingClient.getUser.mockRejectedValue(
      new KeycloakDependencyError("not_found", 404),
    );
    const missing = new UsersService(
      missingClient as never,
      undefined as never,
    );
    await expect(missing.get("token", "missing")).rejects.toBeInstanceOf(
      NotFoundException,
    );

    const forbidden = new KeycloakDependencyError("forbidden", 403);
    const deniedClient = createReadClient();
    deniedClient.getUser.mockRejectedValue(forbidden);
    const denied = new UsersService(deniedClient as never, undefined as never);
    await expect(denied.get("token", "denied")).rejects.toBe(forbidden);
  });

  it("creates a user and returns the id from Location", async () => {
    const admin = createAdmin();
    const service = new UsersService(undefined as never, admin as never);

    await expect(service.create("token", newUser)).resolves.toEqual({
      id: "u-9",
      username: "ana.souza@pucrs.br",
      "first-name": "Ana",
      "last-name": "Souza",
      enabled: true,
    });
    expect(admin.post).toHaveBeenCalledWith(
      "/users",
      expect.objectContaining({
        username: newUser.username,
        email: newUser.username,
        enabled: true,
        credentials: [
          { type: "password", value: newUser.password, temporary: false },
        ],
      }),
      "token",
    );
    expect(admin.get).toHaveBeenCalledWith("/users/u-9", "token");
  });

  it("updates the complete user representation", async () => {
    const admin = createAdmin();
    const service = new UsersService(undefined as never, admin as never);

    await service.update("token", "u-9", {
      username: newUser.username,
      "first-name": newUser["first-name"],
      "last-name": newUser["last-name"],
    });

    expect(admin.put).toHaveBeenCalledWith(
      "/users/u-9",
      {
        username: newUser.username,
        email: newUser.username,
        firstName: newUser["first-name"],
        lastName: newUser["last-name"],
        emailVerified: false,
      },
      "token",
    );
  });

  it("changes the password before updating the profile", async () => {
    const admin = createAdmin();
    const service = new UsersService(undefined as never, admin as never);

    await service.patch("token", "u-9", {
      "first-name": "Aninha",
      password: "senha-nova",
    });

    expect(admin.put).toHaveBeenNthCalledWith(
      1,
      "/users/u-9/reset-password",
      { type: "password", value: "senha-nova", temporary: false },
      "token",
    );
    expect(admin.put).toHaveBeenNthCalledWith(
      2,
      "/users/u-9",
      {
        firstName: "Aninha",
      },
      "token",
    );
  });

  it("rejects an empty patch", async () => {
    const admin = createAdmin();
    const service = new UsersService(undefined as never, admin as never);

    await expect(service.patch("token", "u-9", {})).rejects.toBeInstanceOf(
      ValidationError,
    );
    expect(admin.put).not.toHaveBeenCalled();
  });

  it("disables the user instead of deleting it", async () => {
    const admin = createAdmin();
    const service = new UsersService(undefined as never, admin as never);

    await service.delete("token", "u 9/../admin");

    expect(admin.put).toHaveBeenCalledWith(
      "/users/u%209%2F..%2Fadmin",
      { enabled: false },
      "token",
    );
    expect(admin.delete).not.toHaveBeenCalled();
  });
});
