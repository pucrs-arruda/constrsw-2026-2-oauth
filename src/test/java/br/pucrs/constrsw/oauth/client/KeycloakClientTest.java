package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.AuthTokens;
import br.pucrs.constrsw.oauth.error.InvalidCredentialsException;
import br.pucrs.constrsw.oauth.error.KeycloakCommunicationException;
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
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}"));

        assertThatThrownBy(() -> keycloakClient.authenticate("user@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");
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
