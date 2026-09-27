package br.pucrs.constrsw.oauth.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(KeycloakException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakException(KeycloakException ex) {
        log.error("KeycloakException [{}]: {}", ex.getErrorCode(), ex.getMessage());
        String code = String.valueOf(ex.getStatus().value());
        String desc = ex.getMessage();

        if (ex.getStatus() == HttpStatus.NOT_FOUND) {
            desc = "Objeto não localizado";
        } else if (ex.getStatus() == HttpStatus.CONFLICT) {
            if ("ROLE_ALREADY_EXISTS".equals(ex.getErrorCode())) {
                desc = "Objeto já existente";
            } else {
                desc = "Username já existente";
            }
        } else if (ex.getStatus() == HttpStatus.FORBIDDEN) {
            desc = "Access token não concede permissão para acessar esse endpoint ou objeto";
        }

        List<ErrorItem> stack = new ArrayList<>();
        if (ex.getCause() instanceof HttpClientErrorException
                || ex.getCause() instanceof HttpServerErrorException) {
            stack.add(new ErrorItem(code, ex.getCause().getMessage(), "Keycloak"));
        }
        stack.add(new ErrorItem(code, desc, "OAuthAPI"));

        ErrorResponse errorResponse = new ErrorResponse(code, desc, "OAuthAPI", stack);
        return ResponseEntity.status(ex.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("Erro de validação de argumentos: {}", validationErrors);
        List<ErrorItem> stack = new ArrayList<>();
        stack.add(new ErrorItem("400", validationErrors, "OAuthAPI"));

        ErrorResponse errorResponse = new ErrorResponse("400", validationErrors, "OAuthAPI", stack);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleHttpClientErrorException(HttpClientErrorException ex) {
        log.error("HttpClientErrorException: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
        String code = String.valueOf(ex.getStatusCode().value());
        String description = ex.getStatusText() != null ? ex.getStatusText() : ex.getMessage();

        if (ex.getStatusCode() == HttpStatus.CONFLICT) {
            description = "Username já existente";
        } else if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            description = "Objeto não localizado";
        } else if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            description = "Access token inválido";
        } else if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
            description = "Access token não concede permissão para acessar esse endpoint ou objeto";
        }

        List<ErrorItem> stack = new ArrayList<>();
        String kcDetails = ex.getResponseBodyAsString().isBlank() ? ex.getMessage() : ex.getResponseBodyAsString();
        stack.add(new ErrorItem(code, kcDetails, "Keycloak"));
        stack.add(new ErrorItem(code, description, "OAuthAPI"));

        ErrorResponse errorResponse = new ErrorResponse(code, description, "OAuthAPI", stack);
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleHttpServerErrorException(HttpServerErrorException ex) {
        log.error("HttpServerErrorException do Keycloak: {}", ex.getMessage());
        String code = String.valueOf(ex.getStatusCode().value());
        String desc = "Erro interno no provedor de autenticação Keycloak";

        List<ErrorItem> stack = new ArrayList<>();
        stack.add(new ErrorItem(code, ex.getMessage(), "Keycloak"));
        stack.add(new ErrorItem(code, desc, "OAuthAPI"));

        ErrorResponse errorResponse = new ErrorResponse(code, desc, "OAuthAPI", stack);
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        List<ErrorItem> stack = new ArrayList<>();
        stack.add(new ErrorItem("400", ex.getMessage(), "OAuthAPI"));

        ErrorResponse errorResponse = new ErrorResponse("400", ex.getMessage(), "OAuthAPI", stack);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception ex) {
        String description = "Estrutura da chamada inválida";
        log.warn("Requisição inválida: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("400", description));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Erro interno não tratado: {}", ex.getMessage(), ex);
        String desc = ex.getMessage() != null ? ex.getMessage() : "Ocorreu um erro interno inesperado";

        List<ErrorItem> stack = new ArrayList<>();
        stack.add(new ErrorItem("500", desc, "OAuthAPI"));

        ErrorResponse errorResponse = new ErrorResponse("500", desc, "OAuthAPI", stack);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
