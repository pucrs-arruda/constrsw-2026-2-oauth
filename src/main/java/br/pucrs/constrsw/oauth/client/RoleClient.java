package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.Role;
import br.pucrs.constrsw.oauth.dto.RoleDto;
import br.pucrs.constrsw.oauth.error.KeycloakServiceException;
import br.pucrs.constrsw.oauth.port.RoleGateway;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RoleClient implements RoleGateway {

    private final RestClient restClient;
    private final KeycloakProperties properties;

    public RoleClient(RestClient keycloakRestClient, KeycloakProperties properties) {
        this.restClient = keycloakRestClient;
        this.properties = properties;
    }

    @Override
    public Role createRole(String authorization, Role role) {
        try {
            RoleDto created = restClient.post()
                    .uri("/admin/realms/{realm}/roles", properties.realm())
                    .headers(headers -> headers.set("Authorization", authorization))
                    .body(RoleDto.fromDomain(role))
                    .retrieve()
                    .body(RoleDto.class);
            return created == null ? null : created.toDomain();
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to create role");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public List<Role> getAllRoles(String authorization) {
        try {
            List<RoleDto> roles = restClient.get()
                    .uri("/admin/realms/{realm}/roles", properties.realm())
                    .headers(headers -> headers.set("Authorization", authorization))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<RoleDto>>() {});
            return roles == null ? null : roles.stream().map(RoleDto::toDomain).toList();
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to retrieve roles");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Role getRoleById(String authorization, String id) {
        try {
            RoleDto role = restClient.get()
                    .uri("/admin/realms/{realm}/roles-by-id/{id}", properties.realm(), id)
                    .headers(headers -> headers.set("Authorization", authorization))
                    .retrieve()
                    .body(RoleDto.class);
            return role == null ? null : role.toDomain();
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to retrieve role");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Role updateRole(String authorization, String id, Role role) {
        try {
            RoleDto updated = restClient.put()
                    .uri("/admin/realms/{realm}/roles-by-id/{id}", properties.realm(), id)
                    .headers(headers -> headers.set("Authorization", authorization))
                    .body(RoleDto.fromDomain(role))
                    .retrieve()
                    .body(RoleDto.class);
            return updated == null ? null : updated.toDomain();
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to update role");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private KeycloakServiceException keycloakException(RestClientResponseException exception, String operation) {
        return new KeycloakServiceException(exception.getRawStatusCode(),
                operation + ": " + exception.getMessage(), exception.getResponseBodyAsString());
    }

    private KeycloakServiceException unavailable(RestClientException exception) {
        return new KeycloakServiceException(HttpStatus.BAD_GATEWAY.value(),
                "Identity provider is unavailable", exception.getMessage());
    }
}
