package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.GetRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.out.RoleGateway;
import br.pucrs.constrsw.oauth.domain.model.Role;

public class GetRoleService implements GetRoleUseCase {

    private final RoleGateway roleGateway;

    public GetRoleService(RoleGateway roleGateway) {
        this.roleGateway = roleGateway;
    }

    @Override
    public Role execute(String bearer, String id) {
        return roleGateway.findById(bearer, id);
    }
}
