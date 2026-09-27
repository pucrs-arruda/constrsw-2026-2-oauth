package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.UpdateUserUseCase;
import br.pucrs.constrsw.oauth.application.port.out.UserGateway;
import br.pucrs.constrsw.oauth.domain.exception.InvalidEmailException;
import br.pucrs.constrsw.oauth.domain.exception.InvalidInputException;
import br.pucrs.constrsw.oauth.domain.model.UserUpdate;
import br.pucrs.constrsw.oauth.domain.util.EmailValidator;

public class UpdateUserService implements UpdateUserUseCase {

    private final UserGateway userGateway;

    public UpdateUserService(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    @Override
    public void execute(String bearer, String id, UserUpdate update) {
        if (update == null || update.isEmpty()) {
            throw new InvalidInputException("No fields to update");
        }
        if (update.getUsername() != null && !EmailValidator.isValid(update.getUsername())) {
            throw new InvalidEmailException(update.getUsername());
        }
        userGateway.update(bearer, id, update);
    }
}
