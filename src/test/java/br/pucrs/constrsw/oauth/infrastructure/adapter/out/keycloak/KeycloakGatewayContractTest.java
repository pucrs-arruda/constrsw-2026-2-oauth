package br.pucrs.constrsw.oauth.infrastructure.adapter.out.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import br.pucrs.constrsw.oauth.domain.exception.UpstreamErrorException;
import br.pucrs.constrsw.oauth.domain.exception.UserAlreadyExistsException;
import br.pucrs.constrsw.oauth.domain.model.AuthTokens;
import br.pucrs.constrsw.oauth.domain.model.Credentials;
import br.pucrs.constrsw.oauth.domain.model.NewRole;
import br.pucrs.constrsw.oauth.domain.model.NewUser;
import br.pucrs.constrsw.oauth.domain.model.Role;
import br.pucrs.constrsw.oauth.domain.model.RoleUpdate;
import br.pucrs.constrsw.oauth.domain.model.User;
import br.pucrs.constrsw.oauth.infrastructure.config.KeycloakProperties;

class KeycloakGatewayContractTest {

  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private KeycloakProperties properties;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
      @Override
      public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
        return false;
      }
    });
    server = MockRestServiceServer.bindTo(restTemplate).build();
    properties = new KeycloakProperties();
    properties.setBaseUrl("http://keycloak:8080");
    properties.setRealm("constrsw");
    properties.setClientId("oauth");
    properties.setClientSecret("client-secret");
  }

  @Test
  void authenticatesAgainstOpenIdTokenEndpoint() {
    server.expect(requestTo("http://keycloak:8080/realms/constrsw/protocol/openid-connect/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("client_id=oauth"),
            org.hamcrest.Matchers.containsString("grant_type=password"),
            org.hamcrest.Matchers.containsString("username=ana%40example.com"))))
        .andRespond(withSuccess(
            "{\"token_type\":\"Bearer\",\"access_token\":\"access\",\"expires_in\":300,\"refresh_token\":\"refresh\",\"refresh_expires_in\":1800}",
            MediaType.APPLICATION_JSON));

    AuthTokens tokens = new KeycloakAuthGateway(restTemplate, properties)
        .authenticate(new Credentials("ana@example.com", "secret"));

    assertEquals("Bearer", tokens.getTokenType());
    assertEquals("access", tokens.getAccessToken());
    assertEquals(300L, tokens.getExpiresIn());
    server.verify();
  }

  @Test
  void createsUserUsingLocationIdAndBearerToken() {
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/users"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(AUTHORIZATION, "Bearer access"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(
            "{\"username\":\"ana@example.com\",\"email\":\"ana@example.com\",\"enabled\":true,\"credentials\":[{\"type\":\"password\",\"value\":\"secret\",\"temporary\":false}]}",
            false))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
            .withStatus(org.springframework.http.HttpStatus.CREATED)
            .location(URI.create("http://keycloak:8080/admin/realms/constrsw/users/u1"))
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}"));

    User user = new KeycloakUserGateway(restTemplate, properties)
        .create("Bearer access", new NewUser("ana@example.com", "secret", "Ana", "Silva"));

    assertEquals("u1", user.getId());
    assertEquals("ana@example.com", user.getUsername());
    assertEquals("Ana", user.getFirstName());
    assertEquals("Silva", user.getLastName());
    assertEquals(true, user.isEnabled());
    server.verify();
  }

  @Test
  void mapsDuplicateUserResponseToDomainConflict() {
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/users"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
            .withStatus(org.springframework.http.HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"errorMessage\":\"User exists\"}"));

    UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class,
        () -> new KeycloakUserGateway(restTemplate, properties)
            .create("access", new NewUser("ana@example.com", "secret", "Ana", "Silva")));

    // O erro original do Keycloak fica preservado como causa (vira item do error_stack)
    UpstreamErrorException upstream = (UpstreamErrorException) ex.getCause();
    assertEquals("Keycloak", upstream.getSource());
    assertEquals(409, upstream.getStatus());
    assertEquals("HTTP 409 - User exists", upstream.getMessage());
    server.verify();
  }

  @Test
  void createsRoleThenReadsItByName() {
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(AUTHORIZATION, "Bearer access"))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
            .withStatus(org.springframework.http.HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}"));
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles/teacher"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(AUTHORIZATION, "Bearer access"))
        .andRespond(withSuccess(
            "{\"id\":\"r1\",\"name\":\"teacher\",\"description\":\"Teaching\",\"attributes\":{\"enabled\":[\"true\"]}}",
            MediaType.APPLICATION_JSON));

    Role role = new KeycloakRoleGateway(restTemplate, properties)
        .create("Bearer access", new NewRole("teacher", "Teaching"));

    assertEquals("r1", role.getId());
    assertEquals("teacher", role.getName());
    assertEquals(true, role.isEnabled());
    server.verify();
  }

  @Test
  void listsRolesWithFullRepresentationToFilterByEnabled() {
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles?briefRepresentation=false"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(
            "[{\"id\":\"r1\",\"name\":\"ativo\",\"attributes\":{\"enabled\":[\"true\"]}},"
                + "{\"id\":\"r2\",\"name\":\"excluido\",\"attributes\":{\"enabled\":[\"false\"]}}]",
            MediaType.APPLICATION_JSON));

    java.util.List<Role> disabled = new KeycloakRoleGateway(restTemplate, properties)
        .list("Bearer access", false);

    assertEquals(1, disabled.size());
    assertEquals("r2", disabled.get(0).getId());
    server.verify();
  }

  private static final String ROLE_WITH_CUSTOM_ATTRIBUTE =
      "{\"id\":\"r1\",\"name\":\"teacher\",\"description\":\"Teaching\","
          + "\"attributes\":{\"enabled\":[\"true\"],\"origem\":[\"sistema-x\"]}}";

  @Test
  void deletesRoleLogicallyKeepingCustomAttributes() {
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles-by-id/r1"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(ROLE_WITH_CUSTOM_ATTRIBUTE, MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles-by-id/r1"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header(AUTHORIZATION, "Bearer access"))
        .andExpect(content().json("{\"id\":\"r1\",\"name\":\"teacher\",\"description\":\"Teaching\","
            + "\"attributes\":{\"enabled\":[\"false\"],\"origem\":[\"sistema-x\"]}}", true))
        .andRespond(withNoContent());

    new KeycloakRoleGateway(restTemplate, properties).delete("Bearer access", "r1");
    server.verify();
  }

  @Test
  void patchRoleMergesOnlyInformedFields() {
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles-by-id/r1"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(ROLE_WITH_CUSTOM_ATTRIBUTE, MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles-by-id/r1"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(content().json("{\"id\":\"r1\",\"name\":\"teacher\",\"description\":\"Nova\","
            + "\"attributes\":{\"enabled\":[\"true\"],\"origem\":[\"sistema-x\"]}}", true))
        .andRespond(withNoContent());

    new KeycloakRoleGateway(restTemplate, properties)
        .update("Bearer access", "r1", new RoleUpdate(null, "Nova", null));
    server.verify();
  }

  @Test
  void putRoleReplacesFieldsButKeepsCustomAttributes() {
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles-by-id/r1"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(ROLE_WITH_CUSTOM_ATTRIBUTE, MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles-by-id/r1"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(content().json("{\"id\":\"r1\",\"name\":\"professor\",\"description\":null,"
            + "\"attributes\":{\"enabled\":[\"true\"],\"origem\":[\"sistema-x\"]}}", true))
        .andRespond(withNoContent());

    new KeycloakRoleGateway(restTemplate, properties)
        .replace("Bearer access", "r1", new RoleUpdate("professor", null, true));
    server.verify();
  }

  @Test
  void assignsRoleWithKeycloakRoleMappingPayload() {
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/roles-by-id/r1"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"id\":\"r1\",\"name\":\"teacher\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://keycloak:8080/admin/realms/constrsw/users/u1/role-mappings/realm"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(AUTHORIZATION, "Bearer access"))
        .andExpect(content().json("[{\"id\":\"r1\",\"name\":\"teacher\"}]"))
        .andRespond(withNoContent());

    new KeycloakRoleGateway(restTemplate, properties).assignToUser("Bearer access", "u1", "r1");
    server.verify();
  }
}
