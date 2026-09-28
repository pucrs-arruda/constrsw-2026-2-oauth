package br.pucrs.constrsw.oauth;

import br.pucrs.constrsw.oauth.domain.User;
import br.pucrs.constrsw.oauth.dto.UserResponse;
import br.pucrs.constrsw.oauth.port.UserGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "keycloak.client-secret=test-client-secret")
class OAuthApiE2ETest {

    private static final String AUTHORIZATION = "Bearer e2e-test-token";

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private UserGateway userGateway;

    @Test
    void listsUsersThroughTheRunningHttpServer() {
        User user = new User("user-id", "user@example.com", "First", "Last", true);
        when(userGateway.findAll(AUTHORIZATION)).thenReturn(List.of(user));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("e2e-test-token");

        ResponseEntity<UserResponse[]> response = restTemplate.exchange(
                "/users", HttpMethod.GET, new HttpEntity<>(headers), UserResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(
                new UserResponse("user-id", "user@example.com", "First", "Last", true));
        verify(userGateway).findAll(AUTHORIZATION);
    }
}
