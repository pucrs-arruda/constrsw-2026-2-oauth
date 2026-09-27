package br.pucrs.constrsw.oauth.application.usecase;

import java.util.List;

import br.pucrs.constrsw.oauth.application.port.in.ListRolesUseCase;
import br.pucrs.constrsw.oauth.application.port.out.RoleGateway;
import br.pucrs.constrsw.oauth.domain.model.Role;

public class ListRolesService implements ListRolesUseCase {

    private final RoleGateway roleGateway;

    public ListRolesService(RoleGateway roleGateway) {
        this.roleGateway = roleGateway;
    }

    @Override
    public List<Role> execute(String bearer, Boolean enabled) {
        return roleGateway.list(bearer, enabled);
    }
}
