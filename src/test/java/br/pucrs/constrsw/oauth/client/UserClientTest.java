package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.CreateUserCommand;
import br.pucrs.constrsw.oauth.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserClientTest {

    private MockRestServiceServer server;
    private UserClient userClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak:8080");
        server = MockRestServiceServer.bindTo(builder).build();
        KeycloakProperties properties = new KeycloakProperties(
                "http://keycloak:8080", "constrsw", "oauth", "secret");
        userClient = new UserClient(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void listsUsersUsingServiceAccountToken() {
        server.expect(once(), requestTo("http://keycloak:8080/admin/realms/constrsw/users"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("[{\"id\":\"user-id\",\"username\":\"user@example.com\",\"firstName\":\"First\",\"lastName\":\"Last\",\"enabled\":true}]", MediaType.APPLICATION_JSON));

        User user = userClient.findAll("Bearer user-token").get(0);

        assertThat(user).isEqualTo(new User(
                "user-id", "user@example.com", "First", "Last", true));
        server.verify();
    }

    @Test
    void createsUserAndReadsLocationHeader() {
        server.expect(once(), requestTo("http://keycloak:8080/admin/realms/constrsw/users"))
                .andExpect(method(HttpMethod.POST))
                                                                .andExpect(content().json("""
                                                                                                {
                                                                                                        "username": "user@example.com",
                                                                                                        "email": "user@example.com",
                                                                                                        "firstName": "First",
                                                                                                        "lastName": "Last",
                                                                                                        "enabled": true,
                                                                                                        "credentials": [{
                                                                                                                "type": "password",
                                                                                                                "value": "secret",
                                                                                                                "temporary": false
                                                                                                        }]
                                                                                                }
                                                                                                """))
                                                                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess().header("Location", "http://keycloak:8080/admin/realms/constrsw/users/user-id"));

        User user = userClient.create("Bearer user-token", new CreateUserCommand(
                "user@example.com", "secret", "First", "Last"));

        assertThat(user.id()).isEqualTo("user-id");
        assertThat(user.username()).isEqualTo("user@example.com");
        server.verify();
    }

}
