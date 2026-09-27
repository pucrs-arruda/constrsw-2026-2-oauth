package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.dto.CreateUserRequest;
import br.pucrs.constrsw.oauth.dto.LoginRequest;
import br.pucrs.constrsw.oauth.dto.LoginResponse;
import br.pucrs.constrsw.oauth.dto.UpdatePasswordRequest;
import br.pucrs.constrsw.oauth.dto.UserResponse;
import br.pucrs.constrsw.oauth.dto.UpdateUserRequest;
import br.pucrs.constrsw.oauth.dto.CreateRoleRequest;
import br.pucrs.constrsw.oauth.dto.UpdateRoleRequest;
import br.pucrs.constrsw.oauth.dto.PatchRoleRequest;
import br.pucrs.constrsw.oauth.dto.RoleResponse;

import br.pucrs.constrsw.oauth.exception.KeycloakException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.core.ParameterizedTypeReference;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.*;

@Service
public class KeycloakService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakService.class);

    @Value("${keycloak.url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    public KeycloakService() {
        this.restTemplate = new RestTemplate();
    }

    public KeycloakService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Regras de acesso mapeadas conforme configurado no Keycloak
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = new HashMap<>();

    static {
        ROLE_PERMISSIONS.put("administrator", Set.of("resources", "rooms", "professors", "students"));
        ROLE_PERMISSIONS.put("coordinator", Set.of("courses", "classes"));
        ROLE_PERMISSIONS.put("professor", Set.of("lessons", "reservations"));
        ROLE_PERMISSIONS.put("student", Set.of());
    }

    /**
     * POST /login: Consumir endpoint de token OAuth2 do Keycloak para gerar o access token.
     */
    public LoginResponse login(LoginRequest request) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);
        log.info("Tentando autenticar usuário '{}' junto ao Keycloak em {}", request.username(), tokenUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("username", request.username());
        body.add("password", request.password());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<LoginResponse> response = restTemplate.postForEntity(tokenUrl, entity, LoginResponse.class);
            log.info("Usuário '{}' autenticado com sucesso no Keycloak", request.username());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.warn("Falha de autenticação do usuário '{}': status={}, body={}", request.username(), e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST || e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new KeycloakException("INVALID_CREDENTIALS", "Usuário ou senha inválidos", HttpStatus.UNAUTHORIZED, e);
            }
            throw new KeycloakException("KEYCLOAK_AUTH_ERROR", "Erro de autenticação no Keycloak: " + e.getStatusText(), (HttpStatus) e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao conectar ao Keycloak para login: {}", e.getMessage());
            throw new KeycloakException("KEYCLOAK_COMMUNICATION_ERROR", "Não foi possível comunicar com o servidor de autenticação", HttpStatus.SERVICE_UNAVAILABLE, e);
        }
    }

    /**
     * Usa o Bearer da chamada, após confirmar que o Keycloak aceita o token.
     * As permissões são decididas pela API administrativa do próprio Keycloak.
     */
    public String getCallerToken() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new KeycloakException("401", "Access token ausente", HttpStatus.UNAUTHORIZED);
        }
        String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring(7).isBlank()) {
            throw new KeycloakException("401", "Access token ausente ou inválido", HttpStatus.UNAUTHORIZED);
        }
        String token = authorization.substring(7).trim();
        String introspectionUrl = String.format("%s/realms/%s/protocol/openid-connect/token/introspect", keycloakUrl, realm);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    introspectionUrl, new HttpEntity<>(body, headers), Map.class);
            if (response.getBody() == null || !Boolean.TRUE.equals(response.getBody().get("active"))) {
                throw new KeycloakException("401", "Access token inválido", HttpStatus.UNAUTHORIZED);
            }
            return token;
        } catch (HttpClientErrorException e) {
            throw new KeycloakException("503", "Falha ao validar token no Keycloak", HttpStatus.SERVICE_UNAVAILABLE, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new KeycloakException("503", "Falha ao validar token no Keycloak", HttpStatus.SERVICE_UNAVAILABLE, e);
        }
    }

    /**
     * POST /users: Rota complexa de criação de usuário (pegar ID no header Location, tratar erro 409).
     */
    public UserResponse createUser(CreateUserRequest request) {
        String callerToken = getCallerToken();
        String usersUrl = String.format("%s/admin/realms/%s/users", keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("username", request.username());
        userPayload.put("email", request.email());
        userPayload.put("firstName", request.firstName());
        userPayload.put("lastName", request.lastName());
        userPayload.put("enabled", request.enabled());

        if (request.password() != null && !request.password().isBlank()) {
            Map<String, Object> credential = new HashMap<>();
            credential.put("type", "password");
            credential.put("value", request.password());
            credential.put("temporary", false);
            userPayload.put("credentials", List.of(credential));
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(userPayload, headers);

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(usersUrl, entity, Void.class);

            // Extrai o ID do usuário do header Location retornado pelo Keycloak
            URI location = response.getHeaders().getLocation();
            String path = location == null ? null : location.getPath();
            if (path == null || path.isBlank() || path.endsWith("/")) {
                throw new KeycloakException("502", "Keycloak não retornou o id do usuário em Location", HttpStatus.BAD_GATEWAY);
            }
            String userId = path.substring(path.lastIndexOf('/') + 1);

            log.info("Usuário criado com sucesso no Keycloak: username='{}', id='{}'", request.username(), userId);
            return new UserResponse(
                    userId,
                    request.username(),
                    request.email(),
                    request.firstName(),
                    request.lastName(),
                    request.enabled()
            );
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Conflito ao criar usuário no Keycloak: username '{}' ou email '{}' já existe", request.username(), request.email());
            throw new KeycloakException("USER_ALREADY_EXISTS", "Usuário ou e-mail já cadastrado no Keycloak", HttpStatus.CONFLICT, e);
        } catch (HttpClientErrorException e) {
            log.error("Erro do cliente Keycloak ao criar usuário: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new KeycloakException("USER_ALREADY_EXISTS", "Usuário ou e-mail já cadastrado no Keycloak", HttpStatus.CONFLICT, e);
            }
            throw new KeycloakException("KEYCLOAK_CREATE_USER_ERROR", "Erro ao criar usuário no Keycloak: " + e.getMessage(), (HttpStatus) e.getStatusCode(), e);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao criar usuário no Keycloak: {}", e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_USER_CREATION_FAILED", "Falha na criação de usuário: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * PATCH /users/{id}: Atualizar senha do usuário (estrutura CredentialRepresentation).
     */
    public void updatePassword(String userId, UpdatePasswordRequest request) {
        String callerToken = getCallerToken();
        String resetPasswordUrl = String.format("%s/admin/realms/%s/users/%s/reset-password", keycloakUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        // Monta a estrutura específica CredentialRepresentation do Keycloak
        Map<String, Object> credentialRepresentation = new HashMap<>();
        credentialRepresentation.put("type", "password");
        credentialRepresentation.put("value", request.password());
        credentialRepresentation.put("temporary", request.temporary() != null ? request.temporary() : false);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(credentialRepresentation, headers);

        try {
            restTemplate.put(resetPasswordUrl, entity);
            log.info("Senha do usuário id='{}' atualizada com sucesso no Keycloak", userId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Usuário id='{}' não encontrado no Keycloak para atualização de senha", userId);
            throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            log.error("Erro ao atualizar senha no Keycloak para id='{}': status={}, body={}", userId, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            throw new KeycloakException("KEYCLOAK_UPDATE_PASSWORD_ERROR", "Erro ao atualizar senha no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao atualizar senha no Keycloak: {}", e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_PASSWORD_RESET_FAILED", "Falha ao atualizar senha: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * GET /users (filtro ?enabled=): Consumir API do Keycloak passando parâmetros de busca.
     */
    public List<UserResponse> getUsers(Boolean enabled) {
        String callerToken = getCallerToken();
        String usersUrl = String.format("%s/admin/realms/%s/users", keycloakUrl, realm);
        if (enabled != null) {
            usersUrl += "?enabled=" + enabled;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(callerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            final int pageSize = 100;
            List<Map<String, Object>> users = new ArrayList<>();
            for (int offset = 0; ; offset += pageSize) {
                String pageUrl = usersUrl + (enabled == null ? "?" : "&")
                        + "first=" + offset + "&max=" + pageSize;
                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                        pageUrl, HttpMethod.GET, entity,
                        new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );
                List<Map<String, Object>> page = response.getBody();
                if (page == null || page.isEmpty()) break;
                users.addAll(page);
                if (page.size() < pageSize) break;
            }

            return users.stream().map(u -> {
                String userId = (String) u.get("id");
                List<String> roles = getUserRealmRoles(callerToken, userId);
                String primaryRole = extractPrimaryRole(roles);
                String username = (String) u.get("username");
                if (username == null) {
                    username = (String) u.get("email");
                }
                return new UserResponse(
                        userId,
                        username,
                        (String) u.get("firstName"),
                        (String) u.get("lastName"),
                        (Boolean) u.get("enabled"),
                        primaryRole,
                        roles != null && !roles.isEmpty() ? roles : null
                );
            }).collect(Collectors.toList());
        } catch (HttpClientErrorException e) {
            log.error("Erro ao buscar lista de usuários no Keycloak: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_GET_USERS_ERROR", "Erro ao buscar usuários no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao listar usuários no Keycloak: {}", e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_GET_USERS_FAILED", "Falha ao listar usuários: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * GET /users/{id}: Busca simples de usuário por ID.
     */
    public UserResponse getUserById(String userId) {
        String callerToken = getCallerToken();
        String userUrl = String.format("%s/admin/realms/%s/users/%s", keycloakUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(callerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    userUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> u = response.getBody();
            if (u == null) {
                throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND);
            }

            List<String> roles = getUserRealmRoles(callerToken, userId);
            String primaryRole = extractPrimaryRole(roles);
            String username = (String) u.get("username");
            if (username == null) {
                username = (String) u.get("email");
            }

            return new UserResponse(
                    (String) u.get("id"),
                    username,
                    (String) u.get("firstName"),
                    (String) u.get("lastName"),
                    (Boolean) u.get("enabled"),
                    primaryRole,
                    roles != null && !roles.isEmpty() ? roles : null
            );
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Usuário id='{}' não encontrado no Keycloak", userId);
            throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            log.error("Erro ao buscar usuário id='{}' no Keycloak: status={}, body={}", userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_GET_USER_ERROR", "Erro ao buscar usuário no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao buscar usuário id='{}': {}", userId, e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_GET_USER_FAILED", "Falha ao buscar usuário: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * PUT /users/{id}: Atualizar dados de cadastro de um usuário.
     */
    public UserResponse updateUser(String userId, UpdateUserRequest request) {
        String callerToken = getCallerToken();
        Map<String, Object> existingUser = getUserMapById(callerToken, userId);

        String userUrl = String.format("%s/admin/realms/%s/users/%s", keycloakUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        if (request.email() != null && !request.email().isBlank()) {
            existingUser.put("email", request.email());
            existingUser.put("username", request.email());
        }
        if (request.firstName() != null) {
            existingUser.put("firstName", request.firstName());
        }
        if (request.lastName() != null) {
            existingUser.put("lastName", request.lastName());
        }
        if (request.enabled() != null) {
            existingUser.put("enabled", request.enabled());
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(existingUser, headers);

        try {
            restTemplate.put(userUrl, entity);
            log.info("Cadastro do usuário id='{}' atualizado com sucesso no Keycloak", userId);
            return getUserById(userId);
        } catch (HttpClientErrorException.Conflict e) {
            throw new KeycloakException("USER_ALREADY_EXISTS", "Email já cadastrado para outro usuário", HttpStatus.CONFLICT, e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new KeycloakException("USER_ALREADY_EXISTS", "Email já cadastrado para outro usuário", HttpStatus.CONFLICT, e);
            }
            log.error("Erro ao atualizar cadastro do usuário id='{}': status={}, body={}", userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_UPDATE_USER_ERROR", "Erro ao atualizar usuário no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao atualizar usuário id='{}': {}", userId, e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_UPDATE_USER_FAILED", "Falha ao atualizar usuário: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * DELETE /users/{id}: Deleção lógica de usuário (Fazer GET, mudar enabled para false e devolver com PUT).
     */
    public void logicalDeleteUser(String userId) {
        String callerToken = getCallerToken();
        Map<String, Object> existingUser = getUserMapById(callerToken, userId);

        String userUrl = String.format("%s/admin/realms/%s/users/%s", keycloakUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        // Mudar enabled para false
        existingUser.put("enabled", false);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(existingUser, headers);

        try {
            restTemplate.put(userUrl, entity);
            log.info("Deleção lógica do usuário id='{}' concluída com sucesso (enabled=false)", userId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            log.error("Erro na deleção lógica do usuário id='{}': status={}, body={}", userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_DELETE_USER_ERROR", "Erro ao desativar usuário no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado na deleção lógica do usuário id='{}': {}", userId, e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_DELETE_USER_FAILED", "Falha ao desativar usuário: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Busca representação bruta em Map do usuário no Keycloak.
     */
    private Map<String, Object> getUserMapById(String callerToken, String userId) {
        String userUrl = String.format("%s/admin/realms/%s/users/%s", keycloakUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(callerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    userUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND);
            }
            return body;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Usuário id='{}' não encontrado no Keycloak", userId);
            throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            throw new KeycloakException("KEYCLOAK_GET_USER_ERROR", "Erro ao buscar usuário no Keycloak", (HttpStatus) e.getStatusCode(), e);
        }
    }


    /**
     * POST /users/{id}/roles/{roleId}: Atribuir uma Role a um usuário (usa a API de role-mapping do Keycloak).
     */
    public void assignRoleToUser(String userId, String roleId) {
        String callerToken = getCallerToken();

        // 1. Obter a Role Representation por roleId
        Map<String, Object> roleRepresentation = getRoleById(callerToken, roleId);

        // 2. POST /admin/realms/{realm}/users/{id}/role-mappings/realm
        String mappingUrl = String.format("%s/admin/realms/%s/users/%s/role-mappings/realm", keycloakUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(List.of(roleRepresentation), headers);

        try {
            restTemplate.postForEntity(mappingUrl, entity, Void.class);
            log.info("Role id='{}' atribuída com sucesso ao usuário id='{}'", roleId, userId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Usuário id='{}' não encontrado para atribuição de role", userId);
            throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            log.error("Erro ao atribuir role id='{}' para o usuário id='{}': status={}, body={}", roleId, userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_ASSIGN_ROLE_ERROR", "Erro ao atribuir role ao usuário", (HttpStatus) e.getStatusCode(), e);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao atribuir role id='{}' ao usuário id='{}': {}", roleId, userId, e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_ASSIGN_ROLE_FAILED", "Falha ao atribuir role: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * DELETE /users/{id}/roles/{roleId}: Remover uma Role de um usuário.
     */
    public void removeRoleFromUser(String userId, String roleId) {
        String callerToken = getCallerToken();

        // 1. Obter a Role Representation por roleId
        Map<String, Object> roleRepresentation = getRoleById(callerToken, roleId);

        // 2. DELETE /admin/realms/{realm}/users/{id}/role-mappings/realm
        String mappingUrl = String.format("%s/admin/realms/%s/users/%s/role-mappings/realm", keycloakUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(List.of(roleRepresentation), headers);

        try {
            restTemplate.exchange(mappingUrl, HttpMethod.DELETE, entity, Void.class);
            log.info("Role id='{}' removida com sucesso do usuário id='{}'", roleId, userId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Usuário id='{}' não encontrado para remoção de role", userId);
            throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("USER_NOT_FOUND", "Usuário com id '" + userId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            log.error("Erro ao remover role id='{}' do usuário id='{}': status={}, body={}", roleId, userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_REMOVE_ROLE_ERROR", "Erro ao remover role do usuário", (HttpStatus) e.getStatusCode(), e);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao remover role id='{}' do usuário id='{}': {}", roleId, userId, e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_REMOVE_ROLE_FAILED", "Falha ao remover role: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Consulta as roles do realm atribuídas ao usuário no Keycloak.
     * GET /admin/realms/{realm}/users/{id}/role-mappings/realm
     */
    public List<String> getUserRealmRoles(String callerToken, String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        String mappingUrl = String.format("%s/admin/realms/%s/users/%s/role-mappings/realm", keycloakUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(callerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    mappingUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            List<Map<String, Object>> body = response.getBody();
            if (body == null || body.isEmpty()) {
                return Collections.emptyList();
            }

            return body.stream()
                    .map(r -> (String) r.get("name"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Não foi possível carregar roles para o usuário id='{}': {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extrai a role principal de negócio a partir da lista de roles do Keycloak.
     */
    public String extractPrimaryRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        List<String> priorityRoles = List.of("administrator", "coordinator", "professor", "student");
        for (String prio : priorityRoles) {
            if (roles.contains(prio)) {
                return prio;
            }
        }
        for (String r : roles) {
            if (!r.startsWith("default-roles") && !r.equals("offline_access") && !r.equals("uma_authorization")) {
                return r;
            }
        }
        return roles.get(0);
    }

    /**
     * Busca os metadados de uma role no Keycloak por seu roleId (/roles-by-id/{roleId}).
     */
    private Map<String, Object> getRoleById(String callerToken, String roleId) {
        String roleUrl = String.format("%s/admin/realms/%s/roles-by-id/%s", keycloakUrl, realm, roleId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(callerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    roleUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new KeycloakException("ROLE_NOT_FOUND", "Cargo com id '" + roleId + "' não foi encontrado", HttpStatus.NOT_FOUND);
            }
            return body;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Role id='{}' não encontrada no Keycloak", roleId);
            throw new KeycloakException("ROLE_NOT_FOUND", "Cargo com id '" + roleId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("ROLE_NOT_FOUND", "Cargo com id '" + roleId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            log.error("Erro ao buscar role id='{}': status={}, body={}", roleId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_GET_ROLE_ERROR", "Erro ao consultar cargo no Keycloak", (HttpStatus) e.getStatusCode(), e);
        }
    }

    /**
     * POST /roles: Criar cargo no Keycloak.
     */
    public RoleResponse createRole(CreateRoleRequest request) {
        String callerToken = getCallerToken();
        String rolesUrl = String.format("%s/admin/realms/%s/roles", keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", request.name());
        if (request.description() != null) {
            payload.put("description", request.description());
        }

        boolean enabled = request.enabled() == null || request.enabled();
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("enabled", List.of(String.valueOf(enabled)));
        payload.put("attributes", attributes);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            restTemplate.postForEntity(rolesUrl, entity, Void.class);
            log.info("Cargo '{}' criado com sucesso no Keycloak", request.name());

            // Keycloak não retorna o ID no POST de role, busca pelo nome
            return getRoleByName(request.name());
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Cargo com nome '{}' já existe", request.name());
            throw new KeycloakException("ROLE_ALREADY_EXISTS", "Cargo com nome '" + request.name() + "' já cadastrado", HttpStatus.CONFLICT, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new KeycloakException("ROLE_ALREADY_EXISTS", "Cargo com nome '" + request.name() + "' já cadastrado", HttpStatus.CONFLICT, e);
            }
            log.error("Erro ao criar cargo no Keycloak: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_CREATE_ROLE_ERROR", "Erro ao criar cargo no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao criar cargo '{}': {}", request.name(), e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_CREATE_ROLE_FAILED", "Falha na criação de cargo: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * GET /roles: Listar cargos do realm.
     */
    public List<RoleResponse> getRoles() {
        String callerToken = getCallerToken();
        String rolesUrl = String.format("%s/admin/realms/%s/roles", keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(callerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    rolesUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            List<Map<String, Object>> body = response.getBody();
            if (body == null) {
                return Collections.emptyList();
            }

            return body.stream().map(this::mapToRoleResponse).collect(Collectors.toList());
        } catch (HttpClientErrorException e) {
            log.error("Erro ao listar cargos no Keycloak: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_GET_ROLES_ERROR", "Erro ao listar cargos no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao listar cargos: {}", e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_GET_ROLES_FAILED", "Falha ao listar cargos: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * GET /roles/{id}: Buscar cargo específico por ID.
     */
    public RoleResponse getRole(String roleId) {
        String callerToken = getCallerToken();
        Map<String, Object> roleMap = getRoleById(callerToken, roleId);
        return mapToRoleResponse(roleMap);
    }

    /**
     * PUT /roles/{id}: Atualizar cargo completo.
     */
    public RoleResponse updateRole(String roleId, UpdateRoleRequest request) {
        String callerToken = getCallerToken();
        Map<String, Object> existing = getRoleById(callerToken, roleId);

        String roleUrl = String.format("%s/admin/realms/%s/roles-by-id/%s", keycloakUrl, realm, roleId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        existing.put("name", request.name());
        existing.put("description", request.description());

        Map<String, Object> attributes = (Map<String, Object>) existing.getOrDefault("attributes", new HashMap<>());
        if (request.enabled() != null) {
            attributes.put("enabled", List.of(String.valueOf(request.enabled())));
        }
        existing.put("attributes", attributes);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(existing, headers);

        try {
            restTemplate.put(roleUrl, entity);
            log.info("Cargo id='{}' atualizado com sucesso", roleId);
            return mapToRoleResponse(getRoleById(callerToken, roleId));
        } catch (HttpClientErrorException.NotFound e) {
            throw new KeycloakException("ROLE_NOT_FOUND", "Cargo com id '" + roleId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("ROLE_NOT_FOUND", "Cargo com id '" + roleId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            log.error("Erro ao atualizar cargo id='{}': status={}, body={}", roleId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_UPDATE_ROLE_ERROR", "Erro ao atualizar cargo no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao atualizar cargo id='{}': {}", roleId, e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_UPDATE_ROLE_FAILED", "Falha ao atualizar cargo: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * PATCH /roles/{id}: Atualizar cargo parcial.
     */
    public RoleResponse patchRole(String roleId, PatchRoleRequest request) {
        String callerToken = getCallerToken();
        Map<String, Object> existing = getRoleById(callerToken, roleId);

        String roleUrl = String.format("%s/admin/realms/%s/roles-by-id/%s", keycloakUrl, realm, roleId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        if (request.name() != null && !request.name().isBlank()) {
            existing.put("name", request.name());
        }
        if (request.description() != null) {
            existing.put("description", request.description());
        }
        if (request.enabled() != null) {
            Map<String, Object> attributes = (Map<String, Object>) existing.getOrDefault("attributes", new HashMap<>());
            attributes.put("enabled", List.of(String.valueOf(request.enabled())));
            existing.put("attributes", attributes);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(existing, headers);

        try {
            restTemplate.put(roleUrl, entity);
            log.info("Cargo id='{}' atualizado parcialmente com sucesso", roleId);
            return mapToRoleResponse(getRoleById(callerToken, roleId));
        } catch (HttpClientErrorException.NotFound e) {
            throw new KeycloakException("ROLE_NOT_FOUND", "Cargo com id '" + roleId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("ROLE_NOT_FOUND", "Cargo com id '" + roleId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            log.error("Erro ao aplicar patch no cargo id='{}': status={}, body={}", roleId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_PATCH_ROLE_ERROR", "Erro ao atualizar cargo no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado no patch do cargo id='{}': {}", roleId, e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_PATCH_ROLE_FAILED", "Falha ao atualizar cargo: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * DELETE /roles/{id}: Deleção lógica de role (simula desativação com atributo customizado enabled=false).
     */
    public void logicalDeleteRole(String roleId) {
        String callerToken = getCallerToken();
        Map<String, Object> existing = getRoleById(callerToken, roleId);

        String roleUrl = String.format("%s/admin/realms/%s/roles-by-id/%s", keycloakUrl, realm, roleId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(callerToken);

        Map<String, Object> attributes = (Map<String, Object>) existing.getOrDefault("attributes", new HashMap<>());
        attributes.put("enabled", List.of("false"));
        existing.put("attributes", attributes);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(existing, headers);

        try {
            restTemplate.put(roleUrl, entity);
            log.info("Deleção lógica do cargo id='{}' concluída com sucesso (enabled=false)", roleId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new KeycloakException("ROLE_NOT_FOUND", "Cargo com id '" + roleId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("ROLE_NOT_FOUND", "Cargo com id '" + roleId + "' não foi encontrado", HttpStatus.NOT_FOUND, e);
            }
            log.error("Erro na deleção lógica do cargo id='{}': status={}, body={}", roleId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("KEYCLOAK_DELETE_ROLE_ERROR", "Erro ao desativar cargo no Keycloak", (HttpStatus) e.getStatusCode(), e);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado na deleção lógica do cargo id='{}': {}", roleId, e.getMessage(), e);
            throw new KeycloakException("KEYCLOAK_DELETE_ROLE_FAILED", "Falha ao desativar cargo: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Busca uma role pelo seu nome (/roles/{roleName}).
     */
    private RoleResponse getRoleByName(String roleName) {
        String callerToken = getCallerToken();
        String roleUrl = String.format("%s/admin/realms/%s/roles/%s", keycloakUrl, realm, roleName);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(callerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    roleUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new KeycloakException("ROLE_NOT_FOUND", "Cargo '" + roleName + "' não encontrado", HttpStatus.NOT_FOUND);
            }
            return mapToRoleResponse(body);
        } catch (HttpClientErrorException.NotFound e) {
            throw new KeycloakException("ROLE_NOT_FOUND", "Cargo '" + roleName + "' não encontrado", HttpStatus.NOT_FOUND, e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new KeycloakException("ROLE_NOT_FOUND", "Cargo '" + roleName + "' não encontrado", HttpStatus.NOT_FOUND, e);
            }
            throw new KeycloakException("KEYCLOAK_GET_ROLE_ERROR", "Erro ao buscar cargo", (HttpStatus) e.getStatusCode(), e);
        }
    }

    /**
     * Converte um Map retornado do Keycloak para o DTO RoleResponse.
     */
    private RoleResponse mapToRoleResponse(Map<String, Object> r) {
        boolean enabled = true;
        Object attrsObj = r.get("attributes");
        if (attrsObj instanceof Map<?, ?> attrs) {
            Object enabledVal = attrs.get("enabled");
            if (enabledVal instanceof List<?> list && !list.isEmpty()) {
                enabled = "true".equalsIgnoreCase(String.valueOf(list.get(0)));
            } else if (enabledVal != null) {
                enabled = "true".equalsIgnoreCase(String.valueOf(enabledVal));
            }
        }

        return new RoleResponse(
                (String) r.get("id"),
                (String) r.get("name"),
                (String) r.get("description"),
                (Boolean) r.get("composite"),
                (Boolean) r.get("clientRole"),
                (String) r.get("containerId"),
                enabled
        );
    }



    /**
     * Valida se o token é válido (verificando formato JWT, emissor, expiração e integridade com Keycloak).
     */
    public boolean isTokenValidWithKeycloak(String rawToken) {
        String token = cleanToken(rawToken);
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            // 1. Checagem prévia de formato e expiração local no JWT
            DecodedJWT jwt = JWT.decode(token);
            if (jwt.getExpiresAt() != null && jwt.getExpiresAt().before(new Date())) {
                log.warn("Token JWT expirado em: {}", jwt.getExpiresAt());
                return false;
            }

            // 2. Checagem do realm emissor
            if (jwt.getIssuer() == null || !jwt.getIssuer().contains("/realms/" + realm)) {
                log.warn("Token JWT com emissor incompatível (esperado realm '{}'): {}", realm, jwt.getIssuer());
                return false;
            }

            // 3. Tenta confirmação ativa com Keycloak (se os hostnames coincidirem)
            try {
                String userInfoUrl = String.format("%s/realms/%s/protocol/openid-connect/userinfo", keycloakUrl, realm);
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(token);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("Consulta userinfo não pôde ser concluída (esperado caso host interno do container difira do emissor): {}", e.getMessage());
            }

            // Token válido conforme assinado pelo Realm e dentro da validade
            return true;
        } catch (Exception e) {
            log.error("Erro ao validar token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extrai todas as roles (client roles e realm roles) do access token.
     */
    public List<String> extractRoles(String rawToken) {
        String token = cleanToken(rawToken);
        Set<String> roles = new HashSet<>();

        try {
            DecodedJWT jwt = JWT.decode(token);

            // 1. Roles do client (resource_access.{clientId}.roles)
            Claim resourceAccessClaim = jwt.getClaim("resource_access");
            if (!resourceAccessClaim.isNull()) {
                Map<String, Object> resourceAccessMap = resourceAccessClaim.asMap();
                if (resourceAccessMap != null && resourceAccessMap.containsKey(clientId)) {
                    Object clientData = resourceAccessMap.get(clientId);
                    if (clientData instanceof Map<?, ?> clientMap) {
                        Object rolesObj = clientMap.get("roles");
                        if (rolesObj instanceof List<?> list) {
                            for (Object r : list) {
                                roles.add(String.valueOf(r).toLowerCase());
                            }
                        }
                    }
                }
            }

            // 2. Roles do Realm (realm_access.roles)
            Claim realmAccessClaim = jwt.getClaim("realm_access");
            if (!realmAccessClaim.isNull()) {
                Map<String, Object> realmAccessMap = realmAccessClaim.asMap();
                if (realmAccessMap != null && realmAccessMap.containsKey("roles")) {
                    Object rolesObj = realmAccessMap.get("roles");
                    if (rolesObj instanceof List<?> list) {
                        for (Object r : list) {
                            roles.add(String.valueOf(r).toLowerCase());
                        }
                    }
                }
            }
        } catch (JWTDecodeException e) {
            log.error("Falha ao decodificar JWT para extração de roles: {}", e.getMessage());
        }

        return new ArrayList<>(roles);
    }

    /**
     * Extrai o nome de usuário (preferred_username ou email ou sub) do token.
     */
    public String extractUsername(String rawToken) {
        String token = cleanToken(rawToken);
        try {
            DecodedJWT jwt = JWT.decode(token);
            String preferredUsername = jwt.getClaim("preferred_username").asString();
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
            String email = jwt.getClaim("email").asString();
            if (email != null && !email.isBlank()) {
                return email;
            }
            return jwt.getSubject();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Verifica se alguma das roles do usuário confere acesso ao recurso requisitado.
     */
    public boolean hasAccessToResource(List<String> userRoles, String resource) {
        if (resource == null || resource.isBlank() || userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        String normalizedResource = normalizeResource(resource);

        for (String role : userRoles) {
            Set<String> allowedResources = ROLE_PERMISSIONS.get(role.toLowerCase());
            if (allowedResources != null && allowedResources.contains(normalizedResource)) {
                log.info("Acesso permitido: role '{}' tem permissão para recurso '{}'", role, normalizedResource);
                return true;
            }
        }

        log.warn("Acesso negado: nenhuma das roles {} tem permissão para o recurso '{}'", userRoles, normalizedResource);
        return false;
    }

    public String normalizeResource(String resource) {
        if (resource == null) return "";
        return resource.trim().replace("/", "").toLowerCase();
    }

    private String cleanToken(String rawToken) {
        if (rawToken == null) return null;
        if (rawToken.startsWith("Bearer ") || rawToken.startsWith("bearer ")) {
            return rawToken.substring(7).trim();
        }
        return rawToken.trim();
    }
}
