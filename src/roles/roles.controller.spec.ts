import { RolesController } from "./roles.controller";

function createController() {
  const roles = {
    create: jest.fn(),
    findAll: jest.fn(),
    findOne: jest.fn(),
    update: jest.fn(),
    patch: jest.fn(),
    delete: jest.fn(),
    assignToUser: jest.fn(),
    removeFromUser: jest.fn(),
  };
  return { controller: new RolesController(roles as never), roles };
}

describe("RolesController", () => {
  it("delegates creation to the service", async () => {
    const { controller, roles } = createController();
    roles.create.mockResolvedValue({ id: "r1", name: "professor" });

    await expect(controller.create({ name: "professor" })).resolves.toEqual({
      id: "r1",
      name: "professor",
    });
    expect(roles.create).toHaveBeenCalledWith({ name: "professor" });
  });

  it("delegates listing to the service", async () => {
    const { controller, roles } = createController();
    roles.findAll.mockResolvedValue([{ id: "r1", name: "professor" }]);

    await expect(controller.findAll()).resolves.toEqual([
      { id: "r1", name: "professor" },
    ]);
  });

  it("delegates fetching one role to the service", async () => {
    const { controller, roles } = createController();
    roles.findOne.mockResolvedValue({ id: "r1", name: "professor" });

    await controller.findOne("r1");

    expect(roles.findOne).toHaveBeenCalledWith("r1");
  });

  it("delegates full update to the service", async () => {
    const { controller, roles } = createController();

    await controller.update("r1", { name: "novo" });

    expect(roles.update).toHaveBeenCalledWith("r1", { name: "novo" });
  });

  it("delegates partial update to the service", async () => {
    const { controller, roles } = createController();

    await controller.patch("r1", { description: "d" });

    expect(roles.patch).toHaveBeenCalledWith("r1", { description: "d" });
  });

  it("delegates logical deletion to the service", async () => {
    const { controller, roles } = createController();

    await controller.delete("r1");

    expect(roles.delete).toHaveBeenCalledWith("r1");
  });

  it("delegates role assignment to the service", async () => {
    const { controller, roles } = createController();

    await controller.assign("r1", "user-1");

    expect(roles.assignToUser).toHaveBeenCalledWith("r1", "user-1");
  });

  it("delegates role unassignment to the service", async () => {
    const { controller, roles } = createController();

    await controller.unassign("r1", "user-1");

    expect(roles.removeFromUser).toHaveBeenCalledWith("r1", "user-1");
  });
});
