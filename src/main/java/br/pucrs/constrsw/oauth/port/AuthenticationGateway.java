package br.pucrs.constrsw.oauth.port;

import br.pucrs.constrsw.oauth.domain.AuthTokens;

public interface AuthenticationGateway {
    AuthTokens authenticate(String username, String password);
}
