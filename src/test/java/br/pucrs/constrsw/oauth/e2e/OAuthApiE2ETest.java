package br.pucrs.constrsw.oauth.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Teste fim a fim: exercita a API oauth rodando de verdade (docker compose),
 * que por sua vez fala com o Keycloak real. Nada e simulado.
 *
 * Fica fora do `mvn test` (tag "e2e"). Rodar com a stack no ar:
 *   mvn test -Pe2e
 * ou pelo script scripts/run-all-tests.ps1 / .sh.
 *
 * Todo dado criado usa o prefixo "e2e-" e e excluido fisicamente no final,
 * direto na Admin API do Keycloak (o DELETE da API e apenas logico).
 */
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OAuthApiE2ETest {

  private static final String E2E_EMAIL_DOMAIN = "e2e.constrsw.test";
  private static final String E2E_ROLE_PREFIX = "e2e-role-";

  private final String apiUrl = config("E2E_BASE_URL", "http://localhost:8181");
  private final String keycloakUrl = config("E2E_KEYCLOAK_URL", "http://localhost:8081");
  private final String realm = config("KEYCLOAK_REALM", "constrsw");
  private final String adminUser = config("E2E_ADMIN_USER", "admin@pucrs.br");
  private final String adminPassword = config("E2E_ADMIN_PASSWORD", "a12345678");
  private final String unprivilegedUser = config("E2E_UNPRIVILEGED_USER", "student@pucrs.br");
  private final String unprivilegedPassword = config("E2E_UNPRIVILEGED_PASSWORD", "a12345678");
  private final String keycloakMasterAdmin = config("KEYCLOAK_ADMIN", "admin");
  private final String keycloakMasterPassword = config("KEYCLOAK_ADMIN_PASSWORD", "a12345678");

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final ObjectMapper mapper = new ObjectMapper();

  private final String suffix = UUID.randomUUID().toString().substring(0, 8);
  private final String username = "e2e-" + suffix + "@" + E2E_EMAIL_DOMAIN;
  private final String roleName = E2E_ROLE_PREFIX + suffix;

  private String token;
  private String userId;
  private String roleId;

  @BeforeAll
  void apiMustBeUp() throws Exception {
    HttpResponse<String> login = login(adminUser, adminPassword);
    assertThat(login.statusCode())
        .as("API em %s precisa estar no ar (docker compose up) e aceitar %s", apiUrl, adminUser)
        .isEqualTo(201);
    token = body(login).get("access_token").asText();
  }

  @AfterAll
  void cleanup() throws Exception {
    String master = keycloakMasterToken();
    String admin = keycloakUrl + "/admin/realms/" + realm;

    JsonNode users = body(send(get(admin + "/users?max=1000&search=" + enc(E2E_EMAIL_DOMAIN), master)));
    for (JsonNode u : users) {
      if (u.path("username").asText().endsWith("@" + E2E_EMAIL_DOMAIN)) {
        send(authorized(HttpRequest.newBuilder(URI.create(admin + "/users/" + u.get("id").asText())), master)
            .DELETE().build());
      }
    }
    JsonNode roles = body(send(get(admin + "/roles?max=1000&search=" + enc(E2E_ROLE_PREFIX), master)));
    for (JsonNode r : roles) {
      if (r.path("name").asText().startsWith(E2E_ROLE_PREFIX)) {
        send(authorized(HttpRequest.newBuilder(URI.create(admin + "/roles-by-id/" + r.get("id").asText())), master)
            .DELETE().build());
      }
    }
  }

  // ------------------------------------------------------------------ login

  @Test
  @Order(1)
  void loginReturnsTokens() throws Exception {
    HttpResponse<String> res = login(adminUser, adminPassword);

    assertThat(res.statusCode()).isEqualTo(201);
    JsonNode b = body(res);
    assertThat(b.get("token_type").asText()).isEqualToIgnoringCase("Bearer");
    assertThat(b.get("access_token").asText()).isNotBlank();
    assertThat(b.get("refresh_token").asText()).isNotBlank();
    assertThat(b.get("expires_in").asLong()).isPositive();
    assertThat(b.get("refresh_expires_in").asLong()).isPositive();
  }

  @Test
  @Order(1)
  void loginAlsoAcceptsUrlEncodedForm() throws Exception {
    HttpResponse<String> res = send(HttpRequest.newBuilder(URI.create(apiUrl + "/login"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(
            "username=" + enc(adminUser) + "&password=" + enc(adminPassword)))
        .build());

    assertThat(res.statusCode()).isEqualTo(201);
    assertThat(body(res).get("access_token").asText()).isNotBlank();
  }

  @Test
  @Order(2)
  void loginWithWrongPasswordReturns401() throws Exception {
    HttpResponse<String> res = login(adminUser, "senha-errada");

    assertErrorEnvelope(res, 401);
  }

  @Test
  @Order(3)
  void loginWithoutPasswordReturns400() throws Exception {
    HttpResponse<String> res = send(multipart(apiUrl + "/login", Map.of("username", adminUser)));

    assertErrorEnvelope(res, 400);
  }

  @Test
  @Order(4)
  void protectedRouteWithoutTokenReturns401() throws Exception {
    assertErrorEnvelope(send(HttpRequest.newBuilder(URI.create(apiUrl + "/users")).GET().build()), 401);
    assertErrorEnvelope(send(get(apiUrl + "/users", "token-invalido")), 401);
  }

  // ------------------------------------------------------------------ users

  @Test
  @Order(10)
  void createUserWithInvalidEmailReturns400() throws Exception {
    HttpResponse<String> res = send(postJson(apiUrl + "/users", Map.of(
        "username", "nao-eh-email", "password", "a12345678",
        "first-name", "E2E", "last-name", "Invalido")));

    assertErrorEnvelope(res, 400);
  }

  @Test
  @Order(11)
  void createUser() throws Exception {
    HttpResponse<String> res = send(postJson(apiUrl + "/users", Map.of(
        "username", username, "password", "senha-inicial",
        "first-name", "E2E", "last-name", "Original")));

    assertThat(res.statusCode()).isEqualTo(201);
    JsonNode b = body(res);
    userId = b.get("id").asText();
    assertThat(userId).isNotBlank();
    assertThat(b.get("username").asText()).isEqualTo(username);
    assertThat(b.get("first-name").asText()).isEqualTo("E2E");
    assertThat(b.get("last-name").asText()).isEqualTo("Original");
    assertThat(b.get("enabled").asBoolean()).isTrue();
  }

  @Test
  @Order(12)
  void createDuplicateUserReturns409() throws Exception {
    assumeTrue(userId != null);
    HttpResponse<String> res = send(postJson(apiUrl + "/users", Map.of(
        "username", username, "password", "outra", "first-name", "E2E", "last-name", "Dup")));

    assertErrorEnvelope(res, 409);
    // error_stack: erro original do Keycloak primeiro, erro final da OAuthAPI por ultimo
    JsonNode stack = body(res).get("error_stack");
    assertThat(stack.size()).isEqualTo(2);
    assertThat(stack.get(0).get("source").asText()).isEqualTo("Keycloak");
    assertThat(stack.get(0).get("message").asText()).startsWith("HTTP 409");
    assertThat(stack.get(1).get("source").asText()).isEqualTo("OAuthAPI");
    assertThat(stack.get(1).get("type").asText()).isEqualTo("UserAlreadyExistsException");
  }

  @Test
  @Order(13)
  void unprivilegedUserCannotCreateUsers() throws Exception {
    HttpResponse<String> login = login(unprivilegedUser, unprivilegedPassword);
    assumeTrue(login.statusCode() == 201, "usuario sem privilegios nao disponivel no realm");
    String weakToken = body(login).get("access_token").asText();

    HttpResponse<String> res = send(authorized(HttpRequest.newBuilder(URI.create(apiUrl + "/users")), weakToken)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
            "username", "e2e-proibido-" + suffix + "@" + E2E_EMAIL_DOMAIN, "password", "x",
            "first-name", "E2E", "last-name", "Proibido"))))
        .build());

    assertErrorEnvelope(res, 403);
  }

  @Test
  @Order(14)
  void listUsersContainsCreatedUser() throws Exception {
    assumeTrue(userId != null);
    HttpResponse<String> all = send(get(apiUrl + "/users", token));
    HttpResponse<String> enabledOnly = send(get(apiUrl + "/users?enabled=true", token));

    assertThat(all.statusCode()).isEqualTo(200);
    assertThat(ids(body(all))).contains(userId);
    assertThat(enabledOnly.statusCode()).isEqualTo(200);
    assertThat(ids(body(enabledOnly))).contains(userId);
    body(enabledOnly).forEach(u -> assertThat(u.get("enabled").asBoolean()).isTrue());
  }

  @Test
  @Order(15)
  void getUserById() throws Exception {
    assumeTrue(userId != null);
    HttpResponse<String> res = send(get(apiUrl + "/users/" + userId, token));

    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(body(res).get("username").asText()).isEqualTo(username);
  }

  @Test
  @Order(16)
  void getUnknownUserReturns404() throws Exception {
    assertErrorEnvelope(send(get(apiUrl + "/users/" + UUID.randomUUID(), token)), 404);
  }

  @Test
  @Order(17)
  void updateUser() throws Exception {
    assumeTrue(userId != null);
    HttpResponse<String> res = send(json(apiUrl + "/users/" + userId, "PUT",
        Map.of("username", username, "first-name", "E2E", "last-name", "Atualizado")));

    assertThat(res.statusCode()).isEqualTo(200);
    JsonNode after = body(send(get(apiUrl + "/users/" + userId, token)));
    assertThat(after.get("last-name").asText()).isEqualTo("Atualizado");
  }

  @Test
  @Order(18)
  void updateUnknownUserReturns404() throws Exception {
    HttpResponse<String> res = send(json(apiUrl + "/users/" + UUID.randomUUID(), "PUT",
        Map.of("first-name", "Ninguem")));

    assertErrorEnvelope(res, 404);
  }

  @Test
  @Order(19)
  void changePasswordAllowsLoginWithNewPassword() throws Exception {
    assumeTrue(userId != null);
    HttpResponse<String> res = send(json(apiUrl + "/users/" + userId, "PATCH",
        Map.of("password", "senha-nova-e2e")));

    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(login(username, "senha-nova-e2e").statusCode()).isEqualTo(201);
    assertThat(login(username, "senha-inicial").statusCode()).isEqualTo(401);
  }

  // ------------------------------------------------------------------ roles

  @Test
  @Order(30)
  void createRole() throws Exception {
    HttpResponse<String> res = send(postJson(apiUrl + "/roles",
        Map.of("name", roleName, "description", "role criado pelo teste e2e")));

    assertThat(res.statusCode()).isEqualTo(201);
    JsonNode b = body(res);
    roleId = b.get("id").asText();
    assertThat(roleId).isNotBlank();
    assertThat(b.get("name").asText()).isEqualTo(roleName);
    assertThat(b.get("enabled").asBoolean()).isTrue();
  }

  @Test
  @Order(31)
  void createDuplicateRoleReturns409() throws Exception {
    assumeTrue(roleId != null);
    assertErrorEnvelope(send(postJson(apiUrl + "/roles", Map.of("name", roleName))), 409);
  }

  @Test
  @Order(32)
  void createRoleWithoutNameReturns400() throws Exception {
    assertErrorEnvelope(send(postJson(apiUrl + "/roles", Map.of("description", "sem nome"))), 400);
  }

  @Test
  @Order(33)
  void listAndGetRole() throws Exception {
    assumeTrue(roleId != null);
    HttpResponse<String> all = send(get(apiUrl + "/roles", token));
    HttpResponse<String> one = send(get(apiUrl + "/roles/" + roleId, token));

    assertThat(all.statusCode()).isEqualTo(200);
    assertThat(ids(body(all))).contains(roleId);
    assertThat(one.statusCode()).isEqualTo(200);
    assertThat(body(one).get("name").asText()).isEqualTo(roleName);
  }

  @Test
  @Order(34)
  void getUnknownRoleReturns404() throws Exception {
    assertErrorEnvelope(send(get(apiUrl + "/roles/" + UUID.randomUUID(), token)), 404);
  }

  @Test
  @Order(35)
  void patchRoleChangesOnlySentFieldsAndKeepsCustomAttributes() throws Exception {
    assumeTrue(roleId != null);
    addRoleAttributeInKeycloak(roleId, "origem", "e2e");

    HttpResponse<String> res = send(json(apiUrl + "/roles/" + roleId, "PATCH",
        Map.of("description", "descricao via PATCH")));

    assertThat(res.statusCode()).isEqualTo(200);
    JsonNode after = body(send(get(apiUrl + "/roles/" + roleId, token)));
    assertThat(after.get("description").asText()).isEqualTo("descricao via PATCH");
    assertThat(after.get("name").asText()).isEqualTo(roleName);
    assertThat(roleAttributesInKeycloak(roleId).path("origem").path(0).asText()).isEqualTo("e2e");
  }

  @Test
  @Order(36)
  void putRoleReplacesWholeRoleAndKeepsCustomAttributes() throws Exception {
    assumeTrue(roleId != null);
    // Sem description e sem enabled: o PUT substitui, entao a descricao e apagada.
    HttpResponse<String> res = send(json(apiUrl + "/roles/" + roleId, "PUT", Map.of("name", roleName)));

    assertThat(res.statusCode()).isEqualTo(200);
    JsonNode after = body(send(get(apiUrl + "/roles/" + roleId, token)));
    assertThat(after.get("name").asText()).isEqualTo(roleName);
    assertThat(after.path("description").asText("")).isEmpty();
    assertThat(after.get("enabled").asBoolean()).isTrue();
    assertThat(roleAttributesInKeycloak(roleId).path("origem").path(0).asText()).isEqualTo("e2e");
  }

  @Test
  @Order(36)
  void putRoleWithoutNameReturns400() throws Exception {
    assumeTrue(roleId != null);
    assertErrorEnvelope(send(json(apiUrl + "/roles/" + roleId, "PUT",
        Map.of("description", "sem nome"))), 400);
  }

  @Test
  @Order(37)
  void attachRoleToUser() throws Exception {
    assumeTrue(roleId != null && userId != null);
    HttpResponse<String> res = send(authorized(HttpRequest.newBuilder(
        URI.create(apiUrl + "/roles/" + roleId + "/users/" + userId)), token)
        .POST(HttpRequest.BodyPublishers.noBody()).build());

    assertThat(res.statusCode()).isEqualTo(204);
    assertThat(realmRolesOfUser(userId)).contains(roleName);
  }

  @Test
  @Order(38)
  void detachRoleFromUser() throws Exception {
    assumeTrue(roleId != null && userId != null);
    HttpResponse<String> res = send(authorized(HttpRequest.newBuilder(
        URI.create(apiUrl + "/roles/" + roleId + "/users/" + userId)), token)
        .DELETE().build());

    assertThat(res.statusCode()).isEqualTo(204);
    assertThat(realmRolesOfUser(userId)).doesNotContain(roleName);
  }

  @Test
  @Order(39)
  void deleteRoleIsLogical() throws Exception {
    assumeTrue(roleId != null);
    HttpResponse<String> res = send(authorized(HttpRequest.newBuilder(
        URI.create(apiUrl + "/roles/" + roleId)), token).DELETE().build());

    assertThat(res.statusCode()).isEqualTo(204);
    JsonNode after = body(send(get(apiUrl + "/roles/" + roleId, token)));
    assertThat(after.get("enabled").asBoolean()).isFalse();
    assertThat(ids(body(send(get(apiUrl + "/roles?enabled=false", token))))).contains(roleId);
  }

  // ------------------------------------------------------------------ delete user (por ultimo)

  @Test
  @Order(50)
  void deleteUserIsLogical() throws Exception {
    assumeTrue(userId != null);
    HttpResponse<String> res = send(authorized(HttpRequest.newBuilder(
        URI.create(apiUrl + "/users/" + userId)), token).DELETE().build());

    assertThat(res.statusCode()).isEqualTo(204);
    assertThat(body(send(get(apiUrl + "/users/" + userId, token))).get("enabled").asBoolean()).isFalse();
    assertThat(ids(body(send(get(apiUrl + "/users?enabled=false", token))))).contains(userId);
    assertThat(login(username, "senha-nova-e2e").statusCode())
        .as("usuario desabilitado nao pode logar").isEqualTo(401);
  }

  @Test
  @Order(51)
  void deleteUnknownUserReturns404() throws Exception {
    HttpResponse<String> res = send(authorized(HttpRequest.newBuilder(
        URI.create(apiUrl + "/users/" + UUID.randomUUID())), token).DELETE().build());

    assertErrorEnvelope(res, 404);
  }

  // ------------------------------------------------------------------ helpers

  private void assertErrorEnvelope(HttpResponse<String> res, int expectedStatus) throws IOException {
    assertThat(res.statusCode()).as("status HTTP (body: %s)", res.body()).isEqualTo(expectedStatus);
    JsonNode b = body(res);
    assertThat(b.get("error_code").asText()).isEqualTo(String.valueOf(expectedStatus));
    assertThat(b.get("error_description").asText()).isNotBlank();
    assertThat(b.get("error_source").asText()).isEqualTo("OAuthAPI");
    assertThat(b.get("error_stack").isArray()).isTrue();
  }

  private HttpResponse<String> login(String user, String password) throws Exception {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("username", user);
    fields.put("password", password);
    return send(multipart(apiUrl + "/login", fields));
  }

  private HttpRequest multipart(String url, Map<String, String> fields) {
    String boundary = "----e2e" + UUID.randomUUID();
    String payload = fields.entrySet().stream()
        .map(e -> "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + e.getKey()
            + "\"\r\n\r\n" + e.getValue() + "\r\n")
        .collect(Collectors.joining()) + "--" + boundary + "--\r\n";
    return HttpRequest.newBuilder(URI.create(url))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
  }

  private HttpRequest get(String url, String bearer) {
    return authorized(HttpRequest.newBuilder(URI.create(url)), bearer).GET().build();
  }

  private HttpRequest postJson(String url, Object payload) throws IOException {
    return json(url, "POST", payload);
  }

  private HttpRequest json(String url, String method, Object payload) throws IOException {
    return authorized(HttpRequest.newBuilder(URI.create(url)), token)
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
        .build();
  }

  private HttpRequest.Builder authorized(HttpRequest.Builder builder, String bearer) {
    return builder.timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + bearer);
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode body(HttpResponse<String> res) throws IOException {
    return mapper.readTree(res.body() == null || res.body().isBlank() ? "{}" : res.body());
  }

  private static java.util.List<String> ids(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false).map(n -> n.get("id").asText()).toList();
  }

  /** Confere a atribuicao direto no Keycloak (a API nao expoe GET de roles do usuario). */
  private java.util.List<String> realmRolesOfUser(String id) throws Exception {
    JsonNode mappings = body(send(get(
        keycloakUrl + "/admin/realms/" + realm + "/users/" + id + "/role-mappings/realm", keycloakMasterToken())));
    return StreamSupport.stream(mappings.spliterator(), false).map(n -> n.get("name").asText()).toList();
  }

  /** Atributos da role como estao gravados no Keycloak (a API nao os expoe). */
  private JsonNode roleAttributesInKeycloak(String id) throws Exception {
    return body(send(get(keycloakUrl + "/admin/realms/" + realm + "/roles-by-id/" + id, keycloakMasterToken())))
        .path("attributes");
  }

  /** Grava um atributo customizado direto no Keycloak, simulando um que a API nao gerencia. */
  private void addRoleAttributeInKeycloak(String id, String key, String value) throws Exception {
    String master = keycloakMasterToken();
    String url = keycloakUrl + "/admin/realms/" + realm + "/roles-by-id/" + id;
    com.fasterxml.jackson.databind.node.ObjectNode role =
        (com.fasterxml.jackson.databind.node.ObjectNode) body(send(get(url, master)));
    role.withObject("/attributes").putArray(key).add(value);
    HttpResponse<String> res = send(authorized(HttpRequest.newBuilder(URI.create(url)), master)
        .header("Content-Type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(role)))
        .build());
    assertThat(res.statusCode()).as("PUT da role no Keycloak (body: %s)", res.body()).isEqualTo(204);
  }

  private String keycloakMasterToken() throws Exception {
    String form = "grant_type=password&client_id=admin-cli&username=" + enc(keycloakMasterAdmin)
        + "&password=" + enc(keycloakMasterPassword);
    HttpResponse<String> res = send(HttpRequest.newBuilder(
        URI.create(keycloakUrl + "/realms/master/protocol/openid-connect/token"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form))
        .build());
    assertThat(res.statusCode()).as("login do admin master no Keycloak em %s", keycloakUrl).isEqualTo(200);
    return body(res).get("access_token").asText();
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String config(String key, String fallback) {
    String value = System.getProperty(key, System.getenv(key));
    return value == null || value.isBlank() ? fallback : value;
  }
}
