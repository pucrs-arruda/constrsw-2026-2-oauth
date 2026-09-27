package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RoleMappingClientTest {

    private MockRestServiceServer server;
    private RoleMappingClient client;
    private final Role role = new Role("role-id", "admin", null, null);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak:8080");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RoleMappingClient(builder.build(),
                new KeycloakProperties("http://keycloak:8080", "constrsw", "oauth", "secret"));
    }

    @Test
    void assignsRoleToUser() {
        server.expect(once(), requestTo("http://keycloak:8080/admin/realms/constrsw/users/user-id/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(content().json("[{\"id\":\"role-id\",\"name\":\"admin\"}]"))
                .andRespond(withSuccess());

        client.assignRoleToUser("Bearer token", "user-id", role);

        server.verify();
    }

    @Test
    void removesRoleFromUser() {
        server.expect(once(), requestTo("http://keycloak:8080/admin/realms/constrsw/users/user-id/role-mappings/realm"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(content().json("[{\"id\":\"role-id\",\"name\":\"admin\"}]"))
                .andRespond(withSuccess());

        client.removeRoleFromUser("Bearer token", "user-id", role);

        server.verify();
    }
}
