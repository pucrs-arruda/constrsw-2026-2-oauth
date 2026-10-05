import { INestApplication, ValidationPipe } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import cookieParser from "cookie-parser";
import request from "supertest";
import { AppModule } from "../src/app.module";
import { ProblemDetailsFilter } from "../src/common/problem-details.filter";
import {
  createKeycloakFake,
  KeycloakFake,
} from "./support/keycloak-fake";

const PASSWORD = "secret";

describe("Identity gateway — fluxo completo (e2e)", () => {
  let app: INestApplication;
  let keycloak: KeycloakFake;

  beforeEach(async () => {
    keycloak = createKeycloakFake();
    jest
      .spyOn(global, "fetch")
      .mockImplementation(keycloak.impl as unknown as typeof fetch);

    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleRef.createNestApplication();
    app.use(cookieParser());
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));
    app.useGlobalFilters(new ProblemDetailsFilter());
    await app.init();
  });

  afterEach(async () => {
    await app.close();
    jest.restoreAllMocks();
  });

  const server = () => app.getHttpServer();
  const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

  async function loginAs(
    username = "one@pucrs.br",
    password = PASSWORD,
  ): Promise<string> {
    const response = await request(server())
      .post("/login")
      .field("username", username)
      .field("password", password)
      .expect(201);
    return response.body.access_token as string;
  }

  async function createUser(
    token: string,
    overrides: Record<string, unknown> = {},
  ) {
    const response = await request(server())
      .post("/users")
      .set(auth(token))
      .send({
        username: "ana.souza@pucrs.br",
        "first-name": "Ana",
        "last-name": "Souza",
        password: "initial-1",
        ...overrides,
      })
      .expect(201);
    return response.body as {
      id: string;
      username: string;
      "first-name": string;
      "last-name": string;
      enabled: boolean;
    };
  }

  describe("Autenticação", () => {
    it("autentica com credenciais válidas e devolve o contrato com cookie httpOnly", async () => {
      const response = await request(server())
        .post("/login")
        .field("username", "one@pucrs.br")
        .field("password", PASSWORD)
        .expect(201);

      expect(response.body).toMatchObject({
        token_type: "Bearer",
        expires_in: 300,
        refresh_expires_in: 1800,
      });
      expect(typeof response.body.access_token).toBe("string");
      expect(response.body.access_token).not.toHaveLength(0);
      expect(typeof response.body.refresh_token).toBe("string");

      const cookie = String(response.headers["set-cookie"]);
      expect(cookie).toContain("session=");
      expect(cookie.toLowerCase()).toContain("httponly");
    });

    it("recusa payload malformado com 400 antes de chamar o Keycloak", async () => {
      await request(server())
        .post("/login")
        .field("username", "not-an-email")
        .field("password", PASSWORD)
        .expect(400)
        .expect((res) => expect(res.body.error_code).toBe("OA-400"));

      expect(
        keycloak.requests.some((entry) => entry.path.endsWith("/token")),
      ).toBe(false);
    });

    it("mapeia credenciais inválidas para 401", async () => {
      await request(server())
        .post("/login")
        .field("username", "one@pucrs.br")
        .field("password", "senha-errada")
        .expect(401)
        .expect((res) => expect(res.body.error_code).toBe("OA-401"));
    });

    it("renova a sessão a partir do cookie e rotaciona o refresh token", async () => {
      const agent = request.agent(server());
      const created = await agent
        .post("/login")
        .field("username", "one@pucrs.br")
        .field("password", PASSWORD)
        .expect(201);
      const originalRefresh = created.body.refresh_token as string;

      const refreshed = await agent.post("/refresh").expect(201);
      expect(typeof refreshed.body.access_token).toBe("string");
      expect(refreshed.body.access_token).not.toBe(created.body.access_token);
      expect(refreshed.body.refresh_token).not.toBe(originalRefresh);
      expect(String(refreshed.headers["set-cookie"])).toContain("session=");

      // O refresh antigo foi consumido e não pode ser reapresentado.
      await request(server())
        .post("/refresh")
        .set(
          "Cookie",
          `session=${encodeURIComponent(
            JSON.stringify({ refresh_token: originalRefresh }),
          )}`,
        )
        .expect(401)
        .expect((res) => expect(res.body.error_code).toBe("OA-401"));

      // O refresh rotacionado continua funcionando.
      await agent.post("/refresh").expect(201);
    });

    it("encerra a sessão limpando o cookie", async () => {
      const agent = request.agent(server());
      await agent
        .post("/login")
        .field("username", "one@pucrs.br")
        .field("password", PASSWORD)
        .expect(201);

      const loggedOut = await agent
        .post("/logout")
        .expect(201)
        .expect({ status: "signed_out" });
      expect(String(loggedOut.headers["set-cookie"])).toContain("session=");
    });
  });

  describe("Usuários", () => {
    let token: string;

    beforeEach(async () => {
      token = await loginAs();
    });

    it("cria, lista e lê um usuário de volta", async () => {
      const created = await createUser(token);
      expect(created).toMatchObject({
        username: "ana.souza@pucrs.br",
        "first-name": "Ana",
        "last-name": "Souza",
        enabled: true,
      });
      expect(created.id).toBeDefined();

      const list = await request(server())
        .get("/users")
        .set(auth(token))
        .expect(200);
      expect(list.body.map((user: { id: string }) => user.id)).toContain(
        created.id,
      );

      const read = await request(server())
        .get(`/users/${created.id}`)
        .set(auth(token))
        .expect(200);
      expect(read.body).toEqual(created);
    });

    it("filtra por enabled e esconde contas de serviço", async () => {
      const all = await request(server())
        .get("/users")
        .set(auth(token))
        .expect(200);
      expect(all.body.map((user: { username: string }) => user.username)).toEqual(
        ["one@pucrs.br", "two@pucrs.br"],
      );

      const enabled = await request(server())
        .get("/users?enabled=true")
        .set(auth(token))
        .expect(200);
      expect(
        enabled.body.map((user: { username: string }) => user.username),
      ).toEqual(["one@pucrs.br"]);

      const disabled = await request(server())
        .get("/users?enabled=false")
        .set(auth(token))
        .expect(200);
      expect(
        disabled.body.map((user: { username: string }) => user.username),
      ).toEqual(["two@pucrs.br"]);
    });

    it("substitui com PUT e altera campos com PATCH", async () => {
      const created = await createUser(token);

      await request(server())
        .put(`/users/${created.id}`)
        .set(auth(token))
        .send({
          username: "ana.souza@pucrs.br",
          "first-name": "Ana Maria",
          "last-name": "Souza",
        })
        .expect(200);

      let current = await request(server())
        .get(`/users/${created.id}`)
        .set(auth(token))
        .expect(200);
      expect(current.body["first-name"]).toBe("Ana Maria");

      await request(server())
        .patch(`/users/${created.id}`)
        .set(auth(token))
        .send({ "first-name": "Ana", "last-name": "S." })
        .expect(200);

      current = await request(server())
        .get(`/users/${created.id}`)
        .set(auth(token))
        .expect(200);
      expect(current.body["first-name"]).toBe("Ana");
      expect(current.body["last-name"]).toBe("S.");
    });

    it("ignora enabled no POST: o usuário nasce habilitado", async () => {
      const created = await createUser(token, { enabled: false });

      expect(created.enabled).toBe(true);

      const read = await request(server())
        .get(`/users/${created.id}`)
        .set(auth(token))
        .expect(200);
      expect(read.body.enabled).toBe(true);
    });

    it("PUT não reativa um usuário desabilitado", async () => {
      const created = await createUser(token);

      await request(server())
        .delete(`/users/${created.id}`)
        .set(auth(token))
        .expect(204);

      await request(server())
        .put(`/users/${created.id}`)
        .set(auth(token))
        .send({
          username: "ana.souza@pucrs.br",
          "first-name": "Ana Maria",
          "last-name": "Souza",
        })
        .expect(200);

      const read = await request(server())
        .get(`/users/${created.id}`)
        .set(auth(token))
        .expect(200);
      expect(read.body.enabled).toBe(false);
    });

    it("recusa PATCH sem nenhum campo com 400", async () => {
      const created = await createUser(token);
      await request(server())
        .patch(`/users/${created.id}`)
        .set(auth(token))
        .send({})
        .expect(400)
        .expect((res) => expect(res.body.error_code).toBe("OA-400"));
    });

    it("troca a senha com PATCH e o usuário passa a autenticar com ela", async () => {
      const created = await createUser(token, {
        username: "novo.docente@pucrs.br",
        password: "initial-1",
      });

      await request(server())
        .patch(`/users/${created.id}`)
        .set(auth(token))
        .send({ password: "rotated-7" })
        .expect(200);

      await request(server())
        .post("/login")
        .field("username", "novo.docente@pucrs.br")
        .field("password", "initial-1")
        .expect(401);

      await request(server())
        .post("/login")
        .field("username", "novo.docente@pucrs.br")
        .field("password", "rotated-7")
        .expect(201);
    });

    it("rejeita username duplicado com 409", async () => {
      await createUser(token);

      await request(server())
        .post("/users")
        .set(auth(token))
        .send({
          username: "ana.souza@pucrs.br",
          "first-name": "Outra",
          "last-name": "Ana",
          password: "outra-senha",
        })
        .expect(409)
        .expect((res) => expect(res.body.error_code).toBe("OA-409"));
    });

    it("rejeita payload inválido com 400", async () => {
      await request(server())
        .post("/users")
        .set(auth(token))
        .send({
          username: "sem-arroba",
          "first-name": "A",
          "last-name": "B",
          password: "123456",
        })
        .expect(400)
        .expect((res) => expect(res.body.error_code).toBe("OA-400"));
    });

    it("exclui logicamente desabilitando o usuário", async () => {
      const created = await createUser(token);

      await request(server())
        .delete(`/users/${created.id}`)
        .set(auth(token))
        .expect(204);

      const read = await request(server())
        .get(`/users/${created.id}`)
        .set(auth(token))
        .expect(200);
      expect(read.body.enabled).toBe(false);

      const disabled = await request(server())
        .get("/users?enabled=false")
        .set(auth(token))
        .expect(200);
      expect(disabled.body.map((user: { id: string }) => user.id)).toContain(
        created.id,
      );
    });

    it("exige bearer em todas as operações de usuário", async () => {
      const payload = {
        username: "sem.token@pucrs.br",
        "first-name": "Sem",
        "last-name": "Token",
        password: "123456",
      };

      await request(server())
        .get("/users")
        .expect(401)
        .expect((res) => expect(res.body.error_code).toBe("OA-401"));
      await request(server()).post("/users").send(payload).expect(401);
      await request(server()).get("/users/u1").expect(401);
      await request(server()).delete("/users/u1").expect(401);
    });

    it("mapeia acesso negado do upstream para 403", async () => {
      await request(server())
        .get("/users")
        .set(auth(keycloak.tokens.forbidden))
        .expect(403)
        .expect((res) => expect(res.body.error_code).toBe("OA-403"));
    });

    it("mapeia usuário inexistente para 404", async () => {
      await request(server())
        .get("/users/ghost")
        .set(auth(token))
        .expect(404)
        .expect((res) => expect(res.body.error_code).toBe("OA-404"));
    });
  });

  describe("Roles", () => {
    let token: string;

    beforeEach(async () => {
      token = await loginAs();
    });

    it("executa o ciclo CRUD completo terminando em exclusão lógica", async () => {
      const created = await request(server())
        .post("/roles")
        .set(auth(token))
        .send({ name: "professor", description: "Docente" })
        .expect(201);
      const id = created.body.id as string;
      expect(created.body).toMatchObject({
        name: "professor",
        description: "Docente",
      });

      await request(server())
        .get("/roles")
        .set(auth(token))
        .expect(200)
        .expect((res) => expect(res.body).toHaveLength(1));

      await request(server())
        .get(`/roles/${id}`)
        .set(auth(token))
        .expect(200)
        .expect((res) => expect(res.body.id).toBe(id));

      await request(server())
        .put(`/roles/${id}`)
        .set(auth(token))
        .send({ name: "professor-titular" })
        .expect(200)
        .expect((res) => expect(res.body.name).toBe("professor-titular"));

      await request(server())
        .patch(`/roles/${id}`)
        .set(auth(token))
        .send({ description: "atualizado" })
        .expect(200)
        .expect((res) => expect(res.body.description).toBe("atualizado"));

      await request(server()).delete(`/roles/${id}`).set(auth(token)).expect(204);

      await request(server()).get(`/roles/${id}`).set(auth(token)).expect(404);
      await request(server())
        .get("/roles")
        .set(auth(token))
        .expect(200)
        .expect((res) => expect(res.body).toHaveLength(0));
    });

    it("atribui e remove um role de um usuário", async () => {
      const role = await request(server())
        .post("/roles")
        .set(auth(token))
        .send({ name: "aluno" })
        .expect(201);
      const id = role.body.id as string;

      await request(server())
        .post(`/roles/${id}/users/user-1`)
        .set(auth(token))
        .expect(204);
      expect(keycloak.assignments.get("user-1")?.has(id)).toBe(true);

      await request(server())
        .delete(`/roles/${id}/users/user-1`)
        .set(auth(token))
        .expect(204);
      expect(keycloak.assignments.get("user-1")?.has(id)).toBe(false);
    });

    it("remove as atribuições ao excluir logicamente um role", async () => {
      const role = await request(server())
        .post("/roles")
        .set(auth(token))
        .send({ name: "monitor" })
        .expect(201);
      const id = role.body.id as string;

      await request(server())
        .post(`/roles/${id}/users/u1`)
        .set(auth(token))
        .expect(204);
      await request(server())
        .post(`/roles/${id}/users/u2`)
        .set(auth(token))
        .expect(204);

      await request(server()).delete(`/roles/${id}`).set(auth(token)).expect(204);

      expect(keycloak.assignments.get("u1")?.has(id)).toBe(false);
      expect(keycloak.assignments.get("u2")?.has(id)).toBe(false);
      expect(keycloak.roles.get(id)?.attributes).toEqual({ deleted: ["true"] });
    });

    it("rejeita nome de role duplicado com 409", async () => {
      await request(server())
        .post("/roles")
        .set(auth(token))
        .send({ name: "dup" })
        .expect(201);

      await request(server())
        .post("/roles")
        .set(auth(token))
        .send({ name: "dup" })
        .expect(409)
        .expect((res) => expect(res.body.error_code).toBe("OA-409"));
    });

    it("explica conflito com um role logicamente excluído", async () => {
      const created = await request(server())
        .post("/roles")
        .set(auth(token))
        .send({ name: "antigo" })
        .expect(201);

      await request(server())
        .delete(`/roles/${created.body.id}`)
        .set(auth(token))
        .expect(204);

      await request(server())
        .post("/roles")
        .set(auth(token))
        .send({ name: "antigo" })
        .expect(409)
        .expect((res) =>
          expect(res.body.error_description).toMatch(/papel excluído/),
        );
    });

    it("recusa payload de role inválido com 400", async () => {
      await request(server())
        .post("/roles")
        .set(auth(token))
        .send({ description: "sem nome" })
        .expect(400)
        .expect((res) => expect(res.body.error_code).toBe("OA-400"));
    });

    it("exige bearer nas rotas de roles", async () => {
      await request(server())
        .get("/roles")
        .expect(401)
        .expect((res) => expect(res.body.error_code).toBe("OA-401"));
      await request(server()).post("/roles").send({ name: "x" }).expect(401);
    });

    it("recusa um token válido sem o papel administrator com 403", async () => {
      await request(server())
        .get("/roles")
        .set(auth(keycloak.tokens.forbidden))
        .expect(403)
        .expect((res) => expect(res.body.error_code).toBe("OA-403"));
      await request(server())
        .post("/roles")
        .set(auth(keycloak.tokens.forbidden))
        .send({ name: "x" })
        .expect(403);
      await request(server())
        .post("/roles/role-1/users/u1")
        .set(auth(keycloak.tokens.forbidden))
        .expect(403);
    });

    it("recusa um token inválido com 401", async () => {
      await request(server())
        .get("/roles")
        .set(auth("not-a-real-token"))
        .expect(401)
        .expect((res) => expect(res.body.error_code).toBe("OA-401"));
    });

    it("não expõe client roles do realm", async () => {
      keycloak.roles.set("client-1", {
        id: "client-1",
        name: "manage-users",
        clientRole: true,
      });

      await request(server()).get("/roles/client-1").set(auth(token)).expect(404);
      await request(server())
        .post(`/roles/client-1/users/u1`)
        .set(auth(token))
        .expect(404);
    });
  });

  describe("Jornada completa", () => {
    it("provisiona, autentica e revoga um docente", async () => {
      const adminToken = await loginAs("one@pucrs.br", PASSWORD);

      const role = await request(server())
        .post("/roles")
        .set(auth(adminToken))
        .send({ name: "professor", description: "Docente" })
        .expect(201);

      const user = await createUser(adminToken, {
        username: "novo.professor@pucrs.br",
        "first-name": "Novo",
        "last-name": "Professor",
        password: "senha-inicial",
      });
      expect(user.enabled).toBe(true);

      await request(server())
        .post(`/roles/${role.body.id}/users/${user.id}`)
        .set(auth(adminToken))
        .expect(204);
      expect(keycloak.assignments.get(user.id)?.has(role.body.id)).toBe(true);

      const docenteToken = await loginAs(
        "novo.professor@pucrs.br",
        "senha-inicial",
      );
      expect(docenteToken).not.toHaveLength(0);

      const list = await request(server())
        .get("/users")
        .set(auth(adminToken))
        .expect(200);
      expect(list.body.map((entry: { id: string }) => entry.id)).toContain(
        user.id,
      );

      await request(server())
        .delete(`/roles/${role.body.id}/users/${user.id}`)
        .set(auth(adminToken))
        .expect(204);
      await request(server())
        .delete(`/roles/${role.body.id}`)
        .set(auth(adminToken))
        .expect(204);
      await request(server())
        .delete(`/users/${user.id}`)
        .set(auth(adminToken))
        .expect(204);

      await request(server())
        .get(`/roles/${role.body.id}`)
        .set(auth(adminToken))
        .expect(404);

      const revoked = await request(server())
        .get(`/users/${user.id}`)
        .set(auth(adminToken))
        .expect(200);
      expect(revoked.body.enabled).toBe(false);

      await request(server())
        .post("/login")
        .field("username", "novo.professor@pucrs.br")
        .field("password", "senha-inicial")
        .expect(401);
    });
  });
});
