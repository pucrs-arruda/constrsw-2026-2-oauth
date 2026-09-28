package br.pucrs.constrsw.oauth.controller;

import br.pucrs.constrsw.oauth.error.GlobalExceptionHandler;
import br.pucrs.constrsw.oauth.service.AccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AccessController.class, GlobalExceptionHandler.class})
class AccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessService accessService;

    @Test
    void returnsOkWhenKeycloakGrantsAccess() throws Exception {
        when(accessService.hasAccess("Bearer token", "/courses")).thenReturn(true);

        mockMvc.perform(get("/access")
                        .header("Authorization", "Bearer token")
                        .param("resource", "/courses"))
                .andExpect(status().isOk());

        verify(accessService).hasAccess("Bearer token", "/courses");
    }

    @Test
    void returnsForbiddenWhenKeycloakDeniesAccess() throws Exception {
        when(accessService.hasAccess("Bearer token", "/courses")).thenReturn(false);

        mockMvc.perform(get("/access")
                        .header("Authorization", "Bearer token")
                        .param("resource", "/courses"))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/access").param("resource", "/courses"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accessService);
    }

    @Test
    void returnsBadRequestWithoutResource() throws Exception {
        mockMvc.perform(get("/access").header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accessService);
    }
}
