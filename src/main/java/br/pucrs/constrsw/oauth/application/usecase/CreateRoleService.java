package br.pucrs.constrsw.oauth.application.usecase;

import br.pucrs.constrsw.oauth.application.port.in.CreateRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.out.RoleGateway;
import br.pucrs.constrsw.oauth.domain.exception.InvalidInputException;
import br.pucrs.constrsw.oauth.domain.model.NewRole;
import br.pucrs.constrsw.oauth.domain.model.Role;

public class CreateRoleService implements CreateRoleUseCase {

    private final RoleGateway roleGateway;

    public CreateRoleService(RoleGateway roleGateway) {
        this.roleGateway = roleGateway;
    }

    @Override
    public Role execute(String bearer, NewRole newRole) {
        if (newRole == null || newRole.getName() == null || newRole.getName().isBlank()) {
            throw new InvalidInputException("name is required");
        }
        return roleGateway.create(bearer, newRole);
    }
}
