import { Module } from "@nestjs/common";
import { KeycloakAdminClient } from "./keycloak-admin.client";

/** Exporta o cliente admin compartilhado para as trilhas B, C e D. */
@Module({
  providers: [KeycloakAdminClient],
  exports: [KeycloakAdminClient],
})
export class KeycloakModule {}
