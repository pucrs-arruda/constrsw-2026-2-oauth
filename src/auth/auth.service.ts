import { Injectable } from '@nestjs/common';
import { KeycloakClient, TokenResponse } from './keycloak.client';

@Injectable()
export class AuthService {
  constructor(private readonly keycloak: KeycloakClient) {}

  login(username: string, password: string): Promise<TokenResponse> {
    return this.keycloak.login(username, password);
  }

  refresh(refreshToken: string): Promise<TokenResponse> {
    return this.keycloak.refresh(refreshToken);
  }
}