import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from "@nestjs/common";
import type { Response } from "express";
import { AppError, KeycloakDependencyError } from "./errors";

/**
 * Envelope de erro único do serviço, acordado no Sprint 0. Toda rota responde
 * falha exatamente nestas quatro chaves:
 *
 *   { error_code, error_description, error_source, error_stack }
 *
 * `error_code` espelha o status do Keycloak (`OA-<status>`), `error_source` é a
 * origem do erro final (`OAuthAPI`) e `error_stack` carrega a cadeia completa,
 * do upstream até o `OAuthAPI`.
 */
export interface ErrorStackEntry {
  error_code: string;
  error_description: string;
  error_source: string;
}

export interface ErrorResponseBody {
  error_code: string;
  error_description: string;
  error_source: string;
  error_stack: ErrorStackEntry[];
}

// Upstream (Keycloak) statuses we surface to the client as-is.
// Anything else from a dependency is reported as 503 (dependency failure).
const SURFACED_UPSTREAM_STATUSES = new Set([400, 401, 403, 404, 409]);

const DETAIL_BY_STATUS: Record<number, string> = {
  400: "A requisição é inválida.",
  401: "O access token é obrigatório ou inválido.",
  403: "Acesso negado.",
  404: "Recurso não encontrado.",
  409: "Conflito com o estado atual do recurso.",
};

const GENERIC_DESCRIPTION = "Erro interno no serviço de identidade.";
const OAUTH_SOURCE = "OAuthAPI";

@Catch()
export class ProblemDetailsFilter implements ExceptionFilter {
  private readonly logger = new Logger(ProblemDetailsFilter.name);

  catch(exception: unknown, host: ArgumentsHost) {
    const response = host.switchToHttp().getResponse<Response>();
    const { status, body } = this.describe(exception);
    // `@Catch()` engole a exceção antes do handler padrão do Nest: se não
    // logarmos aqui, a causa de um 500 em produção some por completo.
    if (status >= 500) this.log(exception);
    response.status(status).type("application/json").send(body);
  }

  private log(exception: unknown) {
    if (exception instanceof Error) {
      this.logger.error(exception.message, exception.stack);
      return;
    }
    this.logger.error(String(exception));
  }

  private describe(exception: unknown): {
    status: number;
    body: ErrorResponseBody;
  } {
    if (exception instanceof KeycloakDependencyError) {
      return this.describeKeycloakDependency(exception);
    }

    if (exception instanceof AppError) {
      const status = exception.status;
      const code = exception.code;
      const detail = exception.message;
      return {
        status,
        body: {
          error_code: code,
          error_description: detail,
          error_source: OAUTH_SOURCE,
          error_stack: [
            ...exception.chain.map((entry) => ({
              error_code: entry.code,
              error_description: entry.description,
              error_source: entry.source,
            })),
            { error_code: code, error_description: detail, error_source: OAUTH_SOURCE },
          ],
        },
      };
    }

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      const code = `OA-${status}`;
      const details = this.validationDetails(exception, code);
      const detail =
        details.length > 0
          ? "A requisição é inválida."
          : (DETAIL_BY_STATUS[status] ?? exception.message);
      return {
        status,
        body: {
          error_code: code,
          error_description: detail,
          error_source: OAUTH_SOURCE,
          error_stack: [
            ...details,
            { error_code: code, error_description: detail, error_source: OAUTH_SOURCE },
          ],
        },
      };
    }

    const stack = this.unexpectedDetails(exception);
    return {
      status: HttpStatus.INTERNAL_SERVER_ERROR,
      body: {
        error_code: "OA-500",
        error_description: GENERIC_DESCRIPTION,
        error_source: OAUTH_SOURCE,
        error_stack: [
          ...stack,
          {
            error_code: "OA-500",
            error_description: GENERIC_DESCRIPTION,
            error_source: OAUTH_SOURCE,
          },
        ],
      },
    };
  }

  private describeKeycloakDependency(exception: KeycloakDependencyError): {
    status: number;
    body: ErrorResponseBody;
  } {
    const status =
      exception.reason === "invalid_credentials"
        ? HttpStatus.UNAUTHORIZED
        : exception.upstreamStatus &&
            SURFACED_UPSTREAM_STATUSES.has(exception.upstreamStatus)
          ? exception.upstreamStatus
          : HttpStatus.SERVICE_UNAVAILABLE;
    const code = `OA-${status}`;
    const detail =
      exception.reason === "invalid_credentials"
        ? "Credenciais inválidas."
        : status === HttpStatus.SERVICE_UNAVAILABLE
          ? "O provedor de identidade está indisponível."
          : (DETAIL_BY_STATUS[status] ??
            "A requisição não pôde ser processada.");
    return {
      status,
      body: {
        error_code: code,
        error_description: detail,
        error_source: OAUTH_SOURCE,
        error_stack: [
          {
            error_code: String(status),
            error_description: "Erro retornado pelo Keycloak.",
            error_source: "Keycloak",
          },
          { error_code: code, error_description: detail, error_source: OAUTH_SOURCE },
        ],
      },
    };
  }

  /**
   * O `ValidationPipe` embala as mensagens do class-validator num array em
   * `message`. Cada mensagem vira uma entrada da cadeia, para o cliente saber
   * qual campo recusou.
   */
  private validationDetails(
    exception: HttpException,
    code: string,
  ): ErrorStackEntry[] {
    const payload = exception.getResponse();
    if (typeof payload !== "object" || payload === null) return [];
    const messages = (payload as { message?: unknown }).message;
    if (!Array.isArray(messages)) return [];
    return messages.map((message) => ({
      error_code: code,
      error_description: String(message),
      error_source: "validation",
    }));
  }

  /**
   * Causa de erro não previsto só é exposta fora de produção — em produção o
   * cliente recebe a descrição genérica e o detalhe fica no log.
   */
  private unexpectedDetails(exception: unknown): ErrorStackEntry[] {
    if (process.env.NODE_ENV === "production") return [];
    const description =
      exception instanceof Error ? exception.message : String(exception);
    return [
      {
        error_code: "OA-500",
        error_description: description,
        error_source: "runtime",
      },
    ];
  }
}
