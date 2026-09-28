import { Injectable } from '@nestjs/common';
import { TokenSet } from '../../domain/entities/token-set.entity';
import { AuthGateway } from '../../domain/repositories/auth.gateway';
import { KeycloakClientService } from './keycloak-client.service';

@Injectable()
export class KeycloakAuthGateway implements AuthGateway {
  constructor(private readonly keycloak: KeycloakClientService) {}

  async login(username: string, password: string): Promise<TokenSet> {
    const token = await this.keycloak.passwordGrant(username, password);
    return {
      tokenType: token.token_type,
      accessToken: token.access_token,
      expiresIn: token.expires_in,
      refreshToken: token.refresh_token,
      refreshExpiresIn: token.refresh_expires_in,
      raw: token,
    };
  }
}
