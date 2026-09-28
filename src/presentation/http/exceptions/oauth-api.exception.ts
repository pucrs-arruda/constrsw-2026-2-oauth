import { HttpException } from '@nestjs/common';

export interface ErrorStackEntry {
  error_code: string;
  error_description: string;
  error_source: string;
}

/**
 * Excecao de negocio da API oauth. Carrega o formato de erro definido no
 * enunciado do T1:
 *   { error_code, error_description, error_source, error_stack }
 *
 * error_code: por padrao, o proprio codigo HTTP da resposta (string). Pode
 * ser sobrescrito quando uma rota exigir um codigo diferente.
 * error_source: origem do erro final (aqui, sempre "OAuthAPI").
 * error_stack: cadeia de erros que levou ao erro final (ex.: o erro cru
 * devolvido pelo Keycloak, quando existir).
 */
export class OAuthApiException extends HttpException {
  constructor(
    status: number,
    description: string,
    stack: ErrorStackEntry[] = [],
    errorCode?: string,
  ) {
    super(
      {
        error_code: errorCode ?? String(status),
        error_description: description,
        error_source: 'OAuthAPI',
        error_stack: stack,
      },
      status,
    );
  }
}
