/**
 * Uma entrada da cadeia de causas serializada em `error_stack`.
 * Formato fechado pelo grupo no Sprint 0 — não alterar sem combinar com as
 * quatro trilhas.
 */
export interface ErrorStackEntry {
  source: string;
  code: string;
  description: string;
}

/**
 * Base de toda exceção de negócio do serviço. Carrega os campos que o filtro
 * global publica no envelope `error_code` / `error_description` /
 * `error_source` / `error_stack`.
 */
export abstract class AppError extends Error {
  readonly status: number;
  readonly code: string;
  readonly source: string;
  readonly chain: ErrorStackEntry[];

  constructor(
    status: number,
    description: string,
    source: string,
    chain: ErrorStackEntry[] = [],
  ) {
    super(description);
    this.name = new.target.name;
    this.status = status;
    this.code = `OA-${status}`;
    this.source = source;
    this.chain = chain;
  }

  toStackEntry(): ErrorStackEntry {
    return { source: this.source, code: this.code, description: this.message };
  }
}

/** Requisição malformada: falha de forma, não de regra de negócio. */
export class ValidationError extends AppError {
  constructor(
    description: string,
    source = "validation",
    chain: ErrorStackEntry[] = [],
  ) {
    super(400, description, source, chain);
  }
}

/** Recurso inexistente no realm. */
export class NotFoundError extends AppError {
  constructor(
    description: string,
    source = "keycloak",
    chain: ErrorStackEntry[] = [],
  ) {
    super(404, description, source, chain);
  }
}

/** Colisão de unicidade — username ou e-mail já usados no realm. */
export class ConflictError extends AppError {
  constructor(
    description: string,
    source = "keycloak",
    chain: ErrorStackEntry[] = [],
  ) {
    super(409, description, source, chain);
  }
}

export interface KeycloakErrorOptions {
  /** Status que o serviço devolve ao cliente. 502 por padrão. */
  status?: number;
  source?: string;
  /** Status que o Keycloak devolveu, quando houve resposta. */
  upstreamStatus?: number;
  chain?: ErrorStackEntry[];
}

/** Falha na conversa com o Keycloak — indisponibilidade ou resposta inesperada. */
export class KeycloakError extends AppError {
  readonly upstreamStatus?: number;

  constructor(description: string, options: KeycloakErrorOptions = {}) {
    super(
      options.status ?? 502,
      description,
      options.source ?? "keycloak",
      options.chain ?? [],
    );
    this.upstreamStatus = options.upstreamStatus;
  }
}

/**
 * Falha do fluxo de token do usuário final (trilha do DEV A).
 * Subclasse de `KeycloakError` para que `src/auth/**` siga funcionando sem
 * edição enquanto ganha os campos do envelope.
 */
export class KeycloakDependencyError extends KeycloakError {
  readonly reason: string;

  constructor(reason: string, upstreamStatus?: number) {
    super(
      reason === "invalid_credentials"
        ? "Credenciais inválidas."
        : "O provedor de identidade está indisponível.",
      {
        status: KeycloakDependencyError.statusFor(reason, upstreamStatus),
        upstreamStatus,
      },
    );
    this.reason = reason;
  }

  private static statusFor(reason: string, upstreamStatus?: number): number {
    if (reason === "invalid_credentials") return 401;
    if ([400, 401, 403, 404, 409].includes(upstreamStatus ?? 0)) {
      return upstreamStatus as number;
    }
    return 503;
  }
}
