package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.Role;
import br.pucrs.constrsw.oauth.error.KeycloakServiceException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RoleClientTest {

    private MockRestServiceServer server;
    private RoleClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak:8080");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RoleClient(builder.build(),
                new KeycloakProperties("http://keycloak:8080", "constrsw", "oauth", "secret"));
    }

    @Test
    void createsRole() {
        server.expect(once(), requestTo("http://keycloak:8080/admin/realms/constrsw/roles"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"role-id\",\"name\":\"admin\"}", MediaType.APPLICATION_JSON));

        Role result = client.createRole("Bearer token", new Role(null, "admin", null, null));

        assertThat(result.id()).isEqualTo("role-id");
        assertThat(result.name()).isEqualTo("admin");
        server.verify();
    }

    @Test
    void listsRoles() {
        server.expect(once(), requestTo("http://keycloak:8080/admin/realms/constrsw/roles"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"role-id\",\"name\":\"admin\"}]", MediaType.APPLICATION_JSON));

        List<Role> result = client.getAllRoles("Bearer token");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("admin");
        server.verify();
    }

    @Test
    void mapsKeycloakErrors() {
        server.expect(once(), requestTo("http://keycloak:8080/admin/realms/constrsw/roles-by-id/missing"))
                .andRespond(withStatus(NOT_FOUND).body("{\"error\":\"not found\"}"));

        assertThatThrownBy(() -> client.getRoleById("Bearer token", "missing"))
                .isInstanceOf(KeycloakServiceException.class)
                .hasMessageContaining("Failed to retrieve role");
        server.verify();
    }

    @Test
    void updatesRoleWhenKeycloakReturnsNoContent() {
        server.expect(once(), requestTo("http://keycloak:8080/admin/realms/constrsw/roles-by-id/role-id"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"id\":\"role-id\",\"name\":\"DELETED_admin\"}"))
                .andRespond(withStatus(NO_CONTENT));

        Role result = client.updateRole("Bearer token", "role-id",
                new Role("role-id", "DELETED_admin", null, null));

        assertThat(result).isNull();
        server.verify();
    }
}
