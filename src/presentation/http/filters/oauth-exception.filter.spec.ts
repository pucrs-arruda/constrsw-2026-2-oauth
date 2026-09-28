import { ArgumentsHost, BadRequestException, NotFoundException } from '@nestjs/common';
import { OAuthExceptionFilter } from './oauth-exception.filter';
import { DomainError, DomainErrorKind } from '../../../domain/errors/domain-error';
import { OAuthApiException } from '../exceptions/oauth-api.exception';

function buildHost() {
  const json = jest.fn();
  const status = jest.fn().mockReturnValue({ json });
  const response = { status };
  const request = { method: 'GET', url: '/test' };
  const host = {
    switchToHttp: () => ({ getResponse: () => response, getRequest: () => request }),
  } as unknown as ArgumentsHost;
  return { host, status, json };
}

describe('OAuthExceptionFilter', () => {
  let filter: OAuthExceptionFilter;

  beforeEach(() => {
    filter = new OAuthExceptionFilter();
  });

  it('repassa o body de uma OAuthApiException sem alteracoes', () => {
    const { host, status, json } = buildHost();
    const exception = new OAuthApiException(404, 'Usuario nao encontrado.', [
      { error_code: '404', error_description: 'not found', error_source: 'Keycloak' },
    ]);

    filter.catch(exception, host);

    expect(status).toHaveBeenCalledWith(404);
    expect(json).toHaveBeenCalledWith({
      error_code: '404',
      error_description: 'Usuario nao encontrado.',
      error_source: 'OAuthAPI',
      error_stack: [{ error_code: '404', error_description: 'not found', error_source: 'Keycloak' }],
    });
  });

  it('normaliza uma HttpException padrao do Nest (ex.: NotFoundException) para o envelope do T1', () => {
    const { host, status, json } = buildHost();
    const exception = new NotFoundException('Rota nao encontrada');

    filter.catch(exception, host);

    expect(status).toHaveBeenCalledWith(404);
    expect(json).toHaveBeenCalledWith({
      error_code: '404',
      error_description: 'Rota nao encontrada',
      error_source: 'OAuthAPI',
      error_stack: [
        { error_code: '404', error_description: 'Rota nao encontrada', error_source: 'OAuthAPI' },
      ],
    });
  });

  it('junta as mensagens de erro de validacao do ValidationPipe (array) com "; "', () => {
    const { host, status, json } = buildHost();
    const exception = new BadRequestException({
      statusCode: 400,
      message: ['username e obrigatorio.', 'password e obrigatorio.'],
      error: 'Bad Request',
    });

    filter.catch(exception, host);

    expect(status).toHaveBeenCalledWith(400);
    expect(json).toHaveBeenCalledWith(
      expect.objectContaining({
        error_code: '400',
        error_description: 'username e obrigatorio.; password e obrigatorio.',
      }),
    );
  });

  it('normaliza um erro nao tratado (nao HttpException) para 500 generico', () => {
    const { host, status, json } = buildHost();
    const exception = new Error('falha inesperada de infraestrutura');

    filter.catch(exception, host);

    expect(status).toHaveBeenCalledWith(500);
    expect(json).toHaveBeenCalledWith(
      expect.objectContaining({
        error_code: '500',
        error_description: 'Erro interno inesperado na API oauth.',
        error_source: 'OAuthAPI',
      }),
    );
  });

  it.each([
    [DomainErrorKind.BadRequest, undefined, 400],
    [DomainErrorKind.Unauthorized, undefined, 401],
    [DomainErrorKind.InvalidCredentials, undefined, 401],
    [DomainErrorKind.Forbidden, undefined, 403],
    [DomainErrorKind.NotFound, undefined, 404],
    [DomainErrorKind.Conflict, undefined, 409],
    [DomainErrorKind.Unavailable, undefined, 503],
    [DomainErrorKind.Upstream, 500, 500],
    [DomainErrorKind.Upstream, 429, 429],
  ])('traduz DomainError %s (status %s) para HTTP %i', (kind, upstreamStatus, expected) => {
    const { host, status } = buildHost();

    filter.catch(new DomainError(kind, 'x', [], upstreamStatus), host);

    expect(status).toHaveBeenCalledWith(expected);
  });

  it('monta o envelope do T1 a partir de um DomainError, com as causas em error_stack', () => {
    const { host, json } = buildHost();
    const exception = new DomainError(DomainErrorKind.Forbidden, 'sem permissao', [
      { code: '403', description: 'forbidden', source: 'Keycloak' },
    ]);

    filter.catch(exception, host);

    expect(json).toHaveBeenCalledWith({
      error_code: '403',
      error_description: 'sem permissao',
      error_source: 'OAuthAPI',
      error_stack: [
        { error_code: '403', error_description: 'forbidden', error_source: 'Keycloak' },
      ],
    });
  });
});
