import { validate } from "class-validator";
import { LoginDto } from "./login.dto";

describe("LoginDto", () => {
  it("accepts a valid login", async () => {
    const dto = Object.assign(new LoginDto(), {
      username: "usuario@pucrs.br",
      password: "senha-segura",
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });

  it("rejects an invalid email", async () => {
    const dto = Object.assign(new LoginDto(), {
      username: "usuario-invalido",
      password: "senha-segura",
    });

    await expect(validate(dto)).resolves.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ property: "username" }),
      ]),
    );
  });

  it("rejects an empty password", async () => {
    const dto = Object.assign(new LoginDto(), {
      username: "usuario@pucrs.br",
      password: "",
    });

    await expect(validate(dto)).resolves.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ property: "password" }),
      ]),
    );
  });
});
