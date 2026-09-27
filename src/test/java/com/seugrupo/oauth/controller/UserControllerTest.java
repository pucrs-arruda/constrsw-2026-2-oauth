package com.seugrupo.oauth.controller;

import com.seugrupo.oauth.dto.CreateUserRequest;
import com.seugrupo.oauth.dto.UpdatePasswordRequest;
import com.seugrupo.oauth.dto.UpdateUserRequest;
import com.seugrupo.oauth.dto.UserResponse;
import com.seugrupo.oauth.exception.GlobalExceptionHandler;
import com.seugrupo.oauth.exception.OAuthApiException;
import com.seugrupo.oauth.metrics.BusinessMetrics;
import com.seugrupo.oauth.service.KeycloakUserService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private KeycloakUserService userService;
    private MockMvc mockMvc;
    private UserResponse user;

    @BeforeEach
    void setUp() {
        userService = mock(KeycloakUserService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new UserController(userService, new BusinessMetrics(new SimpleMeterRegistry())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        user = new UserResponse("u-1", "ana@example.com", "Ana", "Silva", true);
    }

    @Test
    void postValidoRetorna201EUsuarioSemSenha() throws Exception {
        when(userService.create(eq("Bearer access"), any(CreateUserRequest.class))).thenReturn(user);

        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ana@example.com","password":"segredo123",
                                 "first-name":"Ana","last-name":"Silva"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("u-1"))
                .andExpect(jsonPath("$.username").value("ana@example.com"))
                .andExpect(jsonPath("$.first-name").value("Ana"))
                .andExpect(jsonPath("$.last-name").value("Silva"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.firstName").doesNotExist())
                .andExpect(jsonPath("$.lastName").doesNotExist());
    }

    @Test
    void postSemCamposObrigatoriosRetorna400SemService() throws Exception {
        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("OA-400"))
                .andExpect(jsonPath("$.error_source").value("OAuthAPI"))
                .andExpect(jsonPath("$.error_stack").isArray());

        verify(userService, never()).create(any(), any());
    }

    @Test
    void postComJsonQuebradoRetorna400SemService() throws Exception {
        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("OA-400"))
                .andExpect(jsonPath("$.error_description").value("Erro na estrutura dos dados enviados."));

        verify(userService, never()).create(any(), any());
    }

    @Test
    void getListaSemFiltroPassaNullAoService() throws Exception {
        when(userService.list("Bearer access", null)).thenReturn(List.of(user));

        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].first-name").value("Ana"));

        verify(userService).list("Bearer access", null);
    }

    @Test
    void getListaComEnabledFalsePassaFiltro() throws Exception {
        when(userService.list("Bearer access", false)).thenReturn(List.of());

        mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(userService).list("Bearer access", false);
    }

    @Test
    void getComEnabledInvalidoRetorna400SemService() throws Exception {
        mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .param("enabled", "talvez"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("OA-400"));

        verify(userService, never()).list(any(), any());
    }

    @Test
    void getPorIdRetornaUsuario() throws Exception {
        when(userService.getById("Bearer access", "u-1")).thenReturn(user);

        mockMvc.perform(get("/users/u-1").header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u-1"))
                .andExpect(jsonPath("$.first-name").value("Ana"));
    }

    @Test
    void putAtualizaDadosSemCorpo() throws Exception {
        mockMvc.perform(put("/users/u-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ana@example.com","first-name":"Ana",
                                 "last-name":"Souza","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(userService).update(eq("Bearer access"), eq("u-1"), any(UpdateUserRequest.class));
    }

    @Test
    void patchAtualizaSomenteSenhaSemCorpo() throws Exception {
        mockMvc.perform(patch("/users/u-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"novaSenha123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(userService).updatePassword(eq("Bearer access"), eq("u-1"),
                any(UpdatePasswordRequest.class));
    }

    @Test
    void deleteDesabilitaEretorna204() throws Exception {
        mockMvc.perform(delete("/users/u-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).disable("Bearer access", "u-1");
    }

    @Test
    void erroDoServicePreserva404CodigoESource() throws Exception {
        when(userService.getById("Bearer access", "missing")).thenThrow(new OAuthApiException(
                HttpStatus.NOT_FOUND,
                "OA-404",
                "OAuthAPI.Users",
                "Objeto não localizado no Keycloak."
        ));

        mockMvc.perform(get("/users/missing").header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("OA-404"))
                .andExpect(jsonPath("$.error_source").value("OAuthAPI.Users"));
    }
}
