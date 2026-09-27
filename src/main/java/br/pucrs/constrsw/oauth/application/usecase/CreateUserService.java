package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.CreateUserUseCase;
import br.pucrs.constrsw.oauth.application.port.out.UserGateway;
import br.pucrs.constrsw.oauth.domain.exception.InvalidEmailException;
import br.pucrs.constrsw.oauth.domain.model.NewUser;
import br.pucrs.constrsw.oauth.domain.model.User;
import br.pucrs.constrsw.oauth.domain.util.EmailValidator;

public class CreateUserService implements CreateUserUseCase {

    private final UserGateway userGateway;

    public CreateUserService(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    @Override
    public User execute(String bearer, NewUser newUser) {
        if (!EmailValidator.isValid(newUser.getUsername())) {
            throw new InvalidEmailException(newUser.getUsername());
        }
        return userGateway.create(bearer, newUser);
    }
}
