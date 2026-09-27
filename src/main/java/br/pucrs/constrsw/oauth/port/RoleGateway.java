package br.pucrs.constrsw.oauth.port;

import br.pucrs.constrsw.oauth.domain.Role;
import java.util.List;

public interface RoleGateway {
    Role createRole(String authorization, Role role);
    List<Role> getAllRoles(String authorization);
    Role getRoleById(String authorization, String id);
    Role updateRole(String authorization, String id, Role role);
}
