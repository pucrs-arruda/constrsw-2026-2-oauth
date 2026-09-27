package br.pucrs.constrsw.oauth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do Keycloak (URL base, realm e credenciais do client oauth).
 * Preenchida via application.yml / variaveis de ambiente.
 */
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    private String baseUrl;
    private String realm;
    private String clientId;
    private String clientSecret;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String tokenEndpoint() {
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String usersEndpoint() {
        return baseUrl + "/admin/realms/" + realm + "/users";
    }

    public String userEndpoint(String userId) {
        return usersEndpoint() + "/" + userId;
    }

    public String resetPasswordEndpoint(String userId) {
        return userEndpoint(userId) + "/reset-password";
    }

    public String rolesEndpoint() {
        return baseUrl + "/admin/realms/" + realm + "/roles";
    }

    /** Busca/edicao por nome (usado internamente logo apos o POST, que nao devolve Location). */
    public String roleByNameEndpoint(String name) {
        return rolesEndpoint() + "/" + name;
    }

    /** Busca/edicao/remocao por id - usado pelas rotas publicas /roles/{id}. */
    public String roleByIdEndpoint(String id) {
        return baseUrl + "/admin/realms/" + realm + "/roles-by-id/" + id;
    }

    public String userRealmRoleMappingsEndpoint(String userId) {
        return userEndpoint(userId) + "/role-mappings/realm";
    }
}
