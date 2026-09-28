import { KeycloakDependencyError } from "./errors";

export interface TokenResponse {
  token_type: string;
  access_token: string;
  expires_in: number;
  refresh_token?: string;
  refresh_expires_in?: number;
}

async function withTimeout<T>(
  timeoutMs: number,
  run: (signal: AbortSignal) => Promise<T>,
): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await run(controller.signal);
  } finally {
    clearTimeout(timeout);
  }
}

/**
 * Requests an OAuth token from Keycloak (password / refresh / client_credentials
 * grants). Extracted from the original KeycloakClient so both the login flow and
 * the Admin API client share the same timeout + error-mapping behaviour.
 */
export async function requestToken(
  url: URL,
  body: URLSearchParams,
  timeoutMs: number,
): Promise<TokenResponse> {
  try {
    return await withTimeout(timeoutMs, async (signal) => {
      const response = await fetch(url, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body,
        signal,
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new KeycloakDependencyError(
          response.status === 401 ? "invalid_credentials" : "upstream_rejected",
          response.status,
        );
      }
      return payload as TokenResponse;
    });
  } catch (error) {
    if (error instanceof KeycloakDependencyError) throw error;
    throw new KeycloakDependencyError("unavailable");
  }
}

/** Resposta (parcial) do endpoint de introspecção do Keycloak. */
export interface TokenIntrospection {
  active: boolean;
  realm_access?: { roles?: string[] };
}

export interface IntrospectOptions {
  clientId: string;
  clientSecret: string;
  timeoutMs: number;
}

/**
 * Valida um access token contra o endpoint RFC 7662 do Keycloak. Diferente de
 * decodificar o JWT localmente, o Keycloak confere assinatura e expiração e
 * devolve `active`, além de `realm_access.roles`. Token inválido/expirado vem
 * como `{ active: false }` (não é erro de dependência).
 */
export async function introspectToken(
  url: URL,
  token: string,
  options: IntrospectOptions,
): Promise<TokenIntrospection> {
  const { clientId, clientSecret, timeoutMs } = options;
  try {
    return await withTimeout(timeoutMs, async (signal) => {
      const response = await fetch(url, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          client_id: clientId,
          client_secret: clientSecret,
          token,
        }),
        signal,
      });
      if (!response.ok) {
        throw new KeycloakDependencyError("upstream_rejected", response.status);
      }
      const payload = (await response.json().catch(() => ({}))) as Partial<
        TokenIntrospection
      >;
      return { ...payload, active: payload.active === true };
    });
  } catch (error) {
    if (error instanceof KeycloakDependencyError) throw error;
    throw new KeycloakDependencyError("unavailable");
  }
}

export interface KeycloakJsonOptions {
  method?: string;
  token?: string;
  body?: unknown;
  timeoutMs: number;
}

/**
 * Maps a Keycloak Admin API HTTP status to a stable reason string so the
 * ProblemDetailsFilter can translate it into the right OA-<status> problem.
 */
function reasonForStatus(status: number): string {
  switch (status) {
    case 400:
      return "bad_request";
    case 401:
      return "unauthorized";
    case 403:
      return "forbidden";
    case 404:
      return "not_found";
    case 409:
      return "conflict";
    default:
      return "upstream_rejected";
  }
}

/**
 * Performs an authenticated JSON call against the Keycloak Admin REST API.
 * Returns the parsed body, or `undefined` for empty responses (204 / no content).
 * Non-2xx responses become a KeycloakDependencyError carrying the upstream status.
 */
export async function keycloakJson<T = unknown>(
  url: URL,
  options: KeycloakJsonOptions,
): Promise<T | undefined> {
  const { method = "GET", token, body, timeoutMs } = options;
  try {
    return await withTimeout(timeoutMs, async (signal) => {
      const headers: Record<string, string> = {};
      if (token) headers["authorization"] = `Bearer ${token}`;
      let payload: string | undefined;
      if (body !== undefined) {
        headers["content-type"] = "application/json";
        payload = JSON.stringify(body);
      }
      const response = await fetch(url, {
        method,
        headers,
        body: payload,
        signal,
      });
      if (!response.ok) {
        throw new KeycloakDependencyError(
          reasonForStatus(response.status),
          response.status,
        );
      }
      if (typeof response.text === "function") {
        const text = await response.text();
        return (text ? (JSON.parse(text) as T) : undefined) as T | undefined;
      }
      return (await response.json()) as T;
    });
  } catch (error) {
    if (error instanceof KeycloakDependencyError) throw error;
    throw new KeycloakDependencyError("unavailable");
  }
}
