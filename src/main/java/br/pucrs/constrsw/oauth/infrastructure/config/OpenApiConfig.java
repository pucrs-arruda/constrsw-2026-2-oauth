package br.pucrs.constrsw.oauth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI oauthOpenApi() {
        final String bearerScheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("ConstrSW - OAuth API")
                        .description("API REST em Clean Architecture que encapsula o Keycloak "
                                + "para autenticacao (POST /login), gestao de usuarios "
                                + "(CRUD em /users) e de roles (CRUD em /roles e atribuicao "
                                + "a usuarios). Grupo 04 - students.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
                .components(new Components()
                        .addSecuritySchemes(bearerScheme, new SecurityScheme()
                                .name(bearerScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
