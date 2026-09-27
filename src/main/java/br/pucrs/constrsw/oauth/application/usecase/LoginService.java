package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.LoginUseCase;
import br.pucrs.constrsw.oauth.application.port.out.AuthGateway;
import br.pucrs.constrsw.oauth.domain.exception.InvalidInputException;
import br.pucrs.constrsw.oauth.domain.model.AuthTokens;
import br.pucrs.constrsw.oauth.domain.model.Credentials;

public class LoginService implements LoginUseCase {

    private final AuthGateway authGateway;

    public LoginService(AuthGateway authGateway) {
        this.authGateway = authGateway;
    }

    @Override
    public AuthTokens execute(Credentials credentials) {
        if (credentials == null
                || credentials.getUsername() == null || credentials.getUsername().isBlank()
                || credentials.getPassword() == null || credentials.getPassword().isBlank()) {
            throw new InvalidInputException("Os campos 'username' e 'password' sao obrigatorios.");
        }
        return authGateway.authenticate(credentials);
    }
}
