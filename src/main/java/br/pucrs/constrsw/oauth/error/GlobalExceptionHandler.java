package br.pucrs.constrsw.oauth.error;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.pucrs.constrsw.oauth.dto.ApiErrorResponse;
import br.pucrs.constrsw.oauth.domain.RoleNotFoundException;
import br.pucrs.constrsw.oauth.domain.InvalidLoginRequestException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_SOURCE = "OAuthAPI";

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler({InvalidLoginRequestException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Collections.emptyList());
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    ResponseEntity<ApiErrorResponse> handleInvalidAuthentication(RuntimeException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), Collections.emptyList());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), Collections.emptyList());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return error(HttpStatus.BAD_REQUEST, "Content-Type must be multipart/form-data", Collections.emptyList());
    }

    @ExceptionHandler(KeycloakCommunicationException.class)
    ResponseEntity<ApiErrorResponse> handleKeycloakFailure(KeycloakCommunicationException exception) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage(), Collections.emptyList());
    }

    @ExceptionHandler(KeycloakServiceException.class)
    ResponseEntity<ApiErrorResponse> handleKeycloakServiceException(KeycloakServiceException exception) {
        List<Map<String, Object>> stack = parseErrorBody(exception.getResponseBody());
        HttpStatus status = HttpStatus.resolve(exception.getStatus());
        if (status == null) status = HttpStatus.BAD_GATEWAY;
        return error(status, exception.getMessage(), stack);
    }

    @ExceptionHandler(RoleNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleRoleNotFound(RoleNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Collections.emptyList());
    }

    private List<Map<String, Object>> parseErrorBody(String body) {
        if (body == null || body.isBlank()) return Collections.emptyList();
        try {
            // Try to parse as JSON object or array; normalize into a list of maps
            if (body.trim().startsWith("{")) {
                Map<String, Object> map = objectMapper.readValue(body, new TypeReference<>() {});
                return List.of(map);
            } else if (body.trim().startsWith("[")) {
                return objectMapper.readValue(body, new TypeReference<>() {});
            }
        } catch (Exception ignored) {
        }
        return List.of(Map.of("body", body));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationFailure(MethodArgumentNotValidException exception) {
        String description = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, description, Collections.emptyList());
    }

    @ExceptionHandler(UserManagementNotImplementedException.class)
    ResponseEntity<ApiErrorResponse> handleUserManagementNotImplemented(
            UserManagementNotImplementedException exception) {
        return error(HttpStatus.NOT_IMPLEMENTED, exception.getMessage(), Collections.emptyList());
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String description, List<Map<String, Object>> stack) {
        ApiErrorResponse body = new ApiErrorResponse(
                String.valueOf(status.value()),
                description,
                ERROR_SOURCE,
                stack);
        return ResponseEntity.status(status).body(body);
    }
}
