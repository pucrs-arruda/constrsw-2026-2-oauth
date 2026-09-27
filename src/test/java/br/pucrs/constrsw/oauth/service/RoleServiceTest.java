package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.domain.Role;
import br.pucrs.constrsw.oauth.domain.RoleNotFoundException;
import br.pucrs.constrsw.oauth.port.RoleGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RoleServiceTest {

    private final RoleGateway gateway = mock(RoleGateway.class);
    private final RoleService service = new RoleService(gateway);

    @Test
    void mergesOnlyProvidedFieldsWhenPatchingRole() {
        Role existing = new Role("role-id", "admin", "Administrator", null);
        Role partial = new Role(null, "editor", null, null);
        Role merged = new Role("role-id", "editor", "Administrator", null);
        when(gateway.getRoleById("Bearer token", "role-id")).thenReturn(existing);
        when(gateway.updateRole("Bearer token", "role-id", merged)).thenReturn(merged);

        assertThat(service.patchRole("Bearer token", "role-id", partial)).isEqualTo(merged);
        verify(gateway).updateRole("Bearer token", "role-id", merged);
    }

    @Test
    void marksRoleDeletedWithoutRemovingItFromKeycloak() {
        Role existing = new Role("role-id", "admin", "Administrator", null);
        when(gateway.getRoleById("Bearer token", "role-id")).thenReturn(existing);

        service.deleteRole("Bearer token", "role-id");

        verify(gateway).updateRole("Bearer token", "role-id", existing.markedDeleted());
    }

    @Test
    void rejectsLogicalDeletionWhenRoleDoesNotExist() {
        assertThatThrownBy(() -> service.deleteRole("Bearer token", "missing"))
                .isInstanceOf(RoleNotFoundException.class)
                .hasMessage("Role not found");
        verify(gateway).getRoleById("Bearer token", "missing");
        verifyNoMoreInteractions(gateway);
    }
}
