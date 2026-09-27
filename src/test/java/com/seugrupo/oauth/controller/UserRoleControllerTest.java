package com.seugrupo.oauth.controller;

import com.seugrupo.oauth.exception.GlobalExceptionHandler;
import com.seugrupo.oauth.exception.OAuthApiException;
import com.seugrupo.oauth.metrics.BusinessMetrics;
import com.seugrupo.oauth.service.KeycloakRoleService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserRoleControllerTest {

    private KeycloakRoleService roleService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        roleService = mock(KeycloakRoleService.class);
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new UserRoleController(roleService, new BusinessMetrics(new SimpleMeterRegistry())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void postAtribuiRoleERetorna204() throws Exception {
        mockMvc.perform(post("/users/u-1/roles/r-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(roleService).assignToUser("Bearer access", "u-1", "r-1");
    }

    @Test
    void deleteDesatribuiRoleERetorna204() throws Exception {
        mockMvc.perform(delete("/users/u-1/roles/r-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(roleService).unassignFromUser("Bearer access", "u-1", "r-1");
    }

    @Test
    void erroDoServicePreservaContrato() throws Exception {
        doThrow(new OAuthApiException(
                HttpStatus.FORBIDDEN,
                "OA-403",
                "OAuthAPI.Roles",
                "Access token não concede permissão."
        )).when(roleService).assignToUser("Bearer access", "u-1", "r-1");

        mockMvc.perform(post("/users/u-1/roles/r-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OA-403"))
                .andExpect(jsonPath("$.error_source").value("OAuthAPI.Roles"));
    }
}
