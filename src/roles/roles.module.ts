import { Module } from "@nestjs/common";
import { AuthModule } from "../auth/auth.module";
import { RealmRoleGuard } from "../common/realm-role.guard";
import { KeycloakModule } from "../keycloak/keycloak.module";
import { RolesController } from "./roles.controller";
import { RolesService } from "./roles.service";

@Module({
  imports: [KeycloakModule, AuthModule],
  controllers: [RolesController],
  providers: [RolesService, RealmRoleGuard],
})
export class RolesModule {}
