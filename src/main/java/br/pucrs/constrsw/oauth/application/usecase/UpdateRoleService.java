package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.UpdateRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.out.RoleGateway;
import br.pucrs.constrsw.oauth.domain.exception.InvalidInputException;
import br.pucrs.constrsw.oauth.domain.model.RoleUpdate;

public class UpdateRoleService implements UpdateRoleUseCase {

    private final RoleGateway roleGateway;

    public UpdateRoleService(RoleGateway roleGateway) {
        this.roleGateway = roleGateway;
    }

    @Override
    public void execute(String bearer, String id, RoleUpdate update) {
        if (update == null || update.isEmpty()) {
            throw new InvalidInputException("No fields to update");
        }
        roleGateway.update(bearer, id, update);
    }
}
