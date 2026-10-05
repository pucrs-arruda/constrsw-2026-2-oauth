import { Module } from '@nestjs/common';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { KeycloakClient } from './keycloak.client';

@Module({
  controllers: [AuthController],
  providers: [AuthService, KeycloakClient],
  exports: [KeycloakClient],
})
export class AuthModule {}