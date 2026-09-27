package com.seugrupo.oauth.controller;

import com.seugrupo.oauth.dto.CreateRoleRequest;
import com.seugrupo.oauth.dto.PatchRoleRequest;
import com.seugrupo.oauth.dto.RoleResponse;
import com.seugrupo.oauth.dto.UpdateRoleRequest;
import com.seugrupo.oauth.exception.GlobalExceptionHandler;
import com.seugrupo.oauth.metrics.BusinessMetrics;
import com.seugrupo.oauth.service.KeycloakRoleService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleControllerTest {

    private KeycloakRoleService roleService;
    private MockMvc mockMvc;
    private RoleResponse role;

    @BeforeEach
    void setUp() {
        roleService = mock(KeycloakRoleService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new RoleController(roleService, new BusinessMetrics(new SimpleMeterRegistry())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        role = new RoleResponse("r-1", "teacher", "Docente", false, false, "constrsw");
    }

    @Test
    void postValidoRetorna201ERole() throws Exception {
        when(roleService.create(eq("Bearer access"), any(CreateRoleRequest.class))).thenReturn(role);

        mockMvc.perform(post("/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"teacher\",\"description\":\"Docente\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("r-1"))
                .andExpect(jsonPath("$.name").value("teacher"))
                .andExpect(jsonPath("$.description").value("Docente"))
                .andExpect(jsonPath("$.composite").value(false))
                .andExpect(jsonPath("$.clientRole").value(false))
                .andExpect(jsonPath("$.containerId").value("constrsw"));
    }

    @Test
    void postSemNameRetorna400SemService() throws Exception {
        mockMvc.perform(post("/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Docente\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("OA-400"));

        verify(roleService, never()).create(any(), any());
    }

    @Test
    void getListaRetorna200() throws Exception {
        when(roleService.list("Bearer access")).thenReturn(List.of(role));

        mockMvc.perform(get("/roles").header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("teacher"));
    }

    @Test
    void getPorIdRetorna200() throws Exception {
        when(roleService.getById("Bearer access", "r-1")).thenReturn(role);

        mockMvc.perform(get("/roles/r-1").header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r-1"));
    }

    @Test
    void putRetorna200SemCorpo() throws Exception {
        mockMvc.perform(put("/roles/r-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"professor\",\"description\":\"Docente\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(roleService).update(eq("Bearer access"), eq("r-1"), any(UpdateRoleRequest.class));
    }

    @Test
    void patchVazioRetorna400SemService() throws Exception {
        mockMvc.perform(patch("/roles/r-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("OA-400"));

        verify(roleService, never()).patch(any(), any(), any());
    }

    @Test
    void patchValidoRetorna200SemCorpo() throws Exception {
        mockMvc.perform(patch("/roles/r-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Docente atualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(roleService).patch(eq("Bearer access"), eq("r-1"), any(PatchRoleRequest.class));
    }

    @Test
    void deleteRetorna204SemCorpo() throws Exception {
        mockMvc.perform(delete("/roles/r-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(roleService).delete("Bearer access", "r-1");
    }
}
