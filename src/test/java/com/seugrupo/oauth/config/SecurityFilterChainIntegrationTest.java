package com.seugrupo.oauth.config;

import com.seugrupo.oauth.controller.UserController;
import com.seugrupo.oauth.metrics.BusinessMetrics;
import com.seugrupo.oauth.service.KeycloakUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita a SecurityFilterChain de ponta a ponta (via MockMvc), não apenas os
 * handlers isoladamente como em SecurityConfigTest. Isso cobre uma regressão
 * na ligação entre authenticationEntryPoint/accessDeniedHandler e o filtro
 * real (por exemplo, um AccessDeniedException sendo engolido por um
 * @ExceptionHandler(Exception.class) genérico antes de chegar no Spring
 * Security), que os testes unitários dos handlers não detectariam.
 */
@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
class SecurityFilterChainIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KeycloakUserService userService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private BusinessMetrics businessMetrics;

    @Test
    void requisicaoSemTokenRetorna401ComContratoPadronizado() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.error_code").value("OA-401"))
                .andExpect(jsonPath("$.error_source").value("OAuthAPI.Auth"));
    }

    @Test
    void requisicaoComTokenValidoMasSemPermissaoRetorna403ComContratoPadronizado() throws Exception {
        Jwt jwt = Jwt.withTokenValue("fake-token")
                .header("alg", "none")
                .claim("sub", "testuser")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        given(jwtDecoder.decode(anyString())).willReturn(jwt);
        willThrow(new AccessDeniedException("Usuario sem permissao"))
                .given(userService).list(anyString(), org.mockito.ArgumentMatchers.isNull());

        mockMvc.perform(get("/users").header("Authorization", "Bearer fake-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OA-403"))
                .andExpect(jsonPath("$.error_source").value("OAuthAPI.Auth"));
    }
}
