import { Injectable, Optional } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { KeycloakDependencyError } from "../common/errors";
import {
  introspectToken,
  keycloakJson,
  requestToken,
  TokenIntrospection,
} from "../common/keycloak-http";
import { KeycloakOperation, MetricsService } from "../metrics/metrics.service";

export type { TokenResponse, TokenIntrospection } from "../common/keycloak-http";

export interface KeycloakUser {
  id: string;
  username: string;
  firstName?: string;
  lastName?: string;
  enabled: boolean;
}

@Injectable()
export class KeycloakClient {
  private readonly baseUrl: string;
  private readonly realm: string;
  private readonly timeoutMs: number;
  private readonly clientId: string;
  private readonly clientSecret: string;

  constructor(
    @Optional() config?: ConfigService,
    @Optional() private readonly metrics?: MetricsService,
  ) {
    this.baseUrl =
      this.value(config, "KEYCLOAK_URL") ?? "http://localhost:8080";
    this.realm = this.value(config, "KEYCLOAK_REALM") ?? "closed-cras";
    this.timeoutMs = Number(this.value(config, "KEYCLOAK_TIMEOUT_MS") ?? 5000);
    this.clientId = this.value(config, "KEYCLOAK_CLIENT_ID") ?? "bff";
    this.clientSecret = this.value(config, "KEYCLOAK_CLIENT_SECRET") ?? "";
  }

  async login(username: string, password: string) {
    return this.observe("login", () =>
      requestToken(
        this.tokenUrl(),
        new URLSearchParams({
          grant_type: "password",
          client_id: this.clientId,
          client_secret: this.clientSecret,
          username,
          password,
        }),
        this.timeoutMs,
      ),
    );
  }

  async refresh(refreshToken: string) {
    return this.observe("refresh", () =>
      requestToken(
        this.tokenUrl(),
        new URLSearchParams({
          grant_type: "refresh_token",
          client_id: this.clientId,
          client_secret: this.clientSecret,
          refresh_token: refreshToken,
        }),
        this.timeoutMs,
      ),
    );
  }

  /**
   * Valida o access token do chamador no Keycloak (RFC 7662). O `oauth` client
   * é confidencial, então a chamada leva `client_id`/`client_secret`. Um token
   * expirado/forjado responde `{ active: false }`, mapeado para 401 pelo guard.
   */
  introspect(accessToken: string): Promise<TokenIntrospection> {
    return this.observe("introspect", () =>
      introspectToken(this.introspectUrl(), accessToken, {
        clientId: this.clientId,
        clientSecret: this.clientSecret,
        timeoutMs: this.timeoutMs,
      }),
    );
  }

  async listUsers(
    accessToken: string,
    enabled?: boolean,
  ): Promise<KeycloakUser[]> {
    const url = new URL(`/admin/realms/${this.realm}/users`, this.baseUrl);
    if (enabled !== undefined) url.searchParams.set("enabled", String(enabled));
    try {
      return (
        (await this.observe("admin_api", () =>
          keycloakJson<KeycloakUser[]>(url, {
            token: accessToken,
            timeoutMs: this.timeoutMs,
          }),
        )) ?? []
      );
    } catch (error) {
      if (error instanceof KeycloakDependencyError) {
        throw new KeycloakDependencyError(
          "upstream_rejected",
          error.upstreamStatus,
        );
      }
      throw error;
    }
  }

  async getUser(accessToken: string, id: string): Promise<KeycloakUser> {
    let user: KeycloakUser | undefined;
    try {
      user = await this.observe("admin_api", () =>
        keycloakJson<KeycloakUser>(
          new URL(
            `/admin/realms/${this.realm}/users/${encodeURIComponent(id)}`,
            this.baseUrl,
          ),
          { token: accessToken, timeoutMs: this.timeoutMs },
        ),
      );
    } catch (error) {
      if (error instanceof KeycloakDependencyError) {
        throw new KeycloakDependencyError(
          "upstream_rejected",
          error.upstreamStatus,
        );
      }
      throw error;
    }
    if (!user) throw new KeycloakDependencyError("not_found", 404);
    return user;
  }

  /**
   * Cronometra a chamada e registra o resultado. Envolve a chamada crua, antes
   * de qualquer reembrulho do erro, para não confundir `unavailable` (rede ou
   * timeout) com `rejected` (o Keycloak respondeu não-2xx).
   */
  private async observe<T>(
    operation: KeycloakOperation,
    call: () => Promise<T>,
  ): Promise<T> {
    const endTimer = this.metrics?.keycloakRequests.startTimer({ operation });
    try {
      const value = await call();
      endTimer?.({ result: "ok" });
      return value;
    } catch (error) {
      const unavailable =
        error instanceof KeycloakDependencyError &&
        error.reason === "unavailable";
      endTimer?.({ result: unavailable ? "unavailable" : "rejected" });
      throw error;
    }
  }

  private tokenUrl(): URL {
    return new URL(
      `/realms/${this.realm}/protocol/openid-connect/token`,
      this.baseUrl,
    );
  }

  private introspectUrl(): URL {
    return new URL(
      `/realms/${this.realm}/protocol/openid-connect/token/introspect`,
      this.baseUrl,
    );
  }

  private value(
    config: ConfigService | undefined,
    key: string,
  ): string | undefined {
    if (config && typeof (config as ConfigService).getOrThrow === "function") {
      const value = config.getOrThrow<string | number>(key);
      return value === undefined ? undefined : String(value);
    }
    return process.env[key];
  }
}
