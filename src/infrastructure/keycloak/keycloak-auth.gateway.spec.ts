import { KeycloakAuthGateway } from './keycloak-auth.gateway';
import { KeycloakClientService } from './keycloak-client.service';

describe('KeycloakAuthGateway', () => {
  it('converte a resposta do token do Keycloak em TokenSet, mantendo a resposta completa em raw', async () => {
    const response = {
      token_type: 'Bearer',
      access_token: 'abc',
      expires_in: 600,
      refresh_token: 'def',
      refresh_expires_in: 1800,
      scope: 'profile email',
    };
    const keycloak = { passwordGrant: jest.fn().mockResolvedValue(response) };
    const gateway = new KeycloakAuthGateway(keycloak as unknown as KeycloakClientService);

    const result = await gateway.login('admin@pucrs.br', 'a12345678');

    expect(keycloak.passwordGrant).toHaveBeenCalledWith('admin@pucrs.br', 'a12345678');
    expect(result).toEqual({
      tokenType: 'Bearer',
      accessToken: 'abc',
      expiresIn: 600,
      refreshToken: 'def',
      refreshExpiresIn: 1800,
      raw: response,
    });
  });
});
