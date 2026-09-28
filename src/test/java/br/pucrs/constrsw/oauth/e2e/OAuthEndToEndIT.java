package br.pucrs.constrsw.oauth.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OAuthEndToEndIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String API_URL = setting("e2e.base-url", "E2E_BASE_URL", "http://localhost:8181");
    private static final String KEYCLOAK_URL = setting("e2e.keycloak-url", "E2E_KEYCLOAK_URL", "http://localhost:8081");
    private static final String REALM = setting("e2e.realm", "E2E_REALM", "constrsw");
    private static final String ADMIN_USERNAME = setting("e2e.admin-username", "E2E_ADMIN_USERNAME", "admin@pucrs.br");
    private static final String ADMIN_PASSWORD = setting("e2e.admin-password", "E2E_ADMIN_PASSWORD", "a12345678");

    private static String accessToken;
    private static String createdUserId;
    private static String createdRoleId;

    @BeforeAll
    static void authenticate() throws Exception {
        HttpResponse<String> health = request("GET", API_URL + "/health", null, null);
        assertEquals(200, health.statusCode(), "A API OAuth precisa estar ativa para executar o perfil e2e");

        HttpResponse<String> response = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(201, response.statusCode(), "O usuário E2E precisa autenticar e possuir permissões administrativas");

        accessToken = JSON.readTree(response.body()).path("access_token").asText();
        assertFalse(accessToken.isBlank(), "O login deve retornar access_token");
    }

    @AfterAll
    static void removeTestDataFromKeycloak() throws Exception {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        if (createdUserId != null) {
            request("DELETE", KEYCLOAK_URL + "/admin/realms/" + encode(REALM)
                    + "/users/" + encode(createdUserId), null, accessToken);
        }
        if (createdRoleId != null) {
            request("DELETE", KEYCLOAK_URL + "/admin/realms/" + encode(REALM)
                    + "/roles-by-id/" + encode(createdRoleId), null, accessToken);
        }
    }

    @Test
    @Order(1)
    @DisplayName("E2E: login inválido retorna o erro padronizado")
    void invalidLoginReturnsStandardError() throws Exception {
        HttpResponse<String> response = login(ADMIN_USERNAME, "senha-incorreta-e2e");

        assertEquals(401, response.statusCode());
        JsonNode error = JSON.readTree(response.body());
        assertNotNull(error.get("error_code"));
        assertNotNull(error.get("error_description"));
        assertEquals("OAuthAPI", error.path("error_source").asText());
        assertTrue(error.path("error_stack").isArray());
    }

    @Test
    @Order(2)
    @DisplayName("E2E: ciclo completo de usuário, role e atribuição no Keycloak")
    void completeUserAndRoleLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "e2e." + suffix + "@pucrs.br";
        String password = "E2e@" + suffix.substring(0, 12);
        String roleName = "e2e_role_" + suffix;

        HttpResponse<String> createRole = api("POST", "/roles", """
                {"name":"%s","description":"Role criada pelo E2E","enabled":true}
                """.formatted(roleName));
        assertEquals(201, createRole.statusCode(), createRole.body());
        JsonNode role = JSON.readTree(createRole.body());
        createdRoleId = requiredText(role, "id");
        assertEquals(roleName, role.path("name").asText());

        HttpResponse<String> createUser = api("POST", "/users", """
                {
                  "username":"%s",
                  "email":"%s",
                  "first-name":"Teste",
                  "last-name":"E2E",
                  "enabled":true,
                  "password":"%s"
                }
                """.formatted(email, email, password));
        assertEquals(201, createUser.statusCode(), createUser.body());
        JsonNode user = JSON.readTree(createUser.body());
        createdUserId = requiredText(user, "id");
        assertEquals(email, user.path("username").asText());
        assertTrue(user.path("enabled").asBoolean());

        HttpResponse<String> getUser = api("GET", "/users/" + createdUserId, null);
        assertEquals(200, getUser.statusCode(), getUser.body());
        assertEquals(createdUserId, JSON.readTree(getUser.body()).path("id").asText());

        HttpResponse<String> listUsers = api("GET", "/users?enabled=true", null);
        assertEquals(200, listUsers.statusCode(), listUsers.body());
        assertTrue(containsId(JSON.readTree(listUsers.body()), createdUserId));

        HttpResponse<String> updateUser = api("PUT", "/users/" + createdUserId, """
                {"email":"%s","first-name":"Teste Atualizado","last-name":"E2E","enabled":true}
                """.formatted(email));
        assertEquals(200, updateUser.statusCode(), updateUser.body());
        assertTrue(updateUser.body().isBlank());

        HttpResponse<String> updatePassword = api("PATCH", "/users/" + createdUserId, """
                {"password":"%s","temporary":false}
                """.formatted(password + "Nova"));
        assertEquals(200, updatePassword.statusCode(), updatePassword.body());
        assertTrue(updatePassword.body().isBlank());

        HttpResponse<String> assignRole = api("POST",
                "/users/" + createdUserId + "/roles/" + createdRoleId, null);
        assertEquals(204, assignRole.statusCode(), assignRole.body());

        JsonNode userWithRole = JSON.readTree(api("GET", "/users/" + createdUserId, null).body());
        assertTrue(containsText(userWithRole.path("roles"), roleName));

        HttpResponse<String> removeRole = api("DELETE",
                "/users/" + createdUserId + "/roles/" + createdRoleId, null);
        assertEquals(204, removeRole.statusCode(), removeRole.body());

        JsonNode userWithoutRole = JSON.readTree(api("GET", "/users/" + createdUserId, null).body());
        assertFalse(containsText(userWithoutRole.path("roles"), roleName));

        HttpResponse<String> patchRole = api("PATCH", "/roles/" + createdRoleId,
                "{\"description\":\"Role atualizada pelo E2E\",\"enabled\":true}");
        assertEquals(200, patchRole.statusCode(), patchRole.body());
        assertEquals("Role atualizada pelo E2E", JSON.readTree(patchRole.body()).path("description").asText());

        HttpResponse<String> deleteUser = api("DELETE", "/users/" + createdUserId, null);
        assertEquals(204, deleteUser.statusCode(), deleteUser.body());
        JsonNode disabledUser = JSON.readTree(api("GET", "/users/" + createdUserId, null).body());
        assertFalse(disabledUser.path("enabled").asBoolean());

        HttpResponse<String> deleteRole = api("DELETE", "/roles/" + createdRoleId, null);
        assertEquals(204, deleteRole.statusCode(), deleteRole.body());
        JsonNode disabledRole = JSON.readTree(api("GET", "/roles/" + createdRoleId, null).body());
        assertFalse(disabledRole.path("enabled").asBoolean());
    }

    @Test
    @Order(3)
    @DisplayName("E2E: usuário com e-mail inválido retorna 400 no formato exigido")
    void invalidUserReturnsBadRequest() throws Exception {
        HttpResponse<String> response = api("POST", "/users", """
                {"username":"email-invalido","password":"Senha@123"}
                """);

        assertEquals(400, response.statusCode());
        JsonNode error = JSON.readTree(response.body());
        assertEquals("400", error.path("error_code").asText());
        assertEquals("OAuthAPI", error.path("error_source").asText());
        assertTrue(error.path("error_stack").isArray());
    }

    private static HttpResponse<String> login(String username, String password) throws Exception {
        String boundary = "----OAuthE2E" + UUID.randomUUID();
        String body = multipartField(boundary, "username", username)
                + multipartField(boundary, "password", password)
                + "--" + boundary + "--\r\n";

        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL + "/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String multipartField(String boundary, String name, String value) {
        return "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
    }

    private static HttpResponse<String> api(String method, String path, String body) throws Exception {
        return request(method, API_URL + path, body, accessToken);
    }

    private static HttpResponse<String> request(String method, String url, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }

        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        builder.method(method, publisher);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        assertFalse(value.isBlank(), "Campo obrigatório ausente na resposta: " + field);
        return value;
    }

    private static boolean containsId(JsonNode array, String id) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (id.equals(item.path("id").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsText(JsonNode array, String expected) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (expected.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String setting(String property, String environment, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null || value.isBlank() ? defaultValue : value.replaceAll("/+$", "");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
