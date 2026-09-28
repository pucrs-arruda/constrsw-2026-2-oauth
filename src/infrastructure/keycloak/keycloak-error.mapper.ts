import { AxiosError } from 'axios';
import { DomainError, DomainErrorKind } from '../../domain/errors/domain-error';

const KIND_BY_STATUS: Record<number, DomainErrorKind> = {
  400: DomainErrorKind.BadRequest,
  401: DomainErrorKind.Unauthorized,
  403: DomainErrorKind.Forbidden,
  404: DomainErrorKind.NotFound,
  409: DomainErrorKind.Conflict,
};

/** Traduz um erro HTTP do Keycloak (ou a falta de resposta) em DomainError. */
export function toDomainError(error: AxiosError): DomainError {
  if (!error.response) {
    // Keycloak indisponivel / timeout / DNS
    return new DomainError(
      DomainErrorKind.Unavailable,
      'Nao foi possivel se comunicar com o Keycloak.',
      [{ code: '503', description: error.message, source: 'Keycloak' }],
    );
  }

  const status = error.response.status;
  const data = error.response.data as
    | { error?: string; error_description?: string; errorMessage?: string }
    | string
    | undefined;

  const description =
    (typeof data === 'object' &&
      (data.error_description || data.errorMessage || data.error)) ||
    (typeof data === 'string' ? data : undefined) ||
    error.message;

  const kind = KIND_BY_STATUS[status] ?? DomainErrorKind.Upstream;
  return new DomainError(
    kind,
    description,
    [{ code: String(status), description, source: 'Keycloak' }],
    kind === DomainErrorKind.Upstream ? status : undefined,
  );
}
