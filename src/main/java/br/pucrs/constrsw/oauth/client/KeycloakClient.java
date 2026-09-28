package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.AuthTokens;
import br.pucrs.constrsw.oauth.dto.LoginResponse;
import br.pucrs.constrsw.oauth.error.InvalidCredentialsException;
import br.pucrs.constrsw.oauth.error.InvalidRefreshTokenException;
import br.pucrs.constrsw.oauth.error.KeycloakCommunicationException;
import br.pucrs.constrsw.oauth.error.UnauthorizedException;
import br.pucrs.constrsw.oauth.port.AuthorizationGateway;
import br.pucrs.constrsw.oauth.port.AuthenticationGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

@Component
public class KeycloakClient implements AuthenticationGateway, AuthorizationGateway {

    private final RestClient restClient;
    private final KeycloakProperties properties;
    private final ObjectMapper objectMapper;

    public KeycloakClient(RestClient restClient, KeycloakProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuthTokens authenticate(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        addClientCredentials(form);
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);

        return requestTokens(form, "Unable to authenticate with identity provider", InvalidCredentialsException::new);
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        addClientCredentials(form);
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        return requestTokens(form, "Unable to refresh tokens with identity provider", InvalidRefreshTokenException::new);
    }

    @Override
    public boolean hasAccess(String authorization, String resourceUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:uma-ticket");
        form.add("audience", properties.clientId());
        form.add("permission", resourceUri);
        form.add("permission_resource_format", "uri");
        form.add("response_mode", "decision");

        try {
            JsonNode response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return response != null && response.path("result").asBoolean(false);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                return false;
            }
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new UnauthorizedException("Invalid access token");
            }
            throw new KeycloakCommunicationException("Unable to evaluate resource access", exception);
        } catch (RestClientException exception) {
            throw new KeycloakCommunicationException("Identity provider is unavailable", exception);
        }
    }

    private AuthTokens requestTokens(MultiValueMap<String, String> form, String errorMessage,
                                     Supplier<? extends RuntimeException> invalidGrant) {
        try {
            LoginResponse response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(LoginResponse.class);

            if (response == null) {
                throw new KeycloakCommunicationException("Identity provider returned an empty response", null);
            }
            return new AuthTokens(response.tokenType(), response.accessToken(), response.expiresIn(),
                    response.refreshToken(), response.refreshExpiresIn());
        } catch (RestClientResponseException exception) {
            if (isInvalidGrant(exception)) {
                throw invalidGrant.get();
            }
            throw new KeycloakCommunicationException(errorMessage, exception);
        } catch (RestClientException exception) {
            throw new KeycloakCommunicationException("Identity provider is unavailable", exception);
        }
    }

    private void addClientCredentials(MultiValueMap<String, String> form) {
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
    }

    public String serviceAccountToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "client_credentials");

        try {
            LoginResponse response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(LoginResponse.class);

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new KeycloakCommunicationException("Identity provider returned an empty access token", null);
            }
            return response.accessToken();
        } catch (RestClientResponseException exception) {
            throw new KeycloakCommunicationException("Unable to authenticate service account with identity provider", exception);
        } catch (RestClientException exception) {
            throw new KeycloakCommunicationException("Identity provider is unavailable", exception);
        }
    }

    private boolean isInvalidGrant(RestClientResponseException exception) {
        try {
            JsonNode response = objectMapper.readTree(exception.getResponseBodyAsString());
            return "invalid_grant".equals(response.path("error").asText());
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return false;
        }
    }
}
