import { z } from "zod";

const environmentSchema = z.object({
  PORT: z.coerce.number().int().min(1).max(65535),
  NODE_ENV: z.enum(["development", "test", "production"]),
  KEYCLOAK_URL: z.string().url(),
  KEYCLOAK_REALM: z.string().min(1),
  KEYCLOAK_CLIENT_ID: z.string().min(1),
  KEYCLOAK_CLIENT_SECRET: z.string().min(1),
  KEYCLOAK_TIMEOUT_MS: z.coerce.number().int().positive(),
  // Client de service account usado pelo Admin API (trilhas B, C e D — inclui o
  // CRUD de roles e role-mapping via client_credentials).
  KEYCLOAK_ADMIN_CLIENT_ID: z.string().min(1),
  KEYCLOAK_ADMIN_CLIENT_SECRET: z.string().min(1),
  SESSION_COOKIE_NAME: z.string().min(1),
  COOKIE_SECURE: z.enum(["true", "false"]),
  COOKIE_SAME_SITE: z.enum(["lax", "strict", "none"]),
});

export function validateEnvironment(environment: Record<string, unknown>) {
  return environmentSchema.parse(environment);
}

export type Environment = z.infer<typeof environmentSchema>;
