package br.pucrs.constrsw.oauth.controller;

import br.pucrs.constrsw.oauth.domain.Role;
import br.pucrs.constrsw.oauth.domain.RoleNotFoundException;
import br.pucrs.constrsw.oauth.error.GlobalExceptionHandler;
import br.pucrs.constrsw.oauth.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({RoleController.class, GlobalExceptionHandler.class})
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    @Test
    void createsRoleWithBearerToken() throws Exception {
        Role role = new Role("role-id", "admin", "Administrator", null);
        when(roleService.createRole(eq("Bearer token"), any(Role.class))).thenReturn(role);

        mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content("""
                                {"id":"role-id","name":"admin","description":"Administrator"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("role-id"))
                .andExpect(jsonPath("$.name").value("admin"));

        verify(roleService).createRole(eq("Bearer token"), argThat(value ->
            "role-id".equals(value.id()) && "admin".equals(value.name())));
    }

    @Test
    void rejectsMissingAuthorization() throws Exception {
        mockMvc.perform(get("/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("401"));

        verifyNoInteractions(roleService);
    }

    @Test
    void listsRoles() throws Exception {
        when(roleService.getAllRoles("Bearer token")).thenReturn(List.of(
                new Role("role-id", "admin", "Administrator", null)));

        mockMvc.perform(get("/roles").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("admin"));

        verify(roleService).getAllRoles("Bearer token");
    }

    @Test
    void getsRoleById() throws Exception {
        Role role = new Role("role-id", "admin", "Administrator", null);
        when(roleService.getRoleById("Bearer token", "role-id")).thenReturn(role);

        mockMvc.perform(get("/roles/role-id").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("role-id"));

        verify(roleService).getRoleById("Bearer token", "role-id");
    }

    @Test
    void updatesRole() throws Exception {
        Role role = new Role("role-id", "admin", "Updated", null);
        when(roleService.updateRole(eq("Bearer token"), eq("role-id"), any(Role.class))).thenReturn(role);

        mockMvc.perform(put("/roles/role-id")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content("""
                            {"id":"role-id","name":"admin","description":"Updated"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated"));

        verify(roleService).updateRole(eq("Bearer token"), eq("role-id"), argThat(value ->
            "role-id".equals(value.id()) && "Updated".equals(value.description())));
    }

    @Test
    void patchesRole() throws Exception {
        Role partial = new Role(null, "editor", null, null);
        when(roleService.patchRole(eq("Bearer token"), eq("role-id"), any(Role.class))).thenReturn(partial);

        mockMvc.perform(patch("/roles/role-id")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content("""
                            {"name":"editor"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("editor"));

        verify(roleService).patchRole(eq("Bearer token"), eq("role-id"), argThat(value ->
            "editor".equals(value.name())));
    }

    @Test
    void deletesRole() throws Exception {
        mockMvc.perform(delete("/roles/role-id").header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(roleService).deleteRole("Bearer token", "role-id");
    }

    @Test
    void returnsNotFoundWhenRoleIsMissing() throws Exception {
        doThrow(new RoleNotFoundException()).when(roleService).deleteRole("Bearer token", "missing");

        mockMvc.perform(delete("/roles/missing").header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_description").value("Role not found"));
    }
}
