import { HttpService } from '@nestjs/axios';
import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AxiosError, Method } from 'axios';
import { firstValueFrom } from 'rxjs';
import { toDomainError } from './keycloak-error.mapper';

export interface KeycloakTokenResponse {
  token_type: string;
  access_token: string;
  expires_in: number;
  refresh_token: string;
  refresh_expires_in: number;
  [key: string]: unknown;
}

/**
 * Cliente HTTP fino para a REST API do Keycloak. Nao guarda estado: cada
 * chamada recebe o bearer token que deve ser repassado (login usa client
 * credentials; as demais rotas repassam o access_token do usuario logado,
 * conforme AD-4/enunciado do T1).
 */
@Injectable()
export class KeycloakClientService {
  private readonly baseUrl: string;
  private readonly realm: string;
  private readonly clientId: string;
  private readonly clientSecret: string;

  constructor(
    private readonly http: HttpService,
    private readonly config: ConfigService,
  ) {
    const rawBase = this.config.get<string>('keycloak.baseUrl')!;
    this.baseUrl = rawBase.replace(/\/+$/, '');
    this.realm = this.config.get<string>('keycloak.realm')!;
    this.clientId = this.config.get<string>('keycloak.clientId')!;
    this.clientSecret = this.config.get<string>('keycloak.clientSecret')!;
  }

  get realmName(): string {
    return this.realm;
  }

  /** POST /realms/{realm}/protocol/openid-connect/token (grant_type=password) */
  async passwordGrant(
    username: string,
    password: string,
  ): Promise<KeycloakTokenResponse> {
    const url = `${this.baseUrl}/realms/${this.realm}/protocol/openid-connect/token`;
    const body = new URLSearchParams({
      client_id: this.clientId,
      client_secret: this.clientSecret,
      grant_type: 'password',
      username,
      password,
    });

    try {
      const { data } = await firstValueFrom(
        this.http.post<KeycloakTokenResponse>(url, body.toString(), {
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        }),
      );
      return data;
    } catch (error) {
      throw toDomainError(error as AxiosError);
    }
  }

  /**
   * Chamada generica a /admin/realms/{realm}<path>, repassando o
   * Authorization: Bearer recebido do cliente da API oauth.
   */
  async adminRequest<T = unknown>(
    method: Method,
    path: string,
    bearerToken: string,
    options: { data?: unknown; params?: Record<string, unknown> } = {},
  ): Promise<{ data: T; headers: Record<string, string> }> {
    const url = `${this.baseUrl}/admin/realms/${this.realm}${path}`;

    try {
      const response = await firstValueFrom(
        this.http.request<T>({
          method,
          url,
          data: options.data,
          params: options.params,
          headers: { Authorization: `Bearer ${bearerToken}` },
        }),
      );
      return {
        data: response.data,
        headers: response.headers as unknown as Record<string, string>,
      };
    } catch (error) {
      throw toDomainError(error as AxiosError);
    }
  }
}
