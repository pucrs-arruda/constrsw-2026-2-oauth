package br.pucrs.constrsw.oauth.controller;

import br.pucrs.constrsw.oauth.domain.AuthTokens;
import br.pucrs.constrsw.oauth.error.GlobalExceptionHandler;
import br.pucrs.constrsw.oauth.error.InvalidCredentialsException;
import br.pucrs.constrsw.oauth.domain.InvalidLoginRequestException;
import br.pucrs.constrsw.oauth.service.LoginService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({LoginController.class, GlobalExceptionHandler.class})
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @Test
    void returnsCreatedWithTokensForValidCredentials() throws Exception {
        AuthTokens response = new AuthTokens("Bearer", "access-token", 300, "refresh-token", 1800);
        when(loginService.login("user@example.com", "secret")).thenReturn(response);

        mockMvc.perform(multipart("/login")
                        .part(textPart("username", "user@example.com"))
                        .part(textPart("password", "secret")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.access_token").value("access-token"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.refresh_token").value("refresh-token"))
                .andExpect(jsonPath("$.refresh_expires_in").value(1800));
    }

    @Test
    void returnsUnauthorizedForInvalidCredentials() throws Exception {
        when(loginService.login("user@example.com", "wrong"))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(multipart("/login")
                        .part(textPart("username", "user@example.com"))
                        .part(textPart("password", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("401"))
                .andExpect(jsonPath("$.error_description").value("Invalid username or password"))
                .andExpect(jsonPath("$.error_source").value("OAuthAPI"))
                .andExpect(jsonPath("$.error_stack").isArray());
    }

    @Test
    void returnsBadRequestWhenUsernameIsMissing() throws Exception {
        when(loginService.login(null, "secret"))
                .thenThrow(new InvalidLoginRequestException("Username is required"));

        mockMvc.perform(multipart("/login").part(textPart("password", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("400"))
                .andExpect(jsonPath("$.error_description").value("Username is required"));
    }

    @Test
    void returnsBadRequestForWrongContentType() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("400"));

        verifyNoInteractions(loginService);
    }

    private MockPart textPart(String name, String value) {
        MockPart part = new MockPart(name, value.getBytes());
        part.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        return part;
    }
}
