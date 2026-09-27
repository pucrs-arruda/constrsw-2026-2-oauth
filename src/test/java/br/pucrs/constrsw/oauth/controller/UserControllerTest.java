package br.pucrs.constrsw.oauth.controller;

import br.pucrs.constrsw.oauth.domain.CreateUserCommand;
import br.pucrs.constrsw.oauth.domain.UpdateUserCommand;
import br.pucrs.constrsw.oauth.domain.User;
import br.pucrs.constrsw.oauth.error.GlobalExceptionHandler;
import br.pucrs.constrsw.oauth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserController.class, GlobalExceptionHandler.class})
class UserControllerTest {

        private static final String AUTHORIZATION = "Bearer user-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void returnsCreatedUserAndDelegatesCreation() throws Exception {
        CreateUserCommand request = new CreateUserCommand(
                "user@example.com", "secret", "First", "Last");
        User response = new User(
                "user-id", "user@example.com", "First", "Last", true);
        when(userService.create(AUTHORIZATION, request)).thenReturn(response);

        mockMvc.perform(post("/users")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "user@example.com",
                                  "password": "secret",
                                  "first-name": "First",
                                  "last-name": "Last"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("user-id"))
                .andExpect(jsonPath("$.username").value("user@example.com"))
                .andExpect(jsonPath("$['first-name']").value("First"))
                .andExpect(jsonPath("$['last-name']").value("Last"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService).create(AUTHORIZATION, request);
    }

    @Test
    void rejectsInvalidCreationRequest() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "not-an-email",
                                  "password": "",
                                  "first-name": "",
                                  "last-name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("400"))
                .andExpect(jsonPath("$.error_source").value("OAuthAPI"));

        verifyNoInteractions(userService);
    }

    @Test
    void returnsUsersFromService() throws Exception {
        when(userService.findAll(AUTHORIZATION)).thenReturn(List.of(
                new User("user-id", "user@example.com", "First", "Last", true)));

        mockMvc.perform(get("/users").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("user-id"))
                .andExpect(jsonPath("$[0]['first-name']").value("First"));

        verify(userService).findAll(AUTHORIZATION);
    }

    @Test
    void returnsUserById() throws Exception {
        when(userService.findById(AUTHORIZATION, "user-id")).thenReturn(
                new User("user-id", "user@example.com", "First", "Last", true));

        mockMvc.perform(get("/users/user-id").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-id"));

        verify(userService).findById(AUTHORIZATION, "user-id");
    }

    @Test
    void updatesUserAndReturnsOk() throws Exception {
        UpdateUserCommand request = new UpdateUserCommand("Updated", "User", false);

        mockMvc.perform(put("/users/user-id")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first-name": "Updated",
                                  "last-name": "User",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(userService).update(AUTHORIZATION, "user-id", request);
    }

    @Test
    void updatesPasswordAndReturnsOk() throws Exception {
        mockMvc.perform(patch("/users/user-id")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"new-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(userService).updatePassword(AUTHORIZATION, "user-id", "new-secret");
    }

    @Test
    void rejectsBlankPasswordUpdate() throws Exception {
        mockMvc.perform(patch("/users/user-id")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("400"));

        verifyNoInteractions(userService);
    }

    @Test
    void disablesUserAndReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/users/user-id").header("Authorization", AUTHORIZATION))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).disable(AUTHORIZATION, "user-id");
    }

}
