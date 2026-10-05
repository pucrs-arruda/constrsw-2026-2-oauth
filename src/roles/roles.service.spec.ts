import { ConflictError, NotFoundError } from "../common/errors";
import { RolesService } from "./roles.service";

/** Minimal shape of an `AdminResponse` the service reads back. */
function ok(body: unknown, status = 200) {
  return { status, body, headers: new Headers() };
}

function createService(overrides: Record<string, jest.Mock> = {}) {
  const admin = {
    get: jest.fn(),
    post: jest.fn().mockResolvedValue(ok(undefined, 201)),
    put: jest.fn().mockResolvedValue(ok(undefined, 204)),
    request: jest.fn().mockResolvedValue(ok(undefined, 204)),
    ...overrides,
  };
  return { service: new RolesService(admin as never), admin };
}

describe("RolesService", () => {
  it("creates a role and re-reads it by name to expose the id", async () => {
    const { service, admin } = createService();
    admin.get.mockResolvedValue(
      ok({ id: "r1", name: "professor", description: "Docente" }),
    );

    await expect(
      service.create({ name: "professor", description: "Docente" }),
    ).resolves.toEqual({ id: "r1", name: "professor", description: "Docente" });
    expect(admin.post).toHaveBeenCalledWith("/roles", {
      name: "professor",
      description: "Docente",
    });
    expect(admin.get).toHaveBeenCalledWith("/roles/professor");
  });

  it("restates an upstream conflict as a role conflict", async () => {
    const { service, admin } = createService();
    admin.post.mockRejectedValue(new ConflictError("dup user", "keycloak-admin"));

    await expect(service.create({ name: "dup" })).rejects.toBeInstanceOf(
      ConflictError,
    );
  });

  it("lists only roles that are not logically deleted", async () => {
    const { service, admin } = createService();
    admin.get.mockResolvedValue(
      ok([
        { id: "r1", name: "professor" },
        { id: "r2", name: "antigo", attributes: { deleted: ["true"] } },
      ]),
    );

    await expect(service.findAll()).resolves.toEqual([
      { id: "r1", name: "professor" },
    ]);
    expect(admin.get).toHaveBeenCalledWith("/roles?briefRepresentation=false");
  });

  it("returns a single active role", async () => {
    const { service, admin } = createService();
    admin.get.mockResolvedValue(ok({ id: "r1", name: "professor" }));

    await expect(service.findOne("r1")).resolves.toEqual({
      id: "r1",
      name: "professor",
    });
  });

  it("treats a logically deleted role as not found", async () => {
    const { service, admin } = createService();
    admin.get.mockResolvedValue(
      ok({ id: "r1", name: "antigo", attributes: { deleted: ["true"] } }),
    );

    await expect(service.findOne("r1")).rejects.toBeInstanceOf(NotFoundError);
  });

  it("restates an upstream 404 as a role NotFoundError", async () => {
    const { service, admin } = createService();
    admin.get.mockRejectedValue(new NotFoundError("no user", "keycloak-admin"));

    await expect(service.findOne("missing")).rejects.toBeInstanceOf(
      NotFoundError,
    );
  });

  it("logically deletes by setting the deleted attribute", async () => {
    const { service, admin } = createService();
    admin.get
      .mockResolvedValueOnce(ok({ id: "r1", name: "professor" }))
      .mockResolvedValueOnce(ok([]));

    await service.delete("r1");

    expect(admin.put).toHaveBeenCalledWith(
      "/roles-by-id/r1",
      expect.objectContaining({ attributes: { deleted: ["true"] } }),
    );
  });

  it("strips a deleted role from every holder, across pages", async () => {
    const { service, admin } = createService();
    const fullPage = Array.from({ length: 100 }, (_, i) => ({ id: `u${i}` }));
    admin.get
      .mockResolvedValueOnce(ok({ id: "r1", name: "professor" }))
      .mockResolvedValueOnce(ok(fullPage))
      .mockResolvedValueOnce(ok([{ id: "u100" }]));
    admin.request.mockImplementation(async (_method, path: string) => {
      if (path === "/users/u5/role-mappings/realm") {
        throw new NotFoundError("gone", "keycloak-admin");
      }
      return ok(undefined, 204);
    });

    await service.delete("r1");

    expect(admin.get).toHaveBeenCalledWith(
      "/roles/professor/users?first=100&max=100",
    );
    expect(admin.request).toHaveBeenCalledTimes(101);
    expect(admin.request).toHaveBeenLastCalledWith(
      "DELETE",
      "/users/u100/role-mappings/realm",
      [{ id: "r1", name: "professor" }],
    );
  });

  it("still unassigns a logically deleted role", async () => {
    const { service, admin } = createService();
    admin.get.mockResolvedValue(
      ok({ id: "r1", name: "antigo", attributes: { deleted: ["true"] } }),
    );

    await service.removeFromUser("r1", "user-1");

    expect(admin.request).toHaveBeenCalledWith(
      "DELETE",
      "/users/user-1/role-mappings/realm",
      [{ id: "r1", name: "antigo" }],
    );
  });

  it("says when a conflicting name belongs to a deleted role", async () => {
    const { service, admin } = createService();
    admin.post.mockRejectedValue(new ConflictError("dup user", "keycloak-admin"));
    admin.get.mockResolvedValue(
      ok({ id: "r1", name: "antigo", attributes: { deleted: ["true"] } }),
    );

    await expect(service.create({ name: "antigo" })).rejects.toThrow(
      "Este nome pertence a um papel excluído; escolha outro nome.",
    );
  });

  it("restates a rename conflict as a role conflict", async () => {
    const { service, admin } = createService();
    admin.get
      .mockResolvedValueOnce(ok({ id: "r2", name: "b" }))
      .mockResolvedValueOnce(ok({ id: "r1", name: "a" }));
    admin.put.mockRejectedValue(new ConflictError("dup user", "keycloak-admin"));

    await expect(service.patch("r2", { name: "a" })).rejects.toThrow(
      "Já existe um papel com este nome.",
    );
  });

  it("treats a client role as not found", async () => {
    const { service, admin } = createService();
    admin.get.mockResolvedValue(
      ok({ id: "c1", name: "manage-users", clientRole: true }),
    );

    await expect(service.findOne("c1")).rejects.toBeInstanceOf(NotFoundError);
    await expect(service.assignToUser("c1", "user-1")).rejects.toThrow(
      "Papel não encontrado no realm.",
    );
    expect(admin.post).not.toHaveBeenCalled();
  });

  it("assigns a role to a user using id and name", async () => {
    const { service, admin } = createService();
    admin.get.mockResolvedValue(ok({ id: "r1", name: "professor" }));

    await service.assignToUser("r1", "user-1");

    expect(admin.post).toHaveBeenCalledWith(
      "/users/user-1/role-mappings/realm",
      [{ id: "r1", name: "professor" }],
    );
  });

  it("removes a role from a user using id and name", async () => {
    const { service, admin } = createService();
    admin.get.mockResolvedValue(ok({ id: "r1", name: "professor" }));

    await service.removeFromUser("r1", "user-1");

    expect(admin.request).toHaveBeenCalledWith(
      "DELETE",
      "/users/user-1/role-mappings/realm",
      [{ id: "r1", name: "professor" }],
    );
  });

  it("fully updates a role and returns the reloaded value", async () => {
    const { service, admin } = createService();
    admin.get
      .mockResolvedValueOnce(ok({ id: "r1", name: "antigo" }))
      .mockResolvedValueOnce(ok({ id: "r1", name: "novo", description: "d" }));

    await expect(
      service.update("r1", { name: "novo", description: "d" }),
    ).resolves.toEqual({ id: "r1", name: "novo", description: "d" });
    expect(admin.put).toHaveBeenCalledWith(
      "/roles-by-id/r1",
      expect.objectContaining({ name: "novo", description: "d" }),
    );
  });

  it("patches only the provided fields", async () => {
    const { service, admin } = createService();
    admin.get
      .mockResolvedValueOnce(ok({ id: "r1", name: "antigo", description: "manter" }))
      .mockResolvedValueOnce(ok({ id: "r1", name: "antigo", description: "alterado" }));

    await service.patch("r1", { description: "alterado" });

    expect(admin.put).toHaveBeenCalledWith(
      "/roles-by-id/r1",
      expect.objectContaining({ name: "antigo", description: "alterado" }),
    );
  });
});
