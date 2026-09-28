package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.AuthTokens;
import br.pucrs.constrsw.oauth.error.InvalidCredentialsException;
import br.pucrs.constrsw.oauth.error.InvalidRefreshTokenException;
import br.pucrs.constrsw.oauth.error.KeycloakCommunicationException;
import br.pucrs.constrsw.oauth.error.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakClientTest {

    private MockRestServiceServer server;
    private KeycloakClient keycloakClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak:8080");
        server = MockRestServiceServer.bindTo(builder).build();
        KeycloakProperties properties = new KeycloakProperties(
                "http://keycloak:8080", "constrsw", "oauth", "client-secret");
        keycloakClient = new KeycloakClient(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void sendsFormUrlEncodedCredentialsAndReturnsTokens() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("client_id", "oauth");
        expectedForm.add("client_secret", "client-secret");
        expectedForm.add("grant_type", "password");
        expectedForm.add("username", "user@example.com");
        expectedForm.add("password", "secret");

        server.expect(once(), requestTo("http://keycloak:8080/realms/constrsw/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("""
                        {
                          "token_type": "Bearer",
                          "access_token": "access-token",
                          "expires_in": 300,
                          "refresh_token": "refresh-token",
                          "refresh_expires_in": 1800
                        }
                        """, MediaType.APPLICATION_JSON));

        AuthTokens response = keycloakClient.authenticate("user@example.com", "secret");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        server.verify();
    }

    @Test
    void mapsInvalidGrantToInvalidCredentials() {
        server.expect(once(), requestTo("http://keycloak:8080/realms/constrsw/protocol/openid-connect/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}"));

        assertThatThrownBy(() -> keycloakClient.authenticate("user@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");
        server.verify();
    }

    @Test
    void refreshesTokensWithTheRefreshTokenGrant() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("client_id", "oauth");
        expectedForm.add("client_secret", "client-secret");
        expectedForm.add("grant_type", "refresh_token");
        expectedForm.add("refresh_token", "old-refresh-token");

        server.expect(once(), requestTo("http://keycloak:8080/realms/constrsw/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("""
                        {
                          "token_type": "Bearer",
                          "access_token": "new-access-token",
                          "expires_in": 300,
                          "refresh_token": "new-refresh-token",
                          "refresh_expires_in": 1800
                        }
                        """, MediaType.APPLICATION_JSON));

        AuthTokens response = keycloakClient.refresh("old-refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        server.verify();
    }

    @Test
    void mapsInvalidRefreshGrantToUnauthorized() {
        server.expect(once(), requestTo("http://keycloak:8080/realms/constrsw/protocol/openid-connect/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));

        assertThatThrownBy(() -> keycloakClient.refresh("expired-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Invalid or expired refresh token");
        server.verify();
    }

    @Test
    void evaluatesAResourcePermissionThroughKeycloakUma() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "urn:ietf:params:oauth:grant-type:uma-ticket");
        expectedForm.add("audience", "oauth");
        expectedForm.add("permission", "/courses");
        expectedForm.add("permission_resource_format", "uri");
        expectedForm.add("response_mode", "decision");

        server.expect(once(), requestTo("http://keycloak:8080/realms/constrsw/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("{\"result\":true}", MediaType.APPLICATION_JSON));

        assertThat(keycloakClient.hasAccess("Bearer access-token", "/courses")).isTrue();
        server.verify();
    }

    @Test
    void deniesResourcePermissionWhenKeycloakReturnsForbidden() {
        server.expect(once(), requestTo("http://keycloak:8080/realms/constrsw/protocol/openid-connect/token"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"access_denied\"}"));

        assertThat(keycloakClient.hasAccess("Bearer access-token", "/courses")).isFalse();
        server.verify();
    }

    @Test
    void rejectsInvalidAccessTokenWhenKeycloakReturnsUnauthorized() {
        server.expect(once(), requestTo("http://keycloak:8080/realms/constrsw/protocol/openid-connect/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_token\"}"));

        assertThatThrownBy(() -> keycloakClient.hasAccess("Bearer invalid-token", "/courses"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid access token");
        server.verify();
    }

    @Test
    void mapsOtherProviderFailuresWithoutLeakingTheResponse() {
        server.expect(once(), requestTo("http://keycloak:8080/realms/constrsw/protocol/openid-connect/token"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"internal\":\"sensitive details\"}"));

        assertThatThrownBy(() -> keycloakClient.authenticate("user@example.com", "secret"))
                .isInstanceOf(KeycloakCommunicationException.class)
                .hasMessage("Unable to authenticate with identity provider");
        server.verify();
    }
}
