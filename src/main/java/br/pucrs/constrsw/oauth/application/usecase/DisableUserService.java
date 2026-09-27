package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.DisableUserUseCase;
import br.pucrs.constrsw.oauth.application.port.out.UserGateway;

public class DisableUserService implements DisableUserUseCase {

    private final UserGateway userGateway;

    public DisableUserService(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    @Override
    public void execute(String bearer, String id) {
        userGateway.disable(bearer, id);
    }
}
