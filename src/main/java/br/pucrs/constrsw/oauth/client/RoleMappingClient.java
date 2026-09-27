package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.Role;
import br.pucrs.constrsw.oauth.dto.RoleDto;
import br.pucrs.constrsw.oauth.error.KeycloakServiceException;
import br.pucrs.constrsw.oauth.port.RoleMappingGateway;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RoleMappingClient implements RoleMappingGateway {

    private final RestClient restClient;
    private final KeycloakProperties properties;

    public RoleMappingClient(RestClient keycloakRestClient, KeycloakProperties properties) {
        this.restClient = keycloakRestClient;
        this.properties = properties;
    }

    @Override
    public void assignRoleToUser(String authorization, String userId, Role role) {
        try {
            restClient.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), userId)
                    .headers(headers -> headers.set("Authorization", authorization))
                    .body(List.of(RoleDto.fromDomain(role)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to assign role to user");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void removeRoleFromUser(String authorization, String userId, Role role) {
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), userId)
                    .headers(headers -> headers.set("Authorization", authorization))
                    .body(List.of(RoleDto.fromDomain(role)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw keycloakException(exception, "Failed to remove role from user");
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
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
