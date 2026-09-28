import { TokenSet } from '../../domain/entities/token-set.entity';
import { DomainError, DomainErrorKind } from '../../domain/errors/domain-error';
import { AuthGateway } from '../../domain/repositories/auth.gateway';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let gateway: jest.Mocked<AuthGateway>;

  beforeEach(() => {
    gateway = { login: jest.fn() };
    service = new AuthService(gateway);
  });

  it('retorna o token quando as credenciais sao validas', async () => {
    const token: TokenSet = {
      tokenType: 'Bearer',
      accessToken: 'abc',
      expiresIn: 600,
      refreshToken: 'def',
      refreshExpiresIn: 1800,
      raw: {},
    };
    gateway.login.mockResolvedValue(token);

    const result = await service.login('admin@pucrs.br', 'a12345678');

    expect(result).toBe(token);
    expect(gateway.login).toHaveBeenCalledWith('admin@pucrs.br', 'a12345678');
  });

  it('remapeia erro do provedor (400 invalid_grant) para InvalidCredentials conforme o enunciado, preservando as causas', async () => {
    const causes = [
      { code: '400', description: 'Invalid user credentials', source: 'Keycloak' },
    ];
    gateway.login.mockRejectedValue(
      new DomainError(DomainErrorKind.BadRequest, 'invalid_grant: Invalid user credentials', causes),
    );

    const err = await service.login('admin@pucrs.br', 'errada').catch((e) => e);

    expect(err).toBeInstanceOf(DomainError);
    expect(err.kind).toBe(DomainErrorKind.InvalidCredentials);
    expect(err.description).toBe('username e/ou password invalidos.');
    expect(err.causes).toBe(causes);
  });

  it('preserva Unavailable quando o Keycloak esta fora do ar (nao remapeia)', async () => {
    const original = new DomainError(
      DomainErrorKind.Unavailable,
      'Nao foi possivel se comunicar com o Keycloak.',
    );
    gateway.login.mockRejectedValue(original);

    await expect(service.login('a', 'b')).rejects.toBe(original);
  });

  it('repropaga erros que nao sao DomainError sem alterar', async () => {
    const unexpected = new Error('boom');
    gateway.login.mockRejectedValue(unexpected);

    await expect(service.login('a', 'b')).rejects.toBe(unexpected);
  });
});
