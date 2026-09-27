package br.pucrs.constrsw.oauth.controller;

import br.pucrs.constrsw.oauth.dto.LoginRequest;
import br.pucrs.constrsw.oauth.dto.LoginResponse;
import br.pucrs.constrsw.oauth.dto.ValidateRequest;
import br.pucrs.constrsw.oauth.dto.ValidateResponse;
import br.pucrs.constrsw.oauth.exception.KeycloakException;
import br.pucrs.constrsw.oauth.service.KeycloakService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final KeycloakService keycloakService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public AuthController(KeycloakService keycloakService, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.keycloakService = keycloakService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }

    /**
     * POST /login: Consumir endpoint de token OAuth2 do Keycloak para gerar o access token.
     * Suporta multipart/form-data, application/x-www-form-urlencoded e application/json.
     * Retorna 201 Created.
     */
    @Operation(summary = "Autentica usuário no Keycloak",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = {
                            @Content(mediaType = "multipart/form-data", schema = @Schema(implementation = LoginRequest.class)),
                            @Content(mediaType = "application/x-www-form-urlencoded", schema = @Schema(implementation = LoginRequest.class))
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tokens emitidos",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Estrutura inválida"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(HttpServletRequest servletRequest) {
        String username = servletRequest.getParameter("username");
        String password = servletRequest.getParameter("password");

        String contentType = servletRequest.getContentType();
        if ((username == null || password == null) && contentType != null && contentType.contains("application/json")) {
            try (InputStream is = servletRequest.getInputStream()) {
                LoginRequest loginReq = objectMapper.readValue(is, LoginRequest.class);
                if (loginReq != null) {
                    if (username == null) username = loginReq.username();
                    if (password == null) password = loginReq.password();
                }
            } catch (Exception e) {
                throw new KeycloakException("400", "Erro na estrutura da chamada: corpo JSON inválido", HttpStatus.BAD_REQUEST);
            }
        }

        return login(new LoginRequest(username, password));
    }

    public ResponseEntity<LoginResponse> login(LoginRequest request) {
        if (request == null || request.username() == null || request.username().isBlank() ||
                request.password() == null || request.password().isBlank()) {
            log.warn("Tentativa de login sem parâmetros obrigatórios username/password");
            throw new KeycloakException("400", "Erro na estrutura da chamada (headers, request body etc.)", HttpStatus.BAD_REQUEST);
        }

        log.info("Requisição de login recebida para usuário '{}'", request.username());
        LoginResponse response = keycloakService.login(request);
        meterRegistry.counter("oauth.logins.total", "status", "success").increment();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = {"/validate", "/authorize"})
    public ResponseEntity<ValidateResponse> validateAccessPost(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) ValidateRequest request) {

        String resource = (request != null) ? request.getResource() : null;
        return executeValidation(authHeader, resource);
    }

    @GetMapping(value = {"/validate", "/authorize"})
    public ResponseEntity<ValidateResponse> validateAccessGet(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "resource", required = false) String resource) {

        return executeValidation(authHeader, resource);
    }

    private ResponseEntity<ValidateResponse> executeValidation(String authHeader, String resource) {
        if (authHeader == null || authHeader.isBlank()) {
            log.warn("Tentativa de validação sem cabeçalho Authorization");
            meterRegistry.counter("oauth.validations.denied", "reason", "missing_header").increment();
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ValidateResponse.forbidden("Authorization header is missing", null, resource, List.of()));
        }

        if (resource == null || resource.isBlank()) {
            log.warn("Tentativa de validação sem informar o recurso");
            meterRegistry.counter("oauth.validations.denied", "reason", "missing_resource").increment();
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ValidateResponse.forbidden("Resource must be specified", null, null, List.of()));
        }

        boolean isTokenValid = keycloakService.isTokenValidWithKeycloak(authHeader);
        if (!isTokenValid) {
            log.warn("Access token inválido ou expirado");
            meterRegistry.counter("oauth.validations.denied", "reason", "invalid_token").increment();
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ValidateResponse.forbidden("Invalid or expired access token", null, resource, List.of()));
        }

        String username = keycloakService.extractUsername(authHeader);
        List<String> roles = keycloakService.extractRoles(authHeader);

        boolean hasAccess = keycloakService.hasAccessToResource(roles, resource);

        if (hasAccess) {
            log.info("Usuário '{}' autorizado com sucesso para o recurso '{}'", username, resource);
            meterRegistry.counter("oauth.validations.total", "status", "allowed", "resource", resource).increment();
            return ResponseEntity.ok(ValidateResponse.allowed(username, resource, roles));
        } else {
            log.warn("Usuário '{}' não possui role para acessar o recurso '{}'. Roles: {}", username, resource, roles);
            meterRegistry.counter("oauth.validations.denied", "reason", "unauthorized_role").increment();
            meterRegistry.counter("oauth.validations.total", "status", "forbidden", "resource", resource).increment();
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ValidateResponse.forbidden("User roles do not grant access to the requested resource", username, resource, roles));
        }
    }
}
