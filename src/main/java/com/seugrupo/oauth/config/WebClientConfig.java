package com.seugrupo.oauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient único, com a base-url do Keycloak já configurada.
 * Injetem esse bean nos services (KeycloakAuthService, KeycloakUserService,
 * KeycloakRoleService) em vez de instanciar um WebClient novo em cada um.
 *
 * Parte do WebClient.Builder do Spring Boot (e não de WebClient.builder())
 * para herdar a instrumentação do Micrometer: cada chamada ao Keycloak gera
 * a métrica http_client_requests_seconds, usada no dashboard do Grafana.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient keycloakWebClient(WebClient.Builder builder, KeycloakProperties properties) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
