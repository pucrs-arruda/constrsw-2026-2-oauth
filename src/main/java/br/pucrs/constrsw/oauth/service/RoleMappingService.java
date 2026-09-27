package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.domain.Role;
import br.pucrs.constrsw.oauth.domain.RoleNotFoundException;
import br.pucrs.constrsw.oauth.port.RoleGateway;
import br.pucrs.constrsw.oauth.port.RoleMappingGateway;

public class RoleMappingService {

    private final RoleGateway roleGateway;
    private final RoleMappingGateway roleMappingGateway;

    public RoleMappingService(RoleGateway roleGateway, RoleMappingGateway roleMappingGateway) {
        this.roleGateway = roleGateway;
        this.roleMappingGateway = roleMappingGateway;
    }

    public void assignRoleToUser(String authorization, String userId, String roleId) {
        Role role = requiredRole(authorization, roleId);
        roleMappingGateway.assignRoleToUser(authorization, userId, role);
    }

    public void removeRoleFromUser(String authorization, String userId, String roleId) {
        Role role = requiredRole(authorization, roleId);
        roleMappingGateway.removeRoleFromUser(authorization, userId, role);
    }

    private Role requiredRole(String authorization, String roleId) {
        Role role = roleGateway.getRoleById(authorization, roleId);
        if (role == null) {
            throw new RoleNotFoundException();
        }
        return role;
    }
}
