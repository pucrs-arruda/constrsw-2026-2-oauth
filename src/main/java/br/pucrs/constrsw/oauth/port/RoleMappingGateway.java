package br.pucrs.constrsw.oauth.port;

import br.pucrs.constrsw.oauth.domain.Role;

public interface RoleMappingGateway {
    void assignRoleToUser(String authorization, String userId, Role role);
    void removeRoleFromUser(String authorization, String userId, Role role);
}
