import { validate } from "class-validator";
import { CreateRoleDto } from "./create-role.dto";

describe("CreateRoleDto", () => {
  it("accepts a valid role", async () => {
    const dto = Object.assign(new CreateRoleDto(), {
      name: "professor",
      description: "Docente da instituição",
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });

  it("accepts a role without description", async () => {
    const dto = Object.assign(new CreateRoleDto(), { name: "aluno" });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });

  it("rejects an empty name", async () => {
    const dto = Object.assign(new CreateRoleDto(), { name: "" });

    await expect(validate(dto)).resolves.toEqual(
      expect.arrayContaining([expect.objectContaining({ property: "name" })]),
    );
  });

  it("rejects a non-string description", async () => {
    const dto = Object.assign(new CreateRoleDto(), {
      name: "aluno",
      description: 42,
    });

    await expect(validate(dto)).resolves.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ property: "description" }),
      ]),
    );
  });
});
