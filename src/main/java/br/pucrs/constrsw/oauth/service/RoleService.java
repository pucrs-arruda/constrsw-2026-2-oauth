package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.domain.Role;
import br.pucrs.constrsw.oauth.domain.RoleNotFoundException;
import br.pucrs.constrsw.oauth.port.RoleGateway;
import java.util.List;

public class RoleService {

    private final RoleGateway roleGateway;

    public RoleService(RoleGateway roleGateway) {
        this.roleGateway = roleGateway;
    }

    public Role createRole(String authorization, Role role) {
        return roleGateway.createRole(authorization, role);
    }

    public List<Role> getAllRoles(String authorization) {
        return roleGateway.getAllRoles(authorization);
    }

    public Role getRoleById(String authorization, String id) {
        return roleGateway.getRoleById(authorization, id);
    }

    public Role updateRole(String authorization, String id, Role role) {
        return roleGateway.updateRole(authorization, id, role);
    }

    public Role patchRole(String authorization, String id, Role partial) {
        Role existing = roleGateway.getRoleById(authorization, id);
        return roleGateway.updateRole(authorization, id, existing.merge(partial));
    }

    public void deleteRole(String authorization, String id) {
        Role existing = roleGateway.getRoleById(authorization, id);
        if (existing == null) {
            throw new RoleNotFoundException();
        }
        roleGateway.updateRole(authorization, id, existing.markedDeleted());
    }
}
