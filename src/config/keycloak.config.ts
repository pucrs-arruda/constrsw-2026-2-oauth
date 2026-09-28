/**
 * Binds the settings the professor's docker-compose injects into this
 * container. These names come from the root `.env` of the `base` repo and are
 * the only ones this service reads.
 */

export const KEYCLOAK_CONFIG_KEY = 'keycloak';
export const OAUTH_CONFIG_KEY = 'oauth';

export const DEFAULT_REALM = 'constrsw';
export const DEFAULT_CLIENT_ID = 'oauth';
export const DEFAULT_INTERNAL_API_PORT = 3001;
export const DEFAULT_INTERNAL_METRICS_PORT = 9464;

export interface KeycloakSettings {
  /** Normalized: no trailing slash. */
  readonly serverUrl: string;
  readonly realm: string;
  readonly clientId: string;
  readonly clientSecret: string;
  /** Admin credentials, used by the Admin API in Epics 4-5. */
  readonly adminUser?: string;
  readonly adminPassword?: string;
}

export interface OAuthServiceSettings {
  /** Port this API listens on inside the container. */
  readonly internalApiPort: number;
  /** Port the Prometheus exporter listens on inside the container. */
  readonly internalMetricsPort: number;
}

export interface AppConfig {
  readonly [KEYCLOAK_CONFIG_KEY]: KeycloakSettings;
  readonly [OAUTH_CONFIG_KEY]: OAuthServiceSettings;
}

export class MissingEnvironmentVariableError extends Error {
  constructor(name: string) {
    super(
      `Required environment variable ${name} is missing or blank. ` +
        `It is injected by the professor's docker-compose; when running outside ` +
        `compose, export it in your shell. Do not create an .env in this service.`,
    );
    this.name = 'MissingEnvironmentVariableError';
  }
}

function required(env: NodeJS.ProcessEnv, name: string): string {
  const value = env[name]?.trim();
  if (!value) {
    throw new MissingEnvironmentVariableError(name);
  }
  return value;
}

function optional(env: NodeJS.ProcessEnv, name: string): string | undefined {
  return env[name]?.trim() || undefined;
}

function withDefault(
  env: NodeJS.ProcessEnv,
  name: string,
  fallback: string,
): string {
  return env[name]?.trim() || fallback;
}

export function readInternalMetricsPort(
  env: NodeJS.ProcessEnv = process.env,
): number {
  return port(env, 'OAUTH_INTERNAL_METRICS_PORT', DEFAULT_INTERNAL_METRICS_PORT);
}

function port(
  env: NodeJS.ProcessEnv,
  name: string,
  fallback: number,
): number {
  const raw = env[name]?.trim();
  if (!raw) {
    return fallback;
  }
  const parsed = Number(raw);
  if (!Number.isInteger(parsed) || parsed <= 0 || parsed > 65535) {
    throw new Error(
      `Environment variable ${name} must be a valid port number, got "${raw}".`,
    );
  }
  return parsed;
}

/**
 * Removes trailing slashes so URL joining never produces `//realms`.
 * Deliberately does NOT touch a `/auth` suffix: Keycloak 26 omits it, but if
 * the provided base already includes it we keep it as-is (NFR3).
 */
export function normalizeServerUrl(rawUrl: string): string {
  return rawUrl.trim().replace(/\/+$/, '');
}

export function buildAppConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  return {
    [KEYCLOAK_CONFIG_KEY]: {
      serverUrl: normalizeServerUrl(required(env, 'KEYCLOAK_SERVER_URL')),
      realm: withDefault(env, 'KEYCLOAK_REALM', DEFAULT_REALM),
      clientId: withDefault(env, 'KEYCLOAK_CLIENT_ID', DEFAULT_CLIENT_ID),
      clientSecret: required(env, 'KEYCLOAK_CLIENT_SECRET'),
      adminUser: optional(env, 'KEYCLOAK_ADMIN'),
      adminPassword: optional(env, 'KEYCLOAK_ADMIN_PASSWORD'),
    },
    [OAUTH_CONFIG_KEY]: {
      internalApiPort: port(
        env,
        'OAUTH_INTERNAL_API_PORT',
        DEFAULT_INTERNAL_API_PORT,
      ),
      internalMetricsPort: readInternalMetricsPort(env),
    },
  };
}

export const keycloakConfig = (): AppConfig => buildAppConfig(process.env);
