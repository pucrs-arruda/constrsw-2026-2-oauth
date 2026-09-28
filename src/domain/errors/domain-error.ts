export enum DomainErrorKind {
  BadRequest = 'BadRequest',
  Unauthorized = 'Unauthorized',
  InvalidCredentials = 'InvalidCredentials',
  Forbidden = 'Forbidden',
  NotFound = 'NotFound',
  Conflict = 'Conflict',
  Unavailable = 'Unavailable',
  /** Erro do provedor sem kind dedicado; `status` guarda o codigo original. */
  Upstream = 'Upstream',
}

export interface DomainErrorCause {
  code: string;
  description: string;
  source: string;
}

/**
 * Erro de dominio, independente de HTTP. A camada de apresentacao traduz
 * `kind` (e `status`, quando `Upstream`) para o codigo HTTP e o envelope de
 * erro do T1.
 */
export class DomainError extends Error {
  constructor(
    public readonly kind: DomainErrorKind,
    public readonly description: string,
    public readonly causes: DomainErrorCause[] = [],
    public readonly status?: number,
  ) {
    super(description);
    this.name = 'DomainError';
  }
}
