package br.pucrs.constrsw.oauth.infrastructure.adapter.out.keycloak;

import static br.pucrs.constrsw.oauth.infrastructure.adapter.out.keycloak.KeycloakErrors.causedBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import br.pucrs.constrsw.oauth.application.port.out.UserGateway;
import br.pucrs.constrsw.oauth.domain.exception.AccessDeniedException;
import br.pucrs.constrsw.oauth.domain.exception.AuthorizationRequiredException;
import br.pucrs.constrsw.oauth.domain.exception.IdentityProviderUnavailableException;
import br.pucrs.constrsw.oauth.domain.exception.InvalidInputException;
import br.pucrs.constrsw.oauth.domain.exception.UserAlreadyExistsException;
import br.pucrs.constrsw.oauth.domain.exception.UpstreamErrorException;
import br.pucrs.constrsw.oauth.domain.exception.UserNotFoundException;
import br.pucrs.constrsw.oauth.domain.model.NewUser;
import br.pucrs.constrsw.oauth.domain.model.User;
import br.pucrs.constrsw.oauth.domain.model.UserUpdate;
import br.pucrs.constrsw.oauth.infrastructure.config.KeycloakProperties;

/**
 * Adapter de saida: implementa {@link UserGateway} contra a Admin REST API do
 * Keycloak ({@code /admin/realms/{realm}/users}), propagando o Bearer do
 * chamador. Erros do Keycloak sao traduzidos em excecoes de dominio.
 */
@Component
@SuppressWarnings({"rawtypes", "unchecked"})
public class KeycloakUserGateway implements UserGateway {

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserGateway.class);

    private final RestTemplate restTemplate;
    private final KeycloakProperties properties;

    public KeycloakUserGateway(RestTemplate restTemplate, KeycloakProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public User create(String bearer, NewUser newUser) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", newUser.getUsername());
        body.put("email", newUser.getUsername());
        body.put("firstName", newUser.getFirstName());
        body.put("lastName", newUser.getLastName());
        body.put("enabled", true);
        body.put("emailVerified", true);

        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "password");
        credential.put("value", newUser.getPassword());
        credential.put("temporary", false);
        body.put("credentials", Collections.singletonList(credential));

        ResponseEntity<Map> response = call(properties.usersEndpoint(), HttpMethod.POST,
                new HttpEntity<>(body, headers(bearer, MediaType.APPLICATION_JSON)));

        HttpStatusCode status = response.getStatusCode();
        if (status.value() == HttpStatus.CREATED.value()) {
            String id = extractIdFromLocation(response.getHeaders().getLocation());
            if (id == null) {
                throw new IdentityProviderUnavailableException(
                        "Keycloak nao devolveu o Location header no POST /users.");
            }
            return new User(id, newUser.getUsername(), newUser.getFirstName(),
                    newUser.getLastName(), true);
        }
        translateError(status, response.getBody(), newUser.getUsername(), null);
        throw new IdentityProviderUnavailableException("unreachable");
    }

    @Override
    public List<User> list(String bearer, Boolean enabled) {
        UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(properties.usersEndpoint())
                .queryParam("briefRepresentation", true)
                .queryParam("first", 0)
                .queryParam("max", 1000);
        if (enabled != null) {
            ub.queryParam("enabled", enabled);
        }

        // Object.class (nao List.class): em erro (401/403/...) o Keycloak devolve um
        // objeto JSON, nao um array - forcar List.class quebraria a deserializacao
        // antes mesmo de chegar ao translateError, virando 500 em vez do codigo certo.
        ResponseEntity<Object> response = call(ub.build(true).toUriString(), HttpMethod.GET,
                new HttpEntity<>(headers(bearer, null)), Object.class);

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            List<Map<String, Object>> raw = (List<Map<String, Object>>) response.getBody();
            List<User> out = new ArrayList<>();
            if (raw != null) {
                for (Map<String, Object> u : raw) {
                    out.add(toUser(u));
                }
            }
            // Fallback: algumas versoes do Keycloak ignoram o filtro enabled
            // com briefRepresentation=true. Filtramos client-side.
            if (enabled != null) {
                out.removeIf(u -> u.isEnabled() != enabled);
            }
            return out;
        }
        translateError(status, response.getBody(), null, null);
        return Collections.emptyList();
    }

    @Override
    public User findById(String bearer, String id) {
        ResponseEntity<Map> response = call(properties.userEndpoint(id), HttpMethod.GET,
                new HttpEntity<>(headers(bearer, null)));

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            Map<String, Object> u = response.getBody();
            if (u == null) throw new UserNotFoundException(id);
            return toUser(u);
        }
        translateError(status, response.getBody(), null, id);
        throw new IdentityProviderUnavailableException("unreachable");
    }

    @Override
    public void update(String bearer, String id, UserUpdate update) {
        Map<String, Object> body = new HashMap<>();
        if (update.getUsername() != null) {
            body.put("username", update.getUsername());
            body.put("email", update.getUsername());
        }
        if (update.getFirstName() != null) body.put("firstName", update.getFirstName());
        if (update.getLastName() != null) body.put("lastName", update.getLastName());
        if (update.getEnabled() != null) body.put("enabled", update.getEnabled());

        if (body.isEmpty()) {
            throw new InvalidInputException("No fields to update");
        }

        ResponseEntity<Map> response = call(properties.userEndpoint(id), HttpMethod.PUT,
                new HttpEntity<>(body, headers(bearer, MediaType.APPLICATION_JSON)));

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) return;
        translateError(status, response.getBody(), update.getUsername(), id);
    }

    @Override
    public void updatePassword(String bearer, String id, String newPassword) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "password");
        body.put("value", newPassword);
        body.put("temporary", false);

        ResponseEntity<Map> response = call(properties.resetPasswordEndpoint(id), HttpMethod.PUT,
                new HttpEntity<>(body, headers(bearer, MediaType.APPLICATION_JSON)));

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) return;
        translateError(status, response.getBody(), null, id);
    }

    @Override
    public void disable(String bearer, String id) {
        // Confirma existencia primeiro para conseguir devolver 404 correto.
        User existing = findById(bearer, id);

        Map<String, Object> body = new HashMap<>();
        body.put("enabled", Boolean.FALSE);

        ResponseEntity<Map> response = call(properties.userEndpoint(existing.getId()),
                HttpMethod.PUT,
                new HttpEntity<>(body, headers(bearer, MediaType.APPLICATION_JSON)));

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) return;
        translateError(status, response.getBody(), null, id);
    }

    // -------------------- helpers --------------------

    private HttpHeaders headers(String bearer, MediaType contentType) {
        if (bearer == null || bearer.isBlank()) {
            throw new AuthorizationRequiredException("Missing bearer token");
        }
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION,
                bearer.startsWith("Bearer ") ? bearer : "Bearer " + bearer);
        if (contentType != null) h.setContentType(contentType);
        h.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return h;
    }

    private String extractIdFromLocation(URI location) {
        if (location == null) return null;
        String path = location.getPath();
        int idx = path.lastIndexOf('/');
        return (idx >= 0 && idx < path.length() - 1) ? path.substring(idx + 1) : null;
    }

    private User toUser(Map<String, Object> u) {
        Object usernameOrEmail = u.getOrDefault("username", u.get("email"));
        Object enabled = u.getOrDefault("enabled", Boolean.TRUE);
        return new User(
                (String) u.get("id"),
                (String) usernameOrEmail,
                (String) u.get("firstName"),
                (String) u.get("lastName"),
                enabled instanceof Boolean ? (Boolean) enabled : Boolean.TRUE);
    }

    private ResponseEntity<Map> call(String url, HttpMethod method, HttpEntity<?> entity) {
        return call(url, method, entity, Map.class);
    }

    private <T> ResponseEntity<T> call(String url, HttpMethod method, HttpEntity<?> entity,
                                       Class<T> respType) {
        try {
            return restTemplate.exchange(url, method, entity, respType);
        } catch (ResourceAccessException ex) {
            log.error("Keycloak Admin API inacessivel em {}: {}", url, ex.getMessage());
            throw new IdentityProviderUnavailableException(
                    "Nao foi possivel contatar o Keycloak em " + url, ex);
        }
    }

    private void translateError(HttpStatusCode status, Object body, String username, String id) {
        int code = status.value();
        String description = extractErrorMessage(body);
        UpstreamErrorException upstream = KeycloakErrors.upstream(code, body);
        if (code == 400) throw causedBy(new InvalidInputException(
                description.isEmpty() ? "Invalid request for Keycloak" : description), upstream);
        if (code == 401) throw causedBy(new AuthorizationRequiredException(
                "Invalid or expired access token"), upstream);
        if (code == 403) throw causedBy(new AccessDeniedException(
                "Access token does not grant permission for this operation"), upstream);
        if (code == 404) throw causedBy(new UserNotFoundException(id != null ? id : ""), upstream);
        if (code == 409) throw causedBy(new UserAlreadyExistsException(username != null ? username : ""), upstream);
        throw new IdentityProviderUnavailableException(
                "Unexpected Keycloak response " + code + ": " + description, upstream);
    }

    private String extractErrorMessage(Object body) {
        if (body instanceof Map<?, ?> m) {
            Object msg = m.get("errorMessage");
            if (msg != null) return msg.toString();
            Object err = m.get("error");
            if (err != null) return err.toString();
        }
        return "";
    }
}
