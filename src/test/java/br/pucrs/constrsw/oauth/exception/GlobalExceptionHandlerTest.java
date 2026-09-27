package br.pucrs.constrsw.oauth.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Deveria formatar KeycloakException com error_code, error_description, error_source e error_stack")
    void testHandleKeycloakException() {
        KeycloakException ex = new KeycloakException("409", "Username já existente", HttpStatus.CONFLICT);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleKeycloakException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("409", response.getBody().getErrorCode());
        assertEquals("Username já existente", response.getBody().getErrorDescription());
        assertEquals("OAuthAPI", response.getBody().getErrorSource());
        assertNotNull(response.getBody().getErrorStack());
        assertFalse(response.getBody().getErrorStack().isEmpty());
    }

    @Test
    void invalidBearerKeepsTokenErrorDescription() {
        KeycloakException ex = new KeycloakException("401", "Access token inválido", HttpStatus.UNAUTHORIZED);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleKeycloakException(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Access token inválido", response.getBody().getErrorDescription());
        assertEquals(1, response.getBody().getErrorStack().size());
        assertEquals("OAuthAPI", response.getBody().getErrorStack().get(0).getErrorSource());
    }

    @Test
    @DisplayName("Deveria formatar HttpClientErrorException.Conflict com código 409")
    void testHandleHttpClientErrorExceptionConflict() {
        HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleHttpClientErrorException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("409", response.getBody().getErrorCode());
        assertEquals("Username já existente", response.getBody().getErrorDescription());
        assertEquals("OAuthAPI", response.getBody().getErrorSource());
    }

    @Test
    @DisplayName("Deveria formatar IllegalArgumentException como código 400")
    void testHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Parâmetro inválido");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgumentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("400", response.getBody().getErrorCode());
        assertEquals("Parâmetro inválido", response.getBody().getErrorDescription());
        assertEquals("OAuthAPI", response.getBody().getErrorSource());
    }

    @Test
    @DisplayName("Deveria formatar exceção genérica como código 500")
    void testHandleGenericException() {
        RuntimeException ex = new RuntimeException("Erro inesperado no sistema");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("500", response.getBody().getErrorCode());
        assertEquals("Erro inesperado no sistema", response.getBody().getErrorDescription());
        assertEquals("OAuthAPI", response.getBody().getErrorSource());
    }
}
