import { BadRequestException, UnauthorizedException } from "@nestjs/common";
import { UsersController } from "./users.controller";

function createController() {
  const users = {
    list: jest.fn().mockResolvedValue([]),
    get: jest.fn().mockResolvedValue({ id: "user-1" }),
    create: jest.fn().mockResolvedValue({ id: "u-9" }),
    update: jest.fn().mockResolvedValue(undefined),
    patch: jest.fn().mockResolvedValue(undefined),
    delete: jest.fn().mockResolvedValue(undefined),
  };
  return { users, controller: new UsersController(users as never) };
}

describe("UsersController", () => {
  it("passes the bearer token and enabled filter to the service", async () => {
    const { users, controller } = createController();

    await controller.list("Bearer access-token", true);

    expect(users.list).toHaveBeenCalledWith("access-token", true);
  });

  it("gets a user by id with the bearer token", async () => {
    const { users, controller } = createController();

    await controller.get("bearer access-token", "user-1");

    expect(users.get).toHaveBeenCalledWith("access-token", "user-1");
  });

  it("delegates user mutations with the bearer token", async () => {
    const { users, controller } = createController();
    const input = {
      username: "ana.souza@pucrs.br",
      "first-name": "Ana",
      "last-name": "Souza",
      password: "senha-segura",
    };

    await controller.create("Bearer token", input as never);
    await controller.update("Bearer token", "u-9", input as never);
    await controller.patch("Bearer token", "u-9", { "first-name": "Aninha" });
    await controller.delete("Bearer token", "u-9");

    expect(users.create).toHaveBeenCalledWith("token", input);
    expect(users.update).toHaveBeenCalledWith("token", "u-9", input);
    expect(users.patch).toHaveBeenCalledWith("token", "u-9", {
      "first-name": "Aninha",
    });
    expect(users.delete).toHaveBeenCalledWith("token", "u-9");
  });

  it.each([undefined, "Basic credentials", "Bearer"])(
    "rejects malformed authorization header: %s",
    (authorization) => {
      const { controller } = createController();

      expect(() => controller.list(authorization)).toThrow(
        UnauthorizedException,
      );
    },
  );

  it("rejects a missing user id", () => {
    const { controller } = createController();

    expect(() => controller.get("Bearer token", undefined)).toThrow(
      BadRequestException,
    );
  });
});
