package br.pucrs.constrsw.oauth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer publicEndpointsCustomizer() {
        return api -> {
            if (api.getPaths().get("/login") != null && api.getPaths().get("/login").getPost() != null) {
                api.getPaths().get("/login").getPost().setSecurity(List.of());
            }
            if (api.getPaths().get("/health") != null && api.getPaths().get("/health").getGet() != null) {
                api.getPaths().get("/health").getGet().setSecurity(List.of());
            }
        };
    }

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "BearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("OAuth API - ConstrSW 2026/2")
                        .version("1.0.0")
                        .description("Documentação interativa dos endpoints de Autenticação, Usuários e Cargos (Roles) integrados ao Keycloak.")
                        .contact(new Contact()
                                .name("Grupo 05")
                                .email("grupo05@pucrs.br")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
