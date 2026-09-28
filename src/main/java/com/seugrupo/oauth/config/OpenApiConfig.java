package com.seugrupo.oauth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Set;

/**
 * Documentação Swagger (/docs) com esquema Bearer: o botão "Authorize" da UI
 * envia o access token obtido em POST /login para as rotas protegidas.
 *
 * Os controllers recebem o header Authorization via @RequestHeader para
 * repassá-lo ao Keycloak; a especificação OpenAPI manda ignorar parâmetros de
 * header com esse nome, então ele é removido da documentação e passa a ser
 * preenchido pelo esquema de segurança.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final Set<String> PUBLIC_PATHS = Set.of("/login", "/refresh-token", "/health");

    @Bean
    public OpenAPI oauthOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OAuth API - Grupo 08")
                        .version("1.0.0")
                        .description("Adapter REST sobre a Admin API do Keycloak: autenticação, "
                                + "usuários, roles e atribuição de roles. Obtenha o token em "
                                + "POST /login e use o botão Authorize."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public OperationCustomizer hideAuthorizationHeader() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() != null) {
                operation.getParameters().removeIf(parameter -> "header".equals(parameter.getIn())
                        && HttpHeaders.AUTHORIZATION.equalsIgnoreCase(parameter.getName()));
            }
            return operation;
        };
    }

    /** Rotas públicas não exibem cadeado na UI. */
    @Bean
    public OpenApiCustomizer publicPathsWithoutSecurity() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (PUBLIC_PATHS.contains(path)) {
                item.readOperations().forEach(operation -> operation.setSecurity(List.of()));
            }
        });
    }
}
