import { INestApplication, ValidationPipe } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import cookieParser from "cookie-parser";
import request from "supertest";
import { AppModule } from "../src/app.module";
import { ProblemDetailsFilter } from "../src/common/problem-details.filter";
import { createKeycloakFake, KeycloakFake } from "./support/keycloak-fake";

describe("Metrics (e2e)", () => {
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
  const scrape = async () => {
    const res = await request(server()).get("/metrics").expect(200);
    expect(res.headers["content-type"]).toContain("text/plain");
    return res.text;
  };

  it("records HTTP requests by route pattern and Keycloak calls by result", async () => {
    await request(server())
      .post("/login")
      .field("username", "one@pucrs.br")
      .field("password", "secret")
      .expect(201);
    await request(server())
      .post("/login")
      .field("username", "one@pucrs.br")
      .field("password", "wrong")
      .expect(401);
    await request(server()).get("/roles").set(auth()).expect(200);
    await request(server()).get("/roles/missing").set(auth()).expect(404);
    await request(server()).get("/health").expect(200);

    const text = await scrape();

    expect(text).toContain(
      'http_server_requests_seconds_count{method="POST",uri="/login",status="201"} 1',
    );
    expect(text).toContain(
      'http_server_requests_seconds_count{method="POST",uri="/login",status="401"} 1',
    );
    // O padrão da rota, não o id cru.
    expect(text).toContain(
      'http_server_requests_seconds_count{method="GET",uri="/roles/:id",status="404"} 1',
    );
    expect(text).not.toContain('uri="/roles/missing"');
    expect(text).not.toContain('uri="/health"');
    expect(text).not.toContain('uri="/metrics"');

    expect(text).toContain(
      'oauth_keycloak_request_duration_seconds_count{operation="login",result="ok"} 1',
    );
    expect(text).toContain(
      'oauth_keycloak_request_duration_seconds_count{operation="login",result="rejected"} 1',
    );
    expect(text).toMatch(
      /oauth_keycloak_request_duration_seconds_count\{operation="admin_api",result="ok"\} [1-9]/,
    );
    // Nasce em 0 para o `increase()` do alerta enxergar a primeira falha.
    expect(text).toContain(
      'oauth_keycloak_request_duration_seconds_count{operation="refresh",result="unavailable"} 0',
    );
  });

  it("records Keycloak as unavailable when the network call fails", async () => {
    jest.spyOn(global, "fetch").mockRejectedValue(new TypeError("fetch failed"));

    await request(server())
      .post("/login")
      .field("username", "one@pucrs.br")
      .field("password", "secret")
      .expect(503);

    const text = await scrape();
    expect(text).toContain(
      'oauth_keycloak_request_duration_seconds_count{operation="login",result="unavailable"} 1',
    );
    expect(text).toContain(
      'http_server_requests_seconds_count{method="POST",uri="/login",status="503"} 1',
    );
  });
});
