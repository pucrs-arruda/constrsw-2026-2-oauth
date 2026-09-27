package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.domain.AuthTokens;
import br.pucrs.constrsw.oauth.domain.InvalidLoginRequestException;
import br.pucrs.constrsw.oauth.port.AuthenticationGateway;

public class LoginService {

    private final AuthenticationGateway authenticationGateway;

    public LoginService(AuthenticationGateway authenticationGateway) {
        this.authenticationGateway = authenticationGateway;
    }

    public AuthTokens login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new InvalidLoginRequestException("Username is required");
        }
        if (password == null || password.isBlank()) {
            throw new InvalidLoginRequestException("Password is required");
        }
        return authenticationGateway.authenticate(username, password);
    }
}
