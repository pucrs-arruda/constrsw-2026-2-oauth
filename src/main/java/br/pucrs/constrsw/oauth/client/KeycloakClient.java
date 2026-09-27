package br.pucrs.constrsw.oauth.client;

import br.pucrs.constrsw.oauth.config.KeycloakProperties;
import br.pucrs.constrsw.oauth.domain.AuthTokens;
import br.pucrs.constrsw.oauth.dto.LoginResponse;
import br.pucrs.constrsw.oauth.error.InvalidCredentialsException;
import br.pucrs.constrsw.oauth.error.KeycloakCommunicationException;
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

@Component
public class KeycloakClient implements AuthenticationGateway {

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
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);

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
            if (exception.getStatusCode() == HttpStatus.BAD_REQUEST && isInvalidGrant(exception)) {
                throw new InvalidCredentialsException();
            }
            throw new KeycloakCommunicationException("Unable to authenticate with identity provider", exception);
        } catch (RestClientException exception) {
            throw new KeycloakCommunicationException("Identity provider is unavailable", exception);
        }
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
