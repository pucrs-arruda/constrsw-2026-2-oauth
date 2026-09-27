package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.CreateUserCommand;
import br.pucrs.constrsw.oauth.domain.UpdateUserCommand;
import br.pucrs.constrsw.oauth.domain.User;
import br.pucrs.constrsw.oauth.error.KeycloakServiceException;
import br.pucrs.constrsw.oauth.port.UserGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class UserClient implements UserGateway {

    private final RestClient restClient;
    private final KeycloakProperties properties;
    private final ObjectMapper objectMapper;

    public UserClient(RestClient restClient, KeycloakProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public User create(String authorization, CreateUserCommand request) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("username", request.username())
            .put("email", request.username())
                .put("firstName", request.firstName())
                .put("lastName", request.lastName())
                .put("enabled", true);
        body.set("credentials", objectMapper.valueToTree(List.of(Map.of(
                "type", "password",
                "value", request.password(),
                "temporary", false))));

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/admin/realms/{realm}/users", properties.realm())
                    .headers(headers -> bearer(headers, authorization))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            String id = userIdFromLocation(response.getHeaders());
            return new User(id, request.username(), request.firstName(), request.lastName(), true);
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to create user");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public List<User> findAll(String authorization) {
        try {
            List<JsonNode> users = restClient.get()
                    .uri("/admin/realms/{realm}/users", properties.realm())
                    .headers(headers -> bearer(headers, authorization))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<JsonNode>>() {});
            if (users == null) {
                return List.of();
            }
            return users.stream().map(this::toUser).toList();
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to retrieve users");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public User findById(String authorization, String id) {
        try {
            JsonNode user = restClient.get()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), id)
                    .headers(headers -> bearer(headers, authorization))
                    .retrieve()
                    .body(JsonNode.class);
            return toUser(user);
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to retrieve user");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void update(String authorization, String id, UpdateUserCommand request) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("firstName", request.firstName())
                .put("lastName", request.lastName())
                .put("enabled", request.enabled());
        updateUser(authorization, id, body, "Failed to update user");
    }

    @Override
    public void updatePassword(String authorization, String id, String password) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("type", "password")
                .put("value", password)
                .put("temporary", false);
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}/reset-password", properties.realm(), id)
                    .headers(headers -> bearer(headers, authorization))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to update user password");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void disable(String authorization, String id) {
        ObjectNode body = objectMapper.createObjectNode().put("enabled", false);
        updateUser(authorization, id, body, "Failed to disable user");
    }

    private void updateUser(String authorization, String id, ObjectNode body, String operation) {
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), id)
                    .headers(headers -> bearer(headers, authorization))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, operation);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private User toUser(JsonNode user) {
        return new User(
                user.path("id").asText(),
                user.path("username").asText(),
                user.path("firstName").asText(),
                user.path("lastName").asText(),
                user.path("enabled").asBoolean());
    }

    private String userIdFromLocation(HttpHeaders headers) {
        String location = headers.getFirst(HttpHeaders.LOCATION);
        if (location == null || location.isBlank()) {
            throw new KeycloakServiceException(HttpStatus.BAD_GATEWAY.value(),
                    "Identity provider did not return the created user id", null);
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private void bearer(HttpHeaders headers, String token) {
        headers.set(HttpHeaders.AUTHORIZATION, token);
    }

    private KeycloakServiceException keycloakException(RestClientResponseException exception, String operation) {
        return new KeycloakServiceException(exception.getStatusCode().value(),
                operation + ": " + exception.getMessage(), exception.getResponseBodyAsString());
    }

    private KeycloakServiceException unavailable(RestClientException exception) {
        return new KeycloakServiceException(HttpStatus.BAD_GATEWAY.value(),
                "Identity provider is unavailable", exception.getMessage());
    }
}
