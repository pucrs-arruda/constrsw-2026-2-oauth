package br.pucrs.constrsw.oauth.infrastructure.adapter.out.keycloak;

import static br.pucrs.constrsw.oauth.infrastructure.adapter.out.keycloak.KeycloakErrors.causedBy;

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

import br.pucrs.constrsw.oauth.application.port.out.RoleGateway;
import br.pucrs.constrsw.oauth.domain.exception.AccessDeniedException;
import br.pucrs.constrsw.oauth.domain.exception.AuthorizationRequiredException;
import br.pucrs.constrsw.oauth.domain.exception.IdentityProviderUnavailableException;
import br.pucrs.constrsw.oauth.domain.exception.InvalidInputException;
import br.pucrs.constrsw.oauth.domain.exception.RoleAlreadyExistsException;
import br.pucrs.constrsw.oauth.domain.exception.RoleNotFoundException;
import br.pucrs.constrsw.oauth.domain.exception.UpstreamErrorException;
import br.pucrs.constrsw.oauth.domain.exception.UserNotFoundException;
import br.pucrs.constrsw.oauth.domain.model.NewRole;
import br.pucrs.constrsw.oauth.domain.model.Role;
import br.pucrs.constrsw.oauth.domain.model.RoleUpdate;
import br.pucrs.constrsw.oauth.infrastructure.config.KeycloakProperties;

/**
 * Adapter de saida: implementa {@link RoleGateway} contra a Admin REST API do
 * Keycloak ({@code /admin/realms/{realm}/roles} e {@code roles-by-id}),
 * propagando o Bearer do chamador. Erros do Keycloak sao traduzidos em
 * excecoes de dominio.
 *
 * Keycloak nao possui um campo nativo "enabled" para roles. A exclusao
 * logica e simulada gravando o atributo customizado {@code attributes.enabled}
 * (RoleRepresentation.attributes e um Map<String, List<String>>).
 */
@Component
@SuppressWarnings({"rawtypes", "unchecked"})
public class KeycloakRoleGateway implements RoleGateway {

    private static final Logger log = LoggerFactory.getLogger(KeycloakRoleGateway.class);

    private final RestTemplate restTemplate;
    private final KeycloakProperties properties;

    public KeycloakRoleGateway(RestTemplate restTemplate, KeycloakProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public Role create(String bearer, NewRole newRole) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", newRole.getName());
        body.put("description", newRole.getDescription());

        ResponseEntity<Map> response = call(properties.rolesEndpoint(), HttpMethod.POST,
                new HttpEntity<>(body, headers(bearer, MediaType.APPLICATION_JSON)));

        HttpStatusCode status = response.getStatusCode();
        if (status.value() == HttpStatus.CREATED.value()) {
            // Keycloak nao devolve Location no POST /roles: buscamos pelo nome.
            return findByName(bearer, newRole.getName());
        }
        translateError(status, response.getBody(), newRole.getName(), null);
        throw new IdentityProviderUnavailableException("unreachable");
    }

    @Override
    public List<Role> list(String bearer, Boolean enabled) {
        // Object.class (nao List.class): em erro (401/403/...) o Keycloak devolve um
        // objeto JSON, nao um array - forcar List.class quebraria a deserializacao
        // antes mesmo de chegar ao translateError, virando 500 em vez do codigo certo.
        // briefRepresentation=false: sem isso o Keycloak omite "attributes" na listagem e
        // todo role pareceria enabled=true (a exclusao logica vive em attributes.enabled).
        ResponseEntity<Object> response = call(properties.rolesEndpoint() + "?briefRepresentation=false",
                HttpMethod.GET, new HttpEntity<>(headers(bearer, null)), Object.class);

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            List<Map<String, Object>> raw = (List<Map<String, Object>>) response.getBody();
            List<Role> out = new ArrayList<>();
            if (raw != null) {
                for (Map<String, Object> r : raw) {
                    out.add(toRole(r));
                }
            }
            if (enabled != null) {
                out.removeIf(r -> r.isEnabled() != enabled);
            }
            return out;
        }
        translateError(status, response.getBody(), null, null);
        return Collections.emptyList();
    }

    @Override
    public Role findById(String bearer, String id) {
        return toRole(findRawById(bearer, id));
    }

    @Override
    public void update(String bearer, String id, RoleUpdate update) {
        // Merge: campos nao informados mantem o valor atual.
        Map<String, Object> current = findRawById(bearer, id);
        Role currentRole = toRole(current);
        write(bearer, id, current,
                update.getName() != null ? update.getName() : currentRole.getName(),
                update.getDescription() != null ? update.getDescription() : currentRole.getDescription(),
                update.getEnabled() != null ? update.getEnabled() : currentRole.isEnabled());
    }

    @Override
    public void replace(String bearer, String id, RoleUpdate replacement) {
        // Substituicao: os campos do recurso vem todos da requisicao, inclusive
        // description null (apaga a descricao).
        Map<String, Object> current = findRawById(bearer, id);
        write(bearer, id, current, replacement.getName(), replacement.getDescription(),
                !Boolean.FALSE.equals(replacement.getEnabled()));
    }

    @Override
    public void delete(String bearer, String id) {
        // Exclusao logica: mesma rota de update, forcando enabled=false.
        update(bearer, id, new RoleUpdate(null, null, Boolean.FALSE));
    }

    @Override
    public void assignToUser(String bearer, String userId, String roleId) {
        Role role = findById(bearer, roleId);

        List<Map<String, Object>> body = Collections.singletonList(roleMappingPayload(role));

        ResponseEntity<Map> response = call(properties.userRealmRoleMappingsEndpoint(userId),
                HttpMethod.POST, new HttpEntity<>(body, headers(bearer, MediaType.APPLICATION_JSON)));

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) return;
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            throw causedBy(new UserNotFoundException(userId),
                    KeycloakErrors.upstream(status.value(), response.getBody()));
        }
        translateError(status, response.getBody(), role.getName(), roleId);
    }

    @Override
    public void unassignFromUser(String bearer, String userId, String roleId) {
        Role role = findById(bearer, roleId);

        List<Map<String, Object>> body = Collections.singletonList(roleMappingPayload(role));
        HttpEntity<List<Map<String, Object>>> entity =
                new HttpEntity<>(body, headers(bearer, MediaType.APPLICATION_JSON));

        ResponseEntity<Map> response = call(properties.userRealmRoleMappingsEndpoint(userId),
                HttpMethod.DELETE, entity);

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) return;
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            throw causedBy(new UserNotFoundException(userId),
                    KeycloakErrors.upstream(status.value(), response.getBody()));
        }
        translateError(status, response.getBody(), role.getName(), roleId);
    }

    // -------------------- helpers --------------------

    /** Representacao completa do Keycloak (inclui attributes), ou 404 traduzido. */
    private Map<String, Object> findRawById(String bearer, String id) {
        ResponseEntity<Map> response = call(properties.roleByIdEndpoint(id), HttpMethod.GET,
                new HttpEntity<>(headers(bearer, null)));

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            Map<String, Object> r = response.getBody();
            if (r == null) throw new RoleNotFoundException(id);
            return r;
        }
        translateError(status, response.getBody(), null, id);
        throw new IdentityProviderUnavailableException("unreachable");
    }

    /**
     * PUT em roles-by-id. O Keycloak substitui o mapa "attributes" inteiro pelo
     * enviado, entao os atributos customizados que ja existem na role sao
     * copiados e so "enabled" e sobrescrito.
     */
    private void write(String bearer, String id, Map<String, Object> current,
                       String name, String description, boolean enabled) {
        Map<String, Object> attributes = new HashMap<>();
        if (current.get("attributes") instanceof Map<?, ?> existing) {
            existing.forEach((k, v) -> attributes.put(String.valueOf(k), v));
        }
        attributes.put("enabled", Collections.singletonList(String.valueOf(enabled)));

        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("name", name);
        body.put("description", description);
        body.put("attributes", attributes);

        ResponseEntity<Map> response = call(properties.roleByIdEndpoint(id), HttpMethod.PUT,
                new HttpEntity<>(body, headers(bearer, MediaType.APPLICATION_JSON)));

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) return;
        translateError(status, response.getBody(), name, id);
    }

    private Role findByName(String bearer, String name) {
        ResponseEntity<Map> response = call(properties.roleByNameEndpoint(name), HttpMethod.GET,
                new HttpEntity<>(headers(bearer, null)));

        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            return toRole(response.getBody());
        }
        translateError(status, response.getBody(), name, null);
        throw new IdentityProviderUnavailableException("unreachable");
    }

    private Map<String, Object> roleMappingPayload(Role role) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", role.getId());
        m.put("name", role.getName());
        return m;
    }

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

    private Role toRole(Map<String, Object> r) {
        boolean enabled = true;
        Object attrs = r.get("attributes");
        if (attrs instanceof Map<?, ?> am) {
            Object en = am.get("enabled");
            if (en instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
                enabled = !"false".equalsIgnoreCase(String.valueOf(list.get(0)));
            }
        }
        return new Role(
                (String) r.get("id"),
                (String) r.get("name"),
                (String) r.get("description"),
                enabled);
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

    private void translateError(HttpStatusCode status, Object body, String name, String id) {
        int code = status.value();
        String description = extractErrorMessage(body);
        UpstreamErrorException upstream = KeycloakErrors.upstream(code, body);
        if (code == 400) throw causedBy(new InvalidInputException(
                description.isEmpty() ? "Invalid request for Keycloak" : description), upstream);
        if (code == 401) throw causedBy(new AuthorizationRequiredException(
                "Invalid or expired access token"), upstream);
        if (code == 403) throw causedBy(new AccessDeniedException(
                "Access token does not grant permission for this operation"), upstream);
        if (code == 404) throw causedBy(new RoleNotFoundException(
                id != null ? id : (name != null ? name : "")), upstream);
        if (code == 409) throw causedBy(new RoleAlreadyExistsException(name != null ? name : ""), upstream);
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
