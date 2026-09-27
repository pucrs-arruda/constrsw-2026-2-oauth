package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.domain.Role;
import br.pucrs.constrsw.oauth.domain.RoleNotFoundException;
import br.pucrs.constrsw.oauth.port.RoleGateway;
import br.pucrs.constrsw.oauth.port.RoleMappingGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoleMappingServiceTest {

    private final RoleGateway roleGateway = mock(RoleGateway.class);
    private final RoleMappingGateway mappingGateway = mock(RoleMappingGateway.class);
    private final RoleMappingService service = new RoleMappingService(roleGateway, mappingGateway);

    @Test
    void assignsExistingRoleToUser() {
        Role role = new Role("role-id", "admin", null, null);
        when(roleGateway.getRoleById("Bearer token", "role-id")).thenReturn(role);

        service.assignRoleToUser("Bearer token", "user-id", "role-id");

        verify(mappingGateway).assignRoleToUser("Bearer token", "user-id", role);
    }

    @Test
    void removesExistingRoleFromUser() {
        Role role = new Role("role-id", "admin", null, null);
        when(roleGateway.getRoleById("Bearer token", "role-id")).thenReturn(role);

        service.removeRoleFromUser("Bearer token", "user-id", "role-id");

        verify(mappingGateway).removeRoleFromUser("Bearer token", "user-id", role);
    }

    @Test
    void rejectsUnknownRoleBeforeCallingMappingGateway() {
        assertThatThrownBy(() -> service.assignRoleToUser("Bearer token", "user-id", "missing"))
                .isInstanceOf(RoleNotFoundException.class)
                .hasMessage("Role not found");
        verifyNoInteractions(mappingGateway);
    }
}
