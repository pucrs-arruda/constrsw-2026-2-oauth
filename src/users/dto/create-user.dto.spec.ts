import { plainToInstance } from "class-transformer";
import { validate } from "class-validator";
import { CreateUserDto } from "./create-user.dto";
import { PatchUserDto } from "./patch-user.dto";
import { ReplaceUserDto } from "./replace-user.dto";

function createDto(overrides: Partial<CreateUserDto> = {}) {
  return Object.assign(new CreateUserDto(), {
    username: "ana.souza@pucrs.br",
    "first-name": "Ana",
    "last-name": "Souza",
    password: "senha-segura",
    ...overrides,
  });
}

describe("CreateUserDto", () => {
  it("accepts username, password and names — there is no email field", async () => {
    await expect(validate(createDto())).resolves.toHaveLength(0);
  });

  it.each([
    ["ana@@pucrs.br", "double at sign"],
    ["ana@pucrs", "domain without a dot"],
    [".ana@pucrs.br", "local part starting with a dot"],
    ["ana pucrs@pucrs.br", "whitespace in the local part"],
  ])("rejects username %s (%s)", async (username) => {
    await expect(validate(createDto({ username }))).resolves.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ property: "username" }),
      ]),
    );
  });

  it.each(["ana.souza@pucrs.br", "ana+turma1@aluno.pucrs.br", "a_b-c@x.co"])(
    "accepts username %s",
    async (username) => {
      await expect(validate(createDto({ username }))).resolves.toHaveLength(0);
    },
  );

  it("trims surrounding whitespace before validating", async () => {
    const dto = plainToInstance(CreateUserDto, {
      username: "  ana.souza@pucrs.br  ",
      "first-name": "  Ana  ",
      "last-name": "Souza",
      password: "senha-segura",
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
    expect(dto["first-name"]).toBe("Ana");
    expect(dto.username).toBe("ana.souza@pucrs.br");
  });

  it("rejects a whitespace-only name", async () => {
    const dto = plainToInstance(CreateUserDto, {
      username: "ana.souza@pucrs.br",
      "first-name": "   ",
      "last-name": "Souza",
      password: "senha-segura",
    });

    await expect(validate(dto)).resolves.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ property: "first-name" }),
      ]),
    );
  });

  it("rejects a short password", async () => {
    await expect(validate(createDto({ password: "123" }))).resolves.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ property: "password" }),
      ]),
    );
  });
});

describe("ReplaceUserDto", () => {
  it("accepts username and names", async () => {
    const dto = Object.assign(new ReplaceUserDto(), {
      username: "ana.souza@pucrs.br",
      "first-name": "Ana",
      "last-name": "Souza",
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });

  it("rejects a non-email username", async () => {
    const dto = Object.assign(new ReplaceUserDto(), {
      username: "ana",
      "first-name": "Ana",
      "last-name": "Souza",
    });

    await expect(validate(dto)).resolves.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ property: "username" }),
      ]),
    );
  });

  it("rejects a missing username", async () => {
    const dto = Object.assign(new ReplaceUserDto(), {
      "first-name": "Ana",
      "last-name": "Souza",
    });

    await expect(validate(dto)).resolves.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ property: "username" }),
      ]),
    );
  });
});

describe("PatchUserDto", () => {
  it("accepts a single field", async () => {
    const dto = Object.assign(new PatchUserDto(), { "first-name": "Ana" });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });

  it("accepts an empty body at the DTO layer — the service refuses it", async () => {
    await expect(validate(new PatchUserDto())).resolves.toHaveLength(0);
  });

  it.each([
    ["password", { password: null }],
    ["username", { username: null }],
    ["first-name", { "first-name": null }],
  ])("rejects an explicit null %s", async (property, body) => {
    // @IsOptional ignoraria o null e ele chegaria cru ao Keycloak.
    const dto = Object.assign(new PatchUserDto(), body);

    await expect(validate(dto)).resolves.toEqual(
      expect.arrayContaining([expect.objectContaining({ property })]),
    );
  });

  it("still validates username as an email when present", async () => {
    const dto = Object.assign(new PatchUserDto(), {
      username: "ana@@pucrs.br",
    });

    await expect(validate(dto)).resolves.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ property: "username" }),
      ]),
    );
  });
});
