import { Module } from "@nestjs/common";
import { KeycloakClient } from "../auth/keycloak.client";
import { KeycloakModule } from "../keycloak/keycloak.module";
import { UsersController } from "./users.controller";
import { UsersService } from "./users.service";

@Module({
  imports: [KeycloakModule],
  controllers: [UsersController],
  providers: [UsersService, KeycloakClient],
})
export class UsersModule {}
