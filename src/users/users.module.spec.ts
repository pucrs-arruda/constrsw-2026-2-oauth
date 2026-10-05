import { ConfigModule } from "@nestjs/config";
import { Test } from "@nestjs/testing";
import {
  HTTP_CODE_METADATA,
  METHOD_METADATA,
  PATH_METADATA,
} from "@nestjs/common/constants";
import { RequestMethod } from "@nestjs/common";
import { KeycloakAdminClient } from "../keycloak/keycloak-admin.client";
import { UsersController } from "./users.controller";
import { UsersModule } from "./users.module";
import { UsersService } from "./users.service";

/**
 * Os demais specs instanciam as classes na mão, então a fiação do Nest fica
 * sem cobertura: `UsersModule` poderia não resolver `KeycloakAdminClient` e
 * toda a suíte seguiria verde.
 */
describe("UsersModule wiring", () => {
  async function compile() {
    return Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          ignoreEnvFile: true,
          load: [
            () => ({
              KEYCLOAK_URL: "http://keycloak:8080",
              KEYCLOAK_REALM: "closed-cras",
              KEYCLOAK_CLIENT_ID: "oauth",
              KEYCLOAK_CLIENT_SECRET: "client-secret",
              KEYCLOAK_TIMEOUT_MS: 5000,
              KEYCLOAK_ADMIN_CLIENT_ID: "oauth-admin",
              KEYCLOAK_ADMIN_CLIENT_SECRET: "admin-secret",
            }),
          ],
        }),
        UsersModule,
      ],
    }).compile();
  }

  it("resolves the controller, the service and the shared admin client", async () => {
    const moduleRef = await compile();

    expect(moduleRef.get(UsersController)).toBeInstanceOf(UsersController);
    expect(moduleRef.get(UsersService)).toBeInstanceOf(UsersService);
    expect(moduleRef.get(KeycloakAdminClient)).toBeInstanceOf(
      KeycloakAdminClient,
    );
  });

  it("mounts the controller under users", () => {
    expect(Reflect.getMetadata(PATH_METADATA, UsersController)).toBe("users");
  });

  it.each([
    ["create", RequestMethod.POST, "/", 201],
    ["update", RequestMethod.PUT, ":id", 200],
    ["patch", RequestMethod.PATCH, ":id", 200],
    ["delete", RequestMethod.DELETE, ":id", 204],
  ])("maps %s to its verb, path and status", (handler, verb, path, status) => {
    const target = (
      UsersController.prototype as unknown as Record<string, () => unknown>
    )[handler];

    expect(Reflect.getMetadata(METHOD_METADATA, target)).toBe(verb);
    expect(Reflect.getMetadata(PATH_METADATA, target)).toBe(path);
    expect(Reflect.getMetadata(HTTP_CODE_METADATA, target)).toBe(status);
  });
});
