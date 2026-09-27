package com.seugrupo.oauth.controller;

import com.seugrupo.oauth.dto.LoginRequest;
import com.seugrupo.oauth.dto.RefreshTokenRequest;
import com.seugrupo.oauth.dto.TokenResponse;
import com.seugrupo.oauth.exception.GlobalExceptionHandler;
import com.seugrupo.oauth.exception.OAuthApiException;
import com.seugrupo.oauth.metrics.BusinessMetrics;
import com.seugrupo.oauth.service.KeycloakAuthService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private KeycloakAuthService authService;
    private MockMvc mockMvc;
    private TokenResponse tokens;

    @BeforeEach
    void setUp() {
        authService = mock(KeycloakAuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new AuthController(authService, new BusinessMetrics(new SimpleMeterRegistry())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        tokens = new TokenResponse("Bearer", "access", 300, "refresh", 1800);
    }

    @Test
    void loginUrlEncodedRetorna201EContratoSnakeCaseExato() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(tokens);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "lucas@example.com")
                        .param("password", "senha"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.access_token").value("access"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.refresh_token").value("refresh"))
                .andExpect(jsonPath("$.refresh_expires_in").value(1800));
    }

    @Test
    void loginMultipartRetorna201() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(tokens);

        mockMvc.perform(multipart("/login")
                        .param("username", "lucas@example.com")
                        .param("password", "senha"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").value("access"));
    }

    @Test
    void refreshUrlEncodedRetorna200() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(tokens);

        mockMvc.perform(post("/refresh-token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("refresh_token", "refresh-value"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.refresh_token").value("refresh"));
    }

    @Test
    void loginSemUsernameRetorna400SemChamarKeycloak() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("password", "senha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("OA-400"))
                .andExpect(jsonPath("$.error_description").exists())
                .andExpect(jsonPath("$.error_source").value("OAuthAPI"))
                .andExpect(jsonPath("$.error_stack[0].message").exists());

        verify(authService, never()).login(any());
    }

    @Test
    void loginComCampoEmBrancoRetorna400() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .param("username", "   ")
                        .param("password", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_stack[0].message").exists());
    }

    @Test
    void refreshSemTokenRetorna400SemChamarKeycloak() throws Exception {
        mockMvc.perform(post("/refresh-token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("OA-400"))
                .andExpect(jsonPath("$.error_stack[0].message").exists());

        verify(authService, never()).refresh(any());
    }

    @Test
    void credenciaisInvalidasRetornam401NoContratoPadrao() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new OAuthApiException(
                HttpStatus.UNAUTHORIZED,
                "OA-401",
                "OAuthAPI.Auth",
                "Credenciais ou access token inválidos."
        ));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "lucas@example.com")
                        .param("password", "incorreta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("OA-401"))
                .andExpect(jsonPath("$.error_source").value("OAuthAPI.Auth"))
                .andExpect(jsonPath("$.error_stack[0].message").exists());
    }

    @Test
    void refreshInvalidoPreserva400() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class))).thenThrow(new OAuthApiException(
                HttpStatus.BAD_REQUEST,
                "OA-400",
                "OAuthAPI.Auth",
                "Erro na estrutura da chamada ao Keycloak (headers, body etc.)."
        ));

        mockMvc.perform(post("/refresh-token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("refresh_token", "expirado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("OA-400"))
                .andExpect(jsonPath("$.error_source").value("OAuthAPI.Auth"));
    }

    @Test
    void loginComContentTypeNaoSuportadoRetorna400() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"lucas@example.com\",\"password\":\"senha\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("OA-400"))
                .andExpect(jsonPath("$.error_stack[0].message").exists());

        verify(authService, never()).login(any());
    }
}
