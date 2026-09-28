import { ExecutionContext } from '@nestjs/common';
import { BearerTokenGuard } from './bearer-token.guard';
import { OAuthApiException } from '../exceptions/oauth-api.exception';

function buildContext(headers: Record<string, string | undefined>): ExecutionContext {
  const request = { headers } as { headers: Record<string, string | undefined>; bearerToken?: string };
  return {
    switchToHttp: () => ({ getRequest: () => request }),
  } as unknown as ExecutionContext;
}

describe('BearerTokenGuard', () => {
  let guard: BearerTokenGuard;

  beforeEach(() => {
    guard = new BearerTokenGuard();
  });

  it('lanca OAuthApiException 400 quando o header Authorization esta ausente', () => {
    const context = buildContext({});

    expect(() => guard.canActivate(context)).toThrow(OAuthApiException);
    try {
      guard.canActivate(context);
    } catch (err) {
      expect((err as OAuthApiException).getStatus()).toBe(400);
    }
  });

  it('lanca 400 quando o header nao comeca com "Bearer "', () => {
    const context = buildContext({ authorization: 'Token abc123' });

    expect(() => guard.canActivate(context)).toThrow(OAuthApiException);
    try {
      guard.canActivate(context);
    } catch (err) {
      expect((err as OAuthApiException).getStatus()).toBe(400);
    }
  });

  it('lanca 400 quando o token apos "Bearer " esta vazio', () => {
    const context = buildContext({ authorization: 'Bearer    ' });

    expect(() => guard.canActivate(context)).toThrow(OAuthApiException);
  });

  it('aceita um header valido, anexa o token na request e retorna true', () => {
    const request: { headers: Record<string, string>; bearerToken?: string } = {
      headers: { authorization: 'Bearer abc123' },
    };
    const context = {
      switchToHttp: () => ({ getRequest: () => request }),
    } as unknown as ExecutionContext;

    const result = guard.canActivate(context);

    expect(result).toBe(true);
    expect(request.bearerToken).toBe('abc123');
  });
});
