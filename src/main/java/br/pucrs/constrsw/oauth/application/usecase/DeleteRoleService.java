package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.DeleteRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.out.RoleGateway;

public class DeleteRoleService implements DeleteRoleUseCase {

    private final RoleGateway roleGateway;

    public DeleteRoleService(RoleGateway roleGateway) {
        this.roleGateway = roleGateway;
    }

    @Override
    public void execute(String bearer, String id) {
        roleGateway.delete(bearer, id);
    }
}
