import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common';
import { Request } from 'express';
import { OAuthApiException } from '../exceptions/oauth-api.exception';

/**
 * Garante que a requisicao trouxe um header "Authorization: Bearer <token>"
 * bem formado. Nao valida o token em si (isso e responsabilidade do
 * Keycloak, chamado em seguida pelo service) - so a ESTRUTURA da chamada,
 * por isso responde 400 (nao 401) quando o header esta ausente/malformado,
 * conforme o enunciado do T1.
 */
@Injectable()
export class BearerTokenGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    const header = request.headers['authorization'];

    if (!header || Array.isArray(header) || !header.startsWith('Bearer ')) {
      throw new OAuthApiException(
        400,
        'Header Authorization ausente ou mal formado. Esperado: "Authorization: Bearer <access_token>".',
      );
    }

    const token = header.slice('Bearer '.length).trim();
    if (!token) {
      throw new OAuthApiException(
        400,
        'Header Authorization ausente ou mal formado. Esperado: "Authorization: Bearer <access_token>".',
      );
    }

    (request as Request & { bearerToken: string }).bearerToken = token;
    return true;
  }
}
