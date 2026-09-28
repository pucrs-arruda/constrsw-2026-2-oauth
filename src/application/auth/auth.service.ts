import { Inject, Injectable } from '@nestjs/common';
import { TokenSet } from '../../domain/entities/token-set.entity';
import {
  DomainError,
  DomainErrorKind,
} from '../../domain/errors/domain-error';
import { AUTH_GATEWAY, AuthGateway } from '../../domain/repositories/auth.gateway';

@Injectable()
export class AuthService {
  constructor(@Inject(AUTH_GATEWAY) private readonly gateway: AuthGateway) {}

  async login(username: string, password: string): Promise<TokenSet> {
    try {
      return await this.gateway.login(username, password);
    } catch (error) {
      if (
        error instanceof DomainError &&
        error.kind !== DomainErrorKind.Unavailable
      ) {
        // O endpoint de token do Keycloak segue o RFC 6749 e devolve 400
        // (invalid_grant) para credencial invalida - NAO 401. Como a
        // estrutura da chamada ja foi validada (LoginDto) antes de chegarmos
        // aqui, qualquer erro que o provedor devolva neste ponto so pode ser
        // credencial invalida; mapeamos para o 401 exigido pelo enunciado.
        // Unavailable (Keycloak fora do ar) e preservado como esta.
        throw new DomainError(
          DomainErrorKind.InvalidCredentials,
          'username e/ou password invalidos.',
          error.causes,
        );
      }
      throw error;
    }
  }
}
