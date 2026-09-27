package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.UpdatePasswordUseCase;
import br.pucrs.constrsw.oauth.application.port.out.UserGateway;
import br.pucrs.constrsw.oauth.domain.exception.InvalidInputException;

public class UpdatePasswordService implements UpdatePasswordUseCase {

    private final UserGateway userGateway;

    public UpdatePasswordService(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    @Override
    public void execute(String bearer, String id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new InvalidInputException("Field 'password' is required");
        }
        userGateway.updatePassword(bearer, id, newPassword);
    }
}
