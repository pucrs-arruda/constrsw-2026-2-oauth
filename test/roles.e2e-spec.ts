import { INestApplication, ValidationPipe } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import cookieParser from "cookie-parser";
import request from "supertest";
import { AppModule } from "../src/app.module";
import { ProblemDetailsFilter } from "../src/common/problem-details.filter";
import { createKeycloakFake, KeycloakFake } from "./support/keycloak-fake";

describe("Roles (e2e)", () => {
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
  const auth = () => ({ Authorization: `Bearer ${keycloak.tokens.admin}` });

  it("runs the full CRUD lifecycle ending in a logical delete", async () => {
    const created = await request(server())
      .post("/roles")
      .set(auth())
      .send({ name: "professor", description: "Docente" })
      .expect(201);
    const id = created.body.id as string;
    expect(created.body).toMatchObject({ name: "professor" });
    expect(id).toBeDefined();

    await request(server())
      .get("/roles")
      .set(auth())
      .expect(200)
      .expect((res) => expect(res.body).toHaveLength(1));

    await request(server()).get(`/roles/${id}`).set(auth()).expect(200);

    await request(server())
      .put(`/roles/${id}`)
      .set(auth())
      .send({ name: "professor-x" })
      .expect(200)
      .expect((res) => expect(res.body.name).toBe("professor-x"));

    await request(server())
      .patch(`/roles/${id}`)
      .set(auth())
      .send({ description: "atualizado" })
      .expect(200);

    await request(server()).delete(`/roles/${id}`).set(auth()).expect(204);

    await request(server()).get(`/roles/${id}`).set(auth()).expect(404);
    await request(server())
      .get("/roles")
      .set(auth())
      .expect(200)
      .expect((res) => expect(res.body).toHaveLength(0));
  });

  it("assigns and unassigns a role to a user", async () => {
    const created = await request(server())
      .post("/roles")
      .set(auth())
      .send({ name: "aluno" })
      .expect(201);
    const id = created.body.id as string;

    await request(server()).post(`/roles/${id}/users/user-1`).set(auth()).expect(204);
    expect(keycloak.assignments.get("user-1")?.has(id)).toBe(true);

    await request(server()).delete(`/roles/${id}/users/user-1`).set(auth()).expect(204);
    expect(keycloak.assignments.get("user-1")?.has(id)).toBe(false);
  });

  it("maps upstream conflicts and missing roles to problem responses", async () => {
    await request(server()).post("/roles").set(auth()).send({ name: "dup" }).expect(201);

    await request(server())
      .post("/roles")
      .set(auth())
      .send({ name: "dup" })
      .expect(409)
      .expect((res) => expect(res.body.error_code).toBe("OA-409"));

    await request(server())
      .get("/roles/inexistente")
      .set(auth())
      .expect(404)
      .expect((res) => expect(res.body.error_code).toBe("OA-404"));
  });

  it("rejects invalid payloads with 400", async () => {
    await request(server())
      .post("/roles")
      .set(auth())
      .send({ description: "sem nome" })
      .expect(400)
      .expect((res) => expect(res.body.error_code).toBe("OA-400"));
  });

  it("strips a deleted role from its holders and still allows unassigning it", async () => {
    const created = await request(server())
      .post("/roles")
      .set(auth())
      .send({ name: "monitor" })
      .expect(201);
    const id = created.body.id as string;
    await request(server())
      .post(`/roles/${id}/users/user-1`)
      .set(auth())
      .expect(204);
    await request(server())
      .post(`/roles/${id}/users/user-2`)
      .set(auth())
      .expect(204);

    await request(server()).delete(`/roles/${id}`).set(auth()).expect(204);

    expect(keycloak.assignments.get("user-1")?.has(id)).toBe(false);
    expect(keycloak.assignments.get("user-2")?.has(id)).toBe(false);
    expect(keycloak.roles.get(id)?.attributes).toEqual({ deleted: ["true"] });
    await request(server())
      .delete(`/roles/${id}/users/user-1`)
      .set(auth())
      .expect(204);
  });

  it("explains a name conflict with a logically deleted role", async () => {
    const created = await request(server())
      .post("/roles")
      .set(auth())
      .send({ name: "antigo" })
      .expect(201);
    await request(server())
      .delete(`/roles/${created.body.id}`)
      .set(auth())
      .expect(204);

    await request(server())
      .post("/roles")
      .set(auth())
      .send({ name: "antigo" })
      .expect(409)
      .expect((res) =>
        expect(res.body.error_description).toMatch(/papel excluído/),
      );
  });

  it("words a rename conflict as a role conflict", async () => {
    await request(server())
      .post("/roles")
      .set(auth())
      .send({ name: "a" })
      .expect(201);
    const b = await request(server())
      .post("/roles")
      .set(auth())
      .send({ name: "b" })
      .expect(201);

    for (const method of ["put", "patch"] as const) {
      await request(server())
        [method](`/roles/${b.body.id}`)
        .set(auth())
        .send({ name: "a" })
        .expect(409)
        .expect((res) =>
          expect(res.body.error_description).toBe(
            "Já existe um papel com este nome.",
          ),
        );
    }
  });

  it("does not expose client roles through the by-id endpoints", async () => {
    keycloak.roles.set("client-1", {
      id: "client-1",
      name: "manage-users",
      clientRole: true,
    });

    await request(server()).get("/roles/client-1").set(auth()).expect(404);
    await request(server())
      .patch("/roles/client-1")
      .set(auth())
      .send({ description: "x" })
      .expect(404);
    await request(server()).delete("/roles/client-1").set(auth()).expect(404);
    await request(server())
      .post("/roles/client-1/users/user-1")
      .set(auth())
      .expect(404)
      .expect((res) =>
        expect(res.body.error_description).toBe(
          "Papel não encontrado no realm.",
        ),
      );
  });
});
