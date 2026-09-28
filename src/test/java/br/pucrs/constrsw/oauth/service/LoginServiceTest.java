package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.domain.AuthTokens;
import br.pucrs.constrsw.oauth.domain.InvalidLoginRequestException;
import br.pucrs.constrsw.oauth.port.AuthenticationGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void refreshesTokensWithTheProvidedRefreshToken() {
        AuthTokens tokens = new AuthTokens("Bearer", "new-access", 300, "new-refresh", 1800);
        when(authenticationGateway.refresh("refresh-token")).thenReturn(tokens);

        assertThat(loginService.refresh("refresh-token")).isEqualTo(tokens);

        verify(authenticationGateway).refresh("refresh-token");
    }

    @Test
    void rejectsBlankRefreshToken() {
        assertThatThrownBy(() -> loginService.refresh("  "))
                .isInstanceOf(InvalidLoginRequestException.class)
                .hasMessage("Refresh token is required");

        verifyNoInteractions(authenticationGateway);
    }
}
