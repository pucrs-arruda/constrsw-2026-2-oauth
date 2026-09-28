import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Request, Response } from 'express';
import {
  DomainError,
  DomainErrorKind,
} from '../../../domain/errors/domain-error';
import { OAuthApiException } from '../exceptions/oauth-api.exception';

const STATUS_BY_KIND: Record<Exclude<DomainErrorKind, DomainErrorKind.Upstream>, number> = {
  [DomainErrorKind.BadRequest]: HttpStatus.BAD_REQUEST,
  [DomainErrorKind.Unauthorized]: HttpStatus.UNAUTHORIZED,
  [DomainErrorKind.InvalidCredentials]: HttpStatus.UNAUTHORIZED,
  [DomainErrorKind.Forbidden]: HttpStatus.FORBIDDEN,
  [DomainErrorKind.NotFound]: HttpStatus.NOT_FOUND,
  [DomainErrorKind.Conflict]: HttpStatus.CONFLICT,
  [DomainErrorKind.Unavailable]: HttpStatus.SERVICE_UNAVAILABLE,
};

/**
 * Filtro global de excecoes. Normaliza QUALQUER erro lancado pela aplicacao
 * (DomainError, OAuthApiException, HttpException padrao do Nest - inclusive as do
 * ValidationPipe - ou um erro nao tratado) para o envelope exigido pelo T1:
 *   { error_code, error_description, error_source, error_stack }
 */
@Catch()
export class OAuthExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger('OAuthExceptionFilter');

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<Request>();

    if (exception instanceof DomainError) {
      const status =
        exception.kind === DomainErrorKind.Upstream
          ? (exception.status ?? HttpStatus.BAD_GATEWAY)
          : STATUS_BY_KIND[exception.kind];
      response.status(status).json({
        error_code: String(status),
        error_description: exception.description,
        error_source: 'OAuthAPI',
        error_stack: exception.causes.map((c) => ({
          error_code: c.code,
          error_description: c.description,
          error_source: c.source,
        })),
      });
      return;
    }

    if (exception instanceof OAuthApiException) {
      const body = exception.getResponse() as Record<string, unknown>;
      response.status(exception.getStatus()).json(body);
      return;
    }

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      const raw = exception.getResponse();
      const description = this.extractDescription(raw, exception.message);

      response.status(status).json({
        error_code: String(status),
        error_description: description,
        error_source: 'OAuthAPI',
        error_stack: [
          {
            error_code: String(status),
            error_description: description,
            error_source: 'OAuthAPI',
          },
        ],
      });
      return;
    }

    // erro nao mapeado (bug, timeout de rede, etc.)
    this.logger.error(
      `Erro nao tratado em ${request.method} ${request.url}`,
      exception instanceof Error ? exception.stack : String(exception),
    );

    response.status(HttpStatus.INTERNAL_SERVER_ERROR).json({
      error_code: String(HttpStatus.INTERNAL_SERVER_ERROR),
      error_description: 'Erro interno inesperado na API oauth.',
      error_source: 'OAuthAPI',
      error_stack: [
        {
          error_code: String(HttpStatus.INTERNAL_SERVER_ERROR),
          error_description:
            exception instanceof Error ? exception.message : String(exception),
          error_source: 'OAuthAPI',
        },
      ],
    });
  }

  private extractDescription(raw: unknown, fallback: string): string {
    if (typeof raw === 'string') return raw;
    if (raw && typeof raw === 'object' && 'message' in raw) {
      const msg = (raw as { message: unknown }).message;
      return Array.isArray(msg) ? msg.join('; ') : String(msg);
    }
    return fallback;
  }
}
