package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.GetUserUseCase;
import br.pucrs.constrsw.oauth.application.port.out.UserGateway;
import br.pucrs.constrsw.oauth.domain.model.User;

public class GetUserService implements GetUserUseCase {

    private final UserGateway userGateway;

    public GetUserService(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    @Override
    public User execute(String bearer, String id) {
        return userGateway.findById(bearer, id);
    }
}
