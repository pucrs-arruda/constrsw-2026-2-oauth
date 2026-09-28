import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import {
  KEYCLOAK_CONFIG_KEY,
  KeycloakSettings,
  OAUTH_CONFIG_KEY,
  OAuthServiceSettings,
} from './keycloak.config';

/**
 * Feature modules inject this instead of reading `process.env` directly, so
 * the env names and the `/auth` rule stay in one place.
 */
@Injectable()
export class KeycloakSettingsService {
  constructor(private readonly configService: ConfigService) {}

  get settings(): KeycloakSettings {
    return this.configService.getOrThrow<KeycloakSettings>(KEYCLOAK_CONFIG_KEY);
  }

  get service(): OAuthServiceSettings {
    return this.configService.getOrThrow<OAuthServiceSettings>(
      OAUTH_CONFIG_KEY,
    );
  }

  get serverUrl(): string {
    return this.settings.serverUrl;
  }

  get realm(): string {
    return this.settings.realm;
  }

  get clientId(): string {
    return this.settings.clientId;
  }

  get clientSecret(): string {
    return this.settings.clientSecret;
  }

  get adminUser(): string | undefined {
    return this.settings.adminUser;
  }

  get adminPassword(): string | undefined {
    return this.settings.adminPassword;
  }

  get internalApiPort(): number {
    return this.service.internalApiPort;
  }

  get internalMetricsPort(): number {
    return this.service.internalMetricsPort;
  }

  get realmUrl(): string {
    return `${this.serverUrl}/realms/${this.realm}`;
  }

  get adminRealmUrl(): string {
    return `${this.serverUrl}/admin/realms/${this.realm}`;
  }

  get tokenUrl(): string {
    return `${this.realmUrl}/protocol/openid-connect/token`;
  }

  get userInfoUrl(): string {
    return `${this.realmUrl}/protocol/openid-connect/userinfo`;
  }

  realmPath(path: string): string {
    return `${this.realmUrl}/${path.replace(/^\/+/, '')}`;
  }

  adminPath(path: string): string {
    return `${this.adminRealmUrl}/${path.replace(/^\/+/, '')}`;
  }
}
