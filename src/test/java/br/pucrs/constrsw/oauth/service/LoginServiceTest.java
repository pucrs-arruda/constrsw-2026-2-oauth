package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.port.AuthenticationGateway;
import br.pucrs.constrsw.oauth.domain.InvalidLoginRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LoginServiceTest {

    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final LoginService loginService = new LoginService(authenticationGateway);

    @Test
    void rejectsBlankUsername() {
        assertThatThrownBy(() -> loginService.login("  ", "secret"))
                .isInstanceOf(InvalidLoginRequestException.class)
                .hasMessage("Username is required");

        verifyNoInteractions(authenticationGateway);
    }

    @Test
    void rejectsBlankPassword() {
        assertThatThrownBy(() -> loginService.login("user@example.com", "  "))
                .isInstanceOf(InvalidLoginRequestException.class)
                .hasMessage("Password is required");

        verifyNoInteractions(authenticationGateway);
    }
}
