import { AxiosError } from 'axios';
import { DomainErrorKind } from '../../domain/errors/domain-error';
import { toDomainError } from './keycloak-error.mapper';

function axiosError(status: number, data?: unknown): AxiosError {
  return {
    response: { status, data },
    message: `Request failed with status code ${status}`,
  } as unknown as AxiosError;
}

describe('toDomainError', () => {
  it.each([
    [400, DomainErrorKind.BadRequest],
    [401, DomainErrorKind.Unauthorized],
    [403, DomainErrorKind.Forbidden],
    [404, DomainErrorKind.NotFound],
    [409, DomainErrorKind.Conflict],
  ])('status %i vira %s', (status, kind) => {
    const err = toDomainError(axiosError(status, { error: 'x' }));

    expect(err.kind).toBe(kind);
    expect(err.status).toBeUndefined();
    expect(err.causes).toEqual([
      { code: String(status), description: 'x', source: 'Keycloak' },
    ]);
  });

  it('status sem kind dedicado vira Upstream e preserva o status original', () => {
    const err = toDomainError(axiosError(500, 'boom'));

    expect(err.kind).toBe(DomainErrorKind.Upstream);
    expect(err.status).toBe(500);
    expect(err.description).toBe('boom');
  });

  it('prefere error_description, depois errorMessage, depois error', () => {
    expect(
      toDomainError(axiosError(400, { error: 'e', error_description: 'd', errorMessage: 'm' }))
        .description,
    ).toBe('d');
    expect(toDomainError(axiosError(400, { error: 'e', errorMessage: 'm' })).description).toBe('m');
  });

  it('usa a mensagem do axios quando o corpo nao traz descricao', () => {
    expect(toDomainError(axiosError(404)).description).toBe(
      'Request failed with status code 404',
    );
  });

  it('sem response (Keycloak fora do ar) vira Unavailable com causa 503', () => {
    const err = toDomainError({ message: 'ECONNREFUSED' } as unknown as AxiosError);

    expect(err.kind).toBe(DomainErrorKind.Unavailable);
    expect(err.causes).toEqual([
      { code: '503', description: 'ECONNREFUSED', source: 'Keycloak' },
    ]);
  });
});
