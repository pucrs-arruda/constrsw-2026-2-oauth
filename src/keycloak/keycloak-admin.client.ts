import { Injectable, Optional } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import {
  AppError,
  ConflictError,
  ErrorStackEntry,
  KeycloakError,
  NotFoundError,
  ValidationError,
} from "../common/errors";
import { KeycloakOperation, MetricsService } from "../metrics/metrics.service";

export type AdminMethod = "GET" | "POST" | "PUT" | "DELETE";

export interface AdminResponse<T = unknown> {
  status: number;
  body: T;
  /** Cru porque o `POST /users` só devolve o id no header `Location`. */
  headers: Headers;
}

interface RawResponse {
  ok: boolean;
  status: number;
  headers: Headers;
  body: unknown;
}

/**
 * Dono único do token administrativo do realm.
 *
 * Toda trilha que precise do Admin API (B: escrita de usuários, C: leitura,
 * D: roles e role-mapping) injeta este cliente. Ninguém abre `fetch` próprio
 * para o Keycloak — é o contrato nº 4 do Sprint 0.
 *
 * Separado de `auth/keycloak.client.ts` de propósito: aquele negocia grants do
 * usuário final, este fala com o Admin API como service account.
 */
@Injectable()
export class KeycloakAdminClient {
  private readonly baseUrl: string;
  private readonly realm: string;
  private readonly timeoutMs: number;
  private readonly clientId: string;
  private readonly clientSecret: string;

  constructor(
    private readonly config: ConfigService,
    @Optional() private readonly metrics?: MetricsService,
  ) {
    this.baseUrl = this.config
      .getOrThrow<string>("KEYCLOAK_URL")
      .replace(/\/+$/, "");
    this.realm = this.config.getOrThrow<string>("KEYCLOAK_REALM");
    this.timeoutMs = this.config.getOrThrow<number>("KEYCLOAK_TIMEOUT_MS");
    this.clientId = this.config.getOrThrow<string>("KEYCLOAK_ADMIN_CLIENT_ID");
    this.clientSecret = this.config.getOrThrow<string>(
      "KEYCLOAK_ADMIN_CLIENT_SECRET",
    );
  }

  get<T>(path: string, accessToken?: string): Promise<AdminResponse<T>> {
    return this.request<T>("GET", path, undefined, accessToken);
  }

  post<T>(
    path: string,
    body?: unknown,
    accessToken?: string,
  ): Promise<AdminResponse<T>> {
    return this.request<T>("POST", path, body, accessToken);
  }

  put<T>(
    path: string,
    body?: unknown,
    accessToken?: string,
  ): Promise<AdminResponse<T>> {
    return this.request<T>("PUT", path, body, accessToken);
  }

  delete<T>(path: string): Promise<AdminResponse<T>> {
    return this.request<T>("DELETE", path);
  }

  /**
   * `path` é relativo ao realm — `/users`, `/users/{id}/reset-password`.
   * Sem token do chamador, obtém um token de service account novo a cada
   * chamada (o enunciado não pede cache nem retry).
   */
  async request<T>(
    method: AdminMethod,
    path: string,
    body?: unknown,
    accessToken?: string,
  ): Promise<AdminResponse<T>> {
    const used = accessToken ?? (await this.fetchToken());
    return this.translate<T>(await this.send(method, path, body, used));
  }

  private async fetchToken(): Promise<string> {
    const response = await this.exchange(
      this.url(`/realms/${this.realm}/protocol/openid-connect/token`),
      {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "client_credentials",
          client_id: this.clientId,
          client_secret: this.clientSecret,
        }),
      },
      "admin_token",
    );

    if (!response.ok) {
      throw new KeycloakError(
        "Falha ao obter o token administrativo no provedor de identidade.",
        {
          source: "keycloak-admin",
          upstreamStatus: response.status,
          chain: this.upstreamChain(response),
        },
      );
    }

    const payload =
      typeof response.body === "object" && response.body !== null
        ? (response.body as { access_token?: unknown })
        : {};
    if (typeof payload.access_token !== "string" || !payload.access_token) {
      throw new KeycloakError(
        "O provedor de identidade não devolveu um token administrativo.",
        { source: "keycloak-admin" },
      );
    }

    return payload.access_token;
  }

  private send(
    method: AdminMethod,
    path: string,
    body: unknown,
    token: string,
  ): Promise<RawResponse> {
    const headers: Record<string, string> = {
      authorization: `Bearer ${token}`,
    };
    if (body !== undefined) headers["content-type"] = "application/json";

    return this.exchange(
      this.url(`/admin/realms/${this.realm}${path}`),
      {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      },
      "admin_api",
    );
  }

  /**
   * Preserva qualquer prefixo de caminho em `KEYCLOAK_URL` — `new URL(path, base)`
   * com path absoluto descartaria silenciosamente um `/auth` de proxy reverso.
   */
  private url(path: string): URL {
    return new URL(`${this.baseUrl}${path}`);
  }

  /**
   * O deadline cobre também a leitura do corpo: um upstream que manda headers e
   * trava o stream não pode prender a requisição para sempre.
   */
  private async exchange(
    url: URL,
    init: RequestInit,
    operation: KeycloakOperation,
  ): Promise<RawResponse> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    const endTimer = this.metrics?.keycloakRequests.startTimer({ operation });
    try {
      const response = await fetch(url, { ...init, signal: controller.signal });
      const raw = {
        ok: response.ok,
        status: response.status,
        headers: response.headers,
        body: await this.readBody(response),
      };
      endTimer?.({ result: raw.ok ? "ok" : "rejected" });
      return raw;
    } catch (error) {
      if (error instanceof AppError) throw error;
      endTimer?.({ result: "unavailable" });
      throw new KeycloakError("O provedor de identidade está indisponível.", {
        status: 503,
        source: "keycloak-admin",
      });
    } finally {
      clearTimeout(timeout);
    }
  }

  /** Traduz o status do Admin API para a hierarquia de exceções do serviço. */
  private translate<T>(response: RawResponse): AdminResponse<T> {
    if (response.ok) {
      return {
        status: response.status,
        body: response.body as T,
        headers: response.headers,
      };
    }

    const source = "keycloak-admin";
    const chain = this.upstreamChain(response);
    switch (response.status) {
      case 400:
        throw new ValidationError(
          "O provedor de identidade recusou a requisição.",
          source,
          chain,
        );
      case 404:
        throw new NotFoundError(
          "Usuário não encontrado no realm.",
          source,
          chain,
        );
      case 409:
        throw new ConflictError(
          "Já existe um usuário com este username ou e-mail.",
          source,
          chain,
        );
      case 401:
      case 403:
        throw new KeycloakError(
          "O serviço não tem permissão administrativa no realm.",
          {
            status: response.status,
            source,
            upstreamStatus: response.status,
            chain,
          },
        );
      default:
        throw new KeycloakError(
          "O provedor de identidade respondeu de forma inesperada.",
          { source, upstreamStatus: response.status, chain },
        );
    }
  }

  /**
   * Preserva o status e o motivo do upstream numa entrada de `error_stack` — é
   * o único lugar onde o cliente consegue distinguir um 500 de um 504, ou saber
   * qual campo o Keycloak recusou.
   */
  private upstreamChain(response: RawResponse): ErrorStackEntry[] {
    return [
      {
        source: "keycloak",
        code: `KC-${response.status}`,
        description: this.describe(response.body, response.status),
      },
    ];
  }

  private describe(body: unknown, status: number): string {
    if (typeof body === "string" && body) return body;
    if (typeof body === "object" && body !== null) {
      const payload = body as Record<string, unknown>;
      for (const key of ["errorMessage", "error_description", "error"]) {
        const value = payload[key];
        if (typeof value === "string" && value) return value;
      }
    }
    return `O provedor de identidade respondeu ${status}.`;
  }

  /** `204` e corpos vazios são comuns no Admin API — não são erro de parse. */
  private async readBody(response: Response): Promise<unknown> {
    if (response.status === 204) return undefined;
    const text = await response.text();
    if (!text) return undefined;
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }
}
