import { HttpModule } from '@nestjs/axios';
import { Module } from '@nestjs/common';
import { AUTH_GATEWAY } from '../domain/repositories/auth.gateway';
import { ROLE_REPOSITORY } from '../domain/repositories/role.repository';
import { USER_REPOSITORY } from '../domain/repositories/user.repository';
import { KeycloakAuthGateway } from './keycloak/keycloak-auth.gateway';
import { KeycloakClientService } from './keycloak/keycloak-client.service';
import { KeycloakRoleRepository } from './keycloak/keycloak-role.repository';
import { KeycloakUserRepository } from './keycloak/keycloak-user.repository';

@Module({
  imports: [HttpModule],
  providers: [
    KeycloakClientService,
    { provide: USER_REPOSITORY, useClass: KeycloakUserRepository },
    { provide: ROLE_REPOSITORY, useClass: KeycloakRoleRepository },
    { provide: AUTH_GATEWAY, useClass: KeycloakAuthGateway },
  ],
  exports: [USER_REPOSITORY, ROLE_REPOSITORY, AUTH_GATEWAY],
})
export class InfrastructureModule {}
