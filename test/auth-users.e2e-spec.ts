import { INestApplication, ValidationPipe } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import cookieParser from "cookie-parser";
import request from "supertest";
import { AppModule } from "../src/app.module";
import { ProblemDetailsFilter } from "../src/common/problem-details.filter";
import { createKeycloakFake, KeycloakFake } from "./support/keycloak-fake";

describe("Authentication and users (e2e)", () => {
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
  const bearer = (token: string) => ({ Authorization: `Bearer ${token}` });

  it("authenticates with multipart form data and returns the token contract", async () => {
    await request(server())
      .post("/login")
      .field("username", "one@pucrs.br")
      .field("password", "secret")
      .expect(201)
      .expect((res) => {
        expect(res.body).toMatchObject({
          token_type: "Bearer",
          expires_in: 300,
          refresh_expires_in: 1800,
        });
        expect(typeof res.body.access_token).toBe("string");
        expect(res.body.access_token).not.toHaveLength(0);
        expect(res.headers["set-cookie"]).toBeDefined();
      });
  });

  it("refreshes and logs out the session cookie", async () => {
    const agent = request.agent(server());
    await agent
      .post("/login")
      .field("username", "one@pucrs.br")
      .field("password", "secret")
      .expect(201);

    await agent.post("/refresh").expect(201);
    await agent.post("/logout").expect(201).expect({ status: "signed_out" });
  });

  it("rejects invalid login payloads before calling Keycloak", async () => {
    await request(server())
      .post("/login")
      .field("username", "invalid")
      .field("password", "secret")
      .expect(400)
      .expect((res) => expect(res.body.error_code).toBe("OA-400"));
  });

  it("lists users, filters enabled and excludes service accounts", async () => {
    await request(server())
      .get("/users")
      .set(bearer(keycloak.tokens.user))
      .expect(200)
      .expect((res) =>
        expect(res.body.map((user: { id: string }) => user.id)).toEqual([
          "u1",
          "u2",
        ]),
      );

    await request(server())
      .get("/users?enabled=true")
      .set(bearer(keycloak.tokens.user))
      .expect(200)
      .expect((res) =>
        expect(res.body.map((user: { id: string }) => user.id)).toEqual(["u1"]),
      );
  });

  it("gets one user and maps an upstream 404", async () => {
    await request(server())
      .get("/users/u1")
      .set(bearer(keycloak.tokens.user))
      .expect(200)
      .expect((res) =>
        expect(res.body).toEqual({
          id: "u1",
          username: "one@pucrs.br",
          "first-name": "One",
          "last-name": "User",
          enabled: true,
        }),
      );

    await request(server())
      .get("/users/missing")
      .set(bearer(keycloak.tokens.user))
      .expect(404)
      .expect((res) => expect(res.body.error_code).toBe("OA-404"));
  });

  it("requires a bearer token and surfaces upstream forbidden responses", async () => {
    await request(server())
      .get("/users")
      .expect(401)
      .expect((res) => expect(res.body.error_code).toBe("OA-401"));

    await request(server())
      .get("/users")
      .set(bearer(keycloak.tokens.forbidden))
      .expect(403)
      .expect((res) => expect(res.body.error_code).toBe("OA-403"));
  });
});
