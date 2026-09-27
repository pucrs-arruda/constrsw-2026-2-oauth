package br.pucrs.constrsw.oauth.infrastructure.adapter.out.keycloak;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import br.pucrs.constrsw.oauth.application.port.out.AuthGateway;
import br.pucrs.constrsw.oauth.domain.exception.IdentityProviderUnavailableException;
import br.pucrs.constrsw.oauth.domain.exception.InvalidCredentialsException;
import br.pucrs.constrsw.oauth.domain.model.AuthTokens;
import br.pucrs.constrsw.oauth.domain.model.Credentials;
import br.pucrs.constrsw.oauth.infrastructure.config.KeycloakProperties;

/**
 * Adapter de saida: implementa {@link AuthGateway} contra o token endpoint
 * OpenID Connect do Keycloak (grant_type=password no client "oauth").
 */
@Component
public class KeycloakAuthGateway implements AuthGateway {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthGateway.class);

    private final RestTemplate restTemplate;
    private final KeycloakProperties properties;

    public KeycloakAuthGateway(RestTemplate restTemplate, KeycloakProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public AuthTokens authenticate(Credentials credentials) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("grant_type", "password");
        form.add("username", credentials.getUsername());
        form.add("password", credentials.getPassword());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    properties.tokenEndpoint(),
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            HttpStatusCode status = response.getStatusCode();
            Map<String, Object> body = response.getBody();

            if (status.is2xxSuccessful()) {
                if (body == null) {
                    throw new IdentityProviderUnavailableException(
                            "Resposta vazia do Keycloak ao autenticar.");
                }
                return toAuthTokens(body);
            }

            String error = body == null ? "" : String.valueOf(body.getOrDefault("error", ""));
            if (status.value() == 401 || "invalid_grant".equalsIgnoreCase(error)) {
                throw new InvalidCredentialsException("Username e/ou password invalidos.",
                        KeycloakErrors.upstream(status.value(), body));
            }
            throw new IdentityProviderUnavailableException(
                    "Erro inesperado do Keycloak (" + status + "): " + body,
                    KeycloakErrors.upstream(status.value(), body));

        } catch (ResourceAccessException ex) {
            log.error("Keycloak inacessivel em {}", properties.tokenEndpoint(), ex);
            throw new IdentityProviderUnavailableException(
                    "Nao foi possivel contatar o Keycloak.", ex);
        }
    }

    private AuthTokens toAuthTokens(Map<String, Object> body) {
        return new AuthTokens(
                (String) body.get("token_type"),
                (String) body.get("access_token"),
                toLong(body.get("expires_in")),
                (String) body.get("refresh_token"),
                toLong(body.get("refresh_expires_in")));
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        return v instanceof Number ? ((Number) v).longValue() : Long.valueOf(v.toString());
    }
}
