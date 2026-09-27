package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.domain.CreateUserCommand;
import br.pucrs.constrsw.oauth.domain.UpdateUserCommand;
import br.pucrs.constrsw.oauth.domain.User;
import br.pucrs.constrsw.oauth.port.UserGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final String AUTHORIZATION = "Bearer user-token";

    private final UserGateway userClient = mock(UserGateway.class);
    private final UserService userService = new UserService(userClient);

    @Test
    void delegatesUserCreation() {
        CreateUserCommand request = new CreateUserCommand(
                "user@example.com", "secret", "First", "Last");
        User expected = new User(
                "user-id", "user@example.com", "First", "Last", true);
        when(userClient.create(AUTHORIZATION, request)).thenReturn(expected);

        User response = userService.create(AUTHORIZATION, request);

        assertThat(response).isEqualTo(expected);
        verify(userClient).create(AUTHORIZATION, request);
    }

    @Test
    void delegatesUserListing() {
        List<User> expected = List.of(
                new User("user-id", "user@example.com", "First", "Last", true));
        when(userClient.findAll(AUTHORIZATION)).thenReturn(expected);

        List<User> response = userService.findAll(AUTHORIZATION);

        assertThat(response).isEqualTo(expected);
        verify(userClient).findAll(AUTHORIZATION);
    }

    @Test
    void delegatesFindingUserById() {
        User expected = new User(
                "user-id", "user@example.com", "First", "Last", true);
        when(userClient.findById(AUTHORIZATION, "user-id")).thenReturn(expected);

        User response = userService.findById(AUTHORIZATION, "user-id");

        assertThat(response).isEqualTo(expected);
        verify(userClient).findById(AUTHORIZATION, "user-id");
    }

    @Test
    void delegatesUserUpdate() {
        UpdateUserCommand request = new UpdateUserCommand("Updated", "User", true);

        userService.update(AUTHORIZATION, "user-id", request);

        verify(userClient).update(AUTHORIZATION, "user-id", request);
    }

    @Test
    void delegatesPasswordUpdate() {
        userService.updatePassword(AUTHORIZATION, "user-id", "new-secret");

        verify(userClient).updatePassword(AUTHORIZATION, "user-id", "new-secret");
    }

    @Test
    void delegatesUserDisablement() {
        userService.disable(AUTHORIZATION, "user-id");

        verify(userClient).disable(AUTHORIZATION, "user-id");
    }
}
