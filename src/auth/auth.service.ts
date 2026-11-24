import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ValidationError } from '../common/errors';
import { KeycloakClient, TokenResponse } from './keycloak.client';

export interface TokenValidation {
  active: boolean;
  allowed: boolean;
  resource: string;
}

@Injectable()
export class AuthService {
  constructor(private readonly keycloak: KeycloakClient) {}

  login(username: string, password: string): Promise<TokenResponse> {
    return this.keycloak.login(username, password);
  }

  refresh(refreshToken: string): Promise<TokenResponse> {
    return this.keycloak.refresh(refreshToken);
  }

  /**
   * Valida o access token para um recurso (ex.: courses). O contrato esperado
   * pelos contextos é `{ active, allowed, resource }` após introspecção no Keycloak.
   */
  async validate(authorization: string | undefined, resource: unknown): Promise<TokenValidation> {
    if (typeof resource !== 'string' || resource.trim() === '') {
      throw new ValidationError('resource is required.');
    }
    const match = authorization?.match(/^Bearer\s+([^\s]+)$/i);
    if (!match) {
      throw new UnauthorizedException('A Bearer access token is required.');
    }
    const introspection = await this.keycloak.introspect(match[1]);
    if (!introspection.active) {
      throw new UnauthorizedException('The access token is invalid or expired.');
    }
    return { active: true, allowed: true, resource };
  }
}